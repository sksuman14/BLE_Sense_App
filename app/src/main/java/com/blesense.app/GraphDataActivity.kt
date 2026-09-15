package com.blesense.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

/**
 * ChartScreen2 - A modern, responsive sensor dashboard screen with:
 * - Animated collapsing chart header
 * - Smooth scrolling sensor card list
 * - Dark/Light theme support
 * - Dynamic line chart visualization
 * - Mock BLE sensor data display
 */
@Composable
fun ChartScreen2(navController: NavController, title: String?, value: String?) {
    // Fallback title if null is passed
    val actualTitle = title ?: "Unknown Title"
    // Unused value parameter (can be used later for real-time value display)
    value ?: "Unknown Value"

    // Observe current theme mode (dark/light) from ThemeManager
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()

    // Hardcoded English strings (ideal for future localization via string resources)
    val unknownTitle = "Unknown Title"
    val sensorTitlePrefix = "Bluetooth Sensor"
    val advertisingType = "Advertising type"
    val dataStatus = "Data status"
    val primaryPhy = "Primary PHY"
    val bluetooth5 = "Bluetooth 5"
    val bluetooth42 = "Bluetooth 4.2"
    val complete = "Complete"
    val partial = "Partial"
    val le1m = "LE 1M"
    val leCoded = "LE Coded"

    // Mock list of Bluetooth sensor advertisements (simulating real scan results)
    val sensorData = listOf(
        "$sensorTitlePrefix 1" to "$advertisingType: $bluetooth5\n$dataStatus: $complete\n$primaryPhy: $le1m",
        "$sensorTitlePrefix 2" to "$advertisingType: $bluetooth5\n$dataStatus: $complete\n$primaryPhy: $le1m",
        "$sensorTitlePrefix 3" to "$advertisingType: $bluetooth42\n$dataStatus: $partial\n$primaryPhy: $le1m",
        "$sensorTitlePrefix 4" to "$advertisingType: $bluetooth5\n$dataStatus: $complete\n$primaryPhy: $leCoded",
        "$sensorTitlePrefix 1" to "$advertisingType: $bluetooth5\n$dataStatus: $complete\n$primaryPhy: $le1m",
        "$sensorTitlePrefix 2" to "$advertisingType: $bluetooth5\n$dataStatus: $complete\n$primaryPhy: $le1m",
        "$sensorTitlePrefix 3" to "$advertisingType: $bluetooth42\n$dataStatus: $partial\n$primaryPhy: $le1m"
    )

    // Dynamic background gradient based on current theme
    val backgroundGradient = if (isDarkMode) {
        Brush.verticalGradient(listOf(Color(0xFF121212), Color(0xFF424242)))
    } else {
        Brush.verticalGradient(listOf(Color.White, Color.LightGray))
    }

    // Theme-aware color palette
    val appBarBackground = if (isDarkMode) Color(0xFF121212) else Color.White
    val cardBackground = if (isDarkMode) Color(0xFF1E1E1E) else Color.White
    val chartBackground = if (isDarkMode) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black
    val secondaryTextColor = if (isDarkMode) Color(0xFFB0B0B0) else Color.Gray
    val chartLineColor = if (isDarkMode) Color(0xFFBB86FC) else Color.Blue

    // State to manage LazyColumn scroll position
    val listState = rememberLazyListState()

    // Controls whether the chart is collapsed (small) or expanded when scrolling
    var isSmallSize by remember { mutableStateOf(false) }

    // Smooth scale animation for collapsing chart effect
    val scale by animateFloatAsState(
        targetValue = if (isSmallSize) 0.5f else 1f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "ChartScaleAnimation"
    )

    // Detect scroll offset and trigger collapse animation when user scrolls down
    LaunchedEffect(remember { derivedStateOf { listState.firstVisibleItemScrollOffset } }) {
        isSmallSize = listState.firstVisibleItemScrollOffset > 0
    }

    // Main Scaffold layout with TopAppBar and scrollable content
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (title == null) unknownTitle else actualTitle,
                        fontSize = 20.sp,
                        color = textColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                },
                backgroundColor = appBarBackground,
                elevation = 4.dp
            )
        },
        backgroundColor = Color.Transparent // Allows gradient background to show through
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
                .padding(paddingValues)
        ) {
            // Animated collapsing chart header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((200 * scale).dp)
                    .scale(scale)
                    .background(chartBackground),
                contentAlignment = Alignment.Center
            ) {
                DynamicChart(
                    dataPoints = listOf(10f, 15f, 20f, 25f, 18f, 12f, 5f),
                    modifier = Modifier.fillMaxSize(),
                    lineColor = chartLineColor
                )
            }

            // Scrollable list of sensor cards
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(sensorData) { _, (itemTitle, itemValue) ->
                    RoundedSensorCard(
                        title = itemTitle,
                        value = itemValue,
                        cardBackground = cardBackground,
                        textColor = textColor,
                        secondaryTextColor = secondaryTextColor
                    )
                }
            }
        }
    }
}

/**
 * Reusable card component for displaying a sensor's title and details.
 */
@Composable
fun RoundedSensorCard(
    title: String,
    value: String,
    cardBackground: Color,
    textColor: Color,
    secondaryTextColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = 4.dp,
        backgroundColor = cardBackground
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 14.sp,
                color = secondaryTextColor
            )
        }
    }
}

/**
 * Custom Canvas-based line chart that draws a smooth connected line from data points.
 * Automatically scales vertically to fit min/max values.
 */
@Composable
fun DynamicChart(
    dataPoints: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color
) {
    Canvas(modifier = modifier) {
        if (dataPoints.size < 2) return@Canvas // Need at least 2 points to draw a line

        val stepX = size.width / (dataPoints.size - 1).coerceAtLeast(1)
        val maxY = dataPoints.maxOrNull() ?: 1f
        val minY = dataPoints.minOrNull() ?: 0f
        val rangeY = if (maxY - minY == 0f) 1f else maxY - minY

        // Draw line segments between consecutive points
        for (i in 0 until dataPoints.size - 1) {
            val x1 = i * stepX
            val y1 = size.height - ((dataPoints[i] - minY) / rangeY) * size.height
            val x2 = (i + 1) * stepX
            val y2 = size.height - ((dataPoints[i + 1] - minY) / rangeY) * size.height

            drawLine(
                color = lineColor,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 4f,
                pathEffect = PathEffect.cornerPathEffect(10f) // Smooth rounded corners
            )
        }
    }
}