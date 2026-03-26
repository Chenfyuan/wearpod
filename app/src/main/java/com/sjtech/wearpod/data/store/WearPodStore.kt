package com.sjtech.wearpod.data.store

import android.content.Context
import com.sjtech.wearpod.data.model.AppSnapshot
import com.sjtech.wearpod.data.model.DownloadSettings
import com.sjtech.wearpod.data.model.DownloadState
import com.sjtech.wearpod.data.model.Episode
import com.sjtech.wearpod.data.model.PlaybackMemory
import com.sjtech.wearpod.data.model.SleepTimer
import com.sjtech.wearpod.data.model.Subscription
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class WearPodStore(context: Context) {
    private val stateFile = File(context.filesDir, "wearpod_state.json")

    fun read(): AppSnapshot {
        if (!stateFile.exists()) return AppSnapshot.empty()
        return runCatching {
            fromJson(JSONObject(stateFile.readText()))
        }.getOrElse {
            AppSnapshot.empty()
        }
    }

    suspend fun write(snapshot: AppSnapshot) {
        withContext(Dispatchers.IO) {
            stateFile.parentFile?.mkdirs()
            stateFile.writeText(toJson(snapshot).toString())
        }
    }

    private fun toJson(snapshot: AppSnapshot): JSONObject = JSONObject().apply {
        put("subscriptions", JSONArray().apply {
            snapshot.subscriptions.forEach { subscription ->
                put(JSONObject().apply {
                    put("id", subscription.id)
                    put("title", subscription.title)
                    put("author", subscription.author)
                    put("description", subscription.description)
                    put("feedUrl", subscription.feedUrl)
                    put("artworkUrl", subscription.artworkUrl)
                    put("importedAtEpochMillis", subscription.importedAtEpochMillis)
                    put("refreshedAtEpochMillis", subscription.refreshedAtEpochMillis)
                    put("lastRefreshError", subscription.lastRefreshError)
                })
            }
        })
        put("episodes", JSONArray().apply {
            snapshot.episodes.forEach { episode ->
                put(JSONObject().apply {
                    put("id", episode.id)
                    put("subscriptionId", episode.subscriptionId)
                    put("guid", episode.guid)
                    put("title", episode.title)
                    put("description", episode.description)
                    put("audioUrl", episode.audioUrl)
                    put("artworkUrl", episode.artworkUrl)
                    put("publishedAtEpochMillis", episode.publishedAtEpochMillis)
                    put("durationSeconds", episode.durationSeconds)
                    put("sizeBytes", episode.sizeBytes)
                    put("downloadState", episode.downloadState.name)
                    put("downloadedFilePath", episode.downloadedFilePath)
                    put("downloadedBytes", episode.downloadedBytes)
                    put("lastPlayedPositionMs", episode.lastPlayedPositionMs)
                    put("lastPlayedAtEpochMillis", episode.lastPlayedAtEpochMillis)
                    put("isCompleted", episode.isCompleted)
                })
            }
        })
        put("favoriteSubscriptionIds", JSONArray(snapshot.favoriteSubscriptionIds.toList()))
        put("playbackMemory", JSONObject().apply {
            put("currentEpisodeId", snapshot.playbackMemory.currentEpisodeId)
            put("lastEpisodeId", snapshot.playbackMemory.lastEpisodeId)
            put("positionMs", snapshot.playbackMemory.positionMs)
            put("durationMs", snapshot.playbackMemory.durationMs)
            put("speed", snapshot.playbackMemory.speed)
            put("updatedAtEpochMillis", snapshot.playbackMemory.updatedAtEpochMillis)
        })
        put("downloadSettings", JSONObject().apply {
            put("wifiOnly", snapshot.downloadSettings.wifiOnly)
            put("autoDownloadLatestCount", snapshot.downloadSettings.autoDownloadLatestCount)
            put("backgroundAutoDownloadEnabled", snapshot.downloadSettings.backgroundAutoDownloadEnabled)
            put("backgroundRefreshEnabled", snapshot.downloadSettings.backgroundRefreshEnabled)
            put("backgroundRefreshIntervalHours", snapshot.downloadSettings.backgroundRefreshIntervalHours)
        })
        put("sleepTimer", JSONObject().apply {
            put("endsAtEpochMillis", snapshot.sleepTimer.endsAtEpochMillis)
            put("presetMinutes", snapshot.sleepTimer.presetMinutes)
        })
    }

    private fun fromJson(json: JSONObject): AppSnapshot {
        val subscriptions = buildList {
            val array = json.optJSONArray("subscriptions") ?: JSONArray()
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                add(
                    Subscription(
                        id = item.getString("id"),
                        title = item.optString("title"),
                        author = item.optString("author"),
                        description = item.optString("description"),
                        feedUrl = item.optString("feedUrl"),
                        artworkUrl = item.optNullableString("artworkUrl"),
                        importedAtEpochMillis = item.optLong("importedAtEpochMillis"),
                        refreshedAtEpochMillis = item.optLong("refreshedAtEpochMillis"),
                        lastRefreshError = item.optNullableString("lastRefreshError"),
                    ),
                )
            }
        }

        val episodes = buildList {
            val array = json.optJSONArray("episodes") ?: JSONArray()
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                add(
                    Episode(
                        id = item.getString("id"),
                        subscriptionId = item.getString("subscriptionId"),
                        guid = item.optString("guid"),
                        title = item.optString("title"),
                        description = item.optString("description"),
                        audioUrl = item.optString("audioUrl"),
                        artworkUrl = item.optNullableString("artworkUrl"),
                        publishedAtEpochMillis = item.optNullableLong("publishedAtEpochMillis"),
                        durationSeconds = item.optNullableInt("durationSeconds"),
                        sizeBytes = item.optNullableLong("sizeBytes"),
                        downloadState = item.optString("downloadState")
                            .takeIf(String::isNotBlank)
                            ?.let(DownloadState::valueOf)
                            ?: DownloadState.NOT_DOWNLOADED,
                        downloadedFilePath = item.optNullableString("downloadedFilePath"),
                        downloadedBytes = item.optLong("downloadedBytes"),
                        lastPlayedPositionMs = item.optLong("lastPlayedPositionMs"),
                        lastPlayedAtEpochMillis = item.optNullableLong("lastPlayedAtEpochMillis"),
                        isCompleted = item.optBoolean("isCompleted"),
                    ),
                )
            }
        }

        val favoriteSubscriptionIds = buildSet {
            val array = json.optJSONArray("favoriteSubscriptionIds") ?: JSONArray()
            repeat(array.length()) { index -> add(array.getString(index)) }
        }

        val playbackJson = json.optJSONObject("playbackMemory") ?: JSONObject()
        val playbackMemory = PlaybackMemory(
            currentEpisodeId = playbackJson.optNullableString("currentEpisodeId"),
            lastEpisodeId = playbackJson.optNullableString("lastEpisodeId"),
            positionMs = playbackJson.optLong("positionMs"),
            durationMs = playbackJson.optLong("durationMs"),
            speed = playbackJson.optDouble("speed", 1.0).toFloat(),
            updatedAtEpochMillis = playbackJson.optLong("updatedAtEpochMillis"),
        )
        val downloadSettingsJson = json.optJSONObject("downloadSettings") ?: JSONObject()
        val downloadSettings = DownloadSettings(
            wifiOnly = downloadSettingsJson.optBoolean("wifiOnly", true),
            autoDownloadLatestCount = downloadSettingsJson.optInt("autoDownloadLatestCount", 0).coerceIn(0, 3),
            backgroundAutoDownloadEnabled = downloadSettingsJson.optBoolean("backgroundAutoDownloadEnabled", true),
            backgroundRefreshEnabled = downloadSettingsJson.optBoolean("backgroundRefreshEnabled", true),
            backgroundRefreshIntervalHours = downloadSettingsJson.optInt("backgroundRefreshIntervalHours", 6)
                .coerceIn(6, 24),
        )
        val sleepTimerJson = json.optJSONObject("sleepTimer") ?: JSONObject()
        val sleepTimer = SleepTimer(
            endsAtEpochMillis = sleepTimerJson.optNullableLong("endsAtEpochMillis"),
            presetMinutes = sleepTimerJson.optNullableInt("presetMinutes"),
        )

        return AppSnapshot(
            subscriptions = subscriptions,
            episodes = episodes,
            favoriteSubscriptionIds = favoriteSubscriptionIds,
            playbackMemory = playbackMemory,
            downloadSettings = downloadSettings,
            sleepTimer = sleepTimer,
        )
    }
}

private fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

private fun JSONObject.optNullableLong(name: String): Long? =
    if (!has(name) || isNull(name)) null else optLong(name)

private fun JSONObject.optNullableInt(name: String): Int? =
    if (!has(name) || isNull(name)) null else optInt(name)
