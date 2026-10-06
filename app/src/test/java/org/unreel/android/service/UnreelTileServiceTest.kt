package org.unreel.android.service

import android.service.quicksettings.Tile
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.unreel.android.data.FilterPreferencesRepository

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UnreelTileServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dataStoreScope = CoroutineScope(testDispatcher + Job())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: FilterPreferencesRepository
    private lateinit var service: UnreelTileService
    private lateinit var fakeTileController: FakeTileController

    private var simulatedTimeMs = 1_000_000L

    class FakeTileController : TileController {
        override var state: Int = Tile.STATE_UNAVAILABLE
        override var label: CharSequence? = null
        override var subtitle: CharSequence? = null
        var updateTileCallCount: Int = 0

        override fun updateTile() {
            updateTileCallCount++
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val testFile = tempFolder.newFile("test_tile_prefs.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { testFile }
        )
        repository = FilterPreferencesRepository(dataStore)

        fakeTileController = FakeTileController()
        service = Robolectric.buildService(UnreelTileService::class.java).create().get().apply {
            repositoryProvider = { repository }
            currentTimeProvider = { simulatedTimeMs }
            tileController = fakeTileController
            serviceScope = CoroutineScope(testDispatcher + Job())
        }
    }

    @After
    fun tearDown() {
        service.onDestroy()
        Dispatchers.resetMain()
        dataStoreScope.cancel()
    }

    @Test
    fun testOnStartListeningWhenFilteringActiveSetsStateActive() = runTest(testDispatcher) {
        service.refreshTileState().join()

        assertEquals(Tile.STATE_ACTIVE, fakeTileController.state)
        assertEquals(UnreelTileService.SUBTITLE_ACTIVE, fakeTileController.subtitle)
        assertEquals(1, fakeTileController.updateTileCallCount)
    }

    @Test
    fun testOnStartListeningWhenPauseActiveSetsStateInactive() = runTest(testDispatcher) {
        repository.pauseFiltering(durationMinutes = 15, currentEpochMs = simulatedTimeMs)

        service.refreshTileState().join()

        assertEquals(Tile.STATE_INACTIVE, fakeTileController.state)
        assertEquals(UnreelTileService.SUBTITLE_PAUSED, fakeTileController.subtitle)
        assertEquals(1, fakeTileController.updateTileCallCount)
    }

    @Test
    fun testOnClickWhenActivePausesFilteringAndUpdatesTile() = runTest(testDispatcher) {
        // Initially active
        service.refreshTileState().join()
        assertEquals(Tile.STATE_ACTIVE, fakeTileController.state)

        // Click to pause
        service.performClick().join()

        val prefs = repository.filterPreferences.first()
        assertTrue(prefs.isPauseActive(simulatedTimeMs))
        assertEquals(Tile.STATE_INACTIVE, fakeTileController.state)
        assertEquals(UnreelTileService.SUBTITLE_PAUSED, fakeTileController.subtitle)
        assertTrue(fakeTileController.updateTileCallCount >= 2)
    }

    @Test
    fun testOnClickWhenPausedResumesFilteringAndUpdatesTile() = runTest(testDispatcher) {
        // Initially paused
        repository.pauseFiltering(durationMinutes = 15, currentEpochMs = simulatedTimeMs)
        service.refreshTileState().join()
        assertEquals(Tile.STATE_INACTIVE, fakeTileController.state)

        // Click to resume
        service.performClick().join()

        val prefs = repository.filterPreferences.first()
        assertFalse(prefs.isPauseActive(simulatedTimeMs))
        assertEquals(Tile.STATE_ACTIVE, fakeTileController.state)
        assertEquals(UnreelTileService.SUBTITLE_ACTIVE, fakeTileController.subtitle)
        assertTrue(fakeTileController.updateTileCallCount >= 2)
    }

    @Test
    fun testTileServiceCallbacksDoNotCrash() {
        service.onStartListening()
        service.onClick()
        service.onStopListening()
    }
}
