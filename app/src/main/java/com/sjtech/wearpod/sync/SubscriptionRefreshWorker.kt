package com.sjtech.wearpod.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sjtech.wearpod.WearPodApplication

class SubscriptionRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val appContainer = (applicationContext as WearPodApplication).appContainer
        val repository = appContainer.repository
        val downloadScheduler = appContainer.downloadScheduler

        return try {
            repository.refreshAllSubscriptions()
            repository.snapshot.value.subscriptions.forEach { subscription ->
                val candidates = repository.autoDownloadCandidates(subscription.id)
                if (candidates.isNotEmpty()) {
                    downloadScheduler.enqueueAll(candidates)
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
