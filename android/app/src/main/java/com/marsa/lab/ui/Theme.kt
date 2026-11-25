package com.marsa.lab.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object MarsaColors {
    val Bg = Color(0xFF0B141A)
    val Card = Color(0xFF13232B)
    val CardAlt = Color(0xFF1A2F39)
    val Outline = Color(0xFF29424C)
    val Cyan = Color(0xFF00C2D1)
    val OnCyan = Color(0xFF00272B)
    val TextMain = Color(0xFFEEF8FA)
    val Muted = Color(0xFF9BB1BA)
    val Online = Color(0xFF2ECC71)
    val Danger = Color(0xFFFF6B6B)
}

@Composable
fun MarsaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = MarsaColors.Cyan,
            onPrimary = MarsaColors.OnCyan,
            secondary = MarsaColors.Cyan,
            background = MarsaColors.Bg,
            onBackground = MarsaColors.TextMain,
            surface = MarsaColors.Card,
            onSurface = MarsaColors.TextMain,
            surfaceVariant = MarsaColors.CardAlt,
            onSurfaceVariant = MarsaColors.Muted,
            outline = MarsaColors.Outline,
            error = MarsaColors.Danger,
            onError = Color.Black,
            errorContainer = Color(0xFF3A1A1A),
            onErrorContainer = Color(0xFFFFD6D6),
        ),
        content = content,
    )
}
