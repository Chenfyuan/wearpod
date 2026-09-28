package com.sjtech.wearpod.data.store

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.sjtech.wearpod.data.model.AppSnapshot
import com.sjtech.wearpod.data.model.DownloadState
import com.sjtech.wearpod.data.model.Episode
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

    /**
     * Persists [updated], writing only the rows and preferences that differ from [previous].
     * [previous] must be the state that was last persisted (or read) by this store.
     */
    suspend fun write(previous: AppSnapshot, updated: AppSnapshot) {
        if (previous === updated) return
        withContext(Dispatchers.IO) {
            ensureMigrated()
            persistDiff(SnapshotDiff.between(previous, updated), updated)
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
            hasCompletedAudioOutputSetup = preferences.hasCompletedAudioOutputSetup,
            downloadSettings = preferences.downloadSettings,
            sleepTimer = preferences.sleepTimer,
        )
    }

    private suspend fun persistDiff(diff: SnapshotDiff, updated: AppSnapshot) {
        if (diff.hasDatabaseChanges) {
            val dao = database.dao()
            database.withTransaction {
                diff.removedFavoriteIds.chunked(SQL_BATCH_SIZE).forEach { dao.deleteFavoriteSubscriptions(it) }
                diff.deletedEpisodeIds.chunked(SQL_BATCH_SIZE).forEach { dao.deleteEpisodes(it) }
                diff.deletedSubscriptionIds.chunked(SQL_BATCH_SIZE).forEach { dao.deleteSubscriptions(it) }
                if (diff.upsertedSubscriptions.isNotEmpty()) {
                    dao.insertSubscriptions(diff.upsertedSubscriptions.map { it.toEntity() })
                }
                if (diff.upsertedEpisodes.isNotEmpty()) {
                    dao.insertEpisodes(diff.upsertedEpisodes.map { it.toEntity() })
                }
                if (diff.addedFavoriteIds.isNotEmpty()) {
                    dao.insertFavoriteSubscriptions(diff.addedFavoriteIds.map(::FavoriteSubscriptionEntity))
                }
            }
        }
        if (diff.preferencesChanged) {
            preferencesStore.write(updated.toPreferencesSnapshot())
        }
    }

    private suspend fun persistSnapshot(snapshot: AppSnapshot) {
        val dao = database.dao()
        database.withTransaction {
            dao.clearFavoriteSubscriptions()
            dao.clearEpisodes()
            dao.clearSubscriptions()

            if (snapshot.subscriptions.isNotEmpty()) {
                dao.insertSubscriptions(snapshot.subscriptions.map { it.toEntity() })
            }

            if (snapshot.episodes.isNotEmpty()) {
                dao.insertEpisodes(snapshot.episodes.map { it.toEntity() })
            }

            if (snapshot.favoriteSubscriptionIds.isNotEmpty()) {
                dao.insertFavoriteSubscriptions(
                    snapshot.favoriteSubscriptionIds.map { id -> FavoriteSubscriptionEntity(id) },
                )
            }
        }

        preferencesStore.write(snapshot.toPreferencesSnapshot())
    }
}

private const val SQL_BATCH_SIZE = 500

private fun AppSnapshot.toPreferencesSnapshot() = WearPodPreferencesSnapshot(
    playbackMemory = playbackMemory,
    hasCompletedAudioOutputSetup = hasCompletedAudioOutputSetup,
    downloadSettings = downloadSettings,
    sleepTimer = sleepTimer,
)

private fun Subscription.toEntity() = SubscriptionEntity(
    id = id,
    title = title,
    author = author,
    description = description,
    feedUrl = feedUrl,
    artworkUrl = artworkUrl,
    importedAtEpochMillis = importedAtEpochMillis,
    refreshedAtEpochMillis = refreshedAtEpochMillis,
    lastRefreshError = lastRefreshError,
)

private fun Episode.toEntity() = EpisodeEntity(
    id = id,
    subscriptionId = subscriptionId,
    guid = guid,
    title = title,
    description = description,
    audioUrl = audioUrl,
    artworkUrl = artworkUrl,
    publishedAtEpochMillis = publishedAtEpochMillis,
    durationSeconds = durationSeconds,
    sizeBytes = sizeBytes,
    downloadState = downloadState.name,
    downloadedFilePath = downloadedFilePath,
    downloadedBytes = downloadedBytes,
    lastPlayedPositionMs = lastPlayedPositionMs,
    lastPlayedAtEpochMillis = lastPlayedAtEpochMillis,
    isCompleted = isCompleted,
)

private fun String.toDownloadState(): DownloadState =
    runCatching { DownloadState.valueOf(this) }.getOrDefault(DownloadState.NOT_DOWNLOADED)
