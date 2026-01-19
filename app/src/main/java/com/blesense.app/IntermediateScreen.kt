package com.blesense.app

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

// Intermediate screen serving as main navigation hub
@Composable
fun IntermediateScreen(
    navController: NavHostController,
    isDarkMode: Boolean
) {
    // Theme-aware colors
    val backgroundColor = if (isDarkMode) Color(0xFF121212) else Color(0xFFF5F5F5)
    val cardBackgroundColor = if (isDarkMode) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black
    val gradientStart = if (isDarkMode) Color(0xFF0D47A1) else Color(0xFF21CBF3)
    val gradientEnd = if (isDarkMode) Color(0xFF0D47A1) else Color(0xFF21CBF3)

    // Context for launching activities
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { /* Handle result if needed */ }
    )

    // Main screen layout with Scaffold
    Scaffold(
        backgroundColor = backgroundColor,
        topBar = {
            TopAppBar(
                backgroundColor = cardBackgroundColor,
                elevation = 8.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "BLE Sense",
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        style = MaterialTheme.typography.h5,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->
        // Navigation grid using LazyColumn
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Screen title
            item {
                Text(
                    text = "Choose a Destination",
                    style = MaterialTheme.typography.h5,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            // First row: Bluetooth and Data Logger
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Bluetooth navigation button
                    NavigationIconBox(
                        iconResId = R.drawable.bluetooth,
                        label = "Bluetooth",
                        textColor = textColor,
                        gradientStart = gradientStart,
                        gradientEnd = gradientEnd,
                        backgroundColor = cardBackgroundColor,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("home_screen") }
                    )

                    // Data Logger navigation button
                    NavigationIconBox(
                        iconResId = R.drawable.data_logger,
                        label = "Data Logger",
                        textColor = textColor,
                        gradientStart = gradientStart,
                        gradientEnd = gradientEnd,
                        backgroundColor = cardBackgroundColor,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            navController.navigate("data_logger/auto_connect/DataLogger/DataLogger_1")
                        }
                    )
                }
            }

            // Second row: Robot Control and Settings
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Robot Control navigation button (launches activity)
                    NavigationIconBox(
                        iconResId = R.drawable.robo_car_icon,
                        label = "Robot Control",
                        textColor = textColor,
                        gradientStart = gradientStart,
                        gradientEnd = gradientEnd,
                        backgroundColor = cardBackgroundColor,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val intent = Intent(context, RobotControlCompose::class.java)
                            launcher.launch(intent)
                        }
                    )

                    // Settings navigation button
                    NavigationIconBox(
                        iconResId = R.drawable.settings,
                        label = "Settings",
                        textColor = textColor,
                        gradientStart = gradientStart,
                        gradientEnd = gradientEnd,
                        backgroundColor = cardBackgroundColor,
                        modifier = Modifier.weight(1f),
                        onClick = { navController.navigate("settings_screen") }
                    )
                }
            }
        }
    }
}

// Reusable navigation card with press animations
@Composable
fun NavigationIconBox(
    iconResId: Int,
    label: String,
    textColor: Color,
    gradientStart: Color,
    gradientEnd: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // State for press animation
    var isPressed by remember { mutableStateOf(false) }

    // Animated properties for press effect
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "scale"
    )

    val elevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "elevation"
    )

    val cardColor by animateColorAsState(
        targetValue = if (isPressed) backgroundColor.copy(alpha = 0.8f) else backgroundColor,
        animationSpec = tween(durationMillis = 150),
        label = "cardColor"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "iconScale"
    )

    // Animated card with press effect
    Card(
        modifier = modifier
            .aspectRatio(1.1f) // Maintain consistent aspect ratio
            .scale(scale)
            .pointerInput(Unit) {
                // Handle tap gestures with press state
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        val success = tryAwaitRelease()
                        isPressed = false
                        if (success) {
                            onClick()
                        }
                    }
                )
            },
        elevation = elevation,
        shape = RoundedCornerShape(20.dp),
        backgroundColor = cardColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                // Icon with gradient background
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .scale(iconScale)
                        .background(
                            brush = Brush.linearGradient(
                                colors = if (isPressed) {
                                    listOf(
                                        gradientStart.copy(alpha = 0.9f),
                                        gradientEnd.copy(alpha = 0.9f)
                                    )
                                } else {
                                    listOf(gradientStart, gradientEnd)
                                },
                                start = Offset(0f, 0f),
                                end = Offset(1f, 1f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = iconResId),
                        contentDescription = label,
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation label
                Text(
                    text = label,
                    style = MaterialTheme.typography.body1,
                    color = if (isPressed) textColor.copy(alpha = 0.7f) else textColor,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}