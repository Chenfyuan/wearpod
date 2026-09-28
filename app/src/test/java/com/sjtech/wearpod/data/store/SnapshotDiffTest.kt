package com.sjtech.wearpod.data.store

import com.sjtech.wearpod.data.model.AppSnapshot
import com.sjtech.wearpod.data.model.DownloadSettings
import com.sjtech.wearpod.data.model.Episode
import com.sjtech.wearpod.data.model.PlaybackMemory
import com.sjtech.wearpod.data.model.Subscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotDiffTest {
    private val subscription = Subscription(
        id = "sub-1",
        title = "Show",
        author = "Host",
        description = "",
        feedUrl = "https://example.com/feed",
        artworkUrl = null,
        importedAtEpochMillis = 1L,
        refreshedAtEpochMillis = 1L,
    )

    private fun episode(id: String, positionMs: Long = 0L) = Episode(
        id = id,
        subscriptionId = subscription.id,
        guid = id,
        title = "Episode $id",
        description = "",
        audioUrl = "https://example.com/$id.mp3",
        artworkUrl = null,
        publishedAtEpochMillis = null,
        durationSeconds = null,
        sizeBytes = null,
        lastPlayedPositionMs = positionMs,
    )

    private val base = AppSnapshot(
        subscriptions = listOf(subscription),
        episodes = listOf(episode("a"), episode("b"), episode("c")),
        favoriteSubscriptionIds = emptySet(),
        playbackMemory = PlaybackMemory(),
    )

    @Test
    fun identicalSnapshotsProduceNoChanges() {
        val diff = SnapshotDiff.between(base, base.copy())

        assertFalse(diff.hasDatabaseChanges)
        assertFalse(diff.preferencesChanged)
    }

    @Test
    fun singleEpisodeUpdateOnlyUpsertsThatEpisode() {
        val updated = base.copy(
            episodes = base.episodes.map { if (it.id == "b") it.copy(lastPlayedPositionMs = 42L) else it },
            playbackMemory = PlaybackMemory(currentEpisodeId = "b", positionMs = 42L),
        )

        val diff = SnapshotDiff.between(base, updated)

        assertEquals(listOf("b"), diff.upsertedEpisodes.map { it.id })
        assertTrue(diff.deletedEpisodeIds.isEmpty())
        assertTrue(diff.upsertedSubscriptions.isEmpty())
        assertTrue(diff.preferencesChanged)
    }

    @Test
    fun removedAndAddedRowsAreReported() {
        val updated = base.copy(
            subscriptions = emptyList(),
            episodes = listOf(episode("a"), episode("d")),
            favoriteSubscriptionIds = setOf("sub-2"),
        )

        val diff = SnapshotDiff.between(base.copy(favoriteSubscriptionIds = setOf(subscription.id)), updated)

        assertEquals(listOf(subscription.id), diff.deletedSubscriptionIds)
        assertEquals(listOf("d"), diff.upsertedEpisodes.map { it.id })
        assertEquals(setOf("b", "c"), diff.deletedEpisodeIds.toSet())
        assertEquals(listOf("sub-2"), diff.addedFavoriteIds)
        assertEquals(listOf(subscription.id), diff.removedFavoriteIds)
        assertFalse(diff.preferencesChanged)
    }

    @Test
    fun settingsChangeIsPreferencesOnly() {
        val updated = base.copy(downloadSettings = DownloadSettings(wifiOnly = false))

        val diff = SnapshotDiff.between(base, updated)

        assertFalse(diff.hasDatabaseChanges)
        assertTrue(diff.preferencesChanged)
    }
}
