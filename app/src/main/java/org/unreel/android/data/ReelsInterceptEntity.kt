package org.unreel.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reels_intercepts")
data class ReelsInterceptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampEpochMs: Long,
    val triggerType: String = TRIGGER_FULLSCREEN_CLIPS
) {
    companion object {
        const val TRIGGER_FULLSCREEN_CLIPS = "clips_fullscreen"
        const val TRIGGER_BOTTOM_NAV_TAB = "reels_tab"
        const val TRIGGER_FEED_AUTO_SNOOZE = "feed_auto_snooze"
        const val TRIGGER_SETTINGS_AUTO_SNOOZE = "settings_auto_snooze"
        const val TRIGGER_FEED_AD_SHIELD = "feed_ad_shield"
        const val TRIGGER_STORY_AD_SHIELD = "story_ad_shield"
    }
}
