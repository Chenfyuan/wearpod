package com.sjtech.wearpod.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sjtech.wearpod.data.model.Episode
import com.sjtech.wearpod.data.model.ImportSuggestion
import com.sjtech.wearpod.data.repository.WearPodRepository
import com.sjtech.wearpod.download.EpisodeDownloadScheduler
import com.sjtech.wearpod.playback.PlayerGateway
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface WearPodScreen {
    data object Home : WearPodScreen
    data object Subscriptions : WearPodScreen
    data object Import : WearPodScreen
    data object Downloads : WearPodScreen
    data object DownloadSettings : WearPodScreen
    data class PodcastDetail(val subscriptionId: String) : WearPodScreen
    data object Player : WearPodScreen
}

enum class EpisodeFilter {
    ALL,
    UNPLAYED,
    DOWNLOADED,
}

class WearPodViewModel(
    val repository: WearPodRepository,
    val playerGateway: PlayerGateway,
    private val downloadScheduler: EpisodeDownloadScheduler,
) : ViewModel() {
    private val history = ArrayDeque<WearPodScreen>()

    var currentScreen by mutableStateOf<WearPodScreen>(WearPodScreen.Home)
        private set
    val previousScreen: WearPodScreen?
        get() = history.lastOrNull()
    var canGoBack by mutableStateOf(false)
        private set
    var importUrl by mutableStateOf("")
    var importError by mutableStateOf<String?>(null)
        private set
    var isImporting by mutableStateOf(false)
        private set
    var refreshingSubscriptionId by mutableStateOf<String?>(null)
        private set
    var retryingSubscriptionId by mutableStateOf<String?>(null)
        private set
    var pendingUnsubscribeId by mutableStateOf<String?>(null)
        private set
    var episodeFilter by mutableStateOf(EpisodeFilter.ALL)
        private set
    var bannerMessage by mutableStateOf<String?>(null)
        private set

    val snapshot = repository.snapshot
    val playerState = playerGateway.playerState
    val suggestions: List<ImportSuggestion> = repository.importSuggestions

    fun openRoot(screen: WearPodScreen) {
        history.clear()
        currentScreen = screen
        syncBackState()
    }

    fun openSubscriptions() {
        push(WearPodScreen.Subscriptions)
    }

    fun openDownloads() {
        push(WearPodScreen.Downloads)
    }

    fun openDownloadSettings() {
        push(WearPodScreen.DownloadSettings)
    }

    fun openImport() {
        push(WearPodScreen.Import)
    }

    fun push(screen: WearPodScreen) {
        history.addLast(currentScreen)
        currentScreen = screen
        syncBackState()
    }

    fun replaceCurrent(screen: WearPodScreen) {
        currentScreen = screen
        syncBackState()
    }

    fun back() {
        if (history.isNotEmpty()) {
            currentScreen = history.removeLast()
        } else {
            currentScreen = WearPodScreen.Home
        }
        syncBackState()
    }

    fun openSubscription(subscriptionId: String) {
        episodeFilter = EpisodeFilter.ALL
        push(WearPodScreen.PodcastDetail(subscriptionId))
    }

    fun openPlayer() {
        push(WearPodScreen.Player)
    }

    fun useSuggestion(suggestion: ImportSuggestion) {
        importUrl = suggestion.url
        importError = null
    }

    fun submitImport() {
        if (importUrl.isBlank()) {
            importError = "请输入 RSS 地址"
            return
        }
        viewModelScope.launch {
            isImporting = true
            importError = null
            try {
                val subscription = repository.importFeed(importUrl)
                importUrl = ""
                isImporting = false
                enqueueAutoDownloads(subscription.id)
                showBanner("已导入 ${subscription.title}")
                replaceCurrent(WearPodScreen.PodcastDetail(subscription.id))
            } catch (throwable: Throwable) {
                isImporting = false
                importError = throwable.message ?: "导入失败"
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            repository.refreshAllSubscriptions()
            snapshot.value.subscriptions.forEach { subscription ->
                enqueueAutoDownloads(subscription.id)
            }
            val failedCount = snapshot.value.subscriptions.count { !it.lastRefreshError.isNullOrBlank() }
            showBanner(
                if (failedCount > 0) {
                    "${failedCount} 个订阅刷新失败"
                } else {
                    "已刷新订阅"
                },
            )
        }
    }

    fun requestUnsubscribe(subscriptionId: String) {
        pendingUnsubscribeId = subscriptionId
        showBanner("长按后点确认取消订阅")
    }

    fun dismissUnsubscribeRequest() {
        pendingUnsubscribeId = null
    }

    fun unsubscribe(subscriptionId: String) {
        val subscription = repository.subscription(subscriptionId) ?: return
        val playingEpisodeId = playerState.value.episodeId
        val isPlayingFromSubscription = repository.episodesForSubscription(subscriptionId)
            .any { it.id == playingEpisodeId }

        viewModelScope.launch {
            if (isPlayingFromSubscription) {
                playerGateway.stopPlayback()
            }
            repository.unsubscribe(subscriptionId)
            if (currentScreen == WearPodScreen.PodcastDetail(subscriptionId)) {
                back()
            }
            pendingUnsubscribeId = null
            showBanner("已取消 ${subscription.title}")
        }
    }

    fun refreshSubscription(subscriptionId: String) {
        viewModelScope.launch {
            refreshingSubscriptionId = subscriptionId
            try {
                repository.refreshSubscription(subscriptionId)
                enqueueAutoDownloads(subscriptionId)
                showBanner("节目已更新")
            } catch (throwable: Throwable) {
                showBanner(throwable.message ?: "刷新失败")
            }
            refreshingSubscriptionId = null
        }
    }

    fun retrySubscriptionRefresh(subscriptionId: String) {
        viewModelScope.launch {
            retryingSubscriptionId = subscriptionId
            try {
                repository.refreshSubscription(subscriptionId)
                enqueueAutoDownloads(subscriptionId)
                showBanner("重试成功")
            } catch (throwable: Throwable) {
                showBanner(throwable.message ?: "重试失败")
            }
            retryingSubscriptionId = null
        }
    }

    fun toggleFavorite(subscriptionId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(subscriptionId)
        }
    }

    fun updateEpisodeFilter(filter: EpisodeFilter) {
        episodeFilter = filter
    }

    fun toggleEpisodeCompleted(episode: Episode) {
        viewModelScope.launch {
            val completed = !episode.isCompleted
            repository.setEpisodeCompleted(episode.id, completed)
            showBanner(if (completed) "已标记已播" else "已标记未播")
        }
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateDownloadSettings { settings ->
                settings.copy(wifiOnly = enabled)
            }
            showBanner(if (enabled) "仅 Wi‑Fi 下载" else "允许任意网络下载")
        }
    }

    fun setAutoDownloadLatestCount(count: Int) {
        viewModelScope.launch {
            repository.updateDownloadSettings { settings ->
                settings.copy(autoDownloadLatestCount = count.coerceIn(0, 3))
            }
            showBanner(
                if (count <= 0) {
                    "已关闭自动下载"
                } else {
                    "自动下载最新 ${count.coerceIn(0, 3)} 期"
                },
            )
        }
    }

    fun setBackgroundAutoDownload(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateDownloadSettings { settings ->
                settings.copy(backgroundAutoDownloadEnabled = enabled)
            }
            showBanner(if (enabled) "已开启后台自动下载" else "已关闭后台自动下载")
        }
    }

    fun setBackgroundRefreshEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateDownloadSettings { settings ->
                settings.copy(backgroundRefreshEnabled = enabled)
            }
            showBanner(if (enabled) "已开启后台定时刷新" else "已关闭后台定时刷新")
        }
    }

    fun setBackgroundRefreshInterval(hours: Int) {
        val normalizedHours = when (hours) {
            12, 24 -> hours
            else -> 6
        }
        viewModelScope.launch {
            repository.updateDownloadSettings { settings ->
                settings.copy(
                    backgroundRefreshEnabled = true,
                    backgroundRefreshIntervalHours = normalizedHours,
                )
            }
            showBanner("改为每 ${normalizedHours} 小时后台刷新")
        }
    }

    fun playContinueEpisode() {
        val memory = snapshot.value.playbackMemory.lastEpisodeId ?: return
        val episode = repository.episode(memory) ?: return
        playEpisode(episode.subscriptionId, episode.id)
    }

    fun playEpisode(subscriptionId: String, episodeId: String) {
        val subscription = repository.subscription(subscriptionId) ?: return
        val episodes = repository.episodesForSubscription(subscriptionId)
        viewModelScope.launch {
            playerGateway.playEpisodes(
                episodes = episodes,
                startEpisodeId = episodeId,
                subscriptionTitle = subscription.title,
            )
            push(WearPodScreen.Player)
        }
    }

    fun playRandom(subscriptionId: String) {
        val subscription = repository.subscription(subscriptionId) ?: return
        val episodes = repository.episodesForSubscription(subscriptionId)
        val startEpisode = episodes.randomOrNull() ?: return
        viewModelScope.launch {
            playerGateway.playEpisodes(
                episodes = episodes,
                startEpisodeId = startEpisode.id,
                subscriptionTitle = subscription.title,
                shuffleQueue = true,
            )
            push(WearPodScreen.Player)
        }
    }

    fun queueEpisodeDownload(episode: Episode) {
        viewModelScope.launch {
            when (episode.downloadState) {
                com.sjtech.wearpod.data.model.DownloadState.DOWNLOADED -> {
                    repository.deleteDownloadedEpisode(episode.id)
                    showBanner("已删除离线音频")
                }

                com.sjtech.wearpod.data.model.DownloadState.QUEUED,
                com.sjtech.wearpod.data.model.DownloadState.DOWNLOADING,
                -> {
                    downloadScheduler.cancelEpisode(episode.id)
                    repository.resetEpisodeDownload(episode.id)
                    showBanner("已取消下载")
                }

                else -> {
                    downloadScheduler.enqueueEpisode(episode)
                    showBanner("已加入下载队列")
                }
            }
        }
    }

    fun downloadAll(subscriptionId: String) {
        val episodes = repository.episodesForSubscription(subscriptionId)
            .filter {
                it.downloadState == com.sjtech.wearpod.data.model.DownloadState.NOT_DOWNLOADED ||
                    it.downloadState == com.sjtech.wearpod.data.model.DownloadState.FAILED
            }
        viewModelScope.launch {
            downloadScheduler.enqueueAll(episodes)
            showBanner("开始下载 ${episodes.size} 期")
        }
    }

    fun clearDownloads() {
        viewModelScope.launch {
            downloadScheduler.cancelAll()
            repository.clearAllDownloads()
            showBanner("缓存已清空")
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            playerGateway.togglePlayPause()
        }
    }

    fun seekBackward() {
        viewModelScope.launch {
            playerGateway.seekBy(-10_000L)
        }
    }

    fun seekForward() {
        viewModelScope.launch {
            playerGateway.seekBy(30_000L)
        }
    }

    fun previousQueueItem() {
        viewModelScope.launch {
            playerGateway.skipToPrevious()
        }
    }

    fun nextQueueItem() {
        viewModelScope.launch {
            playerGateway.skipToNext()
        }
    }

    fun playQueueItem(episodeId: String) {
        viewModelScope.launch {
            playerGateway.playQueueItem(episodeId)
        }
    }

    fun cycleSpeed() {
        viewModelScope.launch {
            val speed = playerGateway.cyclePlaybackSpeed()
            showBanner("${speed}x")
        }
    }

    fun startSleepTimer(minutes: Int) {
        viewModelScope.launch {
            playerGateway.startSleepTimer(minutes)
            showBanner("${minutes} 分钟后暂停")
        }
    }

    fun clearSleepTimer() {
        viewModelScope.launch {
            playerGateway.clearSleepTimer()
            showBanner("已关闭睡眠定时")
        }
    }

    private fun showBanner(message: String) {
        bannerMessage = message
        viewModelScope.launch {
            delay(2_200L)
            if (bannerMessage == message) {
                bannerMessage = null
            }
        }
    }

    private fun syncBackState() {
        canGoBack = history.isNotEmpty()
    }

    private suspend fun enqueueAutoDownloads(subscriptionId: String) {
        val candidates = repository.autoDownloadCandidates(subscriptionId)
        if (candidates.isNotEmpty()) {
            downloadScheduler.enqueueAll(candidates)
        }
    }
}
