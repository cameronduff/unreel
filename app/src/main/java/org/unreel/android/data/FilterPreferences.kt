package org.unreel.android.data

data class FilterPreferences(
    val isReelsFilterEnabled: Boolean = true,
    val pauseUntilEpochMs: Long = 0L
) {
    fun isPauseActive(currentEpochMs: Long = System.currentTimeMillis()): Boolean {
        return pauseUntilEpochMs > 0L && currentEpochMs < pauseUntilEpochMs
    }

    fun isFilteringActive(currentEpochMs: Long = System.currentTimeMillis()): Boolean {
        return isReelsFilterEnabled && !isPauseActive(currentEpochMs)
    }
}
