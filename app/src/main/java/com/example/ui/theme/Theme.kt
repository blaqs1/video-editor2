package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BordeauxColorScheme = darkColorScheme(
    primary = BordeauxRed,
    onPrimary = StudioAccentWhite,
    secondary = BordeauxMuted,
    onSecondary = BordeauxTextPrimary,
    background = BordeauxBackground,
    onBackground = BordeauxTextPrimary,
    surface = BordeauxSurface,
    onSurface = BordeauxTextPrimary,
    outline = BordeauxBorder,
    surfaceVariant = StudioMediumGray,
    onSurfaceVariant = BordeauxTextPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BordeauxColorScheme,
        typography = Typography,
        content = content
    )
}

