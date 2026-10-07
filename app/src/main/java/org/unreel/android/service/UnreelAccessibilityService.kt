package org.unreel.android.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.unreel.android.data.FilterPreferences
import org.unreel.android.data.FilterPreferencesRepository
import org.unreel.android.data.ReelsInterceptDao
import org.unreel.android.data.ReelsInterceptEntity
import org.unreel.android.data.UnreelDatabase
import org.unreel.android.engine.DebouncedBackDispatcher
import org.unreel.android.engine.InstagramBottomNavDetector
import org.unreel.android.engine.InstagramClipsDetector
import org.unreel.android.engine.InstagramModalDetector
import org.unreel.android.overlay.TouchAbsorberOverlayService

class UnreelAccessibilityService : AccessibilityService() {

    internal var backDispatcher = DebouncedBackDispatcher(this)
    internal var onReelsIntercepted: (() -> Unit)? = null
    internal var rootNodeProvider: (() -> AccessibilityNodeInfo?)? = null
    internal var serviceScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    internal var daoProvider: (() -> ReelsInterceptDao)? = null
    internal var repositoryProvider: (() -> FilterPreferencesRepository)? = null
    internal var isFilterActiveProvider: (() -> Boolean)? = null
    internal var overlayController: ((show: Boolean, bounds: android.graphics.Rect?) -> Unit)? = null

    private var cachedPreferences: FilterPreferences = FilterPreferences()

    private fun updateOverlay(show: Boolean, bounds: android.graphics.Rect? = null) {
        if (overlayController != null) {
            overlayController?.invoke(show, bounds)
            return
        }
        if (show) {
            TouchAbsorberOverlayService.show(this, bounds)
        } else {
            TouchAbsorberOverlayService.hide(this)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Unreel Accessibility Service connected and ready")
        val repo = repositoryProvider?.invoke() ?: FilterPreferencesRepository.getInstance(this)
        serviceScope.launch {
            repo.filterPreferences.collect { prefs ->
                cachedPreferences = prefs
                Log.d(TAG, "Preferences updated: filterEnabled=${prefs.isReelsFilterEnabled}, pauseUntil=${prefs.pauseUntilEpochMs}")
            }
        }
    }

    internal fun isFilteringActive(): Boolean {
        return isFilterActiveProvider?.invoke()
            ?: cachedPreferences.isFilteringActive(System.currentTimeMillis())
    }

    internal fun resolveRootInActiveWindow(): AccessibilityNodeInfo? {
        return rootNodeProvider?.invoke() ?: rootInActiveWindow
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()

        // 1. Never detach or process events from our own overlay window or SystemUI
        if (pkg == null || pkg == "org.unreel.android" || pkg == "com.android.systemui") {
            return
        }

        // 2. Hide overlay when switching away from Instagram to another app
        if (pkg != TARGET_INSTAGRAM_PACKAGE) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                Log.d(TAG, "Navigated away from Instagram to $pkg, hiding overlay")
                updateOverlay(false)
            }
            return
        }

        if (!isFilteringActive()) {
            updateOverlay(false)
            return
        }

        val root = resolveRootInActiveWindow()
        val rootCompat = if (root != null) AccessibilityNodeInfoCompat.wrap(root) else null

        // 3. Maintain blackout touch-absorber overlay over the Reels tab button
        // Check if any modal dialog, daily limit prompt, or bottom sheet is active
        val isModalOpen = InstagramModalDetector.isModalOrDialogPresent(rootCompat)
        val reelsBounds = if (!isModalOpen) InstagramBottomNavDetector.findReelsTabBounds(rootCompat) else null
        Log.d(TAG, "Instagram active in foreground: isModalOpen=$isModalOpen, reelsBounds=$reelsBounds")

        if (reelsBounds != null) {
            updateOverlay(true, reelsBounds)
        } else {
            updateOverlay(false)
        }

        if (rootCompat == null) return

        // 4. Intercept active Reels tab selection or fullscreen clips viewer
        val isClipsVisible = InstagramClipsDetector.isClipsContainerVisible(rootCompat)
        val isReelsTabSelected = InstagramBottomNavDetector.isReelsTabSelected(rootCompat)

        if (isClipsVisible || isReelsTabSelected) {
            val trigger = if (isClipsVisible) {
                ReelsInterceptEntity.TRIGGER_FULLSCREEN_CLIPS
            } else {
                ReelsInterceptEntity.TRIGGER_BOTTOM_NAV_TAB
            }
            Log.w(TAG, "REELS DETECTED ($trigger)! Suppressing via Back Action...")
            val dispatched = backDispatcher.dispatchBack()
            if (dispatched) {
                Log.i(TAG, "Successfully dispatched Back Action to eliminate Reels")
                onReelsIntercepted?.invoke()
                recordIntercept(trigger)
            }
        }
    }

    private fun recordIntercept(trigger: String) {
        serviceScope.launch {
            try {
                val dao = daoProvider?.invoke()
                    ?: UnreelDatabase.getInstance(this@UnreelAccessibilityService).reelsInterceptDao()
                dao.insertIntercept(
                    ReelsInterceptEntity(
                        timestampEpochMs = System.currentTimeMillis(),
                        triggerType = trigger
                    )
                )
                Log.d(TAG, "Recorded intercept telemetry: $trigger")
            } catch (e: Exception) {
                Log.e(TAG, "Error recording intercept telemetry", e)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Unreel Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        updateOverlay(false)
        serviceScope.cancel()
        Log.i(TAG, "Unreel Accessibility Service destroyed")
    }

    companion object {
        const val TAG = "UnreelService"
        const val TARGET_INSTAGRAM_PACKAGE = "com.instagram.android"
    }
}
