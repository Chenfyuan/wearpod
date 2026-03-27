package com.sjtech.wearpod.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.BasicSwipeToDismissBox
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import com.sjtech.wearpod.data.model.DownloadState
import com.sjtech.wearpod.data.model.Episode
import com.sjtech.wearpod.data.model.Subscription
import com.sjtech.wearpod.playback.PlayerQueueItemSnapshot
import com.sjtech.wearpod.playback.PlayerSnapshot
import com.sjtech.wearpod.playback.VolumeSnapshot
import com.sjtech.wearpod.ui.components.ArtworkThumb
import com.sjtech.wearpod.ui.components.CircularProgressRing
import com.sjtech.wearpod.ui.components.MiniIconButton
import com.sjtech.wearpod.ui.components.PillButton
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
    viewModel: WearPodViewModel,
) {
    when (screen) {
        WearPodScreen.Home -> HomeScreen(
            snapshot = snapshot,
            player = player,
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
            viewModel = viewModel,
        )

        WearPodScreen.Import -> ImportScreen(
            viewModel = viewModel,
        )

        WearPodScreen.DownloadSettings -> DownloadSettingsScreen(
            snapshot = snapshot,
            onSetWifiOnly = viewModel::setWifiOnlyDownloads,
            onSetAutoDownloadCount = viewModel::setAutoDownloadLatestCount,
            onSetBackgroundAutoDownload = viewModel::setBackgroundAutoDownload,
            onSetBackgroundRefreshEnabled = viewModel::setBackgroundRefreshEnabled,
            onSetBackgroundRefreshInterval = viewModel::setBackgroundRefreshInterval,
        )

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
    WearPodScreen.Import -> "import"
    WearPodScreen.Downloads -> "downloads"
    WearPodScreen.DownloadSettings -> "download-settings"
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
    onContinue: () -> Unit,
    onTogglePlayback: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenSubscription: (String) -> Unit,
) {
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
        else -> "导入一个 RSS 开始收听"
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
            "${formatDurationShort(continueEpisode.durationSeconds)} • ${formatRelativeTime(continueEpisode.lastPlayedAtEpochMillis ?: continueEpisode.publishedAtEpochMillis)}"
        }
        else -> "公开 RSS，手表独立收听"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item {
            RootScreenHeader(title = "首页")
        }

        item {
            HomePlaybackCard(
                title = cardTitle,
                subtitle = cardSubtitle,
                artworkUrl = cardArtworkUrl,
                isPlaying = player.hasMedia && player.isPlaying,
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

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                WatchCompactChip(
                    text = "收藏(${favoriteSubscriptions.size})",
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
                    subtitle = latestEpisode?.title ?: "暂无节目",
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
    onPlayClick: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0x66FF7B5B), WearPodSurface),
                ),
            )
            .clickable(onClick = onClick)
            .height(104.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (artworkUrl != null) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xD50B080F),
                                Color(0xAA0B080F),
                                Color(0xD90B080F),
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0x9C0B080F)),
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFFF9B84), WearPodPrimary),
                            ),
                        )
                        .clickable(onClick = onPlayClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = WearPodBackground,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = subtitle,
                        color = WearPodTextMuted,
                        fontSize = 11.sp,
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
                        text = "订阅",
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
                RootScreenHeader(title = "订阅")
            }
        }

        item {
            WatchChip(
                title = "导入 RSS",
                subtitle = "添加新的播客订阅源",
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WatchCompactChip(
                    text = "${snapshot.subscriptions.size} 个订阅",
                    highlighted = snapshot.subscriptions.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                )
                WatchCompactChip(
                    text = "刷新",
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
                        text = "刷新失败(${failedSubscriptions.size})",
                        highlighted = true,
                    )
                }
            }

            items(failedSubscriptions, key = { "failed-${it.id}" }) { subscription ->
                WatchChip(
                    title = subscription.title,
                    subtitle = if (retryingSubscriptionId == subscription.id) {
                        "重试中..."
                    } else {
                        subscription.lastRefreshError ?: "刷新失败"
                    },
                    prominent = true,
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
                    Text("还没有订阅\n先导入一个 RSS 地址", color = WearPodTextMuted, textAlign = TextAlign.Center)
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
                        subtitle = if (isPendingUnsubscribe) "点按确认取消订阅" else latestEpisode?.title ?: "暂无新节目",
                        prominent = isPendingUnsubscribe,
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
                                text = "取消",
                                modifier = Modifier.weight(1f),
                                onClick = onDismissUnsubscribeRequest,
                            )
                            WatchCompactChip(
                                text = "确认取消",
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
private fun ImportScreen(
    viewModel: WearPodViewModel,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                WatchTimeHeader(color = WearPodTextMuted)
            }
        }

        item {
            SectionTitle(title = "导入订阅", subtitle = "输入播客 RSS 地址")
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(WearPodSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = viewModel.importUrl,
                    onValueChange = { viewModel.importUrl = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = WearPodTextPrimary,
                        fontSize = 15.sp,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (viewModel.importUrl.isBlank()) {
                            Text("输入 RSS 地址", color = WearPodTextMuted, fontSize = 14.sp)
                        }
                        innerTextField()
                    },
                )
            }
        }

        if (viewModel.importError != null) {
            item {
                Text(viewModel.importError!!, color = WearPodPrimarySoft, fontSize = 12.sp)
            }
        }

        item {
            PillButton(
                text = if (viewModel.isImporting) "导入中..." else "开始导入",
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
                onClick = viewModel::submitImport,
            )
        }

        item {
            Text("推荐源", color = WearPodTextMuted, fontSize = 13.sp)
        }

        items(viewModel.suggestions) { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(WearPodSurfaceSoft)
                    .clickable { viewModel.useSuggestion(suggestion) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Link,
                    contentDescription = null,
                    tint = WearPodAccent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(suggestion.label, color = WearPodTextPrimary, fontSize = 14.sp)
                    Text(
                        suggestion.url,
                        color = WearPodTextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                        .height(152.dp)
                        .clip(RoundedCornerShape(30.dp))
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
                                        Color(0x2207060B),
                                        Color(0x8A07060B),
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
                                    colors = listOf(Color.Transparent, Color(0x7A07060B)),
                                    radius = 420f,
                                ),
                            ),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
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
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                WatchTimeHeader(color = WearPodPrimary)
                            }
                            if (showNavigation) {
                                MiniIconButton(onClick = onToggleFavorite) {
                                    val isFavorite = snapshot.favoriteSubscriptionIds.contains(subscription.id)
                                    Icon(
                                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (isFavorite) WearPodAccent else WearPodTextPrimary,
                                    )
                                }
                            }
                        }
                        Column {
                            Text(
                                text = subscription.title,
                                color = WearPodTextPrimary,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "共 ${allEpisodesCount} 期",
                                color = WearPodTextMuted,
                                fontSize = 13.sp,
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
                        text = "随机播",
                        background = WearPodPrimary,
                        foreground = WearPodBackground,
                        textSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        leading = {
                            Icon(
                                Icons.Rounded.Shuffle,
                                contentDescription = null,
                                tint = WearPodBackground,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        onClick = onPlayRandom,
                    )
                    PillButton(
                        text = "下载全部",
                        background = Color(0xFF17141D),
                        foreground = WearPodTextPrimary,
                        textSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        leading = {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = null,
                                tint = WearPodTextPrimary,
                                modifier = Modifier.size(16.dp),
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
                        text = "全部",
                        modifier = Modifier.weight(1f),
                        highlighted = episodeFilter == EpisodeFilter.ALL,
                        onClick = { onEpisodeFilterChange(EpisodeFilter.ALL) },
                    )
                    WatchCompactChip(
                        text = "未播",
                        modifier = Modifier.weight(1f),
                        highlighted = episodeFilter == EpisodeFilter.UNPLAYED,
                        onClick = { onEpisodeFilterChange(EpisodeFilter.UNPLAYED) },
                    )
                    WatchCompactChip(
                        text = "已下载",
                        modifier = Modifier.weight(1f),
                        highlighted = episodeFilter == EpisodeFilter.DOWNLOADED,
                        onClick = { onEpisodeFilterChange(EpisodeFilter.DOWNLOADED) },
                    )
                }
            }

            items(episodes, key = { it.id }) { episode ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF1B1720), Color(0xFF131118)),
                            ),
                        )
                        .clickable { onPlayEpisode(episode) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                        when (episode.downloadState) {
                            DownloadState.DOWNLOADED -> EpisodeBadge("离线", WearPodSuccess)
                            DownloadState.QUEUED, DownloadState.DOWNLOADING -> EpisodeBadge("下载中", WearPodAccent)
                            DownloadState.FAILED -> EpisodeBadge("失败", WearPodPrimary)
                            else -> if (episode.isCompleted) EpisodeBadge("已播", WearPodTextMuted) else EpisodeBadge("新", Color(0xFFD56BFF))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        MiniIconButton(
                            modifier = Modifier.size(32.dp),
                            onClick = { onTogglePlayed(episode) },
                        ) {
                            Icon(
                                imageVector = if (episode.isCompleted) Icons.Rounded.Undo else Icons.Rounded.Done,
                                contentDescription = null,
                                tint = if (episode.isCompleted) WearPodTextMuted else WearPodSuccess,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        MiniIconButton(
                            modifier = Modifier.size(32.dp),
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
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        episode.title,
                        color = WearPodTextPrimary,
                        fontSize = 16.sp,
                        lineHeight = 19.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${formatDurationShort(episode.durationSeconds)} • ${formatRelativeTime(episode.publishedAtEpochMillis)}",
                            color = WearPodTextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when (episode.downloadState) {
                                DownloadState.DOWNLOADED -> "已缓存"
                                DownloadState.QUEUED, DownloadState.DOWNLOADING -> "缓存中"
                                DownloadState.FAILED -> "下载失败"
                                else -> if (episode.isCompleted) "已播放" else "未播放"
                            },
                            color = when (episode.downloadState) {
                                DownloadState.DOWNLOADED -> WearPodSuccess
                                DownloadState.QUEUED, DownloadState.DOWNLOADING -> WearPodAccent
                                DownloadState.FAILED -> WearPodPrimary
                                else -> WearPodTextMuted
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
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
    val progress = if (playerDurationMs > 0) playerPositionMs / playerDurationMs.toFloat() else 0f
    val currentTitle = playerTitle.ifBlank {
        snapshot.playbackMemory.lastEpisodeId
            ?.let { id -> snapshot.episodes.firstOrNull { it.id == id }?.title }
            ?: "请选择节目"
    }
    val progressLabel = if (playerDurationMs > 0) {
        "${formatDurationShort((playerPositionMs / 1000L).toInt())} / ${formatDurationShort((playerDurationMs / 1000L).toInt())}"
    } else {
        "准备播放"
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
                                text = "${minutes} 分",
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
                            text = "60 分",
                            modifier = Modifier.weight(1f),
                            highlighted = sleepTimerEndsAtEpochMillis != null && sleepTimerPresetMinutes == 60,
                            onClick = { onStartSleepTimer(60) },
                        )
                        WatchCompactChip(
                            text = "关闭",
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
                            text = "当前队列(${queue.size})",
                            highlighted = true,
                        )
                    }
                }
                items(queue, key = { it.episodeId }) { item ->
                    val isCurrent = queue.indexOfFirst { it.episodeId == item.episodeId } == currentQueueIndex
                    WatchChip(
                        title = item.title.ifBlank { "未命名节目" },
                        subtitle = if (isCurrent) "正在播放" else item.subtitle.ifBlank { "点按切换" },
                        prominent = isCurrent,
                        leading = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isCurrent) WearPodPrimary else WearPodSurfaceSoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (isCurrent) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = if (isCurrent) WearPodBackground else WearPodTextPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        onClick = { onPlayQueueItem(item.episodeId) },
                    )
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
                .size(64.dp)
                .align(Alignment.Center),
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
                    .size(46.dp)
                    .graphicsLayer {
                        alpha = centerButtonAlpha
                        scaleX = centerButtonScale
                        scaleY = centerButtonScale
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFFFF9B84), WearPodPrimary),
                        ),
                    )
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
                    tint = WearPodBackground,
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
    onOpenSettings: () -> Unit,
) {
    val queuedEpisodes = snapshot.episodes
        .filter { episode ->
            episode.downloadState == DownloadState.QUEUED || episode.downloadState == DownloadState.DOWNLOADING
        }
        .sortedWith(
            compareByDescending<Episode> { it.downloadState == DownloadState.DOWNLOADING }
                .thenByDescending { it.publishedAtEpochMillis ?: 0L },
        )
    val downloadedEpisodes = snapshot.episodes.filter { it.downloadState == DownloadState.DOWNLOADED }
    val failedEpisodes = snapshot.episodes
        .filter { it.downloadState == DownloadState.FAILED }
        .sortedByDescending { it.publishedAtEpochMillis ?: 0L }
    val usedBytes = downloadedEpisodes.sumOf { it.downloadedBytes.takeIf { size -> size > 0 } ?: 0L }
    val maxBytes = 1_500L * 1024L * 1024L
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
                        text = "离线",
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
                RootScreenHeader(title = "离线")
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WatchMetricPill(
                    label = "已下载",
                    value = "${downloadedEpisodes.size} 集",
                    modifier = Modifier.weight(1f),
                )
                WatchMetricPill(
                    label = "空间",
                    value = formatBytes(usedBytes),
                    modifier = Modifier.weight(1f),
                )
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
                    text = "下载与刷新",
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
                        text = "下载失败(${failedEpisodes.size})",
                        highlighted = true,
                    )
                }
            }

            items(failedEpisodes, key = { "failed-${it.id}" }) { episode ->
                WatchChip(
                    title = episode.title,
                    subtitle = "下载失败 • 点按重试",
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
                        text = "下载队列(${queuedEpisodes.size})",
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
                        "下载中 • $sizeText • 点按取消"
                    }

                    else -> "排队中 • 点按取消"
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
                        text = "已下载(${downloadedEpisodes.size})",
                        highlighted = true,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    WatchCompactChip(
                        text = "清空缓存",
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
                    Text("还没有下载任务", color = WearPodTextMuted, textAlign = TextAlign.Center)
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
    onSetWifiOnly: (Boolean) -> Unit,
    onSetAutoDownloadCount: (Int) -> Unit,
    onSetBackgroundAutoDownload: (Boolean) -> Unit,
    onSetBackgroundRefreshEnabled: (Boolean) -> Unit,
    onSetBackgroundRefreshInterval: (Int) -> Unit,
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
            SectionTitle(title = "下载与刷新", subtitle = "控制网络、下载与后台更新")
        }
        item {
            WatchChip(
                title = "仅 Wi‑Fi 下载",
                subtitle = if (settings.wifiOnly) "已开启" else "允许任意网络",
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
                            text = if (settings.wifiOnly) "Wi‑Fi" else "网络",
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
                title = "后台自动下载",
                subtitle = if (settings.backgroundAutoDownloadEnabled) "刷新后自动排队" else "仅手动下载",
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
                            text = if (settings.backgroundAutoDownloadEnabled) "自动" else "手动",
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
            Text(
                text = "自动下载最新",
                color = WearPodTextMuted,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    listOf(0 to "关闭", 1 to "1 期"),
                    listOf(2 to "2 期", 3 to "3 期"),
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
                title = "后台定时刷新",
                subtitle = if (settings.backgroundRefreshEnabled) {
                    "每 ${settings.backgroundRefreshIntervalHours} 小时检查新节目"
                } else {
                    "已关闭"
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
                text = "后台刷新频率",
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
                        text = "${hours} 小时",
                        modifier = Modifier.weight(1f),
                        highlighted = settings.backgroundRefreshEnabled && settings.backgroundRefreshIntervalHours == hours,
                        onClick = { onSetBackgroundRefreshInterval(hours) },
                    )
                }
            }
        }
    }
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
        "睡眠定时"
    } else {
        val remainingMs = (endsAtEpochMillis - now).coerceAtLeast(0L)
        val remainingMinutes = ((remainingMs + 59_999L) / 60_000L).coerceAtLeast(1L)
        "睡眠剩余 ${remainingMinutes} 分"
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
