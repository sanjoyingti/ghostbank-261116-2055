package com.arin.ghostbank.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    primary = GhostGreen,
    onPrimary = Ink,
    primaryContainer = GhostGreenDim,
    onPrimaryContainer = InkText,
    secondary = GhostBlue,
    onSecondary = Ink,
    tertiary = GhostAmber,
    onTertiary = Ink,
    background = Ink,
    onBackground = InkText,
    surface = Ink,
    onSurface = InkText,
    surfaceVariant = InkRaised,
    onSurfaceVariant = InkMuted,
    outline = InkLine,
    error = GhostRed,
    onError = Ink
)

private val LightScheme = lightColorScheme(
    primary = GhostGreenDim,
    onPrimary = PaperRaised,
    primaryContainer = GhostGreen,
    onPrimaryContainer = PaperText,
    secondary = GhostBlue,
    onSecondary = PaperRaised,
    tertiary = GhostAmber,
    onTertiary = PaperText,
    background = Paper,
    onBackground = PaperText,
    surface = PaperRaised,
    onSurface = PaperText,
    surfaceVariant = Paper,
    onSurfaceVariant = PaperMuted,
    outline = PaperLine,
    error = GhostRed,
    onError = PaperRaised
)

/**
 * GhostBank is dark by design; the light scheme stays wired for anyone who
 * wants to pass it in explicitly.
 */
@Composable
fun GhostBankTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = GhostTypography,
        content = content
    )
}
