package org.unreel.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.filterPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "unreel_filter_preferences")

class FilterPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {
    val filterPreferences: Flow<FilterPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            FilterPreferences(
                isReelsFilterEnabled = preferences[KEY_REELS_FILTER_ENABLED] ?: true,
                pauseUntilEpochMs = preferences[KEY_PAUSE_UNTIL_EPOCH_MS] ?: 0L,
                isAutoSnoozeEnabled = preferences[KEY_AUTO_SNOOZE_ENABLED] ?: true,
                lastAutoSnoozeEpochMs = preferences[KEY_LAST_AUTO_SNOOZE_EPOCH_MS] ?: 0L,
                isFeedAdShieldEnabled = preferences[KEY_FEED_AD_SHIELD_ENABLED] ?: true,
                isStoryAdShieldEnabled = preferences[KEY_STORY_AD_SHIELD_ENABLED] ?: true
            )
        }

    val isReelsFilterEnabled: Flow<Boolean> = filterPreferences.map { it.isReelsFilterEnabled }
    val isAutoSnoozeEnabled: Flow<Boolean> = filterPreferences.map { it.isAutoSnoozeEnabled }
    val lastAutoSnoozeEpochMs: Flow<Long> = filterPreferences.map { it.lastAutoSnoozeEpochMs }
    val isFeedAdShieldEnabled: Flow<Boolean> = filterPreferences.map { it.isFeedAdShieldEnabled }
    val isStoryAdShieldEnabled: Flow<Boolean> = filterPreferences.map { it.isStoryAdShieldEnabled }

    suspend fun setFeedAdShieldEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_FEED_AD_SHIELD_ENABLED] = enabled
        }
    }

    suspend fun setStoryAdShieldEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_STORY_AD_SHIELD_ENABLED] = enabled
        }
    }

    suspend fun setFilterEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_REELS_FILTER_ENABLED] = enabled
        }
    }

    suspend fun setAutoSnoozeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_AUTO_SNOOZE_ENABLED] = enabled
        }
    }

    suspend fun recordAutoSnoozeTimestamp(epochMs: Long = System.currentTimeMillis()) {
        dataStore.edit { preferences ->
            preferences[KEY_LAST_AUTO_SNOOZE_EPOCH_MS] = epochMs
        }
    }

    suspend fun resetAutoSnoozeCooldown() {
        dataStore.edit { preferences ->
            preferences[KEY_LAST_AUTO_SNOOZE_EPOCH_MS] = 0L
        }
    }

    suspend fun pauseFiltering(durationMinutes: Int, currentEpochMs: Long = System.currentTimeMillis()) {
        val pauseUntil = currentEpochMs + (durationMinutes * 60 * 1000L)
        dataStore.edit { preferences ->
            preferences[KEY_PAUSE_UNTIL_EPOCH_MS] = pauseUntil
        }
    }

    suspend fun resumeFiltering() {
        dataStore.edit { preferences ->
            preferences[KEY_PAUSE_UNTIL_EPOCH_MS] = 0L
        }
    }

    companion object {
        val KEY_REELS_FILTER_ENABLED = booleanPreferencesKey("key_reels_filter_enabled")
        val KEY_PAUSE_UNTIL_EPOCH_MS = longPreferencesKey("key_pause_until_epoch_ms")
        val KEY_AUTO_SNOOZE_ENABLED = booleanPreferencesKey("key_auto_snooze_enabled")
        val KEY_LAST_AUTO_SNOOZE_EPOCH_MS = longPreferencesKey("key_last_auto_snooze_epoch_ms")
        val KEY_FEED_AD_SHIELD_ENABLED = booleanPreferencesKey("key_feed_ad_shield_enabled")
        val KEY_STORY_AD_SHIELD_ENABLED = booleanPreferencesKey("key_story_ad_shield_enabled")

        @Volatile
        private var instance: FilterPreferencesRepository? = null

        fun getInstance(context: Context): FilterPreferencesRepository {
            return instance ?: synchronized(this) {
                instance ?: FilterPreferencesRepository(context.applicationContext.filterPreferencesDataStore).also {
                    instance = it
                }
            }
        }
    }
}
