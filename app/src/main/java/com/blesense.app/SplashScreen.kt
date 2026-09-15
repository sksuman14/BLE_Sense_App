package com.blesense.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Data class to hold text for the splash screen
data class SplashScreenText(
    val appName: String = "BLE Sense" // Default app name
)

// Composable for the splash screen
@Composable
fun SplashScreen(onNavigateToLogin: () -> Unit) {
    LocalContext.current // Access the current context

    // Observe theme state only
    val isDarkMode by ThemeManager.isDarkMode.collectAsState() // Dark mode state

    // Use fixed English text
    val splashText = SplashScreenText()

    // Define theme-based colors
    val backgroundColor = if (isDarkMode) Color(0xFF121212) else Color.White // Background color
    val textColor = if (isDarkMode) Color.White else Color.Black // Primary text color
    val secondaryTextColor = if (isDarkMode) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.8f) // Secondary text color with opacity

    // Navigate to login screen after a 2-second delay
    LaunchedEffect(key1 = true) {
        delay(2000L) // Wait for 2 seconds
        onNavigateToLogin() // Trigger navigation to login screen
    }

    // Set up infinite animation transition
    val infiniteTransition = rememberInfiniteTransition()

    // Slow zoom animation for the background image
    val imageScale by infiniteTransition.animateFloat(
        initialValue = 1f, // Initial scale
        targetValue = 1.05f, // Target scale
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing), // 6-second linear animation
            repeatMode = RepeatMode.Reverse // Reverse animation direction
        )
    )

    // Slow zoom animation for the app name text
    val titleScale by infiniteTransition.animateFloat(
        initialValue = 1f, // Initial scale
        targetValue = 1.2f, // Target scale
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearOutSlowInEasing), // 1-second easing animation
            repeatMode = RepeatMode.Reverse // Reverse animation direction
        )
    )

    // Slow zoom animation for the developer credit text
    val footerScale by infiniteTransition.animateFloat(
        initialValue = 1f, // Initial scale
        targetValue = 1.1f, // Target scale
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing), // 3-second linear animation
            repeatMode = RepeatMode.Reverse // Reverse animation direction
        )
    )

    // Main layout for the splash screen
    Box(
        modifier = Modifier
            .fillMaxSize() // Fill entire screen
            .background(backgroundColor), // Apply background color
        contentAlignment = Alignment.Center // Center content
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize() // Fill entire screen
                .padding(16.dp), // Apply padding
            verticalArrangement = Arrangement.Center, // Center vertically
            horizontalAlignment = Alignment.CenterHorizontally // Center horizontally
        ) {
            // Background image with zoom animation
            Image(
                painter = painterResource(id = R.drawable.bg_remove_2), // Load background image
                contentDescription = null, // No content description for decorative image
                modifier = Modifier
                    .fillMaxWidth() // Fill available width
                    .height(300.dp) // Fixed height
                    .graphicsLayer(scaleX = imageScale, scaleY = imageScale), // Apply zoom animation
                contentScale = ContentScale.Crop // Crop image to fit
            )

            Spacer(modifier = Modifier.height(40.dp)) // Space between image and text

            // App name text with zoom animation
            BasicText(
                text = splashText.appName, // Static app name
                style = TextStyle(
                    fontSize = 40.sp, // Large font size
                    color = textColor, // Theme-based text color
                    fontWeight = FontWeight.Bold, // Bold text
                    textAlign = TextAlign.Center, // Center text
                    fontFamily = helveticaFont // Custom font
                ),
                modifier = Modifier
                    .graphicsLayer(scaleX = titleScale, scaleY = titleScale) // Apply zoom animation
            )

            Spacer(modifier = Modifier.height(20.dp)) // Space between texts

        }
    }
}

// Preview composable for the splash screen
@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    SplashScreen(onNavigateToLogin = {}) // Empty callback for preview
}