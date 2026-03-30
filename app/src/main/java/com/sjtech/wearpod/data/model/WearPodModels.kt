package com.sjtech.wearpod.data.model

enum class DownloadState {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED,
}

data class Subscription(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val feedUrl: String,
    val artworkUrl: String?,
    val importedAtEpochMillis: Long,
    val refreshedAtEpochMillis: Long,
    val lastRefreshError: String? = null,
)

data class Episode(
    val id: String,
    val subscriptionId: String,
    val guid: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val artworkUrl: String?,
    val publishedAtEpochMillis: Long?,
    val durationSeconds: Int?,
    val sizeBytes: Long?,
    val downloadState: DownloadState = DownloadState.NOT_DOWNLOADED,
    val downloadedFilePath: String? = null,
    val downloadedBytes: Long = 0L,
    val lastPlayedPositionMs: Long = 0L,
    val lastPlayedAtEpochMillis: Long? = null,
    val isCompleted: Boolean = false,
)

data class PlaybackMemory(
    val currentEpisodeId: String? = null,
    val lastEpisodeId: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val updatedAtEpochMillis: Long = 0L,
)

data class SleepTimer(
    val endsAtEpochMillis: Long? = null,
    val presetMinutes: Int? = null,
)

data class DownloadSettings(
    val wifiOnly: Boolean = true,
    val autoDownloadLatestCount: Int = 0,
    val backgroundAutoDownloadEnabled: Boolean = true,
    val backgroundRefreshEnabled: Boolean = true,
    val backgroundRefreshIntervalHours: Int = 6,
    val autoDeletePlayedDownloads: Boolean = false,
)

data class AppSnapshot(
    val subscriptions: List<Subscription>,
    val episodes: List<Episode>,
    val favoriteSubscriptionIds: Set<String>,
    val playbackMemory: PlaybackMemory,
    val downloadSettings: DownloadSettings = DownloadSettings(),
    val sleepTimer: SleepTimer = SleepTimer(),
) {
    companion object {
        fun empty(): AppSnapshot = AppSnapshot(
            subscriptions = emptyList(),
            episodes = emptyList(),
            favoriteSubscriptionIds = emptySet(),
            playbackMemory = PlaybackMemory(),
            downloadSettings = DownloadSettings(),
            sleepTimer = SleepTimer(),
        )
    }
}
