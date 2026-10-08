package org.unreel.android.data

data class FilterPreferences(
    val isReelsFilterEnabled: Boolean = true,
    val pauseUntilEpochMs: Long = 0L,
    val isAutoSnoozeEnabled: Boolean = true,
    val lastAutoSnoozeEpochMs: Long = 0L,
    val isFeedAdShieldEnabled: Boolean = true,
    val isStoryAdShieldEnabled: Boolean = true
) {
    fun isPauseActive(currentEpochMs: Long = System.currentTimeMillis()): Boolean {
        return pauseUntilEpochMs > 0L && currentEpochMs < pauseUntilEpochMs
    }

    fun isFilteringActive(currentEpochMs: Long = System.currentTimeMillis()): Boolean {
        return isReelsFilterEnabled && !isPauseActive(currentEpochMs)
    }
}
