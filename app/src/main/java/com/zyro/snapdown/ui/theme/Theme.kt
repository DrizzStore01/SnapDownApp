package com.zyro.snapdown.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SnapDownColors = darkColorScheme(
    primary             = SnapBlue,
    onPrimary           = SnapTextPrimary,
    primaryContainer    = SnapSurface,
    secondary           = SnapCyan,
    onSecondary         = SnapTextPrimary,
    tertiary            = SnapPurple,
    background          = SnapBackground,
    onBackground        = SnapTextPrimary,
    surface             = SnapSurface,
    onSurface           = SnapTextPrimary,
    surfaceVariant      = SnapSurface2,
    onSurfaceVariant    = SnapTextSecondary,
    error               = SnapRed
)

@Composable
fun SnapDownTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = SnapBackground.toArgb()
            window.navigationBarColor = SnapBackground.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = SnapDownColors,
        typography  = Typography,
        content     = content
    )
}
