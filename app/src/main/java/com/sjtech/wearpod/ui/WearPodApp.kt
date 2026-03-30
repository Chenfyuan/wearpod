package com.sjtech.wearpod.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.BasicSwipeToDismissBox
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import com.sjtech.wearpod.BuildConfig
import com.sjtech.wearpod.R
import com.sjtech.wearpod.data.model.DownloadState
import com.sjtech.wearpod.data.model.Episode
import com.sjtech.wearpod.data.model.Subscription
import com.sjtech.wearpod.playback.AudioOutputKind
import com.sjtech.wearpod.playback.AudioOutputSnapshot
import com.sjtech.wearpod.playback.PlayerQueueItemSnapshot
import com.sjtech.wearpod.playback.PlayerSnapshot
import com.sjtech.wearpod.playback.VolumeSnapshot
import com.sjtech.wearpod.ui.components.ArtworkThumb
import com.sjtech.wearpod.ui.components.CircularProgressRing
import com.sjtech.wearpod.ui.components.DarkCard
import com.sjtech.wearpod.ui.components.MiniIconButton
import com.sjtech.wearpod.ui.components.PillButton
import com.sjtech.wearpod.ui.components.QrCodeMatrix
import com.sjtech.wearpod.ui.components.SectionTitle
import com.sjtech.wearpod.ui.components.WatchChip
import com.sjtech.wearpod.ui.components.WatchCompactChip
import com.sjtech.wearpod.ui.components.WatchMetricPill
import com.sjtech.wearpod.ui.components.WearPullRefreshIndicator
import com.sjtech.wearpod.ui.components.WatchTimeHeader
import com.sjtech.wearpod.ui.components.WatchViewport
import com.sjtech.wearpod.ui.theme.WearPodAccent
import com.sjtech.wearpod.ui.theme.WearPodBackground
import com.sjtech.wearpod.ui.theme.WearPodPrimary
import com.sjtech.wearpod.ui.theme.WearPodPrimarySoft
import com.sjtech.wearpod.ui.theme.WearPodSurface
import com.sjtech.wearpod.ui.theme.WearPodSurfaceSoft
import com.sjtech.wearpod.ui.theme.WearPodSuccess
import com.sjtech.wearpod.ui.theme.WearPodTextMuted
import com.sjtech.wearpod.ui.theme.WearPodTextPrimary
import com.sjtech.wearpod.util.formatBytes
import com.sjtech.wearpod.util.formatDurationShort
import com.sjtech.wearpod.util.formatRelativeTime
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

private val ROOT_SCREENS = listOf(
    WearPodScreen.Home,
    WearPodScreen.Subscriptions,
    WearPodScreen.Downloads,
)

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun WearPodApp(viewModel: WearPodViewModel) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val player by viewModel.playerState.collectAsStateWithLifecycle()
    val audioOutput by viewModel.audioOutputState.collectAsStateWithLifecycle()
    val volume by viewModel.volumeState.collectAsStateWithLifecycle()
    val screen = viewModel.currentScreen
    val rootIndex = rootScreenIndex(screen)
    val previousScreen = viewModel.previousScreen

    BackHandler(enabled = viewModel.canGoBack) {
        viewModel.back()
    }

    if (rootIndex != null) {
        val pagerState = rememberPagerState(initialPage = rootIndex) { ROOT_SCREENS.size }

        LaunchedEffect(rootIndex) {
            if (pagerState.currentPage != rootIndex) {
                pagerState.animateScrollToPage(rootIndex)
            }
        }

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }
                .distinctUntilChanged()
                .collect { page ->
                    val rootScreen = ROOT_SCREENS[page]
                    if (viewModel.currentScreen != rootScreen) {
                        viewModel.openRoot(rootScreen)
                    }
                }
        }

        WatchViewport(bannerMessage = viewModel.bannerMessage) {
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    RootScreenContent(
                        screen = ROOT_SCREENS[page],
                        snapshot = snapshot,
                        player = player,
                        audioOutput = audioOutput,
                        viewModel = viewModel,
                    )
                }

                PageDots(
                    pageCount = ROOT_SCREENS.size,
                    currentPage = pagerState.currentPage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp),
                )
            }
        }
    } else {
        WatchViewport(bannerMessage = viewModel.bannerMessage) {
            val swipeState = rememberSwipeToDismissBoxState()
            BasicSwipeToDismissBox(
                state = swipeState,
                onDismissed = viewModel::back,
                userSwipeEnabled = previousScreen != null,
                backgroundKey = previousScreen?.let(::screenKey) ?: "wearpod-background",
                contentKey = screenKey(screen),
            ) { isBackground ->
                val targetScreen = if (isBackground) previousScreen else screen
                if (targetScreen != null) {
                    ScreenContent(
                        screen = targetScreen,
                        snapshot = snapshot,
                        player = player,
                        audioOutput = audioOutput,
                        volume = volume,
                        viewModel = viewModel,
                        showNavigation = !isBackground,
                    )
                }
            }
        }
    }
}

@Composable
private fun RootScreenContent(
    screen: WearPodScreen,
    snapshot: com.sjtech.wearpod.data.model.AppSnapshot,
    player: PlayerSnapshot,
    audioOutput: AudioOutputSnapshot,
    viewModel: WearPodViewModel,
) {
    when (screen) {
        WearPodScreen.Home -> HomeScreen(
            snapshot = snapshot,
            player = player,
            audioOutput = audioOutput,
            onContinue = viewModel::playContinueEpisode,
            onTogglePlayback = viewModel::togglePlayPause,
            onOpenPlayer = viewModel::openPlayer,
            onOpenImport = viewModel::openImport,
            onOpenSubscription = viewModel::openSubscription,
        )

        WearPodScreen.Subscriptions -> SubscriptionsScreen(
            snapshot = snapshot,
            onBack = null,
            onOpenImport = viewModel::openImport,
            onOpenPhoneExport = viewModel::openPhoneExport,
            onOpenSubscription = viewModel::openSubscription,
            onRefresh = viewModel::refreshAll,
            onRetrySubscription = viewModel::retrySubscriptionRefresh,
            retryingSubscriptionId = viewModel.retryingSubscriptionId,
            pendingUnsubscribeId = viewModel.pendingUnsubscribeId,
            onRequestUnsubscribe = viewModel::requestUnsubscribe,
            onDismissUnsubscribeRequest = viewModel::dismissUnsubscribeRequest,
            onConfirmUnsubscribe = viewModel::unsubscribe,
        )

        WearPodScreen.Downloads -> DownloadsScreen(
            snapshot = snapshot,
            playerEpisodeId = player.episodeId,
            onBack = null,
            onPlay = { episode -> viewModel.playEpisode(episode.subscriptionId, episode.id) },
            onDelete = viewModel::queueEpisodeDownload,
            onClear = viewModel::clearDownloads,
            onClearCompleted = viewModel::clearCompletedDownloads,
            onClearSubscription = viewModel::clearDownloadsForSubscription,
            onOpenSettings = viewModel::openDownloadSettings,
        )

        else -> Unit
    }
}

@Composable
private fun ScreenContent(
    screen: WearPodScreen,
    snapshot: com.sjtech.wearpod.data.model.AppSnapshot,
    player: PlayerSnapshot,
    audioOutput: AudioOutputSnapshot,
    volume: VolumeSnapshot,
    viewModel: WearPodViewModel,
    showNavigation: Boolean,
) {
    when (screen) {
        WearPodScreen.Home,
        WearPodScreen.Subscriptions,
        WearPodScreen.Downloads,
        -> RootScreenContent(
            screen = screen,
            snapshot = snapshot,
            player = player,
            audioOutput = audioOutput,
            viewModel = viewModel,
        )

        WearPodScreen.PhoneImport -> PhoneImportScreen(
            state = viewModel.phoneImportState,
            onRetry = viewModel::retryPhoneImportSession,
            onConfirmImport = viewModel::confirmPhoneImport,
            onOpenSubscriptions = viewModel::openSubscriptionsRoot,
        )

        WearPodScreen.PhoneExport -> PhoneExportScreen(
            state = viewModel.phoneExportState,
            onRetry = viewModel::retryPhoneExportSession,
            onOpenSubscriptions = viewModel::openSubscriptionsRoot,
        )

        WearPodScreen.DownloadSettings -> DownloadSettingsScreen(
            snapshot = snapshot,
            languagePreference = viewModel.languagePreference,
            onSetWifiOnly = viewModel::setWifiOnlyDownloads,
            onSetAutoDownloadCount = viewModel::setAutoDownloadLatestCount,
            onSetBackgroundAutoDownload = viewModel::setBackgroundAutoDownload,
            onSetBackgroundRefreshEnabled = viewModel::setBackgroundRefreshEnabled,
            onSetBackgroundRefreshInterval = viewModel::setBackgroundRefreshInterval,
            onSetAutoDeletePlayedDownloads = viewModel::setAutoDeletePlayedDownloads,
            onSetLanguage = viewModel::setLanguage,
            onOpenAbout = viewModel::openAbout,
        )

        WearPodScreen.About -> AboutScreen()

        is WearPodScreen.PodcastDetail -> {
            val subscription = snapshot.subscriptions.firstOrNull { it.id == screen.subscriptionId }
            val episodes = snapshot.episodes
                .filter { it.subscriptionId == screen.subscriptionId }
                .sortedByDescending { it.publishedAtEpochMillis ?: 0L }
            val filteredEpisodes = when (viewModel.episodeFilter) {
                EpisodeFilter.ALL -> episodes
                EpisodeFilter.UNPLAYED -> episodes.filter { !it.isCompleted }
                EpisodeFilter.DOWNLOADED -> episodes.filter { it.downloadState == DownloadState.DOWNLOADED }
            }
            if (subscription != null) {
                PodcastDetailScreen(
                    snapshot = snapshot,
                    subscription = subscription,
                    episodes = filteredEpisodes,
                    allEpisodesCount = episodes.size,
                    onPlayRandom = { viewModel.playRandom(subscription.id) },
                    onDownloadAll = { viewModel.downloadAll(subscription.id) },
                    onPlayEpisode = { episode -> viewModel.playEpisode(subscription.id, episode.id) },
                    onToggleFavorite = { viewModel.toggleFavorite(subscription.id) },
                    onRefresh = { viewModel.refreshSubscription(subscription.id) },
                    onToggleDownload = viewModel::queueEpisodeDownload,
                    onTogglePlayed = viewModel::toggleEpisodeCompleted,
                    episodeFilter = viewModel.episodeFilter,
                    onEpisodeFilterChange = viewModel::updateEpisodeFilter,
                    isRefreshing = viewModel.refreshingSubscriptionId == subscription.id,
                    showNavigation = showNavigation,
                )
            }
        }

        WearPodScreen.Player -> PlayerScreen(
            snapshot = snapshot,
            playerTitle = player.title,
            playerSubtitle = player.subtitle,
            playerArtworkUrl = player.artworkUrl,
            audioOutput = audioOutput,
            playerPositionMs = player.positionMs,
            playerDurationMs = player.durationMs,
            playerSpeed = player.speed,
            isPlaying = player.isPlaying,
            queue = player.queue,
            currentQueueIndex = player.currentQueueIndex,
            hasPrevious = player.hasPrevious,
            hasNext = player.hasNext,
            onBack = viewModel::back.takeIf { showNavigation } ?: {},
            onTogglePlay = viewModel::togglePlayPause,
            onPrevious = viewModel::previousQueueItem,
            onNext = viewModel::nextQueueItem,
            onPlayQueueItem = viewModel::playQueueItem,
            onSeekBack = viewModel::seekBackward,
            onSeekForward = viewModel::seekForward,
            onCycleSpeed = viewModel::cycleSpeed,
            onOpenAudioOutput = viewModel::openAudioOutputSwitcher,
            onOpenVolume = viewModel::showSystemVolumePanel,
            sleepTimerEndsAtEpochMillis = snapshot.sleepTimer.endsAtEpochMillis,
            sleepTimerPresetMinutes = snapshot.sleepTimer.presetMinutes,
            onStartSleepTimer = viewModel::startSleepTimer,
            onClearSleepTimer = viewModel::clearSleepTimer,
            showNavigation = showNavigation,
        )
    }
}

private fun rootScreenIndex(screen: WearPodScreen): Int? = when (screen) {
    WearPodScreen.Home -> 0
    WearPodScreen.Subscriptions -> 1
    WearPodScreen.Downloads -> 2
    else -> null
}

private fun screenKey(screen: WearPodScreen): String = when (screen) {
    WearPodScreen.Home -> "home"
    WearPodScreen.Subscriptions -> "subscriptions"
    WearPodScreen.PhoneImport -> "phone-import"
    WearPodScreen.PhoneExport -> "phone-export"
    WearPodScreen.Downloads -> "downloads"
    WearPodScreen.DownloadSettings -> "download-settings"
    WearPodScreen.About -> "about"
    is WearPodScreen.PodcastDetail -> "podcast-${screen.subscriptionId}"
    WearPodScreen.Player -> "player"
}

@Composable
private fun PageDots(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .width(if (index == currentPage) 16.dp else 5.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (index == currentPage) WearPodPrimary else Color(0x66FFFFFF)),
            )
        }
    }
}

@Composable
private fun RootScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth(),
    ) {
        WatchTimeHeader(color = WearPodTextMuted)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            color = WearPodPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .width(38.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(WearPodPrimary),
        )
    }
}

@Composable
private fun HomeScreen(
    snapshot: com.sjtech.wearpod.data.model.AppSnapshot,
    player: PlayerSnapshot,
    audioOutput: AudioOutputSnapshot,
    onContinue: () -> Unit,
    onTogglePlayback: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenSubscription: (String) -> Unit,
) {
    val context = LocalContext.current
    val favoriteSubscriptions = snapshot.subscriptions
        .filter { subscription -> snapshot.favoriteSubscriptionIds.contains(subscription.id) }
        .sortedByDescending { subscription ->
            snapshot.episodes
                .filter { it.subscriptionId == subscription.id }
                .maxOfOrNull { it.publishedAtEpochMillis ?: 0L } ?: 0L
        }
        .take(3)

    val continueEpisode = snapshot.playbackMemory.lastEpisodeId?.let { id ->
        snapshot.episodes.firstOrNull { it.id == id }
    } ?: snapshot.episodes.maxByOrNull { it.lastPlayedAtEpochMillis ?: 0L }
    val activeEpisode = player.episodeId?.let { episodeId ->
        snapshot.episodes.firstOrNull { it.id == episodeId }
    }
    val cardTitle = when {
        player.hasMedia && player.title.isNotBlank() -> player.title
        activeEpisode != null -> activeEpisode.title
        continueEpisode != null -> continueEpisode.title
        else -> stringResource(R.string.home_start_listening)
    }
    val cardArtworkUrl = when {
        player.hasMedia && !player.artworkUrl.isNullOrBlank() -> player.artworkUrl
        activeEpisode?.artworkUrl != null -> activeEpisode.artworkUrl
        continueEpisode?.artworkUrl != null -> continueEpisode.artworkUrl
        else -> null
    }
    val cardSubtitle = when {
        player.hasMedia && player.durationMs > 0 -> {
            "${formatDurationShort((player.positionMs / 1000L).toInt())} / ${formatDurationShort((player.durationMs / 1000L).toInt())}"
        }
        player.hasMedia && player.subtitle.isNotBlank() -> player.subtitle
        continueEpisode != null -> {
            "${formatDurationShort(continueEpisode.durationSeconds)} • ${formatRelativeTime(context, continueEpisode.lastPlayedAtEpochMillis ?: continueEpisode.publishedAtEpochMillis)}"
        }
        else -> stringResource(R.string.home_scan_import_subtitle)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item {
            RootScreenHeader(title = stringResource(R.string.home_title))
        }

        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                HomePlaybackCard(
                    title = cardTitle,
                    subtitle = cardSubtitle,
                    artworkUrl = cardArtworkUrl,
                    isPlaying = player.hasMedia && player.isPlaying,
                    audioOutput = if (player.hasMedia) audioOutput else null,
                    onPlayClick = when {
                        player.hasMedia -> onTogglePlayback
                        continueEpisode != null -> onContinue
                        else -> onOpenImport
                    },
                    onClick = when {
                        player.hasMedia -> onOpenPlayer
                        continueEpisode != null -> onContinue
                        else -> onOpenImport
                    },
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                WatchCompactChip(
                    text = stringResource(R.string.home_favorites_count, favoriteSubscriptions.size),
                    highlighted = favoriteSubscriptions.isNotEmpty(),
                )
            }
        }

        if (favoriteSubscriptions.isNotEmpty()) {
            items(favoriteSubscriptions, key = { it.id }) { subscription ->
                val latestEpisode = snapshot.episodes
                    .filter { it.subscriptionId == subscription.id }
                    .maxByOrNull { it.publishedAtEpochMillis ?: 0L }
                WatchChip(
                    title = subscription.title,
                    subtitle = latestEpisode?.title ?: stringResource(R.string.home_no_episode),
                    titleMarquee = true,
                    subtitleMarquee = true,
                    leading = {
                        ArtworkThumb(subscription.artworkUrl, modifier = Modifier.size(40.dp))
                    },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = WearPodTextMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    onClick = { onOpenSubscription(subscription.id) },
                )
            }
        }
    }
}

@Composable
private fun HomePlaybackCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    isPlaying: Boolean,
    audioOutput: AudioOutputSnapshot?,
    onPlayClick: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = Color(0x22000000),
                spotColor = Color(0x22000000),
            )
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0x0BFFFFFF))
            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0x14FFFFFF))
                        .border(1.dp, Color(0x24FFFFFF), CircleShape)
                        .clickable(onClick = onPlayClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                    ) {
                        if (artworkUrl != null) {
                            AsyncImage(
                                model = artworkUrl,
                                contentDescription = null,
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(Color(0xFFFFAA93), WearPodPrimary),
                                        ),
                                    ),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color(0x380B080F)),
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0x14FFFFFF),
                                            Color.Transparent,
                                            Color(0x4A0B080F),
                                        ),
                                    ),
                                ),
                        )
                    }
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = WearPodTextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = title,
                        color = WearPodTextPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(iterations = Int.MAX_VALUE),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        color = WearPodTextMuted,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = if (audioOutput != null) 44.dp else 0.dp),
                    )
                }
            }
            if (audioOutput != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = 2.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x18FFFFFF))
                        .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(20.dp))
                        .padding(horizontal = 4.dp, vertical = 1.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = when (audioOutput.kind) {
                            AudioOutputKind.SPEAKER -> Icons.Rounded.VolumeUp
                            else -> Icons.Rounded.Headset
                        },
                        contentDescription = null,
                        tint = WearPodTextPrimary.copy(alpha = 0.8f),
                        modifier = Modifier.size(7.dp),
                    )
                    Text(
                        text = compactAudioOutputLabel(audioOutput),
                        color = WearPodTextPrimary.copy(alpha = 0.8f),
                        fontSize = 6.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionsScreen(
    snapshot: com.sjtech.wearpod.data.model.AppSnapshot,
    onBack: (() -> Unit)?,
    onOpenImport: () -> Unit,
    onOpenPhoneExport: () -> Unit,
    onOpenSubscription: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetrySubscription: (String) -> Unit,
    retryingSubscriptionId: String?,
    pendingUnsubscribeId: String?,
    onRequestUnsubscribe: (String) -> Unit,
    onDismissUnsubscribeRequest: () -> Unit,
    onConfirmUnsubscribe: (String) -> Unit,
) {
    val failedSubscriptions = snapshot.subscriptions.filter { !it.lastRefreshError.isNullOrBlank() }
    val subscriptionsTitle = stringResource(R.string.subscriptions_title)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            if (onBack != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiniIconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = WearPodTextPrimary)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        WatchTimeHeader(color = WearPodTextMuted)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = subscriptionsTitle,
                        color = WearPodPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .width(38.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(WearPodPrimary),
                    )
                }
            } else {
                RootScreenHeader(title = subscriptionsTitle)
            }
        }

        item {
            WatchChip(
                title = stringResource(R.string.phone_import_title),
                subtitle = stringResource(R.string.phone_import_card_subtitle),
                prominent = true,
                leading = {
                    Icon(
                        Icons.Rounded.Link,
                        contentDescription = null,
                        tint = WearPodBackground,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailing = {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = WearPodPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                },
                onClick = onOpenImport,
            )
        }

        item {
            WatchChip(
                title = stringResource(R.string.phone_export_title),
                subtitle = stringResource(R.string.phone_export_card_subtitle),
                leading = {
                    Icon(
                        Icons.Rounded.CloudDownload,
                        contentDescription = null,
                        tint = WearPodTextPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailing = {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = WearPodTextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                },
                onClick = onOpenPhoneExport,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WatchCompactChip(
                    text = stringResource(R.string.subscriptions_count, snapshot.subscriptions.size),
                    highlighted = snapshot.subscriptions.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                )
                WatchCompactChip(
                    text = stringResource(R.string.refresh),
                    modifier = Modifier.weight(1f),
                    leading = {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = WearPodTextPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                    },
                    onClick = onRefresh,
                )
            }
        }

        if (failedSubscriptions.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    WatchCompactChip(
                        text = stringResource(R.string.refresh_failed_count, failedSubscriptions.size),
                        highlighted = true,
                    )
                }
            }

            items(failedSubscriptions, key = { "failed-${it.id}" }) { subscription ->
                WatchChip(
                    title = subscription.title,
                    subtitle = if (retryingSubscriptionId == subscription.id) {
                        stringResource(R.string.refresh_retrying)
                    } else {
                        subscription.lastRefreshError ?: stringResource(R.string.refresh_failed)
                    },
                    prominent = true,
                    titleMarquee = true,
                    subtitleMarquee = true,
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FF7B5B)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = WearPodPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = WearPodPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    onClick = { onRetrySubscription(subscription.id) },
                )
            }
        }

        if (snapshot.subscriptions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, bottom = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.subscriptions_empty), color = WearPodTextMuted, textAlign = TextAlign.Center)
                }
            }
        } else {
            items(snapshot.subscriptions, key = { it.id }) { subscription ->
                val latestEpisode = snapshot.episodes
                    .filter { it.subscriptionId == subscription.id }
                    .maxByOrNull { it.publishedAtEpochMillis ?: 0L }
                val isPendingUnsubscribe = pendingUnsubscribeId == subscription.id
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WatchChip(
                        title = subscription.title,
                        subtitle = if (isPendingUnsubscribe) stringResource(R.string.confirm_unsubscribe_hint) else latestEpisode?.title ?: stringResource(R.string.no_new_episode),
                        prominent = isPendingUnsubscribe,
                        titleMarquee = true,
                        subtitleMarquee = !isPendingUnsubscribe,
                        onLongClick = {
                            if (isPendingUnsubscribe) {
                                onDismissUnsubscribeRequest()
                            } else {
                                onRequestUnsubscribe(subscription.id)
                            }
                        },
                        leading = {
                            ArtworkThumb(subscription.artworkUrl, modifier = Modifier.size(40.dp))
                        },
                        trailing = {
                            Icon(
                                if (isPendingUnsubscribe) Icons.Rounded.DeleteOutline else Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = if (isPendingUnsubscribe) WearPodPrimary else WearPodTextMuted,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        onClick = {
                            if (isPendingUnsubscribe) {
                                onConfirmUnsubscribe(subscription.id)
                            } else {
                                onOpenSubscription(subscription.id)
                            }
                        },
                    )
                    if (isPendingUnsubscribe) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            WatchCompactChip(
                                text = stringResource(R.string.cancel),
                                modifier = Modifier.weight(1f),
                                onClick = onDismissUnsubscribeRequest,
                            )
                            WatchCompactChip(
                                text = stringResource(R.string.confirm_unsubscribe),
                                modifier = Modifier.weight(1f),
                                highlighted = true,
                                onClick = { onConfirmUnsubscribe(subscription.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneImportScreen(
    state: PhoneImportUiState,
    onRetry: () -> Unit,
    onConfirmImport: () -> Unit,
    onOpenSubscriptions: () -> Unit,
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.stage, state.expiresAtEpochMillis) {
        while (state.stage == PhoneImportStage.WAITING && state.expiresAtEpochMillis != null) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val secondsRemaining = remember(state.expiresAtEpochMillis, nowMillis) {
        state.expiresAtEpochMillis
            ?.minus(nowMillis)
            ?.div(1_000L)
            ?.coerceAtLeast(0L)
            ?.toInt()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                WatchTimeHeader(color = WearPodTextMuted)
            }
        }

        item {
            SectionTitle(
                title = stringResource(R.string.phone_import_title),
                subtitle = when (state.stage) {
                    PhoneImportStage.CREATING -> stringResource(R.string.phone_import_stage_creating)
                    PhoneImportStage.WAITING -> stringResource(R.string.phone_import_stage_waiting)
                    PhoneImportStage.REVIEW -> stringResource(R.string.phone_import_stage_review)
                    PhoneImportStage.IMPORTING -> stringResource(R.string.phone_import_stage_importing)
                    PhoneImportStage.SUCCESS -> stringResource(R.string.phone_import_stage_success)
                    PhoneImportStage.EXPIRED -> stringResource(R.string.phone_import_stage_expired)
                    PhoneImportStage.ERROR -> stringResource(R.string.phone_import_stage_error)
                    else -> stringResource(R.string.phone_import_stage_idle)
                },
            )
        }

        when (state.stage) {
            PhoneImportStage.CREATING -> item {
                DarkCard {
                    Text(
                        stringResource(R.string.phone_import_creating_session),
                        color = WearPodTextPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            PhoneImportStage.WAITING -> {
                item {
                    DarkCard {
                        if (!state.mobileUrl.isNullOrBlank()) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                QrCodeMatrix(
                                    data = state.mobileUrl,
                                    modifier = Modifier.size(132.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        Text(
                            stringResource(R.string.phone_import_scan_hint),
                            color = WearPodTextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        if (!state.shortCode.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                WatchCompactChip(
                                    text = stringResource(R.string.phone_short_code, state.shortCode ?: ""),
                                    highlighted = true,
                                )
                            }
                        }
                        if (secondsRemaining != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.phone_seconds_remaining, secondsRemaining),
                                color = WearPodTextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                item {
                    Text(
                        stringResource(R.string.phone_import_waiting_hint),
                        color = WearPodTextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            PhoneImportStage.REVIEW -> {
                val preview = state.preview
                if (preview != null) {
                    item {
                        DarkCard {
                            Text(
                                stringResource(R.string.phone_import_ready_count, preview.newCount),
                                color = WearPodTextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                WatchMetricPill(
                                    label = stringResource(R.string.metric_new),
                                    value = preview.newCount.toString(),
                                    modifier = Modifier.weight(1f),
                                )
                                WatchMetricPill(
                                    label = stringResource(R.string.metric_duplicate),
                                    value = preview.duplicateCount.toString(),
                                    modifier = Modifier.weight(1f),
                                )
                                WatchMetricPill(
                                    label = stringResource(R.string.metric_invalid),
                                    value = preview.invalidCount.toString(),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    item {
                        PillButton(
                            text = stringResource(R.string.confirm_import),
                            background = WearPodPrimary,
                            foreground = WearPodBackground,
                            modifier = Modifier.fillMaxWidth(),
                            leading = {
                                Icon(
                                    Icons.Rounded.CloudDownload,
                                    contentDescription = null,
                                    tint = WearPodBackground,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            onClick = onConfirmImport,
                        )
                    }
                }
            }

            PhoneImportStage.IMPORTING -> item {
                DarkCard {
                    Text(
                        stringResource(R.string.phone_import_syncing),
                        color = WearPodTextPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            PhoneImportStage.SUCCESS -> {
                item {
                    DarkCard {
                        Text(
                            stringResource(R.string.phone_imported_count, state.importedCount),
                            color = WearPodTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            WatchMetricPill(
                                label = stringResource(R.string.metric_duplicate),
                                value = state.duplicateCountAfterImport.toString(),
                                modifier = Modifier.weight(1f),
                            )
                            WatchMetricPill(
                                label = stringResource(R.string.metric_failed),
                                value = state.failedCount.toString(),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                item {
                    PillButton(
                        text = stringResource(R.string.view_subscriptions),
                        background = WearPodPrimary,
                        foreground = WearPodBackground,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenSubscriptions,
                    )
                }
            }

            PhoneImportStage.ERROR,
            PhoneImportStage.EXPIRED,
            -> item {
                DarkCard {
                    Text(
                        state.error ?: stringResource(R.string.phone_import_session_unavailable),
                        color = WearPodTextPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PillButton(
                        text = stringResource(R.string.regenerate_qr),
                        background = WearPodSurfaceSoft,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onRetry,
                    )
                }
            }

            PhoneImportStage.IDLE -> item {
                PillButton(
                    text = stringResource(R.string.generate_qr),
                    background = WearPodPrimary,
                    foreground = WearPodBackground,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRetry,
                )
            }
        }
    }
}

@Composable
private fun PhoneExportScreen(
    state: PhoneExportUiState,
    onRetry: () -> Unit,
    onOpenSubscriptions: () -> Unit,
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.stage, state.expiresAtEpochMillis) {
        while (state.stage == PhoneExportStage.READY && state.expiresAtEpochMillis != null) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val secondsRemaining = remember(state.expiresAtEpochMillis, nowMillis) {
        state.expiresAtEpochMillis
            ?.minus(nowMillis)
            ?.div(1_000L)
            ?.coerceAtLeast(0L)
            ?.toInt()
    }
    val isExpired = state.stage == PhoneExportStage.READY &&
        state.expiresAtEpochMillis?.let { it <= nowMillis } == true

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                WatchTimeHeader(color = WearPodTextMuted)
            }
        }

        item {
            SectionTitle(
                title = stringResource(R.string.phone_export_title),
                subtitle = when {
                    state.stage == PhoneExportStage.CREATING -> stringResource(R.string.phone_export_stage_creating)
                    isExpired -> stringResource(R.string.phone_export_stage_expired)
                    state.stage == PhoneExportStage.READY -> stringResource(R.string.phone_export_stage_ready)
                    state.stage == PhoneExportStage.ERROR -> stringResource(R.string.phone_export_stage_error)
                    else -> stringResource(R.string.phone_export_stage_idle)
                },
            )
        }

        when {
            state.stage == PhoneExportStage.CREATING -> item {
                DarkCard {
                    Text(
                        stringResource(R.string.phone_export_creating_session),
                        color = WearPodTextPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            state.stage == PhoneExportStage.READY && !isExpired -> {
                item {
                    DarkCard {
                        if (!state.mobileUrl.isNullOrBlank()) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                QrCodeMatrix(
                                    data = state.mobileUrl,
                                    modifier = Modifier.size(132.dp),
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        Text(
                            stringResource(R.string.phone_export_scan_hint),
                            color = WearPodTextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            WatchMetricPill(
                                label = stringResource(R.string.metric_subscriptions),
                                value = state.outlineCount.toString(),
                                modifier = Modifier.weight(1f),
                            )
                            WatchCompactChip(
                                text = stringResource(R.string.phone_short_code, state.shortCode ?: "--"),
                                highlighted = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (secondsRemaining != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.phone_seconds_remaining, secondsRemaining),
                                color = WearPodTextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                item {
                    Text(
                        stringResource(R.string.phone_export_saved_hint),
                        color = WearPodTextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }

                item {
                    PillButton(
                        text = stringResource(R.string.back_to_subscriptions),
                        background = WearPodSurfaceSoft,
                        foreground = WearPodTextPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenSubscriptions,
                    )
                }
            }

            state.stage == PhoneExportStage.ERROR || isExpired -> {
                item {
                    DarkCard {
                        Text(
                            state.error ?: stringResource(R.string.phone_export_expired_error),
                            color = WearPodTextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PillButton(
                            text = stringResource(R.string.regenerate_qr),
                            background = WearPodSurfaceSoft,
                            foreground = WearPodTextPrimary,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onRetry,
                        )
                    }
                }

                item {
                    PillButton(
                        text = stringResource(R.string.back_to_subscriptions),
                        background = WearPodPrimary,
                        foreground = WearPodBackground,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenSubscriptions,
                    )
                }
            }

            else -> item {
                Text(
                    stringResource(R.string.phone_export_idle_hint),
                    color = WearPodTextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PodcastDetailScreen(
    snapshot: com.sjtech.wearpod.data.model.AppSnapshot,
    subscription: Subscription,
    episodes: List<Episode>,
    allEpisodesCount: Int,
    onPlayRandom: () -> Unit,
    onDownloadAll: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onToggleDownload: (Episode) -> Unit,
    onTogglePlayed: (Episode) -> Unit,
    episodeFilter: EpisodeFilter,
    onEpisodeFilterChange: (EpisodeFilter) -> Unit,
    isRefreshing: Boolean,
    showNavigation: Boolean,
) {
    val context = LocalContext.current
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            WearPullRefreshIndicator(
                state = pullState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            contentPadding = PaddingValues(top = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(128.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(WearPodSurface),
                ) {
                    AsyncImage(
                        model = subscription.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x1807060B),
                                        Color(0x7607060B),
                                        Color(0xF007060B),
                                    ),
                                ),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color.Transparent, Color(0x7A07060B)),
                                    radius = 420f,
                                ),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0xA6131017))
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                            ) {
                                WatchTimeHeader(color = WearPodPrimary)
                            }
                            if (showNavigation) {
                                MiniIconButton(
                                    modifier = Modifier.size(42.dp),
                                    onClick = onToggleFavorite,
                                ) {
                                    val isFavorite = snapshot.favoriteSubscriptionIds.contains(subscription.id)
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (isFavorite) WearPodAccent else WearPodTextPrimary,
                                    )
                                }
                            }
                        }
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(bottom = 8.dp),
                        ) {
                            Text(
                                text = subscription.title,
                                color = WearPodTextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .basicMarquee(iterations = Int.MAX_VALUE),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.podcast_episode_count, allEpisodesCount),
                                color = WearPodTextMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PillButton(
                        text = stringResource(R.string.shuffle_short),
                        background = WearPodPrimary,
                        foreground = WearPodBackground,
                        textSize = 10.sp,
                        horizontalPadding = 12.dp,
                        verticalPadding = 9.dp,
                        modifier = Modifier.weight(1f),
                        leading = {
                            Icon(
                                Icons.Rounded.Shuffle,
                                contentDescription = null,
                                tint = WearPodBackground,
                                modifier = Modifier.size(14.dp),
                            )
                        },
                        onClick = onPlayRandom,
                    )
                    PillButton(
                        text = stringResource(R.string.all_short),
                        background = Color(0xFF17141D),
                        foreground = WearPodTextPrimary,
                        textSize = 10.sp,
                        horizontalPadding = 12.dp,
                        verticalPadding = 9.dp,
                        modifier = Modifier.weight(1f),
                        leading = {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = null,
                                tint = WearPodTextPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        },
                        onClick = onDownloadAll,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WatchCompactChip(
                        text = stringResource(R.string.all_filter),
                        modifier = Modifier.weight(1f),
                        textSize = 10.sp,
                        highlighted = episodeFilter == EpisodeFilter.ALL,
                        onClick = { onEpisodeFilterChange(EpisodeFilter.ALL) },
                    )
                    WatchCompactChip(
                        text = stringResource(R.string.unplayed_filter),
                        modifier = Modifier.weight(1f),
                        textSize = 10.sp,
                        highlighted = episodeFilter == EpisodeFilter.UNPLAYED,
                        onClick = { onEpisodeFilterChange(EpisodeFilter.UNPLAYED) },
                    )
                    WatchCompactChip(
                        text = stringResource(R.string.downloaded_filter),
                        modifier = Modifier.weight(1f),
                        textSize = 10.sp,
                        highlighted = episodeFilter == EpisodeFilter.DOWNLOADED,
                        onClick = { onEpisodeFilterChange(EpisodeFilter.DOWNLOADED) },
                    )
                }
            }

            items(episodes, key = { it.id }) { episode ->
                val statusLabel = when (episode.downloadState) {
                    DownloadState.DOWNLOADED -> stringResource(R.string.status_cached)
                    DownloadState.QUEUED, DownloadState.DOWNLOADING -> stringResource(R.string.status_caching)
                    DownloadState.FAILED -> stringResource(R.string.status_download_failed)
                    else -> if (episode.isCompleted) stringResource(R.string.status_played) else stringResource(R.string.status_unplayed)
                }
                val statusColor = when (episode.downloadState) {
                    DownloadState.DOWNLOADED -> WearPodSuccess
                    DownloadState.QUEUED, DownloadState.DOWNLOADING -> WearPodAccent
                    DownloadState.FAILED -> WearPodPrimary
                    else -> WearPodTextMuted
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF1A171F),
                                    Color(0xFF131019),
                                    Color(0xFF0F0D13),
                                ),
                            ),
                        )
                        .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(22.dp))
                        .clickable { onPlayEpisode(episode) }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            when (episode.downloadState) {
                                DownloadState.DOWNLOADED -> EpisodeBadge(stringResource(R.string.badge_offline), WearPodSuccess)
                                DownloadState.QUEUED, DownloadState.DOWNLOADING -> EpisodeBadge(stringResource(R.string.badge_downloading), WearPodAccent)
                                DownloadState.FAILED -> EpisodeBadge(stringResource(R.string.badge_failed), WearPodPrimary)
                                else -> if (episode.isCompleted) EpisodeBadge(stringResource(R.string.badge_played), WearPodTextMuted) else EpisodeBadge(stringResource(R.string.badge_new), Color(0xFFD56BFF))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = statusLabel,
                                color = statusColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            MiniIconButton(
                                modifier = Modifier.size(28.dp),
                                onClick = { onTogglePlayed(episode) },
                            ) {
                                Icon(
                                    imageVector = if (episode.isCompleted) Icons.Rounded.Undo else Icons.Rounded.Done,
                                    contentDescription = null,
                                    tint = if (episode.isCompleted) WearPodTextMuted else WearPodSuccess,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(3.dp))
                            MiniIconButton(
                                modifier = Modifier.size(28.dp),
                                onClick = { onToggleDownload(episode) },
                            ) {
                                Icon(
                                    imageVector = if (episode.downloadState == DownloadState.NOT_DOWNLOADED ||
                                        episode.downloadState == DownloadState.FAILED
                                    ) {
                                        Icons.Rounded.Download
                                    } else {
                                        Icons.Rounded.DeleteOutline
                                    },
                                    contentDescription = null,
                                    tint = WearPodTextPrimary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(7.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ArtworkThumb(
                                imageUrl = episode.artworkUrl,
                                modifier = Modifier.size(38.dp),
                            )
                            Spacer(modifier = Modifier.width(9.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    episode.title,
                                    color = WearPodTextPrimary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .basicMarquee(iterations = Int.MAX_VALUE),
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "${formatDurationShort(episode.durationSeconds)} • ${formatRelativeTime(context, episode.publishedAtEpochMillis)}",
                                    color = WearPodTextMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
private fun PlayerScreen(
    snapshot: com.sjtech.wearpod.data.model.AppSnapshot,
    playerTitle: String,
    playerSubtitle: String,
    playerArtworkUrl: String?,
    audioOutput: AudioOutputSnapshot,
    playerPositionMs: Long,
    playerDurationMs: Long,
    playerSpeed: Float,
    isPlaying: Boolean,
    queue: List<PlayerQueueItemSnapshot>,
    currentQueueIndex: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayQueueItem: (String) -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onCycleSpeed: () -> Unit,
    onOpenAudioOutput: () -> Unit,
    onOpenVolume: () -> Unit,
    sleepTimerEndsAtEpochMillis: Long?,
    sleepTimerPresetMinutes: Int?,
    onStartSleepTimer: (Int) -> Unit,
    onClearSleepTimer: () -> Unit,
    showNavigation: Boolean,
) {
    val context = LocalContext.current
    val progress = if (playerDurationMs > 0) playerPositionMs / playerDurationMs.toFloat() else 0f
    val currentTitle = playerTitle.ifBlank {
        snapshot.playbackMemory.lastEpisodeId
            ?.let { id -> snapshot.episodes.firstOrNull { it.id == id }?.title }
            ?: stringResource(R.string.player_choose_episode)
    }
    val progressLabel = if (playerDurationMs > 0) {
        "${formatDurationShort((playerPositionMs / 1000L).toInt())} / ${formatDurationShort((playerDurationMs / 1000L).toInt())}"
    } else {
        stringResource(R.string.player_ready)
    }
    val detailsListState = rememberLazyListState()
    val hideCenterPlayback by remember(detailsListState) {
        derivedStateOf {
            detailsListState.firstVisibleItemIndex > 0 || detailsListState.firstVisibleItemScrollOffset > 12
        }
    }
    val centerButtonAlpha by animateFloatAsState(
        targetValue = if (hideCenterPlayback) 0f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "playerCenterButtonAlpha",
    )
    val centerButtonScale by animateFloatAsState(
        targetValue = if (hideCenterPlayback) 0.88f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "playerCenterButtonScale",
    )
    val sleepTimerLabel = rememberSleepTimerLabel(sleepTimerEndsAtEpochMillis)
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = playerArtworkUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x1807060B),
                            Color(0x8007060B),
                            Color(0xF207060B),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color(0xA607060B)),
                        radius = 520f,
                    ),
                ),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            state = detailsListState,
            contentPadding = PaddingValues(top = 252.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiniIconButton(
                            modifier = Modifier.size(42.dp),
                            onClick = onSeekBack,
                        ) {
                            Icon(Icons.Rounded.Replay10, contentDescription = null, tint = WearPodTextPrimary, modifier = Modifier.size(20.dp))
                        }
                        MiniIconButton(
                            modifier = Modifier.size(42.dp),
                            onClick = onCycleSpeed,
                        ) {
                            Text(
                                text = "${playerSpeed}x",
                                color = WearPodAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        MiniIconButton(
                            modifier = Modifier.size(42.dp),
                            onClick = onSeekForward,
                        ) {
                            Icon(Icons.Rounded.Forward30, contentDescription = null, tint = WearPodTextPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    WatchCompactChip(
                        text = sleepTimerLabel,
                        highlighted = sleepTimerEndsAtEpochMillis != null,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(15, 30).forEach { minutes ->
                            WatchCompactChip(
                                text = stringResource(R.string.minutes_short, minutes),
                                modifier = Modifier.weight(1f),
                                highlighted = sleepTimerEndsAtEpochMillis != null && sleepTimerPresetMinutes == minutes,
                                onClick = { onStartSleepTimer(minutes) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WatchCompactChip(
                            text = stringResource(R.string.minutes_short, 60),
                            modifier = Modifier.weight(1f),
                            highlighted = sleepTimerEndsAtEpochMillis != null && sleepTimerPresetMinutes == 60,
                            onClick = { onStartSleepTimer(60) },
                        )
                        WatchCompactChip(
                            text = stringResource(R.string.off),
                            modifier = Modifier.weight(1f),
                            highlighted = sleepTimerEndsAtEpochMillis == null,
                            onClick = onClearSleepTimer,
                        )
                    }
                }
            }
            if (queue.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        WatchCompactChip(
                            text = stringResource(R.string.queue_count, queue.size),
                            highlighted = true,
                        )
                    }
                }
                items(queue, key = { it.episodeId }) { item ->
                    val isCurrent = queue.indexOfFirst { it.episodeId == item.episodeId } == currentQueueIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF19161E), WearPodSurface),
                                ),
                            )
                            .border(
                                width = 1.dp,
                                color = if (isCurrent) Color(0x44FF7B5B) else Color(0x24FFFFFF),
                                shape = RoundedCornerShape(24.dp),
                            )
                            .clickable { onPlayQueueItem(item.episodeId) },
                    ) {
                        AsyncImage(
                            model = item.artworkUrl,
                            contentDescription = null,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xE615121A),
                                            Color(0xB0141119),
                                            Color(0x8A141118),
                                        ),
                                    ),
                                ),
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xF215121A),
                                            Color(0xC1141119),
                                            Color(0x66141119),
                                        ),
                                    ),
                                ),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isCurrent) WearPodPrimary else Color(0xCC1E1B24)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (isCurrent) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = if (isCurrent) WearPodBackground else WearPodTextPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title.ifBlank { stringResource(R.string.untitled_episode) },
                                    color = WearPodTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .basicMarquee(iterations = Int.MAX_VALUE),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isCurrent) stringResource(R.string.now_playing) else item.subtitle.ifBlank { stringResource(R.string.tap_to_switch) },
                                    color = if (isCurrent) WearPodPrimarySoft else WearPodTextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = if (isCurrent) TextOverflow.Ellipsis else TextOverflow.Clip,
                                    modifier = if (isCurrent) {
                                        Modifier.fillMaxWidth()
                                    } else {
                                        Modifier
                                            .fillMaxWidth()
                                            .basicMarquee(iterations = Int.MAX_VALUE)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        WatchTimeHeader(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp),
            color = WearPodTextPrimary.copy(alpha = 0.82f),
        )
        Text(
            text = currentTitle,
            color = WearPodTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 42.dp, start = 24.dp, end = 24.dp)
                .graphicsLayer { alpha = centerButtonAlpha }
                .basicMarquee(iterations = Int.MAX_VALUE),
        )
        Text(
            text = playerSubtitle.ifBlank { "WearPod" },
            color = WearPodPrimarySoft,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp, start = 34.dp, end = 34.dp)
                .graphicsLayer { alpha = centerButtonAlpha },
        )
        Box(
            modifier = Modifier
                .size(184.dp)
                .align(Alignment.Center)
                .graphicsLayer {
                    alpha = centerButtonAlpha
                    scaleX = centerButtonScale
                    scaleY = centerButtonScale
                },
        ) {
            PlayerOverlayCircleButton(
                modifier = Modifier.align(Alignment.CenterStart),
                enabled = !hideCenterPlayback && hasPrevious,
                onClick = onPrevious,
            ) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = null,
                    tint = WearPodTextPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Icon(
                imageVector = Icons.Rounded.Headset,
                contentDescription = null,
                tint = WearPodTextPrimary.copy(alpha = centerButtonAlpha),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = (-54).dp, y = 36.dp)
                    .clickable(onClick = onOpenAudioOutput)
                    .padding(4.dp)
                    .size(18.dp),
            )
            PlayerOverlayCircleButton(
                modifier = Modifier.align(Alignment.CenterEnd),
                enabled = !hideCenterPlayback && hasNext,
                onClick = onNext,
            ) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = null,
                    tint = WearPodTextPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Icon(
                imageVector = Icons.Rounded.VolumeUp,
                contentDescription = null,
                tint = WearPodTextPrimary.copy(alpha = centerButtonAlpha),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 54.dp, y = 36.dp)
                    .clickable(onClick = onOpenVolume)
                    .padding(4.dp)
                    .size(18.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(50.dp)
                .align(Alignment.Center)
                .graphicsLayer {
                    alpha = centerButtonAlpha
                    scaleX = centerButtonScale
                    scaleY = centerButtonScale
                },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressRing(
                progress = progress,
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 3.5.dp,
                trackColor = Color(0x22FFFFFF),
                waveEffect = true,
            )
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x8A15111A))
                    .then(
                        if (hideCenterPlayback) {
                            Modifier
                        } else {
                            Modifier.clickable(onClick = onTogglePlay)
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = WearPodTextPrimary,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        Text(
            text = progressLabel,
            color = WearPodTextPrimary.copy(alpha = 0.82f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 112.dp)
                .graphicsLayer { alpha = centerButtonAlpha },
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .graphicsLayer { alpha = centerButtonAlpha * 0.78f }
                .width(28.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.9f)),
        )
    }
}

@Composable
private fun PlayerOverlayCircleButton(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0x8A15111A))
            .then(
                if (enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun DownloadsScreen(
    snapshot: com.sjtech.wearpod.data.model.AppSnapshot,
    playerEpisodeId: String?,
    onBack: (() -> Unit)?,
    onPlay: (Episode) -> Unit,
    onDelete: (Episode) -> Unit,
    onClear: () -> Unit,
    onClearCompleted: () -> Unit,
    onClearSubscription: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val queuedEpisodes = snapshot.episodes
        .filter { episode ->
            episode.downloadState == DownloadState.QUEUED || episode.downloadState == DownloadState.DOWNLOADING
        }
        .sortedWith(
            compareByDescending<Episode> { it.downloadState == DownloadState.DOWNLOADING }
                .thenByDescending { it.publishedAtEpochMillis ?: 0L },
        )
    val downloadedEpisodes = snapshot.episodes.filter { it.downloadState == DownloadState.DOWNLOADED }
    val completedDownloadedEpisodes = downloadedEpisodes.filter { it.isCompleted }
    val failedEpisodes = snapshot.episodes
        .filter { it.downloadState == DownloadState.FAILED }
        .sortedByDescending { it.publishedAtEpochMillis ?: 0L }
    val usedBytes = downloadedEpisodes.sumOf { it.downloadedBytes.takeIf { size -> size > 0 } ?: 0L }
    val availableBytes = context.filesDir.usableSpace.coerceAtLeast(0L)
    val maxBytes = 1_500L * 1024L * 1024L
    val storageWarning = when {
        availableBytes < 256L * 1024L * 1024L -> stringResource(R.string.storage_low_warning)
        usedBytes >= (maxBytes * 0.85f).toLong() -> stringResource(R.string.storage_heavy_warning)
        else -> null
    }
    val downloadedGroups = downloadedEpisodes
        .groupBy { it.subscriptionId }
        .mapNotNull { (subscriptionId, episodes) ->
            val subscription = snapshot.subscriptions.firstOrNull { it.id == subscriptionId } ?: return@mapNotNull null
            DownloadedSubscriptionGroup(
                subscriptionId = subscriptionId,
                title = subscription.title,
                episodeCount = episodes.size,
                totalBytes = episodes.sumOf { it.downloadedBytes.takeIf { size -> size > 0 } ?: 0L },
            )
        }
        .sortedWith(compareByDescending<DownloadedSubscriptionGroup> { it.episodeCount }.thenBy { it.title.lowercase() })
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            if (onBack != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiniIconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = WearPodTextPrimary)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        WatchTimeHeader(color = WearPodTextMuted)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.downloads_title),
                        color = WearPodPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .width(38.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(WearPodPrimary),
                    )
                }
            } else {
                RootScreenHeader(title = stringResource(R.string.downloads_title))
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WatchMetricPill(
                    label = stringResource(R.string.downloads_metric_downloaded),
                    value = stringResource(R.string.downloads_metric_downloaded_value, downloadedEpisodes.size),
                    modifier = Modifier.weight(1f),
                )
                WatchMetricPill(
                    label = stringResource(R.string.downloads_metric_available),
                    value = formatBytes(availableBytes),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (storageWarning != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    WatchCompactChip(
                        text = storageWarning,
                        highlighted = true,
                    )
                }
            }
        }

        item {
            LinearProgressIndicator(
                progress = { (usedBytes / maxBytes.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp)),
                color = WearPodPrimary,
                trackColor = Color(0x22FFFFFF),
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                WatchCompactChip(
                    text = stringResource(R.string.downloads_and_refresh),
                    leading = {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = WearPodPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                    },
                    onClick = onOpenSettings,
                )
            }
        }

        if (failedEpisodes.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    WatchCompactChip(
                        text = stringResource(R.string.download_failed_count, failedEpisodes.size),
                        highlighted = true,
                    )
                }
            }

            items(failedEpisodes, key = { "failed-${it.id}" }) { episode ->
                WatchChip(
                    title = episode.title,
                    subtitle = stringResource(R.string.download_failed_retry_hint),
                    prominent = true,
                    leading = {
                        ArtworkThumb(episode.artworkUrl, modifier = Modifier.size(40.dp))
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = WearPodPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = { onDelete(episode) },
                )
            }
        }

        if (queuedEpisodes.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    WatchCompactChip(
                        text = stringResource(R.string.download_queue_count, queuedEpisodes.size),
                        highlighted = true,
                    )
                }
            }

            items(queuedEpisodes, key = { it.id }) { episode ->
                val queueSubtitle = when (episode.downloadState) {
                    DownloadState.DOWNLOADING -> {
                        val sizeText = episode.sizeBytes?.takeIf { it > 0 }?.let { total ->
                            "${formatBytes(episode.downloadedBytes)} / ${formatBytes(total)}"
                        } ?: formatBytes(episode.downloadedBytes)
                        stringResource(R.string.downloading_with_size, sizeText)
                    }

                    else -> "${stringResource(R.string.queued_status)} • ${stringResource(R.string.tap_to_cancel)}"
                }
                WatchChip(
                    title = episode.title,
                    subtitle = queueSubtitle,
                    leading = {
                        ArtworkThumb(episode.artworkUrl, modifier = Modifier.size(40.dp))
                    },
                    trailing = {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = WearPodPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = { onDelete(episode) },
                )
            }
        }

        if (downloadedEpisodes.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    WatchCompactChip(
                        text = stringResource(R.string.downloaded_count, downloadedEpisodes.size),
                        highlighted = true,
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    WatchCompactChip(
                        text = stringResource(R.string.clear_all),
                        leading = {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = WearPodPrimary,
                                modifier = Modifier.size(12.dp),
                            )
                        },
                        onClick = onClear,
                    )
                    if (completedDownloadedEpisodes.isNotEmpty()) {
                        WatchCompactChip(
                            text = stringResource(R.string.delete_played_count, completedDownloadedEpisodes.size),
                            leading = {
                                Icon(
                                    Icons.Rounded.Done,
                                    contentDescription = null,
                                    tint = WearPodPrimary,
                                    modifier = Modifier.size(12.dp),
                                )
                            },
                            onClick = onClearCompleted,
                        )
                    }
                }
            }

            if (downloadedGroups.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        WatchCompactChip(
                            text = stringResource(R.string.clear_by_podcast),
                            highlighted = true,
                        )
                    }
                }

                items(downloadedGroups, key = { "cleanup-${it.subscriptionId}" }) { group ->
                    WatchChip(
                        title = group.title,
                        subtitle = stringResource(
                            R.string.download_group_subtitle,
                            group.episodeCount,
                            formatBytes(group.totalBytes),
                        ),
                        leading = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(WearPodSurfaceSoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = WearPodPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        trailing = {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = WearPodPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = { onClearSubscription(group.subscriptionId) },
                    )
                }
            }
        }

        if (failedEpisodes.isEmpty() && queuedEpisodes.isEmpty() && downloadedEpisodes.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, bottom = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.no_download_tasks),
                        color = WearPodTextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(downloadedEpisodes, key = { it.id }) { episode ->
                WatchChip(
                    title = episode.title,
                    subtitle = "${formatDurationShort(episode.durationSeconds)} • ${formatBytes(episode.downloadedBytes)}",
                    leading = {
                        ArtworkThumb(episode.artworkUrl, modifier = Modifier.size(40.dp))
                    },
                    trailing = {
                        Icon(
                            imageVector = if (playerEpisodeId == episode.id) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = WearPodPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = { onPlay(episode) },
                )
            }
        }
    }
}

@Composable
private fun DownloadSettingsScreen(
    snapshot: com.sjtech.wearpod.data.model.AppSnapshot,
    languagePreference: AppLanguage,
    onSetWifiOnly: (Boolean) -> Unit,
    onSetAutoDownloadCount: (Int) -> Unit,
    onSetBackgroundAutoDownload: (Boolean) -> Unit,
    onSetBackgroundRefreshEnabled: (Boolean) -> Unit,
    onSetBackgroundRefreshInterval: (Int) -> Unit,
    onSetAutoDeletePlayedDownloads: (Boolean) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onOpenAbout: () -> Unit,
) {
    val settings = snapshot.downloadSettings
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                WatchTimeHeader(color = WearPodTextMuted)
            }
        }
        item {
            SectionTitle(
                title = stringResource(R.string.download_settings_title),
                subtitle = stringResource(R.string.settings_subtitle),
            )
        }
        item {
            WatchChip(
                title = stringResource(R.string.wifi_only_downloads),
                subtitle = if (settings.wifiOnly) stringResource(R.string.enabled) else stringResource(R.string.any_network),
                prominent = settings.wifiOnly,
                leading = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (settings.wifiOnly) WearPodPrimary else WearPodSurfaceSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (settings.wifiOnly) "Wi-Fi" else stringResource(R.string.network_short),
                            color = if (settings.wifiOnly) WearPodBackground else WearPodTextPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                onClick = { onSetWifiOnly(!settings.wifiOnly) },
            )
        }
        item {
            WatchChip(
                title = stringResource(R.string.background_auto_download),
                subtitle = if (settings.backgroundAutoDownloadEnabled) {
                    stringResource(R.string.auto_download_after_refresh)
                } else {
                    stringResource(R.string.manual_download_only)
                },
                prominent = settings.backgroundAutoDownloadEnabled,
                leading = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (settings.backgroundAutoDownloadEnabled) WearPodPrimary else WearPodSurfaceSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (settings.backgroundAutoDownloadEnabled) {
                                stringResource(R.string.auto_short)
                            } else {
                                stringResource(R.string.manual_short)
                            },
                            color = if (settings.backgroundAutoDownloadEnabled) WearPodBackground else WearPodTextPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                onClick = { onSetBackgroundAutoDownload(!settings.backgroundAutoDownloadEnabled) },
            )
        }
        item {
            WatchChip(
                title = stringResource(R.string.auto_delete_played),
                subtitle = if (settings.autoDeletePlayedDownloads) {
                    stringResource(R.string.auto_delete_after_played)
                } else {
                    stringResource(R.string.keep_played_cache)
                },
                prominent = settings.autoDeletePlayedDownloads,
                leading = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (settings.autoDeletePlayedDownloads) WearPodPrimary else WearPodSurfaceSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Done,
                            contentDescription = null,
                            tint = if (settings.autoDeletePlayedDownloads) WearPodBackground else WearPodTextPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                onClick = { onSetAutoDeletePlayedDownloads(!settings.autoDeletePlayedDownloads) },
            )
        }
        item {
            Text(
                text = stringResource(R.string.auto_download_latest),
                color = WearPodTextMuted,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    listOf(0 to stringResource(R.string.off), 1 to stringResource(R.string.episode_count_short, 1)),
                    listOf(2 to stringResource(R.string.episode_count_short, 2), 3 to stringResource(R.string.episode_count_short, 3)),
                ).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowOptions.forEach { (count, label) ->
                            WatchCompactChip(
                                text = label,
                                modifier = Modifier.weight(1f),
                                highlighted = settings.autoDownloadLatestCount == count,
                                onClick = { onSetAutoDownloadCount(count) },
                            )
                        }
                    }
                }
            }
        }
        item {
            WatchChip(
                title = stringResource(R.string.background_scheduled_refresh),
                subtitle = if (settings.backgroundRefreshEnabled) {
                    stringResource(R.string.refresh_every_h, settings.backgroundRefreshIntervalHours)
                } else {
                    stringResource(R.string.disabled)
                },
                prominent = settings.backgroundRefreshEnabled,
                leading = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (settings.backgroundRefreshEnabled) WearPodPrimary else WearPodSurfaceSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = if (settings.backgroundRefreshEnabled) WearPodBackground else WearPodTextPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                onClick = { onSetBackgroundRefreshEnabled(!settings.backgroundRefreshEnabled) },
            )
        }
        item {
            Text(
                text = stringResource(R.string.background_refresh_frequency),
                color = WearPodTextMuted,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(6, 12, 24).forEach { hours ->
                    WatchCompactChip(
                        text = "${hours}H",
                        modifier = Modifier.weight(1f),
                        highlighted = settings.backgroundRefreshEnabled && settings.backgroundRefreshIntervalHours == hours,
                        onClick = { onSetBackgroundRefreshInterval(hours) },
                    )
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.language_title),
                color = WearPodTextMuted,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WatchCompactChip(
                        text = stringResource(R.string.language_follow_system),
                        modifier = Modifier.weight(1f),
                        highlighted = languagePreference == AppLanguage.SYSTEM,
                        onClick = { onSetLanguage(AppLanguage.SYSTEM) },
                    )
                    WatchCompactChip(
                        text = stringResource(R.string.language_simplified_chinese),
                        modifier = Modifier.weight(1f),
                        highlighted = languagePreference == AppLanguage.ZH_CN,
                        onClick = { onSetLanguage(AppLanguage.ZH_CN) },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    WatchCompactChip(
                        text = stringResource(R.string.language_english),
                        highlighted = languagePreference == AppLanguage.ENGLISH,
                        onClick = { onSetLanguage(AppLanguage.ENGLISH) },
                    )
                }
            }
        }
        item {
            WatchChip(
                title = stringResource(R.string.about_entry_title),
                subtitle = stringResource(R.string.about_entry_subtitle),
                leading = {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = WearPodPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailing = {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = WearPodTextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                },
                onClick = onOpenAbout,
            )
        }
    }
}

@Composable
private fun AboutScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                WatchTimeHeader(color = WearPodTextMuted)
            }
        }
        item {
            SectionTitle(
                title = stringResource(R.string.about_title),
                subtitle = stringResource(R.string.about_page_subtitle),
            )
        }
        item {
            DarkCard {
                AboutInfoRow(label = stringResource(R.string.about_label_app), value = stringResource(R.string.about_app_name))
                AboutInfoRow(label = stringResource(R.string.about_label_version), value = BuildConfig.VERSION_NAME)
                AboutInfoRow(label = stringResource(R.string.about_label_company), value = stringResource(R.string.about_company_name))
                AboutInfoRow(label = stringResource(R.string.about_label_package), value = BuildConfig.APPLICATION_ID, marquee = true)
            }
        }
        item {
            DarkCard {
                Text(
                    text = stringResource(R.string.about_description),
                    color = WearPodTextPrimary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        item {
            DarkCard {
                AboutInfoRow(label = stringResource(R.string.about_label_website), value = stringResource(R.string.about_website_value), marquee = true)
                AboutInfoRow(label = stringResource(R.string.about_label_contact_email), value = stringResource(R.string.about_contact_email_value), marquee = true)
                AboutInfoRow(label = stringResource(R.string.about_label_support_channel), value = stringResource(R.string.about_support_channel_value), marquee = true)
                AboutInfoRow(label = stringResource(R.string.about_label_privacy_effective), value = stringResource(R.string.about_privacy_effective_value))
            }
        }
        item {
            DarkCard {
                Text(
                    text = stringResource(R.string.about_footer),
                    color = WearPodTextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String,
    marquee: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = WearPodTextMuted,
            fontSize = 10.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = WearPodTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = if (marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = if (marquee) {
                Modifier
                    .fillMaxWidth()
                    .basicMarquee(iterations = Int.MAX_VALUE)
            } else {
                Modifier.fillMaxWidth()
            },
        )
    }
}

private data class DownloadedSubscriptionGroup(
    val subscriptionId: String,
    val title: String,
    val episodeCount: Int,
    val totalBytes: Long,
)

@Composable
private fun compactAudioOutputLabel(audioOutput: AudioOutputSnapshot): String = when (audioOutput.kind) {
    AudioOutputKind.SPEAKER -> stringResource(R.string.audio_output_compact_speaker)
    AudioOutputKind.BLUETOOTH -> stringResource(R.string.audio_output_compact_bluetooth)
    AudioOutputKind.WIRED -> stringResource(R.string.audio_output_compact_wired)
    AudioOutputKind.REMOTE -> stringResource(R.string.audio_output_compact_remote)
    AudioOutputKind.OTHER -> audioOutput.label.ifBlank { stringResource(R.string.audio_output_compact_generic) }
}

@Composable
private fun rememberSleepTimerLabel(
    endsAtEpochMillis: Long?,
): String {
    val now by produceState(initialValue = System.currentTimeMillis(), key1 = endsAtEpochMillis) {
        if (endsAtEpochMillis == null) return@produceState
        while (true) {
            value = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000L)
        }
    }
    return if (endsAtEpochMillis == null) {
        stringResource(R.string.sleep_timer)
    } else {
        val remainingMs = (endsAtEpochMillis - now).coerceAtLeast(0L)
        val remainingMinutes = ((remainingMs + 59_999L) / 60_000L).coerceAtLeast(1L)
        stringResource(R.string.sleep_minutes_remaining, remainingMinutes)
    }
}

@Composable
private fun EpisodeBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 1.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
