package com.voxi.captions.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Voxi siempre usa tema oscuro: un captioner se lee mejor sobre fondo oscuro (spec §8).
private val VoxiDarkColors = darkColorScheme(
    primary = VoxiTeal,
    secondary = VoxiBlue,
    tertiary = VoxiViolet,
    background = VoxiBg,
    surface = VoxiSurface,
    onPrimary = VoxiBg,
    onSecondary = VoxiBg,
    onBackground = VoxiMint,
    onSurface = VoxiMint,
    outline = VoxiSlate,
)

@Composable
fun VoxiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VoxiDarkColors,
        typography = VoxiTypography,
        content = content,
    )
}
