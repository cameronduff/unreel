package org.unreel.android.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FilterPreferencesRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dataStoreScope = CoroutineScope(testDispatcher + Job())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: FilterPreferencesRepository

    @Before
    fun setUp() {
        val testFile = tempFolder.newFile("test_filter_preferences.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { testFile }
        )
        repository = FilterPreferencesRepository(dataStore)
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
    }

    @Test
    fun testDefaultPreferences() = runTest(testDispatcher) {
        val prefs = repository.filterPreferences.first()
        assertTrue(prefs.isReelsFilterEnabled)
        assertEquals(0L, prefs.pauseUntilEpochMs)
        assertFalse(prefs.isPauseActive())
        assertTrue(prefs.isFilteringActive())
    }

    @Test
    fun testToggleFilterEnabled() = runTest(testDispatcher) {
        repository.setFilterEnabled(false)
        val prefs = repository.filterPreferences.first()
        assertFalse(prefs.isReelsFilterEnabled)
        assertFalse(prefs.isFilteringActive())

        repository.setFilterEnabled(true)
        assertTrue(repository.isReelsFilterEnabled.first())
    }

    @Test
    fun testPauseFilteringCalculatesTimestamp() = runTest(testDispatcher) {
        val baseTime = 100_000L
        repository.pauseFiltering(durationMinutes = 15, currentEpochMs = baseTime)

        val prefs = repository.filterPreferences.first()
        val expectedPauseUntil = baseTime + (15 * 60 * 1000L)
        assertEquals(expectedPauseUntil, prefs.pauseUntilEpochMs)

        // Test active during pause window
        assertTrue(prefs.isPauseActive(currentEpochMs = baseTime + 5000L))
        assertFalse(prefs.isFilteringActive(currentEpochMs = baseTime + 5000L))

        // Test expired after pause window
        assertFalse(prefs.isPauseActive(currentEpochMs = expectedPauseUntil + 1L))
        assertTrue(prefs.isFilteringActive(currentEpochMs = expectedPauseUntil + 1L))
    }

    @Test
    fun testResumeFilteringClearsPause() = runTest(testDispatcher) {
        val baseTime = 100_000L
        repository.pauseFiltering(durationMinutes = 15, currentEpochMs = baseTime)
        assertTrue(repository.filterPreferences.first().pauseUntilEpochMs > 0L)

        repository.resumeFiltering()
        val prefs = repository.filterPreferences.first()
        assertEquals(0L, prefs.pauseUntilEpochMs)
        assertFalse(prefs.isPauseActive(currentEpochMs = baseTime))
    }

    @Test
    fun testAutoSnoozeDefaultAndToggle() = runTest(testDispatcher) {
        val prefs = repository.filterPreferences.first()
        assertTrue(prefs.isAutoSnoozeEnabled)
        assertEquals(0L, prefs.lastAutoSnoozeEpochMs)

        repository.setAutoSnoozeEnabled(false)
        assertFalse(repository.filterPreferences.first().isAutoSnoozeEnabled)
        assertFalse(repository.isAutoSnoozeEnabled.first())

        repository.setAutoSnoozeEnabled(true)
        assertTrue(repository.filterPreferences.first().isAutoSnoozeEnabled)
        assertTrue(repository.isAutoSnoozeEnabled.first())
    }

    @Test
    fun testRecordAutoSnoozeTimestampAndReset() = runTest(testDispatcher) {
        val snoozeTime = 555_000L
        repository.recordAutoSnoozeTimestamp(snoozeTime)
        assertEquals(snoozeTime, repository.filterPreferences.first().lastAutoSnoozeEpochMs)
        assertEquals(snoozeTime, repository.lastAutoSnoozeEpochMs.first())

        repository.resetAutoSnoozeCooldown()
        assertEquals(0L, repository.filterPreferences.first().lastAutoSnoozeEpochMs)
    }
}
