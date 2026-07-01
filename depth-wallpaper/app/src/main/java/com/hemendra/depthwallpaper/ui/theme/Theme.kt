package com.hemendra.depthwallpaper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF4F7BFF)
private val Violet = Color(0xFF7C4DFF)
private val NearBlack = Color(0xFF0B1020)
private val Surface = Color(0xFF141A2E)

private val DarkColors = darkColorScheme(
    primary = Indigo,
    secondary = Violet,
    background = NearBlack,
    surface = Surface,
)

private val LightColors = lightColorScheme(
    primary = Indigo,
    secondary = Violet,
)

@Composable
fun DepthWallpaperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
