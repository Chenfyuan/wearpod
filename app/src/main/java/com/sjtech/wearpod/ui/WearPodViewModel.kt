package com.sjtech.wearpod.ui

import com.sjtech.wearpod.core.NetworkStatusMonitor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sjtech.wearpod.data.model.Episode
import com.sjtech.wearpod.data.model.ImportSuggestion
import com.sjtech.wearpod.data.model.PhoneImportPreview
import com.sjtech.wearpod.data.model.PhoneImportSessionStatus
import com.sjtech.wearpod.data.repository.WearPodRepository
import com.sjtech.wearpod.download.EpisodeDownloadScheduler
import com.sjtech.wearpod.playback.AudioOutputController
import com.sjtech.wearpod.playback.PlayerGateway
import com.sjtech.wearpod.playback.VolumeController
import java.io.IOException
import java.net.SocketException
import javax.net.ssl.SSLException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface WearPodScreen {
    data object Home : WearPodScreen
    data object Subscriptions : WearPodScreen
    data object Import : WearPodScreen
    data object PhoneImport : WearPodScreen
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

enum class PhoneImportStage {
    IDLE,
    CREATING,
    WAITING,
    REVIEW,
    IMPORTING,
    SUCCESS,
    ERROR,
    EXPIRED,
}

data class PhoneImportUiState(
    val stage: PhoneImportStage = PhoneImportStage.IDLE,
    val sessionId: String? = null,
    val shortCode: String? = null,
    val mobileUrl: String? = null,
    val expiresAtEpochMillis: Long? = null,
    val preview: PhoneImportPreview? = null,
    val importedCount: Int = 0,
    val duplicateCountAfterImport: Int = 0,
    val failedCount: Int = 0,
    val error: String? = null,
)

class WearPodViewModel(
    val repository: WearPodRepository,
    val playerGateway: PlayerGateway,
    private val audioOutputController: AudioOutputController,
    private val volumeController: VolumeController,
    private val downloadScheduler: EpisodeDownloadScheduler,
    private val networkStatusMonitor: NetworkStatusMonitor,
) : ViewModel() {
    private val history = ArrayDeque<WearPodScreen>()
    private var phoneImportCreateJob: Job? = null
    private var phoneImportPollJob: Job? = null

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
    var phoneImportState by mutableStateOf(PhoneImportUiState())
        private set

    val snapshot = repository.snapshot
    val playerState = playerGateway.playerState
    val audioOutputState = audioOutputController.state
    val volumeState = volumeController.state
    val isOnline = networkStatusMonitor.isOnline
    val suggestions: List<ImportSuggestion> = repository.importSuggestions

    fun openRoot(screen: WearPodScreen) {
        if (currentScreen == WearPodScreen.PhoneImport) {
            clearPhoneImportState()
        }
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

    fun openPhoneImport() {
        if (!repository.isPhoneImportAvailable()) {
            showBanner("手机导入服务未配置")
            return
        }
        if (!isOnline.value) {
            showBanner("当前无网络，手机导入需要联网")
            return
        }
        push(WearPodScreen.PhoneImport)
        createPhoneImportSession()
    }

    fun push(screen: WearPodScreen) {
        if (screen != WearPodScreen.PhoneImport) {
            clearPhoneImportIfNeeded()
        }
        history.addLast(currentScreen)
        currentScreen = screen
        syncBackState()
    }

    fun replaceCurrent(screen: WearPodScreen) {
        if (currentScreen == WearPodScreen.PhoneImport && screen != WearPodScreen.PhoneImport) {
            clearPhoneImportState()
        }
        currentScreen = screen
        syncBackState()
    }

    fun back() {
        if (currentScreen == WearPodScreen.PhoneImport) {
            clearPhoneImportState()
        }
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
        volumeController.refresh()
        audioOutputController.refresh()
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
        if (!isOnline.value) {
            importError = "当前无网络，无法导入订阅"
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

    fun retryPhoneImportSession() {
        createPhoneImportSession()
    }

    fun confirmPhoneImport() {
        val preview = phoneImportState.preview ?: return
        viewModelScope.launch {
            phoneImportState = phoneImportState.copy(
                stage = PhoneImportStage.IMPORTING,
                error = null,
            )

            val result = repository.importFeeds(preview.newFeedUrls)
            result.importedSubscriptions.forEach { subscription ->
                enqueueAutoDownloads(subscription.id)
            }

            phoneImportState = phoneImportState.copy(
                stage = PhoneImportStage.SUCCESS,
                importedCount = result.importedSubscriptions.size,
                duplicateCountAfterImport = result.duplicateCount,
                failedCount = result.failedUrls.size,
            )
            showBanner("已导入 ${result.importedSubscriptions.size} 个订阅")
        }
    }

    fun openSubscriptionsRoot() {
        openRoot(WearPodScreen.Subscriptions)
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
            val autoDeletedDownload = completed &&
                episode.downloadState == com.sjtech.wearpod.data.model.DownloadState.DOWNLOADED &&
                snapshot.value.downloadSettings.autoDeletePlayedDownloads
            repository.setEpisodeCompleted(episode.id, completed)
            showBanner(
                when {
                    completed && autoDeletedDownload -> "已标记已播并清理离线"
                    completed -> "已标记已播"
                    else -> "已标记未播"
                },
            )
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

    fun setAutoDeletePlayedDownloads(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateDownloadSettings { settings ->
                settings.copy(autoDeletePlayedDownloads = enabled)
            }
            showBanner(if (enabled) "已开启自动清理已播" else "已关闭自动清理已播")
        }
    }

    fun playContinueEpisode() {
        val memory = snapshot.value.playbackMemory.lastEpisodeId ?: return
        val episode = repository.episode(memory) ?: return
        if (!ensureEpisodeCanPlay(episode.id)) return
        playEpisode(episode.subscriptionId, episode.id)
    }

    fun playEpisode(subscriptionId: String, episodeId: String) {
        val subscription = repository.subscription(subscriptionId) ?: return
        if (!ensureEpisodeCanPlay(episodeId)) return
        val episodes = repository.episodesForSubscription(subscriptionId)
        viewModelScope.launch {
            playerGateway.playEpisodes(
                episodes = episodes,
                startEpisodeId = episodeId,
                subscriptionTitle = subscription.title,
            )
            volumeController.refresh()
            push(WearPodScreen.Player)
        }
    }

    fun playRandom(subscriptionId: String) {
        val subscription = repository.subscription(subscriptionId) ?: return
        val episodes = repository.episodesForSubscription(subscriptionId)
        val playableEpisodes =
            if (isOnline.value) {
                episodes
            } else {
                episodes.filter { repository.isEpisodeAvailableOffline(it.id) }
            }
        if (playableEpisodes.isEmpty()) {
            showBanner("当前无网络，请先下载离线音频")
            return
        }
        val startEpisode = playableEpisodes.randomOrNull() ?: return
        viewModelScope.launch {
            playerGateway.playEpisodes(
                episodes = playableEpisodes,
                startEpisodeId = startEpisode.id,
                subscriptionTitle = subscription.title,
                shuffleQueue = true,
            )
            volumeController.refresh()
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

    fun clearCompletedDownloads() {
        viewModelScope.launch {
            val removedCount = repository.clearCompletedDownloads()
            showBanner(
                if (removedCount > 0) {
                    "已清理 ${removedCount} 集已播缓存"
                } else {
                    "没有可清理的已播缓存"
                },
            )
        }
    }

    fun clearDownloadsForSubscription(subscriptionId: String) {
        val subscription = repository.subscription(subscriptionId) ?: return
        viewModelScope.launch {
            val removedCount = repository.clearDownloadsForSubscription(subscriptionId)
            showBanner(
                if (removedCount > 0) {
                    "已清理 ${subscription.title}"
                } else {
                    "${subscription.title} 没有离线缓存"
                },
            )
        }
    }

    fun togglePlayPause() {
        if (!playerState.value.isPlaying) {
            val episodeId = playerState.value.episodeId
            if (episodeId != null && !ensureEpisodeCanPlay(episodeId)) {
                return
            }
        }
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

    fun syncVolume() {
        volumeController.refresh()
    }

    fun syncAudioOutput() {
        audioOutputController.refresh()
    }

    fun syncNetworkStatus() {
        networkStatusMonitor.refresh()
    }

    fun increaseVolume() {
        volumeController.increase()
    }

    fun decreaseVolume() {
        volumeController.decrease()
    }

    fun showSystemVolumePanel() {
        volumeController.showSystemPanel()
    }

    fun openAudioOutputSwitcher() {
        audioOutputController.showSystemOutputSwitcher()
    }

    private fun createPhoneImportSession() {
        phoneImportCreateJob?.cancel()
        phoneImportPollJob?.cancel()
        phoneImportState = PhoneImportUiState(stage = PhoneImportStage.CREATING)
        phoneImportCreateJob = viewModelScope.launch {
            runCatching { repository.createPhoneImportSession() }
                .onSuccess { session ->
                    if (currentScreen != WearPodScreen.PhoneImport) {
                        return@onSuccess
                    }
                    phoneImportState = PhoneImportUiState(
                        stage = PhoneImportStage.WAITING,
                        sessionId = session.sessionId,
                        shortCode = session.shortCode,
                        mobileUrl = session.mobileUrl,
                        expiresAtEpochMillis = session.expiresAtEpochMillis,
                    )
                    startPhoneImportPolling(session.sessionId)
                }
                .onFailure { throwable ->
                    phoneImportState = PhoneImportUiState(
                        stage = PhoneImportStage.ERROR,
                        error = friendlyImportErrorMessage(throwable, creating = true),
                    )
                }
        }
    }

    private fun startPhoneImportPolling(sessionId: String) {
        phoneImportPollJob?.cancel()
        phoneImportPollJob = viewModelScope.launch {
            while (isActive && currentScreen == WearPodScreen.PhoneImport) {
                val session = runCatching { repository.fetchPhoneImportSession(sessionId) }
                    .getOrElse { throwable ->
                        phoneImportState = phoneImportState.copy(
                            stage = PhoneImportStage.ERROR,
                            error = friendlyImportErrorMessage(throwable, creating = false),
                        )
                        return@launch
                    }

                when (session.status) {
                    PhoneImportSessionStatus.PENDING -> {
                        phoneImportState = phoneImportState.copy(
                            stage = PhoneImportStage.WAITING,
                            sessionId = session.sessionId,
                            shortCode = session.shortCode,
                            mobileUrl = session.mobileUrl,
                            expiresAtEpochMillis = session.expiresAtEpochMillis,
                        )
                    }

                    PhoneImportSessionStatus.SUBMITTED -> {
                        phoneImportState = phoneImportState.copy(
                            stage = PhoneImportStage.REVIEW,
                            sessionId = session.sessionId,
                            shortCode = session.shortCode,
                            mobileUrl = session.mobileUrl,
                            expiresAtEpochMillis = session.expiresAtEpochMillis,
                            preview = repository.previewPhoneImport(
                                feedUrls = session.feedUrls,
                                invalidCount = session.invalidCount,
                                duplicateCountWithinPayload = session.duplicateCountWithinPayload,
                            ),
                            error = null,
                        )
                        phoneImportPollJob?.cancel()
                    }

                    PhoneImportSessionStatus.EXPIRED -> {
                        phoneImportState = phoneImportState.copy(
                            stage = PhoneImportStage.EXPIRED,
                            error = "二维码已过期",
                        )
                        phoneImportPollJob?.cancel()
                    }
                }

                delay(2_500L)
            }
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

    private fun clearPhoneImportIfNeeded() {
        if (currentScreen == WearPodScreen.PhoneImport) {
            clearPhoneImportState()
        }
    }

    private fun clearPhoneImportState() {
        phoneImportCreateJob?.cancel()
        phoneImportCreateJob = null
        phoneImportPollJob?.cancel()
        phoneImportPollJob = null
        phoneImportState = PhoneImportUiState()
    }

    private suspend fun enqueueAutoDownloads(subscriptionId: String) {
        val candidates = repository.autoDownloadCandidates(subscriptionId)
        if (candidates.isNotEmpty()) {
            downloadScheduler.enqueueAll(candidates)
        }
    }

    private fun ensureEpisodeCanPlay(episodeId: String): Boolean {
        if (isOnline.value || repository.isEpisodeAvailableOffline(episodeId)) {
            return true
        }
        showBanner("当前无网络，请先下载离线音频")
        return false
    }

    private fun friendlyImportErrorMessage(
        throwable: Throwable,
        creating: Boolean,
    ): String {
        if (!isOnline.value) {
            return if (creating) {
                "当前无网络，手机导入需要联网"
            } else {
                "网络已断开，等待恢复后重试"
            }
        }
        val message = throwable.message.orEmpty()
        return when {
            throwable is SocketException ||
                throwable is SSLException ||
                throwable is IOException ||
                message.contains("reset", ignoreCase = true) ||
                message.contains("handshake", ignoreCase = true) ||
                message.contains("validation", ignoreCase = true) ||
                message.contains("ssl", ignoreCase = true) ->
                if (creating) {
                    "手机导入服务连接失败，请稍后再试"
                } else {
                    "导入服务连接中断，请重试"
                }

            else -> throwable.message ?: if (creating) "创建二维码失败" else "获取导入状态失败"
        }
    }
}
