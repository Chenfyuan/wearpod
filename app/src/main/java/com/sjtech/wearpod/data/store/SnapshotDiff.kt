package com.sjtech.wearpod.data.store

import com.sjtech.wearpod.data.model.AppSnapshot
import com.sjtech.wearpod.data.model.Episode
import com.sjtech.wearpod.data.model.Subscription

/**
 * The row-level changes needed to move the persisted state from one [AppSnapshot] to another,
 * so a single-field update (e.g. playback position) touches one row instead of the whole database.
 */
data class SnapshotDiff(
    val upsertedSubscriptions: List<Subscription>,
    val deletedSubscriptionIds: List<String>,
    val upsertedEpisodes: List<Episode>,
    val deletedEpisodeIds: List<String>,
    val addedFavoriteIds: List<String>,
    val removedFavoriteIds: List<String>,
    val preferencesChanged: Boolean,
) {
    val hasDatabaseChanges: Boolean
        get() = upsertedSubscriptions.isNotEmpty() ||
            deletedSubscriptionIds.isNotEmpty() ||
            upsertedEpisodes.isNotEmpty() ||
            deletedEpisodeIds.isNotEmpty() ||
            addedFavoriteIds.isNotEmpty() ||
            removedFavoriteIds.isNotEmpty()

    companion object {
        fun between(previous: AppSnapshot, updated: AppSnapshot): SnapshotDiff {
            val (upsertedSubscriptions, deletedSubscriptionIds) = diffById(
                previous = previous.subscriptions,
                updated = updated.subscriptions,
                id = Subscription::id,
            )
            val (upsertedEpisodes, deletedEpisodeIds) = diffById(
                previous = previous.episodes,
                updated = updated.episodes,
                id = Episode::id,
            )
            return SnapshotDiff(
                upsertedSubscriptions = upsertedSubscriptions,
                deletedSubscriptionIds = deletedSubscriptionIds,
                upsertedEpisodes = upsertedEpisodes,
                deletedEpisodeIds = deletedEpisodeIds,
                addedFavoriteIds = (updated.favoriteSubscriptionIds - previous.favoriteSubscriptionIds).toList(),
                removedFavoriteIds = (previous.favoriteSubscriptionIds - updated.favoriteSubscriptionIds).toList(),
                preferencesChanged = previous.playbackMemory != updated.playbackMemory ||
                    previous.hasCompletedAudioOutputSetup != updated.hasCompletedAudioOutputSetup ||
                    previous.downloadSettings != updated.downloadSettings ||
                    previous.sleepTimer != updated.sleepTimer,
            )
        }

        private fun <T> diffById(
            previous: List<T>,
            updated: List<T>,
            id: (T) -> String,
        ): Pair<List<T>, List<String>> {
            if (previous === updated) return emptyList<T>() to emptyList()
            val previousById = previous.associateBy(id)
            val updatedIds = HashSet<String>(updated.size)
            val upserted = updated.filter { item ->
                val itemId = id(item)
                updatedIds += itemId
                previousById[itemId] != item
            }
            val deleted = previousById.keys.filterNot { it in updatedIds }
            return upserted to deleted
        }
    }
}
