package com.scanpdf.ocr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F3D2E),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF2E7D5B),
    onSecondary = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE3EFE9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD1AE),
    onPrimary = Color(0xFF00382A),
    secondary = Color(0xFF4CAF8A),
    onSecondary = Color(0xFF00281C),
)

@Composable
fun ScanPdfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
