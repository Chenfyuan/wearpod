package com.sjtech.wearpod.core

import android.app.Application
import com.sjtech.wearpod.BuildConfig
import com.sjtech.wearpod.data.repository.WearPodRepository
import com.sjtech.wearpod.data.rss.FeedNetworkClient
import com.sjtech.wearpod.data.rss.PodcastFeedParser
import com.sjtech.wearpod.data.store.WearPodStore
import com.sjtech.wearpod.download.EpisodeDownloadScheduler
import com.sjtech.wearpod.playback.AudioOutputController
import com.sjtech.wearpod.playback.PlayerGateway
import com.sjtech.wearpod.playback.VolumeController
import com.sjtech.wearpod.sync.SubscriptionRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppContainer(application: Application) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = WearPodStore(application)
    private val parser = PodcastFeedParser()
    private val networkClient = FeedNetworkClient()

    val repository = WearPodRepository(
        appContext = application,
        store = store,
        parser = parser,
        networkClient = networkClient,
    )

    val playerGateway = PlayerGateway(
        appContext = application,
        repository = repository,
    )
    val audioOutputController = AudioOutputController(application)
    val volumeController = VolumeController(application)

    val downloadScheduler = EpisodeDownloadScheduler(
        appContext = application,
        repository = repository,
    )
    private val subscriptionRefreshScheduler = SubscriptionRefreshScheduler(application)

    init {
        appScope.launch {
            repository.snapshot
                .map { snapshot ->
                    RefreshScheduleConfig(
                        enabled = snapshot.downloadSettings.backgroundRefreshEnabled,
                        intervalHours = snapshot.downloadSettings.backgroundRefreshIntervalHours,
                        hasSubscriptions = snapshot.subscriptions.isNotEmpty(),
                    )
                }
                .distinctUntilChanged()
                .collect { config ->
                    subscriptionRefreshScheduler.sync(
                        enabled = config.enabled,
                        intervalHours = config.intervalHours,
                        hasSubscriptions = config.hasSubscriptions,
                    )
                }
        }
        if (BuildConfig.DEBUG) {
            appScope.launch {
                repository.ensureImportedFeed(DEBUG_EXPERIENCE_FEED)
            }
        }
    }
}

private const val DEBUG_EXPERIENCE_FEED = "https://feed.xyzfm.space/xpa79uvcn9lw"

private data class RefreshScheduleConfig(
    val enabled: Boolean,
    val intervalHours: Int,
    val hasSubscriptions: Boolean,
)
