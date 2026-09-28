package com.sjtech.wearpod.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sjtech.wearpod.WearPodApplication
import kotlinx.coroutines.CancellationException

class SubscriptionRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val appContainer = (applicationContext as WearPodApplication).appContainer
        val repository = appContainer.repository
        val downloadScheduler = appContainer.downloadScheduler

        return try {
            val failedCount = repository.refreshAllSubscriptions()
            if (failedCount > 0) {
                Log.w(TAG, "$failedCount subscription(s) failed to refresh")
            }
            repository.snapshot.value.subscriptions.forEach { subscription ->
                val candidates = repository.autoDownloadCandidates(subscription.id)
                if (candidates.isNotEmpty()) {
                    downloadScheduler.enqueueAll(candidates)
                }
            }
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            Log.e(TAG, "Background refresh failed", exception)
            Result.retry()
        }
    }
}

private const val TAG = "SubscriptionRefreshWorker"
