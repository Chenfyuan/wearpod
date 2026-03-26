package com.sjtech.wearpod.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val WearPodBackground = Color(0xFF07060B)
val WearPodSurface = Color(0xFF15131A)
val WearPodSurfaceSoft = Color(0xFF1D1A22)
val WearPodPrimary = Color(0xFFFF7B5B)
val WearPodPrimarySoft = Color(0xFFFFA287)
val WearPodAccent = Color(0xFFD762F3)
val WearPodTextPrimary = Color(0xFFF6F2FA)
val WearPodTextMuted = Color(0xFFA8A0B3)
val WearPodBorder = Color(0xFF2B2730)
val WearPodSuccess = Color(0xFF7EE2A8)

private val DarkScheme = darkColorScheme(
    primary = WearPodPrimary,
    secondary = WearPodAccent,
    background = WearPodBackground,
    surface = WearPodSurface,
    onPrimary = WearPodBackground,
    onSecondary = WearPodTextPrimary,
    onBackground = WearPodTextPrimary,
    onSurface = WearPodTextPrimary,
)

@Composable
fun WearPodTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        content = content,
    )
}
