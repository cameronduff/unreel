package org.unreel.android.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.unreel.android.data.FilterPreferencesRepository

interface TileController {
    var state: Int
    var label: CharSequence?
    var subtitle: CharSequence?
    fun updateTile()
}

class SystemTileController(private val tile: Tile) : TileController {
    override var state: Int
        get() = tile.state
        set(value) {
            tile.state = value
        }

    override var label: CharSequence?
        get() = tile.label
        set(value) {
            tile.label = value
        }

    override var subtitle: CharSequence?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle else null
        set(value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = value
            }
        }

    override fun updateTile() {
        tile.updateTile()
    }
}

class UnreelTileService : TileService() {

    internal var repositoryProvider: (() -> FilterPreferencesRepository)? = null
    internal var currentTimeProvider: () -> Long = { System.currentTimeMillis() }
    internal var tileController: TileController? = null
    internal var serviceScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun getController(): TileController? {
        return tileController ?: qsTile?.let { SystemTileController(it) }
    }

    internal fun updateTileState(isPauseActive: Boolean) {
        val controller = getController() ?: return
        if (isPauseActive) {
            controller.state = Tile.STATE_INACTIVE
            controller.subtitle = SUBTITLE_PAUSED
        } else {
            controller.state = Tile.STATE_ACTIVE
            controller.subtitle = SUBTITLE_ACTIVE
        }
        controller.updateTile()
    }

    fun refreshTileState(): Job = serviceScope.launch {
        val repo = repositoryProvider?.invoke() ?: FilterPreferencesRepository.getInstance(this@UnreelTileService)
        val prefs = repo.filterPreferences.first()
        updateTileState(prefs.isPauseActive(currentTimeProvider()))
    }

    fun performClick(): Job = serviceScope.launch {
        val repo = repositoryProvider?.invoke() ?: FilterPreferencesRepository.getInstance(this@UnreelTileService)
        val prefs = repo.filterPreferences.first()
        val now = currentTimeProvider()
        val isPaused = prefs.isPauseActive(now)
        if (isPaused) {
            repo.resumeFiltering()
            updateTileState(false)
        } else {
            repo.pauseFiltering(15, now)
            updateTileState(true)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTileState()
    }

    override fun onClick() {
        super.onClick()
        performClick()
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
        } catch (_: Throwable) {
            // Workaround for upstream Robolectric ShadowTileService ClassCastException
        }
        serviceScope.cancel()
    }

    companion object {
        const val SUBTITLE_ACTIVE = "Blocking Reels"
        const val SUBTITLE_PAUSED = "Paused (15m)"
    }
}
