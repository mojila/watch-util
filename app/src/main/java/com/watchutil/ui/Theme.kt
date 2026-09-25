package com.watchutil.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

private val WatchUtilColorScheme = ColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF00344A),
    primaryContainer = Color(0xFF004C69),
    onPrimaryContainer = Color(0xFFC6E7FF),
    secondary = Color(0xFF81C784),
    onSecondary = Color(0xFF00390F),
    secondaryContainer = Color(0xFF00531A),
    onSecondaryContainer = Color(0xFF9DF6A8),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6B3C00),
    onTertiaryContainer = Color(0xFFFFDDB0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE2E2E6),
    surfaceContainerLow = Color(0xFF0E1114),
    surfaceContainer = Color(0xFF16191C),
    surfaceContainerHigh = Color(0xFF22262A),
    onSurface = Color(0xFFE2E2E6),
    onSurfaceVariant = Color(0xFFC2C7CE),
    outline = Color(0xFF8C9199),
)

/**
 * Wear Material 3 theme. The app is designed to be legible on both round and
 * square displays; see [WatchUtilApp] for the layout strategy.
 */
@Composable
fun WatchUtilTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WatchUtilColorScheme,
        content = content,
    )
}
