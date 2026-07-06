package com.matepazy.spectre.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = SpectrePurple,
    secondary = CyberBlue,
    tertiary = CyberGreen,
    background = CyberDarkBg,
    surface = CyberCardBg,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = CyberTextPrimary,
    onSurface = CyberTextPrimary,
    outline = CyberBorder,
    surfaceVariant = Color(0xFFF1EFF8),
    onSurfaceVariant = CyberTextSecondary
)

@Composable
fun SpectreTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}



