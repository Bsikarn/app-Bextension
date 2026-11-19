package com.example.workspacerouter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF5264C5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAEDFF),
    onPrimaryContainer = Color(0xFF273775),
    secondary = Color(0xFF63706C),
    background = Color(0xFFF5F6F8),
    surface = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEF0F4),
    onSurface = Color(0xFF20242E),
    onSurfaceVariant = Color(0xFF687080),
    outlineVariant = Color(0xFFE0E4EC),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC5C2FF),
    secondary = Color(0xFFCCC2DC),
    background = Color(0xFF111318),
    surface = Color(0xFF1B1E25),
    surfaceContainer = Color(0xFF1B1E25),
    surfaceVariant = Color(0xFF282C35),
)

@Composable
fun BextensionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
