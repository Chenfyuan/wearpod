package com.sjtech.wearpod.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.sjtech.wearpod.data.model.DownloadSettings
import com.sjtech.wearpod.data.model.PlaybackMemory
import com.sjtech.wearpod.data.model.SleepTimer
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

data class WearPodPreferencesSnapshot(
    val playbackMemory: PlaybackMemory,
    val downloadSettings: DownloadSettings,
    val sleepTimer: SleepTimer,
)

class WearPodPreferencesStore(context: Context) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("wearpod_preferences") },
    )

    suspend fun read(): WearPodPreferencesSnapshot {
        val preferences = dataStore.data
            .catchReadErrors()
            .first()

        return WearPodPreferencesSnapshot(
            playbackMemory = PlaybackMemory(
                currentEpisodeId = preferences[CURRENT_EPISODE_ID]?.takeUnless { value -> value.isBlank() },
                lastEpisodeId = preferences[LAST_EPISODE_ID]?.takeUnless { value -> value.isBlank() },
                positionMs = preferences[PLAYBACK_POSITION_MS] ?: 0L,
                durationMs = preferences[PLAYBACK_DURATION_MS] ?: 0L,
                speed = preferences[PLAYBACK_SPEED] ?: 1.0f,
                updatedAtEpochMillis = preferences[PLAYBACK_UPDATED_AT] ?: 0L,
            ),
            downloadSettings = DownloadSettings(
                wifiOnly = preferences[WIFI_ONLY] ?: true,
                autoDownloadLatestCount = (preferences[AUTO_DOWNLOAD_LATEST_COUNT] ?: 0).coerceIn(0, 3),
                backgroundAutoDownloadEnabled = preferences[BACKGROUND_AUTO_DOWNLOAD_ENABLED] ?: true,
                backgroundRefreshEnabled = preferences[BACKGROUND_REFRESH_ENABLED] ?: true,
                backgroundRefreshIntervalHours = (preferences[BACKGROUND_REFRESH_INTERVAL_HOURS] ?: 6).coerceIn(6, 24),
                autoDeletePlayedDownloads = preferences[AUTO_DELETE_PLAYED_DOWNLOADS] ?: false,
            ),
            sleepTimer = SleepTimer(
                endsAtEpochMillis = preferences[SLEEP_TIMER_ENDS_AT],
                presetMinutes = preferences[SLEEP_TIMER_PRESET_MINUTES],
            ),
        )
    }

    suspend fun write(snapshot: WearPodPreferencesSnapshot) {
        dataStore.edit { preferences ->
            snapshot.playbackMemory.currentEpisodeId?.let {
                preferences[CURRENT_EPISODE_ID] = it
            } ?: preferences.remove(CURRENT_EPISODE_ID)

            snapshot.playbackMemory.lastEpisodeId?.let {
                preferences[LAST_EPISODE_ID] = it
            } ?: preferences.remove(LAST_EPISODE_ID)
            preferences[PLAYBACK_POSITION_MS] = snapshot.playbackMemory.positionMs
            preferences[PLAYBACK_DURATION_MS] = snapshot.playbackMemory.durationMs
            preferences[PLAYBACK_SPEED] = snapshot.playbackMemory.speed
            preferences[PLAYBACK_UPDATED_AT] = snapshot.playbackMemory.updatedAtEpochMillis
            preferences[WIFI_ONLY] = snapshot.downloadSettings.wifiOnly
            preferences[AUTO_DOWNLOAD_LATEST_COUNT] = snapshot.downloadSettings.autoDownloadLatestCount
            preferences[BACKGROUND_AUTO_DOWNLOAD_ENABLED] = snapshot.downloadSettings.backgroundAutoDownloadEnabled
            preferences[BACKGROUND_REFRESH_ENABLED] = snapshot.downloadSettings.backgroundRefreshEnabled
            preferences[BACKGROUND_REFRESH_INTERVAL_HOURS] = snapshot.downloadSettings.backgroundRefreshIntervalHours
            preferences[AUTO_DELETE_PLAYED_DOWNLOADS] = snapshot.downloadSettings.autoDeletePlayedDownloads

            snapshot.sleepTimer.endsAtEpochMillis?.let {
                preferences[SLEEP_TIMER_ENDS_AT] = it
            } ?: preferences.remove(SLEEP_TIMER_ENDS_AT)

            snapshot.sleepTimer.presetMinutes?.let {
                preferences[SLEEP_TIMER_PRESET_MINUTES] = it
            } ?: preferences.remove(SLEEP_TIMER_PRESET_MINUTES)

            preferences[MIGRATION_COMPLETE] = true
        }
    }

    suspend fun isMigrationComplete(): Boolean =
        dataStore.data.catchReadErrors().first()[MIGRATION_COMPLETE] ?: false

    suspend fun markMigrationComplete() {
        dataStore.edit { preferences ->
            preferences[MIGRATION_COMPLETE] = true
        }
    }
}

private val CURRENT_EPISODE_ID = stringPreferencesKey("current_episode_id")
private val LAST_EPISODE_ID = stringPreferencesKey("last_episode_id")
private val PLAYBACK_POSITION_MS = longPreferencesKey("playback_position_ms")
private val PLAYBACK_DURATION_MS = longPreferencesKey("playback_duration_ms")
private val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
private val PLAYBACK_UPDATED_AT = longPreferencesKey("playback_updated_at")
private val WIFI_ONLY = booleanPreferencesKey("wifi_only")
private val AUTO_DOWNLOAD_LATEST_COUNT = intPreferencesKey("auto_download_latest_count")
private val BACKGROUND_AUTO_DOWNLOAD_ENABLED = booleanPreferencesKey("background_auto_download_enabled")
private val BACKGROUND_REFRESH_ENABLED = booleanPreferencesKey("background_refresh_enabled")
private val BACKGROUND_REFRESH_INTERVAL_HOURS = intPreferencesKey("background_refresh_interval_hours")
private val AUTO_DELETE_PLAYED_DOWNLOADS = booleanPreferencesKey("auto_delete_played_downloads")
private val SLEEP_TIMER_ENDS_AT = longPreferencesKey("sleep_timer_ends_at")
private val SLEEP_TIMER_PRESET_MINUTES = intPreferencesKey("sleep_timer_preset_minutes")
private val MIGRATION_COMPLETE = booleanPreferencesKey("migration_complete")

private fun Flow<Preferences>.catchReadErrors(): Flow<Preferences> =
    catch { throwable: Throwable ->
        if (throwable is IOException) {
            emit(emptyPreferences())
        } else {
            throw throwable
        }
    }
