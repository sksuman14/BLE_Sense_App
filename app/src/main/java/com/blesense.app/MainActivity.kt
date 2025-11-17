package com.blesense.app

import AdjustStatusBarIconsForTheme
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.google.firebase.Firebase
import com.google.firebase.initialize

// Main entry point for the app, extending ComponentActivity for Compose support
class MainActivity : ComponentActivity() {
    // Called when the activity is created
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Prevent activity recreation on orientation changes for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        // Call the parent class's onCreate method
        super.onCreate(savedInstanceState)
        // Install the system splash screen
        installSplashScreen()
        // Initialize Firebase with the current context
        Firebase.initialize(this)
        // Enable edge-to-edge mode and let Compose handle insets
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Set the Compose UI content
        setContent {
            AdjustStatusBarIconsForTheme()
            // Create a NavController for navigation
            val navController = rememberNavController()
            // Set up the app's navigation graph
            AppNavigation(navController)
        }
    }
}