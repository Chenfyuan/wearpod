package com.sjtech.wearpod.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sjtech.wearpod.WearPodApplication
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EpisodeDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val episodeId = inputData.getString(KEY_EPISODE_ID) ?: return Result.failure()
        val audioUrl = inputData.getString(KEY_AUDIO_URL) ?: return Result.failure()
        val repository = (applicationContext as WearPodApplication).appContainer.repository

        return try {
            repository.markEpisodeDownloading(episodeId, 0L)
            val targetFile = withContext(Dispatchers.IO) {
                downloadToFile(episodeId, audioUrl) { downloaded ->
                    repository.markEpisodeDownloading(episodeId, downloaded)
                }
            }
            repository.markEpisodeDownloaded(episodeId, targetFile.absolutePath, targetFile.length())
            Result.success()
        } catch (_: IOException) {
            if (isStopped) {
                repository.resetEpisodeDownload(episodeId)
                Result.failure()
            } else {
                repository.markEpisodeDownloadFailed(episodeId)
                Result.retry()
            }
        } catch (_: Exception) {
            if (isStopped) {
                repository.resetEpisodeDownload(episodeId)
            } else {
                repository.markEpisodeDownloadFailed(episodeId)
            }
            Result.failure()
        }
    }

    private suspend fun downloadToFile(
        episodeId: String,
        audioUrl: String,
        onProgress: suspend (Long) -> Unit,
    ): File {
        val connection = (URL(audioUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "WearPod/0.1 (Wear OS)")
        }

        val outputDir = File(applicationContext.filesDir, "downloads").apply { mkdirs() }
        val extension = audioUrl.substringAfterLast('.', "mp3")
            .substringBefore('?')
            .take(5)
            .ifBlank { "mp3" }
        val target = File(outputDir, "$episodeId.$extension")

        return try {
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    var lastReported = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (isStopped) throw IOException("Download cancelled")
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReported >= 256 * 1024L) {
                            lastReported = downloaded
                            onProgress(downloaded)
                        }
                    }
                    output.flush()
                    onProgress(downloaded)
                }
            }
            target
        } catch (ioe: IOException) {
            target.delete()
            throw ioe
        } catch (throwable: Throwable) {
            target.delete()
            throw throwable
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val KEY_EPISODE_ID = "episode_id"
        const val KEY_AUDIO_URL = "audio_url"
        const val DOWNLOAD_TAG = "episode-download"
    }
}
