package com.sjtech.wearpod.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.sjtech.wearpod.R
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
        repository.awaitLoaded()
        val episodeTitle = repository.episode(episodeId)?.title
        promoteToForeground(episodeTitle)

        return try {
            repository.markEpisodeDownloading(episodeId, 0L)
            var lastNotifiedAt = 0L
            val targetFile = withContext(Dispatchers.IO) {
                downloadToFile(episodeId, audioUrl) { downloaded, totalBytes ->
                    repository.markEpisodeDownloading(episodeId, downloaded)
                    val now = System.currentTimeMillis()
                    if (now - lastNotifiedAt >= NOTIFICATION_UPDATE_INTERVAL_MS) {
                        lastNotifiedAt = now
                        updateNotification(episodeTitle, downloaded, totalBytes)
                    }
                }
            }
            repository.markEpisodeDownloaded(episodeId, targetFile.absolutePath, targetFile.length())
            Result.success()
        } catch (ioe: IOException) {
            Log.w(TAG, "Download of $episodeId failed", ioe)
            if (isStopped) {
                repository.resetEpisodeDownload(episodeId)
                Result.failure()
            } else {
                repository.markEpisodeDownloadFailed(episodeId)
                Result.retry()
            }
        } catch (exception: Exception) {
            Log.e(TAG, "Download of $episodeId failed permanently", exception)
            if (isStopped) {
                repository.resetEpisodeDownload(episodeId)
            } else {
                repository.markEpisodeDownloadFailed(episodeId)
            }
            Result.failure()
        } finally {
            // WorkManager clears the foreground notification itself; this covers the case where
            // promotion was refused and the progress notification was posted directly.
            notificationManager().cancel(notificationId())
        }
    }

    private suspend fun downloadToFile(
        episodeId: String,
        audioUrl: String,
        onProgress: suspend (downloaded: Long, totalBytes: Long?) -> Unit,
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
            val totalBytes = connection.contentLengthLong.takeIf { it > 0 }
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
                            onProgress(downloaded, totalBytes)
                        }
                    }
                    output.flush()
                    onProgress(downloaded, totalBytes)
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

    override suspend fun getForegroundInfo(): ForegroundInfo =
        createForegroundInfo(title = null, downloadedBytes = 0L, totalBytes = null)

    /**
     * Runs the download as a foreground service so long episodes aren't killed at the 10-minute
     * WorkManager limit. Android 12+ can refuse this when the work starts from the background
     * (e.g. auto-downloads after a refresh); the download then continues as regular work.
     */
    private suspend fun promoteToForeground(title: String?) {
        try {
            setForeground(createForegroundInfo(title, downloadedBytes = 0L, totalBytes = null))
        } catch (exception: IllegalStateException) {
            Log.w(TAG, "Could not start download as foreground work", exception)
        }
    }

    private fun updateNotification(title: String?, downloadedBytes: Long, totalBytes: Long?) {
        // Without POST_NOTIFICATIONS (Android 13+) the download still runs; there's just no progress UI.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManager().notify(
            notificationId(),
            buildNotification(title, downloadedBytes, totalBytes),
        )
    }

    private fun createForegroundInfo(title: String?, downloadedBytes: Long, totalBytes: Long?): ForegroundInfo =
        ForegroundInfo(
            notificationId(),
            buildNotification(title, downloadedBytes, totalBytes),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

    private fun buildNotification(title: String?, downloadedBytes: Long, totalBytes: Long?) =
        NotificationCompat.Builder(applicationContext, ensureChannel())
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(
                title?.let { applicationContext.getString(R.string.download_notification_title, it) }
                    ?: applicationContext.getString(R.string.download_notification_fallback_title),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .apply {
                if (totalBytes != null) {
                    setProgress(PROGRESS_MAX, (downloadedBytes * PROGRESS_MAX / totalBytes).toInt(), false)
                } else {
                    setProgress(0, 0, true)
                }
            }
            .build()

    private fun ensureChannel(): String {
        notificationManager().createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.download_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        return CHANNEL_ID
    }

    private fun notificationManager(): NotificationManager =
        applicationContext.getSystemService(NotificationManager::class.java)

    private fun notificationId(): Int = NOTIFICATION_ID_BASE + (inputData.getString(KEY_EPISODE_ID)?.hashCode() ?: 0)

    companion object {
        private const val TAG = "EpisodeDownloadWorker"
        private const val CHANNEL_ID = "episode_downloads"
        private const val NOTIFICATION_ID_BASE = 40_000
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 1_000L
        private const val PROGRESS_MAX = 100

        const val KEY_EPISODE_ID = "episode_id"
        const val KEY_AUDIO_URL = "audio_url"
        const val DOWNLOAD_TAG = "episode-download"
    }
}
