package com.sjtech.wearpod.download

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.sjtech.wearpod.data.model.Episode
import com.sjtech.wearpod.data.repository.WearPodRepository

class EpisodeDownloadScheduler(
    private val appContext: Context,
    private val repository: WearPodRepository,
) {
    private val workManager = WorkManager.getInstance(appContext)

    suspend fun enqueueEpisode(episode: Episode) {
        val wifiOnly = repository.snapshot.value.downloadSettings.wifiOnly
        repository.markEpisodeQueued(episode.id)
        val request = OneTimeWorkRequestBuilder<EpisodeDownloadWorker>()
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(
                        if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
                    )
                    .build(),
            )
            .setInputData(
                workDataOf(
                    EpisodeDownloadWorker.KEY_EPISODE_ID to episode.id,
                    EpisodeDownloadWorker.KEY_AUDIO_URL to episode.audioUrl,
                ),
            )
            .addTag(EpisodeDownloadWorker.DOWNLOAD_TAG)
            .addTag(episode.id)
            .build()
        workManager.enqueueUniqueWork(
            uniqueWorkName(episode.id),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    suspend fun enqueueAll(episodes: List<Episode>) {
        episodes.forEach { episode ->
            if (episode.audioUrl.isNotBlank() && episode.downloadState != com.sjtech.wearpod.data.model.DownloadState.DOWNLOADED) {
                enqueueEpisode(episode)
            }
        }
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(EpisodeDownloadWorker.DOWNLOAD_TAG)
    }

    fun cancelEpisode(episodeId: String) {
        workManager.cancelUniqueWork(uniqueWorkName(episodeId))
    }

    private fun uniqueWorkName(episodeId: String): String = "download-$episodeId"
}
