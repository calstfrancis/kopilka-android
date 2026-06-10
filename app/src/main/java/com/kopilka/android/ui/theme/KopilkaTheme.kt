package com.kopilka.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Adwaita blue seed — matches GNOME/libadwaita default accent
private val AdwaitaBlue = Color(0xFF3584e4)
private val AdwaitaGreen = Color(0xFF33d17a)
private val AdwaitaRed = Color(0xFFe01b24)
private val AdwaitaSurface = Color(0xFFfafafa)
private val AdwaitaSurfaceVariant = Color(0xFFf0f0f0)
private val AdwaitaOnSurface = Color(0xFF1a1a2e)

private val LightColors = lightColorScheme(
    primary = AdwaitaBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFd0e4ff),
    onPrimaryContainer = Color(0xFF001d36),
    secondary = Color(0xFF535f70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFd7e3f7),
    onSecondaryContainer = Color(0xFF101c2b),
    tertiary = Color(0xFF6b5778),
    error = AdwaitaRed,
    background = AdwaitaSurface,
    onBackground = AdwaitaOnSurface,
    surface = AdwaitaSurface,
    onSurface = AdwaitaOnSurface,
    surfaceVariant = AdwaitaSurfaceVariant,
    outline = Color(0xFF73777f),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ecaff),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF1b4c7e),
    secondary = Color(0xFFbbc7db),
    error = Color(0xFFffb4ab),
    background = Color(0xFF1a1c1e),
    onBackground = Color(0xFFe2e2e6),
    surface = Color(0xFF1a1c1e),
    onSurface = Color(0xFFe2e2e6),
    surfaceVariant = Color(0xFF43474e),
)

@Composable
fun KopilkaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
