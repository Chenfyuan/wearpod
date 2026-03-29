package com.sjtech.wearpod.data.repository

import android.content.Context
import com.sjtech.wearpod.data.model.AppSnapshot
import com.sjtech.wearpod.data.model.DownloadSettings
import com.sjtech.wearpod.data.model.DownloadState
import com.sjtech.wearpod.data.model.Episode
import com.sjtech.wearpod.data.model.ImportSuggestion
import com.sjtech.wearpod.data.model.PhoneImportPreview
import com.sjtech.wearpod.data.model.PhoneImportResult
import com.sjtech.wearpod.data.model.PhoneImportSession
import com.sjtech.wearpod.data.model.PhoneImportSessionSnapshot
import com.sjtech.wearpod.data.model.PlaybackMemory
import com.sjtech.wearpod.data.model.SleepTimer
import com.sjtech.wearpod.data.model.Subscription
import com.sjtech.wearpod.data.importing.ImportRelayClient
import com.sjtech.wearpod.data.rss.FeedNetworkClient
import com.sjtech.wearpod.data.rss.ParsedFeed
import com.sjtech.wearpod.data.rss.ParsedEpisode
import com.sjtech.wearpod.data.rss.PodcastFeedParser
import com.sjtech.wearpod.data.store.WearPodStore
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class WearPodRepository(
    private val appContext: Context,
    private val store: WearPodStore,
    private val parser: PodcastFeedParser,
    private val networkClient: FeedNetworkClient,
    private val importRelayClient: ImportRelayClient,
) {
    private val mutex = Mutex()
    private val mutableSnapshot = MutableStateFlow(store.read())

    val snapshot: StateFlow<AppSnapshot> = mutableSnapshot.asStateFlow()

    val importSuggestions = listOf(
        ImportSuggestion("铜镜", "https://feed.xyzfm.space/xpa79uvcn9lw"),
        ImportSuggestion("Vergecast", "https://feeds.megaphone.fm/vergecast"),
        ImportSuggestion("Planet Money", "https://feeds.npr.org/510289/podcast.xml"),
    )

    suspend fun ensureImportedFeed(rawUrl: String): Subscription? {
        val normalizedUrl = normalizeUrl(rawUrl)
        val existing = mutableSnapshot.value.subscriptions.firstOrNull { it.feedUrl == normalizedUrl }
        if (existing != null) return existing
        return runCatching { importFeed(normalizedUrl) }.getOrNull()
    }

    fun isPhoneImportAvailable(): Boolean = importRelayClient.isConfigured()

    suspend fun createPhoneImportSession(): PhoneImportSession = importRelayClient.createSession()

    suspend fun fetchPhoneImportSession(sessionId: String): PhoneImportSessionSnapshot =
        importRelayClient.fetchSession(sessionId)

    fun previewPhoneImport(
        feedUrls: List<String>,
        invalidCount: Int,
        duplicateCountWithinPayload: Int,
    ): PhoneImportPreview {
        val existingFeedUrls = mutableSnapshot.value.subscriptions
            .map { normalizeUrl(it.feedUrl) }
            .toMutableSet()
        val newFeedUrls = mutableListOf<String>()
        var duplicateCount = duplicateCountWithinPayload

        feedUrls.forEach { rawUrl ->
            val normalizedUrl = normalizeUrl(rawUrl)
            if (!existingFeedUrls.add(normalizedUrl)) {
                duplicateCount += 1
            } else {
                newFeedUrls += normalizedUrl
            }
        }

        return PhoneImportPreview(
            newFeedUrls = newFeedUrls,
            newCount = newFeedUrls.size,
            duplicateCount = duplicateCount,
            invalidCount = invalidCount,
        )
    }

    suspend fun importFeeds(feedUrls: List<String>): PhoneImportResult {
        val importedSubscriptions = mutableListOf<Subscription>()
        val failedUrls = mutableListOf<String>()
        var duplicateCount = 0
        val existingFeedUrls = mutableSnapshot.value.subscriptions
            .map { normalizeUrl(it.feedUrl) }
            .toMutableSet()

        feedUrls
            .map(::normalizeUrl)
            .distinct()
            .forEach { feedUrl ->
                if (!existingFeedUrls.add(feedUrl)) {
                    duplicateCount += 1
                    return@forEach
                }

                runCatching { importFeed(feedUrl) }
                    .onSuccess(importedSubscriptions::add)
                    .onFailure { failedUrls += feedUrl }
            }

        return PhoneImportResult(
            importedSubscriptions = importedSubscriptions,
            duplicateCount = duplicateCount,
            failedUrls = failedUrls,
        )
    }

    suspend fun importFeed(rawUrl: String): Subscription {
        val normalizedUrl = normalizeUrl(rawUrl)
        val parsedFeed = withContext(Dispatchers.IO) {
            networkClient.openStream(normalizedUrl).use { parser.parse(normalizedUrl, it) }
        }
        return upsertFeed(normalizedUrl, parsedFeed)
    }

    suspend fun refreshSubscription(subscriptionId: String) {
        val subscription = mutableSnapshot.value.subscriptions.firstOrNull { it.id == subscriptionId } ?: return
        try {
            val parsedFeed = withContext(Dispatchers.IO) {
                networkClient.openStream(subscription.feedUrl).use { parser.parse(subscription.feedUrl, it) }
            }
            upsertFeed(subscription.feedUrl, parsedFeed, preferredSubscriptionId = subscriptionId)
        } catch (throwable: Throwable) {
            markSubscriptionRefreshFailed(subscriptionId, throwable.message ?: "刷新失败")
            throw throwable
        }
    }

    suspend fun refreshAllSubscriptions() {
        mutableSnapshot.value.subscriptions.forEach { subscription ->
            runCatching { refreshSubscription(subscription.id) }
        }
    }

    suspend fun toggleFavorite(subscriptionId: String) {
        mutate { snapshot ->
            val updated = snapshot.favoriteSubscriptionIds.toMutableSet().apply {
                if (!add(subscriptionId)) remove(subscriptionId)
            }
            snapshot.copy(favoriteSubscriptionIds = updated)
        }
    }

    suspend fun updateDownloadSettings(transform: (DownloadSettings) -> DownloadSettings) {
        mutate { snapshot ->
            snapshot.copy(downloadSettings = transform(snapshot.downloadSettings))
        }
    }

    suspend fun setSleepTimer(endsAtEpochMillis: Long?, presetMinutes: Int?) {
        mutate { snapshot ->
            snapshot.copy(
                sleepTimer = SleepTimer(
                    endsAtEpochMillis = endsAtEpochMillis,
                    presetMinutes = presetMinutes,
                ),
            )
        }
    }

    suspend fun setEpisodeCompleted(episodeId: String, completed: Boolean) {
        updateEpisode(episodeId) { episode ->
            applyCompletedState(
                episode = episode,
                completed = completed,
                settings = mutableSnapshot.value.downloadSettings,
                now = System.currentTimeMillis(),
                resetPlaybackPosition = true,
            )
        }
    }

    suspend fun unsubscribe(subscriptionId: String) {
        mutate { snapshot ->
            val episodesToRemove = snapshot.episodes.filter { it.subscriptionId == subscriptionId }
            val removedEpisodeIds = episodesToRemove.mapTo(mutableSetOf()) { it.id }
            episodesToRemove.forEach { episode ->
                episode.downloadedFilePath?.let { path ->
                    runCatching { File(path).delete() }
                }
            }

            val currentEpisodeRemoved = snapshot.playbackMemory.currentEpisodeId in removedEpisodeIds
            val lastEpisodeRemoved = snapshot.playbackMemory.lastEpisodeId in removedEpisodeIds
            val updatedAt = if (currentEpisodeRemoved || lastEpisodeRemoved) {
                System.currentTimeMillis()
            } else {
                snapshot.playbackMemory.updatedAtEpochMillis
            }

            snapshot.copy(
                subscriptions = snapshot.subscriptions.filterNot { it.id == subscriptionId },
                episodes = snapshot.episodes.filterNot { it.subscriptionId == subscriptionId },
                favoriteSubscriptionIds = snapshot.favoriteSubscriptionIds - subscriptionId,
                playbackMemory = snapshot.playbackMemory.copy(
                    currentEpisodeId = if (currentEpisodeRemoved) null else snapshot.playbackMemory.currentEpisodeId,
                    lastEpisodeId = if (lastEpisodeRemoved) null else snapshot.playbackMemory.lastEpisodeId,
                    positionMs = if (currentEpisodeRemoved) 0L else snapshot.playbackMemory.positionMs,
                    durationMs = if (currentEpisodeRemoved) 0L else snapshot.playbackMemory.durationMs,
                    updatedAtEpochMillis = updatedAt,
                ),
            )
        }
    }

    suspend fun markEpisodeQueued(episodeId: String) {
        updateEpisode(episodeId) { it.copy(downloadState = DownloadState.QUEUED) }
    }

    suspend fun markEpisodeDownloading(episodeId: String, downloadedBytes: Long) {
        updateEpisode(episodeId) {
            it.copy(
                downloadState = DownloadState.DOWNLOADING,
                downloadedBytes = downloadedBytes,
            )
        }
    }

    suspend fun markEpisodeDownloaded(
        episodeId: String,
        localPath: String,
        downloadedBytes: Long,
    ) {
        updateEpisode(episodeId) {
            it.copy(
                downloadState = DownloadState.DOWNLOADED,
                downloadedFilePath = localPath,
                downloadedBytes = downloadedBytes,
            )
        }
    }

    suspend fun markEpisodeDownloadFailed(episodeId: String) {
        updateEpisode(episodeId) { it.copy(downloadState = DownloadState.FAILED) }
    }

    suspend fun resetEpisodeDownload(episodeId: String) {
        val episode = mutableSnapshot.value.episodes.firstOrNull { it.id == episodeId } ?: return
        episode.downloadedFilePath?.let { File(it).delete() }
        updateEpisode(episodeId) {
            it.copy(
                downloadState = DownloadState.NOT_DOWNLOADED,
                downloadedFilePath = null,
                downloadedBytes = 0L,
            )
        }
    }

    suspend fun deleteDownloadedEpisode(episodeId: String) {
        resetEpisodeDownload(episodeId)
    }

    suspend fun clearAllDownloads() {
        mutate { snapshot ->
            snapshot.episodes.forEach { episode ->
                episode.downloadedFilePath?.let { File(it).delete() }
            }
            snapshot.copy(
                episodes = snapshot.episodes.map { episode ->
                    episode.copy(
                        downloadState = DownloadState.NOT_DOWNLOADED,
                        downloadedFilePath = null,
                        downloadedBytes = 0L,
                    )
                },
            )
        }
    }

    suspend fun clearCompletedDownloads(): Int {
        var removedCount = 0
        mutate { snapshot ->
            val completedDownloadIds = snapshot.episodes
                .filter { it.isCompleted && it.downloadState == DownloadState.DOWNLOADED }
                .mapTo(mutableSetOf()) { it.id }
            removedCount = completedDownloadIds.size
            if (completedDownloadIds.isEmpty()) return@mutate snapshot

            snapshot.episodes
                .filter { it.id in completedDownloadIds }
                .forEach { episode ->
                    episode.downloadedFilePath?.let { File(it).delete() }
                }

            snapshot.copy(
                episodes = snapshot.episodes.map { episode ->
                    if (episode.id in completedDownloadIds) {
                        episode.copy(
                            downloadState = DownloadState.NOT_DOWNLOADED,
                            downloadedFilePath = null,
                            downloadedBytes = 0L,
                        )
                    } else {
                        episode
                    }
                },
            )
        }
        return removedCount
    }

    suspend fun clearDownloadsForSubscription(subscriptionId: String): Int {
        var removedCount = 0
        mutate { snapshot ->
            val targetIds = snapshot.episodes
                .filter {
                    it.subscriptionId == subscriptionId &&
                        it.downloadState == DownloadState.DOWNLOADED
                }
                .mapTo(mutableSetOf()) { it.id }
            removedCount = targetIds.size
            if (targetIds.isEmpty()) return@mutate snapshot

            snapshot.episodes
                .filter { it.id in targetIds }
                .forEach { episode ->
                    episode.downloadedFilePath?.let { File(it).delete() }
                }

            snapshot.copy(
                episodes = snapshot.episodes.map { episode ->
                    if (episode.id in targetIds) {
                        episode.copy(
                            downloadState = DownloadState.NOT_DOWNLOADED,
                            downloadedFilePath = null,
                            downloadedBytes = 0L,
                        )
                    } else {
                        episode
                    }
                },
            )
        }
        return removedCount
    }

    suspend fun updatePlayback(
        episodeId: String,
        positionMs: Long,
        durationMs: Long,
        speed: Float,
        isCompleted: Boolean,
    ) {
        mutate { snapshot ->
            val now = System.currentTimeMillis()
            val updatedEpisodes = snapshot.episodes.map { episode ->
                if (episode.id != episodeId) {
                    episode
                } else {
                    applyCompletedState(
                        episode = episode.copy(
                            lastPlayedPositionMs = if (isCompleted) 0L else positionMs,
                            lastPlayedAtEpochMillis = now,
                        ),
                        completed = isCompleted,
                        settings = snapshot.downloadSettings,
                        now = now,
                        resetPlaybackPosition = false,
                    )
                }
            }
            snapshot.copy(
                episodes = updatedEpisodes,
                playbackMemory = PlaybackMemory(
                    currentEpisodeId = episodeId,
                    lastEpisodeId = episodeId,
                    positionMs = if (isCompleted) 0L else positionMs,
                    durationMs = durationMs,
                    speed = speed,
                    updatedAtEpochMillis = now,
                ),
            )
        }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        mutate { snapshot ->
            snapshot.copy(playbackMemory = snapshot.playbackMemory.copy(speed = speed))
        }
    }

    fun subscription(subscriptionId: String): Subscription? =
        mutableSnapshot.value.subscriptions.firstOrNull { it.id == subscriptionId }

    fun episodesForSubscription(subscriptionId: String): List<Episode> =
        mutableSnapshot.value.episodes
            .filter { it.subscriptionId == subscriptionId }
            .sortedByDescending { it.publishedAtEpochMillis ?: 0L }

    fun autoDownloadCandidates(subscriptionId: String): List<Episode> {
        val settings = mutableSnapshot.value.downloadSettings
        if (!settings.backgroundAutoDownloadEnabled || settings.autoDownloadLatestCount <= 0) return emptyList()
        return episodesForSubscription(subscriptionId)
            .filter { episode ->
                episode.downloadState == DownloadState.NOT_DOWNLOADED ||
                    episode.downloadState == DownloadState.FAILED
            }
            .take(settings.autoDownloadLatestCount)
    }

    fun episode(episodeId: String): Episode? =
        mutableSnapshot.value.episodes.firstOrNull { it.id == episodeId }

    fun isEpisodeAvailableOffline(episodeId: String): Boolean {
        val episode = episode(episodeId) ?: return false
        val localPath = episode.downloadedFilePath ?: return false
        return episode.downloadState == DownloadState.DOWNLOADED && File(localPath).exists()
    }

    fun storageUsageBytes(): Long =
        mutableSnapshot.value.episodes.sumOf { episode ->
            when {
                episode.downloadState == DownloadState.DOWNLOADED && episode.downloadedFilePath != null -> {
                    File(episode.downloadedFilePath).takeIf(File::exists)?.length() ?: episode.downloadedBytes
                }

                else -> 0L
            }
        }

    fun storageAvailableBytes(): Long = appContext.filesDir.usableSpace.coerceAtLeast(0L)

    private suspend fun upsertFeed(
        feedUrl: String,
        parsedFeed: ParsedFeed,
        preferredSubscriptionId: String? = null,
    ): Subscription {
        lateinit var subscription: Subscription
        mutate { snapshot ->
            val existingSubscription = snapshot.subscriptions.firstOrNull {
                it.id == preferredSubscriptionId || it.feedUrl == feedUrl
            }
            val subscriptionId = existingSubscription?.id ?: stableId("sub:$feedUrl")
            val now = System.currentTimeMillis()
            subscription = Subscription(
                id = subscriptionId,
                title = parsedFeed.title,
                author = parsedFeed.author,
                description = parsedFeed.description,
                feedUrl = feedUrl,
                artworkUrl = parsedFeed.artworkUrl,
                importedAtEpochMillis = existingSubscription?.importedAtEpochMillis ?: now,
                refreshedAtEpochMillis = now,
                lastRefreshError = null,
            )

            val existingEpisodes = snapshot.episodes
                .filter { it.subscriptionId == subscriptionId }
                .associateBy { it.guid.ifBlank { it.audioUrl } }

            val mergedEpisodes = parsedFeed.episodes.map { episode ->
                mergeEpisode(
                    subscriptionId = subscriptionId,
                    parsedEpisode = episode,
                    fallbackArtworkUrl = parsedFeed.artworkUrl,
                    existingEpisode = existingEpisodes[episode.guid.ifBlank { episode.audioUrl }],
                )
            }

            snapshot.copy(
                subscriptions = snapshot.subscriptions
                    .filterNot { it.id == subscriptionId }
                    .plus(subscription)
                    .sortedBy { it.title.lowercase() },
                episodes = snapshot.episodes
                    .filterNot { it.subscriptionId == subscriptionId }
                    .plus(mergedEpisodes),
            )
        }
        return subscription
    }

    private fun mergeEpisode(
        subscriptionId: String,
        parsedEpisode: ParsedEpisode,
        fallbackArtworkUrl: String?,
        existingEpisode: Episode?,
    ): Episode {
        val episodeId = existingEpisode?.id ?: stableId("ep:${subscriptionId}:${parsedEpisode.guid}:${parsedEpisode.audioUrl}")
        return Episode(
            id = episodeId,
            subscriptionId = subscriptionId,
            guid = parsedEpisode.guid,
            title = parsedEpisode.title,
            description = parsedEpisode.description,
            audioUrl = parsedEpisode.audioUrl,
            artworkUrl = parsedEpisode.artworkUrl ?: fallbackArtworkUrl ?: existingEpisode?.artworkUrl,
            publishedAtEpochMillis = parsedEpisode.publishedAtEpochMillis ?: existingEpisode?.publishedAtEpochMillis,
            durationSeconds = parsedEpisode.durationSeconds ?: existingEpisode?.durationSeconds,
            sizeBytes = parsedEpisode.sizeBytes ?: existingEpisode?.sizeBytes,
            downloadState = existingEpisode?.downloadState ?: DownloadState.NOT_DOWNLOADED,
            downloadedFilePath = existingEpisode?.downloadedFilePath,
            downloadedBytes = existingEpisode?.downloadedBytes ?: 0L,
            lastPlayedPositionMs = existingEpisode?.lastPlayedPositionMs ?: 0L,
            lastPlayedAtEpochMillis = existingEpisode?.lastPlayedAtEpochMillis,
            isCompleted = existingEpisode?.isCompleted ?: false,
        )
    }

    private suspend fun updateEpisode(episodeId: String, transform: (Episode) -> Episode) {
        mutate { snapshot ->
            snapshot.copy(
                episodes = snapshot.episodes.map { episode ->
                    if (episode.id == episodeId) transform(episode) else episode
                },
            )
        }
    }

    private suspend fun mutate(transform: (AppSnapshot) -> AppSnapshot) {
        mutex.withLock {
            val updated = transform(mutableSnapshot.value)
            mutableSnapshot.value = updated
            store.write(updated)
        }
    }

    private suspend fun markSubscriptionRefreshFailed(subscriptionId: String, message: String) {
        mutate { snapshot ->
            snapshot.copy(
                subscriptions = snapshot.subscriptions.map { subscription ->
                    if (subscription.id == subscriptionId) {
                        subscription.copy(lastRefreshError = message)
                    } else {
                        subscription
                    }
                },
            )
        }
    }

    private fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun stableId(seed: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(seed.toByteArray())
        return buildString {
            bytes.take(10).forEach { byte -> append("%02x".format(byte)) }
        }
    }

    private fun applyCompletedState(
        episode: Episode,
        completed: Boolean,
        settings: DownloadSettings,
        now: Long,
        resetPlaybackPosition: Boolean,
    ): Episode {
        val shouldDeleteDownload = completed &&
            settings.autoDeletePlayedDownloads &&
            episode.downloadState == DownloadState.DOWNLOADED

        if (shouldDeleteDownload) {
            episode.downloadedFilePath?.let { File(it).delete() }
        }

        return episode.copy(
            isCompleted = completed,
            lastPlayedPositionMs = if (completed && resetPlaybackPosition) 0L else episode.lastPlayedPositionMs,
            lastPlayedAtEpochMillis = if (completed) now else episode.lastPlayedAtEpochMillis,
            downloadState = if (shouldDeleteDownload) DownloadState.NOT_DOWNLOADED else episode.downloadState,
            downloadedFilePath = if (shouldDeleteDownload) null else episode.downloadedFilePath,
            downloadedBytes = if (shouldDeleteDownload) 0L else episode.downloadedBytes,
        )
    }
}
