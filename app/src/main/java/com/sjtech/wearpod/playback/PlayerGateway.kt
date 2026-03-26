package com.sjtech.wearpod.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.sjtech.wearpod.data.model.Episode
import com.sjtech.wearpod.data.repository.WearPodRepository
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerSnapshot(
    val episodeId: String? = null,
    val title: String = "",
    val subtitle: String = "",
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val hasMedia: Boolean = false,
    val queue: List<PlayerQueueItemSnapshot> = emptyList(),
    val currentQueueIndex: Int = -1,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
)

data class PlayerQueueItemSnapshot(
    val episodeId: String,
    val title: String,
    val subtitle: String,
)

class PlayerGateway(
    appContext: Context,
    private val repository: WearPodRepository,
) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controllerDeferred = CompletableDeferred<MediaController>()
    private val mutablePlayerState = MutableStateFlow(PlayerSnapshot())
    private var lastPersistAt = 0L
    private var sleepTimerJob: Job? = null

    val playerState: StateFlow<PlayerSnapshot> = mutablePlayerState.asStateFlow()

    init {
        val sessionToken = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                val controller = controllerFuture.get()
                controller.addListener(
                    object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            updatePlayerSnapshot(controller)
                        }
                    },
                )
                controllerDeferred.complete(controller)
                controller.setPlaybackParameters(PlaybackParameters(repository.snapshot.value.playbackMemory.speed))
                updatePlayerSnapshot(controller)
            },
            appContext.mainExecutor,
        )
        restoreSleepTimer()
        appScope.launch {
            while (isActive) {
                val controller = controllerDeferred.await()
                updatePlayerSnapshot(controller)
                persistPlayback(controller)
                delay(1_000L)
            }
        }
    }

    suspend fun playEpisodes(
        episodes: List<Episode>,
        startEpisodeId: String,
        subscriptionTitle: String,
        shuffleQueue: Boolean = false,
    ) {
        if (episodes.isEmpty()) return
        val queue = if (shuffleQueue) {
            episodes.shuffled().sortedByDescending { if (it.id == startEpisodeId) 1 else 0 }
        } else {
            episodes
        }
        val startIndex = queue.indexOfFirst { it.id == startEpisodeId }.coerceAtLeast(0)
        val controller = controllerDeferred.await()
        val mediaItems = queue.map { episode ->
            MediaItem.Builder()
                .setMediaId(episode.id)
                .setUri(uriForEpisode(episode))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(episode.title)
                        .setArtist(subscriptionTitle)
                        .setArtworkUri(episode.artworkUrl?.let(Uri::parse))
                        .build(),
                )
                .build()
        }
        val startPosition = queue.getOrNull(startIndex)?.lastPlayedPositionMs ?: 0L
        controller.setMediaItems(mediaItems, startIndex, startPosition)
        controller.prepare()
        controller.play()
    }

    suspend fun togglePlayPause() {
        val controller = controllerDeferred.await()
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    suspend fun seekBy(deltaMs: Long) {
        val controller = controllerDeferred.await()
        controller.seekTo((controller.currentPosition + deltaMs).coerceAtLeast(0L))
    }

    suspend fun cyclePlaybackSpeed(): Float {
        val controller = controllerDeferred.await()
        val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f)
        val currentIndex = speeds.indexOfFirst { it == controller.playbackParameters.speed }
        val nextSpeed = speeds[(currentIndex + 1).mod(speeds.size)]
        controller.setPlaybackParameters(PlaybackParameters(nextSpeed))
        repository.setPlaybackSpeed(nextSpeed)
        updatePlayerSnapshot(controller)
        return nextSpeed
    }

    suspend fun skipToPrevious() {
        val controller = controllerDeferred.await()
        controller.seekToPreviousMediaItem()
        if (!controller.isPlaying) {
            controller.play()
        }
    }

    suspend fun skipToNext() {
        val controller = controllerDeferred.await()
        controller.seekToNextMediaItem()
        if (!controller.isPlaying) {
            controller.play()
        }
    }

    suspend fun playQueueItem(episodeId: String) {
        val controller = controllerDeferred.await()
        val targetIndex = (0 until controller.mediaItemCount)
            .firstOrNull { index -> controller.getMediaItemAt(index).mediaId == episodeId }
            ?: return
        controller.seekToDefaultPosition(targetIndex)
        controller.prepare()
        controller.play()
    }

    suspend fun stopPlayback() {
        clearSleepTimer()
        val controller = controllerDeferred.await()
        controller.stop()
        controller.clearMediaItems()
        updatePlayerSnapshot(controller)
    }

    suspend fun startSleepTimer(minutes: Int) {
        val clampedMinutes = minutes.coerceIn(1, 120)
        val endsAtEpochMillis = System.currentTimeMillis() + clampedMinutes * 60_000L
        repository.setSleepTimer(endsAtEpochMillis, clampedMinutes)
        scheduleSleepTimer(endsAtEpochMillis)
    }

    suspend fun clearSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        repository.setSleepTimer(null, null)
    }

    private fun uriForEpisode(episode: Episode): Uri {
        val localPath = episode.downloadedFilePath
        return if (episode.downloadState == com.sjtech.wearpod.data.model.DownloadState.DOWNLOADED &&
            localPath != null &&
            File(localPath).exists()
        ) {
            Uri.fromFile(File(localPath))
        } else {
            Uri.parse(episode.audioUrl)
        }
    }

    private fun updatePlayerSnapshot(controller: MediaController) {
        val metadata = controller.mediaMetadata
        val queue = buildList {
            repeat(controller.mediaItemCount) { index ->
                val item = controller.getMediaItemAt(index)
                add(
                    PlayerQueueItemSnapshot(
                        episodeId = item.mediaId,
                        title = item.mediaMetadata.title?.toString().orEmpty(),
                        subtitle = item.mediaMetadata.artist?.toString().orEmpty(),
                    ),
                )
            }
        }
        mutablePlayerState.value = PlayerSnapshot(
            episodeId = controller.currentMediaItem?.mediaId,
            title = metadata.title?.toString().orEmpty(),
            subtitle = metadata.artist?.toString().orEmpty(),
            artworkUrl = metadata.artworkUri?.toString(),
            isPlaying = controller.isPlaying,
            positionMs = controller.currentPosition.coerceAtLeast(0L),
            durationMs = controller.duration.takeIf { it > 0 } ?: 0L,
            speed = controller.playbackParameters.speed,
            hasMedia = controller.currentMediaItem != null,
            queue = queue,
            currentQueueIndex = controller.currentMediaItemIndex,
            hasPrevious = controller.hasPreviousMediaItem(),
            hasNext = controller.hasNextMediaItem(),
        )
    }

    private fun persistPlayback(controller: MediaController) {
        val mediaId = controller.currentMediaItem?.mediaId ?: return
        val now = System.currentTimeMillis()
        if (now - lastPersistAt < 5_000L && controller.isPlaying) return
        lastPersistAt = now
        val duration = controller.duration.takeIf { it > 0 } ?: 0L
        val position = controller.currentPosition.coerceAtLeast(0L)
        val isCompleted = duration > 0 && position >= duration - 3_000L
        appScope.launch {
            repository.updatePlayback(
                episodeId = mediaId,
                positionMs = position,
                durationMs = duration,
                speed = controller.playbackParameters.speed,
                isCompleted = isCompleted,
            )
        }
    }

    private fun restoreSleepTimer() {
        scheduleSleepTimer(repository.snapshot.value.sleepTimer.endsAtEpochMillis)
    }

    private fun scheduleSleepTimer(endsAtEpochMillis: Long?) {
        sleepTimerJob?.cancel()
        if (endsAtEpochMillis == null) {
            sleepTimerJob = null
            return
        }
        sleepTimerJob = appScope.launch {
            val remainingMs = endsAtEpochMillis - System.currentTimeMillis()
            if (remainingMs > 0) {
                delay(remainingMs)
            }
            val controller = controllerDeferred.await()
            if (controller.currentMediaItem != null) {
                controller.pause()
                updatePlayerSnapshot(controller)
                persistPlayback(controller)
            }
            repository.setSleepTimer(null, null)
        }
    }
}
