package com.hive4apps.petcare.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Warm apricot of the icon: the accent. */
val Apricot = Color(0xFFF08A4B)
val Sage = Color(0xFF4E9C82)
val Late = Color(0xFFD9534F)
val DoneGreen = Color(0xFF3E9D6E)

private val Light = lightColorScheme(
    primary = Color(0xFFC8622A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC7),
    onPrimaryContainer = Color(0xFF3A1600),
    secondary = Sage,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEBDD),
    onSecondaryContainer = Color(0xFF00281C),
    tertiary = Color(0xFF7A5BA6),
    tertiaryContainer = Color(0xFFEBDDFF),
    background = Color(0xFFFBF6F1),
    onBackground = Color(0xFF221A15),
    surface = Color(0xFFFBF6F1),
    onSurface = Color(0xFF221A15),
    surfaceVariant = Color(0xFFF3E6DC),
    onSurfaceVariant = Color(0xFF54443A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF9F5),
    surfaceContainer = Color(0xFFF8EEE7),
    surfaceContainerHigh = Color(0xFFF3E7DF),
    surfaceContainerHighest = Color(0xFFEDE0D7),
    outline = Color(0xFF8A7366),
    outlineVariant = Color(0xFFDCC7BA),
    error = Late,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB68C),
    onPrimary = Color(0xFF552100),
    primaryContainer = Color(0xFF7A3510),
    onPrimaryContainer = Color(0xFFFFDCC7),
    secondary = Color(0xFF8FD3B6),
    onSecondary = Color(0xFF003829),
    secondaryContainer = Color(0xFF1F4F40),
    onSecondaryContainer = Color(0xFFCDEBDD),
    tertiary = Color(0xFFD4BBFF),
    tertiaryContainer = Color(0xFF4F3A75),
    background = Color(0xFF1B1613),
    onBackground = Color(0xFFF0E0D6),
    surface = Color(0xFF1B1613),
    onSurface = Color(0xFFF0E0D6),
    surfaceVariant = Color(0xFF3A2F28),
    onSurfaceVariant = Color(0xFFD8C2B5),
    surfaceContainerLowest = Color(0xFF15110E),
    surfaceContainerLow = Color(0xFF221C18),
    surfaceContainer = Color(0xFF27201B),
    surfaceContainerHigh = Color(0xFF312923),
    surfaceContainerHighest = Color(0xFF3C332C),
    outline = Color(0xFFA08D81),
    outlineVariant = Color(0xFF54443A),
    error = Color(0xFFFF8A80),
)

@Composable
fun PetCareTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
