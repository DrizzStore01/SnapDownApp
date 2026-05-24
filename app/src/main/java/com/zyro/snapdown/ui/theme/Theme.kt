package com.zyro.snapdown.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    background   = Color(0xFF0F172A),
    surface      = Color(0xFF1E293B),
    primary      = Color(0xFF3B82F6),
    onBackground = Color(0xFFF8FAFC),
    onSurface    = Color(0xFFF8FAFC),
)

@Composable
fun SnapDownTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
