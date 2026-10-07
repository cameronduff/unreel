package org.unreel.android.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.robolectric.annotation.Config
import org.unreel.android.data.FilterPreferencesRepository
import org.unreel.android.data.ReelsInterceptDao
import org.unreel.android.data.ReelsInterceptEntity
import org.unreel.android.ui.dashboard.DashboardViewModel

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DashboardViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dataStoreScope = CoroutineScope(testDispatcher + Job())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var preferencesRepository: FilterPreferencesRepository
    private lateinit var fakeDao: FakeReelsInterceptDao
    private lateinit var viewModel: DashboardViewModel

    private var simulatedTimeMs = 1_000_000L

    class FakeReelsInterceptDao : ReelsInterceptDao {
        val countFlow = MutableStateFlow(7)

        override suspend fun insertIntercept(entity: ReelsInterceptEntity): Long = 1L
        override fun getCountSince(startEpochMs: Long): Flow<Int> = countFlow
        override fun getTotalCount(): Flow<Int> = countFlow
        override fun getAllIntercepts(): Flow<List<ReelsInterceptEntity>> = MutableStateFlow(emptyList())
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val testFile = tempFolder.newFile("test_dashboard_prefs.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { testFile }
        )
        preferencesRepository = FilterPreferencesRepository(dataStore)
        fakeDao = FakeReelsInterceptDao()

        viewModel = DashboardViewModel(
            reelsInterceptDao = fakeDao,
            filterPreferencesRepository = preferencesRepository,
            currentTimeProvider = { simulatedTimeMs }
        )
    }

    @After
    fun tearDown() {
        viewModel.onCleared()
        Dispatchers.resetMain()
        dataStoreScope.cancel()
    }

    @Test
    fun testInitialStateCollectsDaoCountAndDefaults() = runTest(testDispatcher) {
        val state = viewModel.uiState.first()
        assertEquals(7, state.blockedTodayCount)
        assertEquals((7 * 45) / 60, state.estimatedMinutesSaved)
        assertTrue(state.isReelsFilterEnabled)
        assertFalse(state.isPaused)
    }

    @Test
    fun testToggleFilterEnabled() = runTest(testDispatcher) {
        viewModel.setReelsFilterEnabled(false).join()
        assertFalse(preferencesRepository.isReelsFilterEnabled.first())

        viewModel.setReelsFilterEnabled(true).join()
        assertTrue(preferencesRepository.isReelsFilterEnabled.first())
    }

    @Test
    fun testPauseAndResumeFlow() = runTest(testDispatcher) {
        viewModel.pauseFor15Minutes().join()
        val pausedState = viewModel.uiState.first { it.isPaused }
        assertTrue(pausedState.isPaused)
        assertEquals(15 * 60L, pausedState.remainingPauseSeconds)

        viewModel.resumeNow().join()
        val resumedState = viewModel.uiState.first { !it.isPaused }
        assertFalse(resumedState.isPaused)
        assertEquals(0L, resumedState.remainingPauseSeconds)
    }

    @Test
    fun testCountdownTimerTicksDown() = runTest(testDispatcher) {
        viewModel.pauseFor15Minutes().join()
        val initialState = viewModel.uiState.first { it.isPaused }
        assertEquals(15 * 60L, initialState.remainingPauseSeconds)

        testScheduler.advanceTimeBy(1000)
        val stateAfter1Sec = viewModel.uiState.first { it.remainingPauseSeconds == 15 * 60L - 1 }
        assertEquals(15 * 60L - 1, stateAfter1Sec.remainingPauseSeconds)

        viewModel.resumeNow().join()
    }

    @Test
    fun testToggleAutoSnoozeEnabled() = runTest(testDispatcher) {
        val initialState = viewModel.uiState.first()
        assertTrue(initialState.isAutoSnoozeEnabled)

        viewModel.setAutoSnoozeEnabled(false).join()
        val disabledState = viewModel.uiState.first { !it.isAutoSnoozeEnabled }
        assertFalse(disabledState.isAutoSnoozeEnabled)
        assertFalse(preferencesRepository.isAutoSnoozeEnabled.first())

        viewModel.setAutoSnoozeEnabled(true).join()
        val enabledState = viewModel.uiState.first { it.isAutoSnoozeEnabled }
        assertTrue(enabledState.isAutoSnoozeEnabled)
        assertTrue(preferencesRepository.isAutoSnoozeEnabled.first())
    }
}
