package com.sjtech.wearpod.util

import android.content.Context
import com.sjtech.wearpod.R
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private fun dayFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM/dd", Locale.getDefault()).withZone(ZoneId.systemDefault())

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

fun formatRelativeTime(context: Context, epochMillis: Long?): String {
    if (epochMillis == null) return context.getString(R.string.relative_updated_just_now)
    val now = System.currentTimeMillis()
    val delta = now - epochMillis
    return when {
        delta < 60_000L -> context.getString(R.string.relative_just_now)
        delta < 3_600_000L -> context.getString(R.string.relative_minutes_ago, delta / 60_000L)
        delta < 86_400_000L -> context.getString(R.string.relative_hours_ago, delta / 3_600_000L)
        delta < 604_800_000L -> context.getString(R.string.relative_days_ago, delta / 86_400_000L)
        else -> dayFormatter().format(Instant.ofEpochMilli(epochMillis))
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
