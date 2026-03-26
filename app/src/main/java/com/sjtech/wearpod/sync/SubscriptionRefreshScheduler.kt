package com.sjtech.wearpod.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SubscriptionRefreshScheduler(
    appContext: Context,
) {
    private val workManager = WorkManager.getInstance(appContext)

    fun sync(
        enabled: Boolean,
        intervalHours: Int,
        hasSubscriptions: Boolean,
    ) {
        if (!enabled || !hasSubscriptions) {
            cancel()
            return
        }

        val request = PeriodicWorkRequestBuilder<SubscriptionRefreshWorker>(
            intervalHours.toLong(),
            TimeUnit.HOURS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .addTag(REFRESH_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "subscription-refresh"
        private const val REFRESH_TAG = "subscription-refresh"
    }
}
