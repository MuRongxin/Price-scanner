package com.pricescanner.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Blue500,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = Blue500,
    secondary = Slate700,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = Slate50,
    onBackground = Slate800,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = Slate800,
    surfaceVariant = Slate50,
    onSurfaceVariant = Slate500,
    outline = Slate200,
    error = Red500,
    errorContainer = Red50,
    onError = androidx.compose.ui.graphics.Color.White
)

@Composable
fun PriceScannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = AppTypography,
        content = content
    )
}
