package com.example.offlinemusic.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF00E5A8)

private val Dark = darkColorScheme(
    primary = Accent,
    onPrimary = Color.Black,
    secondary = Color(0xFF7C4DFF),
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF16161A),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF24242A),
    onSurfaceVariant = Color(0xFFA8A8B0),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = Dark, typography = Typography(), content = content)
