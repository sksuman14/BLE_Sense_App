// Import statements for Android Activity, Jetpack Compose, and window management utilities
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Composable function to adjust status bar icons based on the current theme
@Composable
fun AdjustStatusBarIconsForTheme() {
    // Determine if the system is in dark theme
    val isDarkTheme = isSystemInDarkTheme()

    // Get the current view from Compose context
    val view = LocalView.current

    // SideEffect to perform status bar modifications
    SideEffect {
        // Get the window from the view's context, assuming it's an Activity
        val window = (view.context as Activity).window

        // Set the status bar background to transparent
        window.statusBarColor = Color.Transparent.toArgb()

        // Configure status bar icon appearance:
        // true = dark icons (black, for light background)
        // false = light icons (white, for dark background)
        WindowCompat.getInsetsController(window, view)
            .isAppearanceLightStatusBars = !isDarkTheme
    }
}