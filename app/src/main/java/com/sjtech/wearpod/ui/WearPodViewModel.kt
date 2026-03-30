package com.sjtech.wearpod.ui

import android.content.Context
import com.sjtech.wearpod.core.NetworkStatusMonitor
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sjtech.wearpod.R
import com.sjtech.wearpod.data.model.Episode
import com.sjtech.wearpod.data.model.PhoneImportPreview
import com.sjtech.wearpod.data.model.PhoneImportSessionStatus
import com.sjtech.wearpod.data.repository.WearPodRepository
import com.sjtech.wearpod.download.EpisodeDownloadScheduler
import com.sjtech.wearpod.playback.AudioOutputKind
import com.sjtech.wearpod.playback.AudioOutputController
import com.sjtech.wearpod.playback.AudioOutputSnapshot
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
    data object PhoneImport : WearPodScreen
    data object PhoneExport : WearPodScreen
    data object Downloads : WearPodScreen
    data object DownloadSettings : WearPodScreen
    data object About : WearPodScreen
    data class PodcastDetail(val subscriptionId: String) : WearPodScreen
    data object Player : WearPodScreen
}

enum class EpisodeFilter {
    ALL,
    UNPLAYED,
    DOWNLOADED,
}

enum class AppLanguage(val languageTag: String?) {
    SYSTEM(null),
    ZH_CN("zh-CN"),
    ENGLISH("en");

    companion object {
        fun fromLanguageTags(tags: String): AppLanguage = when {
            tags.startsWith("zh", ignoreCase = true) -> ZH_CN
            tags.startsWith("en", ignoreCase = true) -> ENGLISH
            else -> SYSTEM
        }
    }
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

enum class PhoneExportStage {
    IDLE,
    CREATING,
    READY,
    ERROR,
}

data class PhoneExportUiState(
    val stage: PhoneExportStage = PhoneExportStage.IDLE,
    val sessionId: String? = null,
    val shortCode: String? = null,
    val mobileUrl: String? = null,
    val expiresAtEpochMillis: Long? = null,
    val outlineCount: Int = 0,
    val error: String? = null,
)

class WearPodViewModel(
    private val appContext: Context,
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
    private var phoneExportCreateJob: Job? = null
    private var lastObservedAudioOutput: AudioOutputSnapshot? = null

    var currentScreen by mutableStateOf<WearPodScreen>(WearPodScreen.Home)
        private set
    val previousScreen: WearPodScreen?
        get() = history.lastOrNull()
    var canGoBack by mutableStateOf(false)
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
    var phoneExportState by mutableStateOf(PhoneExportUiState())
        private set
    var languagePreference by mutableStateOf(resolveLanguagePreference())
        private set

    val snapshot = repository.snapshot
    val playerState = playerGateway.playerState
    val audioOutputState = audioOutputController.state
    val volumeState = volumeController.state
    val isOnline = networkStatusMonitor.isOnline

    private fun text(@StringRes id: Int, vararg args: Any): String = appContext.getString(id, *args)

    private fun resolveLanguagePreference(): AppLanguage =
        AppLanguage.fromLanguageTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())

    fun openRoot(screen: WearPodScreen) {
        clearPhoneBridgeStateForNavigation()
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

    fun openAbout() {
        push(WearPodScreen.About)
    }

    fun syncLanguagePreference() {
        languagePreference = resolveLanguagePreference()
    }

    fun setLanguage(language: AppLanguage) {
        if (languagePreference == language) return
        val locales = language.languageTag?.let(LocaleListCompat::forLanguageTags)
            ?: LocaleListCompat.getEmptyLocaleList()
        AppCompatDelegate.setApplicationLocales(locales)
        languagePreference = language
    }

    fun openImport() {
        openPhoneImport()
    }

    fun openPhoneImport() {
        if (!repository.isPhoneImportAvailable()) {
            showBanner(text(R.string.banner_phone_import_service_unavailable))
            return
        }
        if (!isOnline.value) {
            showBanner(text(R.string.banner_phone_import_requires_network))
            return
        }
        push(WearPodScreen.PhoneImport)
        createPhoneImportSession()
    }

    fun openPhoneExport() {
        if (!repository.isPhoneImportAvailable()) {
            showBanner(text(R.string.banner_phone_export_service_unavailable))
            return
        }
        if (snapshot.value.subscriptions.isEmpty()) {
            showBanner(text(R.string.banner_no_exportable_subscriptions))
            return
        }
        if (!isOnline.value) {
            showBanner(text(R.string.banner_phone_export_requires_network))
            return
        }
        push(WearPodScreen.PhoneExport)
        createPhoneExportSession()
    }

    fun push(screen: WearPodScreen) {
        clearPhoneBridgeStateIfLeaving(screen)
        history.addLast(currentScreen)
        currentScreen = screen
        syncBackState()
    }

    fun replaceCurrent(screen: WearPodScreen) {
        clearPhoneBridgeStateIfLeaving(screen)
        currentScreen = screen
        syncBackState()
    }

    fun back() {
        clearPhoneBridgeStateForNavigation()
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

    fun retryPhoneImportSession() {
        createPhoneImportSession()
    }

    fun retryPhoneExportSession() {
        createPhoneExportSession()
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
            showBanner(text(R.string.banner_imported_subscriptions, result.importedSubscriptions.size))
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
                    text(R.string.banner_refresh_failed_count, failedCount)
                } else {
                    text(R.string.banner_subscriptions_refreshed)
                },
            )
        }
    }

    fun requestUnsubscribe(subscriptionId: String) {
        pendingUnsubscribeId = subscriptionId
        showBanner(text(R.string.banner_long_press_unsubscribe))
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
            showBanner(text(R.string.banner_unsubscribed_show, subscription.title))
        }
    }

    fun refreshSubscription(subscriptionId: String) {
        viewModelScope.launch {
            refreshingSubscriptionId = subscriptionId
            try {
                repository.refreshSubscription(subscriptionId)
                enqueueAutoDownloads(subscriptionId)
                showBanner(text(R.string.banner_episodes_updated))
            } catch (throwable: Throwable) {
                showBanner(throwable.message ?: text(R.string.refresh_failed))
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
                showBanner(text(R.string.banner_retry_success))
            } catch (throwable: Throwable) {
                showBanner(throwable.message ?: text(R.string.banner_retry_failed))
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
                    completed && autoDeletedDownload -> text(R.string.banner_marked_played_and_cleared)
                    completed -> text(R.string.banner_marked_played)
                    else -> text(R.string.banner_marked_unplayed)
                },
            )
        }
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateDownloadSettings { settings ->
                settings.copy(wifiOnly = enabled)
            }
            showBanner(
                if (enabled) text(R.string.banner_wifi_only_downloads)
                else text(R.string.banner_any_network_downloads),
            )
        }
    }

    fun setAutoDownloadLatestCount(count: Int) {
        viewModelScope.launch {
            repository.updateDownloadSettings { settings ->
                settings.copy(autoDownloadLatestCount = count.coerceIn(0, 3))
            }
            showBanner(
                if (count <= 0) {
                    text(R.string.banner_auto_download_off)
                } else {
                    text(R.string.banner_auto_download_latest, count.coerceIn(0, 3))
                },
            )
        }
    }

    fun setBackgroundAutoDownload(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateDownloadSettings { settings ->
                settings.copy(backgroundAutoDownloadEnabled = enabled)
            }
            showBanner(
                if (enabled) text(R.string.banner_background_auto_download_on)
                else text(R.string.banner_background_auto_download_off),
            )
        }
    }

    fun setBackgroundRefreshEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateDownloadSettings { settings ->
                settings.copy(backgroundRefreshEnabled = enabled)
            }
            showBanner(
                if (enabled) text(R.string.banner_background_refresh_on)
                else text(R.string.banner_background_refresh_off),
            )
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
            showBanner(text(R.string.banner_background_refresh_every_h, normalizedHours))
        }
    }

    fun setAutoDeletePlayedDownloads(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateDownloadSettings { settings ->
                settings.copy(autoDeletePlayedDownloads = enabled)
            }
            showBanner(
                if (enabled) text(R.string.banner_auto_delete_played_on)
                else text(R.string.banner_auto_delete_played_off),
            )
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
        if (maybeOpenAudioOutputSwitcherBeforePlayback()) {
            return
        }
        playEpisodes(subscription.title, episodes, episodeId, shuffleQueue = false)
    }

    private fun playEpisodes(
        subscriptionTitle: String,
        episodes: List<Episode>,
        startEpisodeId: String,
        shuffleQueue: Boolean,
    ) {
        viewModelScope.launch {
            playerGateway.playEpisodes(
                episodes = episodes,
                startEpisodeId = startEpisodeId,
                subscriptionTitle = subscriptionTitle,
                shuffleQueue = shuffleQueue,
            )
            volumeController.refresh()
            audioOutputController.refresh().also { lastObservedAudioOutput = it }
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
            showBanner(text(R.string.banner_offline_audio_required))
            return
        }
        val startEpisode = playableEpisodes.randomOrNull() ?: return
        if (maybeOpenAudioOutputSwitcherBeforePlayback()) {
            return
        }
        playEpisodes(subscription.title, playableEpisodes, startEpisode.id, shuffleQueue = true)
    }

    fun queueEpisodeDownload(episode: Episode) {
        viewModelScope.launch {
            when (episode.downloadState) {
                com.sjtech.wearpod.data.model.DownloadState.DOWNLOADED -> {
                    repository.deleteDownloadedEpisode(episode.id)
                    showBanner(text(R.string.banner_deleted_offline_audio))
                }

                com.sjtech.wearpod.data.model.DownloadState.QUEUED,
                com.sjtech.wearpod.data.model.DownloadState.DOWNLOADING,
                -> {
                    downloadScheduler.cancelEpisode(episode.id)
                    repository.resetEpisodeDownload(episode.id)
                    showBanner(text(R.string.banner_download_canceled))
                }

                else -> {
                    downloadScheduler.enqueueEpisode(episode)
                    showBanner(text(R.string.banner_added_to_download_queue))
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
            showBanner(text(R.string.banner_start_downloading_count, episodes.size))
        }
    }

    fun clearDownloads() {
        viewModelScope.launch {
            downloadScheduler.cancelAll()
            repository.clearAllDownloads()
            showBanner(text(R.string.banner_cache_cleared))
        }
    }

    fun clearCompletedDownloads() {
        viewModelScope.launch {
            val removedCount = repository.clearCompletedDownloads()
            showBanner(
                if (removedCount > 0) {
                    text(R.string.banner_cleared_played_downloads, removedCount)
                } else {
                    text(R.string.banner_no_played_downloads)
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
                    text(R.string.banner_cleared_subscription_cache, subscription.title)
                } else {
                    text(R.string.banner_no_subscription_cache, subscription.title)
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
            if (maybeOpenAudioOutputSwitcherBeforePlayback()) {
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
            showBanner(text(R.string.banner_sleep_timer_after_minutes, minutes))
        }
    }

    fun clearSleepTimer() {
        viewModelScope.launch {
            playerGateway.clearSleepTimer()
            showBanner(text(R.string.banner_sleep_timer_off))
        }
    }

    fun syncVolume() {
        volumeController.refresh()
    }

    fun syncAudioOutput() {
        val refreshed = audioOutputController.refresh()
        val previous = lastObservedAudioOutput
        if (previous != null && previous != refreshed && playerState.value.hasMedia) {
            showBanner(audioOutputChangedMessage(refreshed))
        }
        lastObservedAudioOutput = refreshed
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

    private fun createPhoneExportSession() {
        phoneExportCreateJob?.cancel()
        phoneExportState = PhoneExportUiState(stage = PhoneExportStage.CREATING)
        phoneExportCreateJob = viewModelScope.launch {
            runCatching { repository.createPhoneExportSession() }
                .onSuccess { session ->
                    if (currentScreen != WearPodScreen.PhoneExport) {
                        return@onSuccess
                    }
                    phoneExportState = PhoneExportUiState(
                        stage = PhoneExportStage.READY,
                        sessionId = session.sessionId,
                        shortCode = session.shortCode,
                        mobileUrl = session.mobileUrl,
                        expiresAtEpochMillis = session.expiresAtEpochMillis,
                        outlineCount = session.outlineCount,
                    )
                }
                .onFailure { throwable ->
                    phoneExportState = PhoneExportUiState(
                        stage = PhoneExportStage.ERROR,
                        error = friendlyExportErrorMessage(throwable),
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
                            error = text(R.string.banner_qr_expired),
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

    private fun clearPhoneBridgeStateIfLeaving(nextScreen: WearPodScreen) {
        if (currentScreen == WearPodScreen.PhoneImport && nextScreen != WearPodScreen.PhoneImport) {
            clearPhoneImportState()
        }
        if (currentScreen == WearPodScreen.PhoneExport && nextScreen != WearPodScreen.PhoneExport) {
            clearPhoneExportState()
        }
    }

    private fun clearPhoneBridgeStateForNavigation() {
        if (currentScreen == WearPodScreen.PhoneImport) {
            clearPhoneImportState()
        }
        if (currentScreen == WearPodScreen.PhoneExport) {
            clearPhoneExportState()
        }
    }

    private fun clearPhoneImportState() {
        phoneImportCreateJob?.cancel()
        phoneImportCreateJob = null
        phoneImportPollJob?.cancel()
        phoneImportPollJob = null
        phoneImportState = PhoneImportUiState()
    }

    private fun clearPhoneExportState() {
        phoneExportCreateJob?.cancel()
        phoneExportCreateJob = null
        phoneExportState = PhoneExportUiState()
    }

    private suspend fun enqueueAutoDownloads(subscriptionId: String) {
        val candidates = repository.autoDownloadCandidates(subscriptionId)
        if (candidates.isNotEmpty()) {
            downloadScheduler.enqueueAll(candidates)
        }
    }

    private fun maybeOpenAudioOutputSwitcherBeforePlayback(): Boolean {
        if (snapshot.value.hasCompletedAudioOutputSetup) {
            return false
        }

        val currentOutput = audioOutputController.refresh().also { lastObservedAudioOutput = it }
        viewModelScope.launch {
            repository.markAudioOutputSetupCompleted()
        }
        audioOutputController.showSystemOutputSwitcher()
        showBanner(
            if (currentOutput.isExternal) {
                text(R.string.banner_continue_playback_switch_if_needed)
            } else {
                text(R.string.banner_choose_output_first)
            },
        )
        return true
    }

    private fun ensureEpisodeCanPlay(episodeId: String): Boolean {
        if (isOnline.value || repository.isEpisodeAvailableOffline(episodeId)) {
            return true
        }
        showBanner(text(R.string.banner_offline_audio_required))
        return false
    }

    private fun friendlyImportErrorMessage(
        throwable: Throwable,
        creating: Boolean,
    ): String {
        if (!isOnline.value) {
            return if (creating) {
                text(R.string.banner_phone_import_requires_network)
            } else {
                text(R.string.banner_network_disconnected_wait_retry)
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
                    text(R.string.banner_phone_import_connection_failed)
                } else {
                    text(R.string.banner_phone_import_interrupted)
                }

            else -> throwable.message ?: if (creating) {
                text(R.string.banner_create_qr_failed)
            } else {
                text(R.string.banner_fetch_import_status_failed)
            }
        }
    }

    private fun friendlyExportErrorMessage(throwable: Throwable): String {
        if (!isOnline.value) {
            return text(R.string.banner_phone_export_requires_network)
        }
        return when (throwable) {
            is SSLException,
            is SocketException,
            is IOException,
            -> text(R.string.banner_export_service_unavailable)
            else -> throwable.message ?: text(R.string.banner_create_qr_failed)
        }
    }

    private fun audioOutputChangedMessage(snapshot: AudioOutputSnapshot): String =
        when (snapshot.kind) {
            AudioOutputKind.SPEAKER -> text(R.string.banner_switched_to_watch_speaker)
            AudioOutputKind.BLUETOOTH -> text(R.string.banner_switched_to_output, snapshot.label)
            AudioOutputKind.WIRED -> text(R.string.banner_switched_to_output, snapshot.label)
            AudioOutputKind.REMOTE -> text(R.string.banner_switched_to_output, snapshot.label)
            AudioOutputKind.OTHER -> text(R.string.banner_output_switched_to, snapshot.label)
        }
}
