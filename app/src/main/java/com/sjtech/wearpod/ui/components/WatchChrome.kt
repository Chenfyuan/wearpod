package com.sjtech.wearpod.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.sjtech.wearpod.ui.theme.WearPodAccent
import com.sjtech.wearpod.ui.theme.WearPodBackground
import com.sjtech.wearpod.ui.theme.WearPodBorder
import com.sjtech.wearpod.ui.theme.WearPodPrimary
import com.sjtech.wearpod.ui.theme.WearPodPrimarySoft
import com.sjtech.wearpod.ui.theme.WearPodSurface
import com.sjtech.wearpod.ui.theme.WearPodSurfaceSoft
import com.sjtech.wearpod.ui.theme.WearPodTextMuted
import com.sjtech.wearpod.ui.theme.WearPodTextPrimary
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun WatchViewport(
    modifier: Modifier = Modifier,
    bannerMessage: String? = null,
    bottomBar: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black, WearPodBackground, Color.Black),
                ),
            )
            .padding(6.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2A1822),
                        Color(0xFF0F0D13),
                        Color(0xFF08070B),
                    ),
                    center = Offset(720f, 180f),
                    radius = 900f,
                ),
            )
            .border(2.dp, Color(0xFF232028), CircleShape)
            .shadow(20.dp, CircleShape),
    ) {
        content()

        if (bottomBar != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xB31B1821))
                    .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = bottomBar,
            )
        }

        AnimatedVisibility(
            visible = bannerMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp),
        ) {
            if (bannerMessage != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF2D2530))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = bannerMessage,
                        color = WearPodTextPrimary,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun WatchTimeHeader(
    modifier: Modifier = Modifier,
    color: Color = WearPodPrimary,
) {
    var time by remember { mutableStateOf(currentTimeString()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = currentTimeString()
            delay(30_000L)
        }
    }
    Text(
        text = time,
        color = color,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

@Composable
fun SectionTitle(
    title: String,
    subtitle: String? = null,
    center: Boolean = true,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = title,
            color = WearPodTextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
            maxLines = 2,
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = WearPodTextMuted,
                fontSize = 12.sp,
                textAlign = if (center) TextAlign.Center else TextAlign.Start,
            )
        }
    }
}

@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    background: Color,
    foreground: Color = WearPodTextPrimary,
    textSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = foreground,
            fontSize = textSize,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun DarkCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(WearPodSurface)
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun WatchChip(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    leadingOnClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    leading: @Composable BoxScope.() -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                if (prominent) {
                    Brush.linearGradient(
                        colors = listOf(Color(0x66FF7B5B), WearPodSurface),
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF19161E), WearPodSurface),
                    )
                },
            )
            .border(
                width = 1.dp,
                color = if (prominent) Color(0x44FF7B5B) else WearPodBorder.copy(alpha = 0.72f),
                shape = RoundedCornerShape(26.dp),
            )
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (leadingOnClick != null) 46.dp else 40.dp)
                .clip(CircleShape)
                .background(if (prominent) WearPodPrimary else WearPodSurfaceSoft)
                .then(
                    if (leadingOnClick != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = leadingOnClick,
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
            content = leading,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = WearPodTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (subtitle == null) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = WearPodTextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = trailing,
            )
        }
    }
}

@Composable
fun WatchCompactChip(
    text: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    leading: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val chipModifier = modifier
        .clip(RoundedCornerShape(999.dp))
        .background(
            if (highlighted) {
                Brush.linearGradient(
                    colors = listOf(Color(0x33FF7B5B), Color(0x221D1A22)),
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1C1922), Color(0xFF15131A)),
                )
            },
        )
        .border(
            width = 1.dp,
            color = if (highlighted) Color(0x44FF7B5B) else WearPodBorder.copy(alpha = 0.6f),
            shape = RoundedCornerShape(999.dp),
        )
        .defaultMinSize(minHeight = 32.dp)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
            } else {
                Modifier
            },
        )
        .padding(horizontal = 12.dp, vertical = 7.dp)

    Row(
        modifier = chipModifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = if (highlighted) WearPodPrimarySoft else WearPodTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun WatchMetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xB315131A))
            .border(1.dp, WearPodBorder.copy(alpha = 0.65f), RoundedCornerShape(22.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = WearPodTextMuted, fontSize = 10.sp)
        Spacer(modifier = Modifier.height(3.dp))
        Text(value, color = WearPodTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun WearPullRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    PullToRefreshDefaults.IndicatorBox(
        state = state,
        isRefreshing = isRefreshing,
        modifier = modifier,
        maxDistance = 68.dp,
        containerColor = Color.Transparent,
        elevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x33FF7B5B), Color(0xE6131017)),
                    ),
                )
                .border(1.dp, WearPodBorder.copy(alpha = 0.75f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = WearPodPrimary,
                    trackColor = Color(0x22FFFFFF),
                    strokeWidth = 2.5.dp,
                )
            } else {
                CircularProgressIndicator(
                    progress = { state.distanceFraction.coerceIn(0f, 1f) },
                    modifier = Modifier.size(26.dp),
                    color = WearPodPrimary,
                    trackColor = Color(0x22FFFFFF),
                    strokeWidth = 2.5.dp,
                )
            }
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                tint = if (isRefreshing) WearPodPrimarySoft else WearPodTextPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun ArtworkThumb(
    imageUrl: String?,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = imageUrl,
        contentDescription = null,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF593D52), Color(0xFF1F1A27)),
                ),
            ),
    )
}

@Composable
fun EpisodeList(
    items: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items) { row ->
            row()
        }
    }
}

@Composable
fun CircularProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 10.dp,
    trackColor: Color = Color(0x33FFFFFF),
    progressColor: Color = WearPodPrimary,
) {
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = stroke,
        )
        drawArc(
            brush = Brush.sweepGradient(listOf(progressColor, Color(0xFFFFB29D), progressColor)),
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            style = stroke,
        )
    }
}

@Composable
fun MiniIconButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(WearPodSurfaceSoft)
            .border(1.dp, WearPodBorder.copy(alpha = 0.64f), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

private fun currentTimeString(): String =
    LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
