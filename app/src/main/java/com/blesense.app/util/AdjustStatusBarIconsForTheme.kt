package com.blesense.app.util

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Adjusts the status bar icon appearance (dark/light) based on the current app theme.
 */
@Composable
fun AdjustStatusBarIconsForTheme() {
    val isSystemDark = isSystemInDarkTheme()
    val context = LocalView.current.context
    LaunchedEffect(isSystemDark) {
        ThemeManager.initializeWithSystemTheme(context, isSystemDark)
    }
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val view = LocalView.current

    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkMode
    }
}
