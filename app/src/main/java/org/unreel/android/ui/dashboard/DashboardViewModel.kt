package org.unreel.android.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.unreel.android.data.FilterPreferencesRepository
import org.unreel.android.data.ReelsInterceptDao
import java.util.Calendar

data class DashboardUiState(
    val blockedTodayCount: Int = 0,
    val estimatedMinutesSaved: Int = 0,
    val isReelsFilterEnabled: Boolean = true,
    val pauseUntilEpochMs: Long = 0L,
    val remainingPauseSeconds: Long = 0L,
    val isAutoSnoozeEnabled: Boolean = true,
    val lastAutoSnoozeEpochMs: Long = 0L
) {
    val isPaused: Boolean get() = remainingPauseSeconds > 0L
}

class DashboardViewModel(
    private val reelsInterceptDao: ReelsInterceptDao,
    private val filterPreferencesRepository: FilterPreferencesRepository,
    private val currentTimeProvider: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _remainingPauseSeconds = MutableStateFlow(0L)
    private var pauseTimerJob: Job? = null

    private fun getStartOfDayEpochMs(): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = currentTimeProvider()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        reelsInterceptDao.getCountSince(getStartOfDayEpochMs()),
        filterPreferencesRepository.filterPreferences,
        _remainingPauseSeconds
    ) { count, prefs, remainingSec ->
        DashboardUiState(
            blockedTodayCount = count,
            estimatedMinutesSaved = (count * 45) / 60,
            isReelsFilterEnabled = prefs.isReelsFilterEnabled,
            pauseUntilEpochMs = prefs.pauseUntilEpochMs,
            remainingPauseSeconds = remainingSec,
            isAutoSnoozeEnabled = prefs.isAutoSnoozeEnabled,
            lastAutoSnoozeEpochMs = prefs.lastAutoSnoozeEpochMs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DashboardUiState()
    )

    fun setReelsFilterEnabled(enabled: Boolean): Job = viewModelScope.launch {
        filterPreferencesRepository.setFilterEnabled(enabled)
    }

    fun setAutoSnoozeEnabled(enabled: Boolean): Job = viewModelScope.launch {
        filterPreferencesRepository.setAutoSnoozeEnabled(enabled)
    }

    fun resetAutoSnoozeCooldown(): Job = viewModelScope.launch {
        filterPreferencesRepository.resetAutoSnoozeCooldown()
    }

    private fun startCountdown(totalSeconds: Long) {
        pauseTimerJob?.cancel()
        pauseTimerJob = viewModelScope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                _remainingPauseSeconds.value = remaining
                delay(1000)
                remaining--
            }
            _remainingPauseSeconds.value = 0L
            filterPreferencesRepository.resumeFiltering()
        }
    }

    fun pauseFor15Minutes(): Job = viewModelScope.launch {
        val now = currentTimeProvider()
        filterPreferencesRepository.pauseFiltering(15, now)
        startCountdown(15 * 60L)
    }

    fun resumeNow(): Job = viewModelScope.launch {
        pauseTimerJob?.cancel()
        pauseTimerJob = null
        filterPreferencesRepository.resumeFiltering()
        _remainingPauseSeconds.value = 0L
    }

    public override fun onCleared() {
        super.onCleared()
        pauseTimerJob?.cancel()
        pauseTimerJob = null
    }
}
