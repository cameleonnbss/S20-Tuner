package com.cameleonnbss.s20tuner.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Green = Color(0xFF4CAF50)
val GreenDim = Color(0xFF2E7D32)
val DarkBg = Color(0xFF0D1117)
val DarkSurface = Color(0xFF161B22)
val DarkCard = Color(0xFF1C2129)
val Amber = Color(0xFFFFB300)
val Red = Color(0xFFEF5350)

private val DarkScheme = darkColorScheme(
    primary = Green,
    onPrimary = Color.Black,
    secondary = GreenDim,
    background = DarkBg,
    surface = DarkSurface,
    surfaceVariant = DarkCard,
    primaryContainer = GreenDim,
    onPrimaryContainer = Color.White
)

private val LightScheme = lightColorScheme(
    primary = GreenDim,
    onPrimary = Color.White,
    secondary = Green
)

@Composable
fun S20TunerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        content = content
    )
}
