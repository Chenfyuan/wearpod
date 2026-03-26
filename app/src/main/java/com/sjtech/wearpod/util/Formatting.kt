package com.sjtech.wearpod.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val dayFormatter = DateTimeFormatter.ofPattern("MM/dd").withZone(ZoneId.systemDefault())

fun formatDurationShort(seconds: Int?): String {
    val safe = seconds ?: return "--"
    val duration = Duration.ofSeconds(safe.toLong())
    val hours = duration.toHours()
    val minutes = duration.toMinutesPart()
    val secs = duration.toSecondsPart()
    return when {
        hours > 0 -> "%d:%02d:%02d".format(hours, minutes, secs)
        else -> "%d:%02d".format(duration.toMinutes(), secs)
    }
}

fun formatRelativeTime(epochMillis: Long?): String {
    if (epochMillis == null) return "刚刚更新"
    val now = System.currentTimeMillis()
    val delta = now - epochMillis
    return when {
        delta < 60_000L -> "刚刚"
        delta < 3_600_000L -> "${delta / 60_000L} 分钟前"
        delta < 86_400_000L -> "${delta / 3_600_000L} 小时前"
        delta < 604_800_000L -> "${delta / 86_400_000L} 天前"
        else -> dayFormatter.format(Instant.ofEpochMilli(epochMillis))
    }
}

fun formatBytes(bytes: Long?): String {
    val safe = bytes ?: return "--"
    if (safe <= 0) return "0B"
    val mb = safe / (1024f * 1024f)
    return when {
        mb >= 1024f -> "%.2fGB".format(mb / 1024f)
        mb >= 1f -> "${mb.roundToInt()}MB"
        else -> "${(safe / 1024f).roundToInt()}KB"
    }
}
