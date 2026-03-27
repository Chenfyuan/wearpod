package com.sjtech.wearpod.data.store

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.sjtech.wearpod.data.model.AppSnapshot
import com.sjtech.wearpod.data.model.DownloadSettings
import com.sjtech.wearpod.data.model.DownloadState
import com.sjtech.wearpod.data.model.Episode
import com.sjtech.wearpod.data.model.PlaybackMemory
import com.sjtech.wearpod.data.model.SleepTimer
import com.sjtech.wearpod.data.model.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class WearPodStore(context: Context) {
    private val database = Room.databaseBuilder(
        context,
        WearPodDatabase::class.java,
        "wearpod.db",
    ).build()
    private val preferencesStore = WearPodPreferencesStore(context)
    private val legacyStore = LegacySnapshotFileStore(context)
    private val migrationMutex = Mutex()

    fun read(): AppSnapshot = runBlocking {
        withContext(Dispatchers.IO) {
            ensureMigrated()
            readSnapshot()
        }
    }

    suspend fun write(snapshot: AppSnapshot) {
        withContext(Dispatchers.IO) {
            ensureMigrated()
            persistSnapshot(snapshot)
        }
    }

    private suspend fun ensureMigrated() {
        migrationMutex.withLock {
            if (preferencesStore.isMigrationComplete()) return

            if (legacyStore.exists()) {
                legacyStore.readOrNull()?.let { snapshot ->
                    persistSnapshot(snapshot)
                }
                legacyStore.delete()
            }

            if (!preferencesStore.isMigrationComplete()) {
                preferencesStore.markMigrationComplete()
            }
        }
    }

    private suspend fun readSnapshot(): AppSnapshot {
        val dao = database.dao()
        val preferences = preferencesStore.read()
        return AppSnapshot(
            subscriptions = dao.subscriptions().map { entity ->
                Subscription(
                    id = entity.id,
                    title = entity.title,
                    author = entity.author,
                    description = entity.description,
                    feedUrl = entity.feedUrl,
                    artworkUrl = entity.artworkUrl,
                    importedAtEpochMillis = entity.importedAtEpochMillis,
                    refreshedAtEpochMillis = entity.refreshedAtEpochMillis,
                    lastRefreshError = entity.lastRefreshError,
                )
            },
            episodes = dao.episodes().map { entity ->
                Episode(
                    id = entity.id,
                    subscriptionId = entity.subscriptionId,
                    guid = entity.guid,
                    title = entity.title,
                    description = entity.description,
                    audioUrl = entity.audioUrl,
                    artworkUrl = entity.artworkUrl,
                    publishedAtEpochMillis = entity.publishedAtEpochMillis,
                    durationSeconds = entity.durationSeconds,
                    sizeBytes = entity.sizeBytes,
                    downloadState = entity.downloadState.toDownloadState(),
                    downloadedFilePath = entity.downloadedFilePath,
                    downloadedBytes = entity.downloadedBytes,
                    lastPlayedPositionMs = entity.lastPlayedPositionMs,
                    lastPlayedAtEpochMillis = entity.lastPlayedAtEpochMillis,
                    isCompleted = entity.isCompleted,
                )
            },
            favoriteSubscriptionIds = dao.favoriteSubscriptionIds().toSet(),
            playbackMemory = preferences.playbackMemory,
            downloadSettings = preferences.downloadSettings,
            sleepTimer = preferences.sleepTimer,
        )
    }

    private suspend fun persistSnapshot(snapshot: AppSnapshot) {
        val dao = database.dao()
        database.withTransaction {
            dao.clearFavoriteSubscriptions()
            dao.clearEpisodes()
            dao.clearSubscriptions()

            if (snapshot.subscriptions.isNotEmpty()) {
                dao.insertSubscriptions(
                    snapshot.subscriptions.map { subscription ->
                        SubscriptionEntity(
                            id = subscription.id,
                            title = subscription.title,
                            author = subscription.author,
                            description = subscription.description,
                            feedUrl = subscription.feedUrl,
                            artworkUrl = subscription.artworkUrl,
                            importedAtEpochMillis = subscription.importedAtEpochMillis,
                            refreshedAtEpochMillis = subscription.refreshedAtEpochMillis,
                            lastRefreshError = subscription.lastRefreshError,
                        )
                    },
                )
            }

            if (snapshot.episodes.isNotEmpty()) {
                dao.insertEpisodes(
                    snapshot.episodes.map { episode ->
                        EpisodeEntity(
                            id = episode.id,
                            subscriptionId = episode.subscriptionId,
                            guid = episode.guid,
                            title = episode.title,
                            description = episode.description,
                            audioUrl = episode.audioUrl,
                            artworkUrl = episode.artworkUrl,
                            publishedAtEpochMillis = episode.publishedAtEpochMillis,
                            durationSeconds = episode.durationSeconds,
                            sizeBytes = episode.sizeBytes,
                            downloadState = episode.downloadState.name,
                            downloadedFilePath = episode.downloadedFilePath,
                            downloadedBytes = episode.downloadedBytes,
                            lastPlayedPositionMs = episode.lastPlayedPositionMs,
                            lastPlayedAtEpochMillis = episode.lastPlayedAtEpochMillis,
                            isCompleted = episode.isCompleted,
                        )
                    },
                )
            }

            if (snapshot.favoriteSubscriptionIds.isNotEmpty()) {
                dao.insertFavoriteSubscriptions(
                    snapshot.favoriteSubscriptionIds.map { id -> FavoriteSubscriptionEntity(id) },
                )
            }
        }

        preferencesStore.write(
            WearPodPreferencesSnapshot(
                playbackMemory = snapshot.playbackMemory,
                downloadSettings = snapshot.downloadSettings,
                sleepTimer = snapshot.sleepTimer,
            ),
        )
    }
}

private fun String.toDownloadState(): DownloadState =
    runCatching { DownloadState.valueOf(this) }.getOrDefault(DownloadState.NOT_DOWNLOADED)
