package com.blesense.app.view.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.blesense.app.ui.theme.BleSenseColors
import com.blesense.app.model.*
import com.blesense.app.viewmodel.*
import com.blesense.app.repository.*
import com.blesense.app.util.*
import com.blesense.app.view.components.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

private val GreenAccent     = BleSenseColors.PrimaryGreen
private val GreenDark       = BleSenseColors.PrimaryGreenDark
private val BlueAccent      = BleSenseColors.BluetoothBlue

@Composable
fun SensorDashboardScreen(
    navController: NavHostController,
    bluetoothViewModel: BluetoothScanViewModel
) {
    val bluetoothDevicesRaw by bluetoothViewModel.devices.collectAsState()
    val activeDevices = remember(bluetoothDevicesRaw) {
        bluetoothDevicesRaw.filter { it.sensorData is SensorData.AWSData }
    }
    val isScanning by bluetoothViewModel.isScanning.collectAsState()
    val context = LocalContext.current
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()

    // Theme tokens
    val bgColor = if (isDarkMode) BleSenseColors.BackgroundDark else BleSenseColors.LightBackground
    val surfaceColor = if (isDarkMode) BleSenseColors.SurfaceDark else BleSenseColors.LightSurface
    val cardColor = if (isDarkMode) BleSenseColors.SurfaceDark else BleSenseColors.LightSurface
    val textPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val textSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary
    val dividerColor = if (isDarkMode) BleSenseColors.BorderDark else BleSenseColors.LightBorder
    val liveBg = if (isDarkMode) Color(0xFF162D1E) else Color(0xFFE8F5E9)

    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dot"
    )

    Scaffold(
        modifier = Modifier.systemBarsPadding(),
        backgroundColor = bgColor,
        topBar = {
            TopAppBar(
                backgroundColor = surfaceColor,
                elevation = 0.dp
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textPrimary)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(GreenAccent, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Sensors,
                                contentDescription = null,
                                tint = GreenDark,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text("Live Hub", fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 16.sp)
                            Text("Real-time Monitor", fontSize = 9.sp, color = textSecondary)
                        }
                    }

                    if (isScanning) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 48.dp)
                                .background(liveBg, RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).background(GreenAccent.copy(alpha = dotAlpha), CircleShape))
                            Text("Live", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = GreenAccent)
                        }
                    }

                    IconButton(
                        onClick = { ThemeManager.toggleDarkMode(context, !isDarkMode) },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = if (isDarkMode) BlueAccent else Color(0xFF5D4037),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (activeDevices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = GreenAccent, modifier = Modifier.size(42.dp), strokeWidth = 3.dp)
                    Text("Scanning for sensors...", color = textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(activeDevices) { device ->
                    val data = device.sensorData as SensorData.AWSData
                    SensorDetailCard(
                        deviceName =device.name,
                        deviceId = device.deviceId,
                        data = data,
                        cardColor = cardColor,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        dividerColor = dividerColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SensorDetailCard(
    deviceName: String,
    deviceId: String,
    data: SensorData.AWSData,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    dividerColor: Color
) {
    var currentTime by remember { mutableStateOf(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())) }
    
    LaunchedEffect(Unit) {
        while(true) {
            currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            delay(1000)
        }
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        backgroundColor = cardColor,
        elevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: Device Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = deviceName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    Text(
                        text = "NODE ID: $deviceId",
                        fontSize = 10.sp,
                        color = textSecondary,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Box(
                    modifier = Modifier
                        .background(GreenAccent.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = GreenAccent, modifier = Modifier.size(12.dp))
                        Text(currentTime, color = GreenAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Divider(color = dividerColor, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(20.dp))

            // Integrated Weather Monitoring Dashboard
            WeatherMonitoringDashboard(
                data = data,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                dividerColor = dividerColor
            )
        }
    }
}

@Composable
private fun WeatherMonitoringDashboard(
    data: SensorData.AWSData,
    textPrimary: Color,
    textSecondary: Color,
    dividerColor: Color
) {
    val temp = data.temperature.toFloatOrNull() ?: 0f
    val humidity = data.humidity.toFloatOrNull() ?: 0f
    val windSpeedMs = data.windSpeed.toFloatOrNull() ?: 0f
    val windSpeedKmH = windSpeedMs * 3.6f
    val windDirection = data.windDirection.toFloatOrNull() ?: 0f

    // Animated values with threshold filtering
    val animTemp by rememberAnimatedThresholdValue(temp, 0.2f)
    val animHumidity by rememberAnimatedThresholdValue(humidity, 1.0f)
    val animWindSpeed by rememberAnimatedThresholdValue(windSpeedKmH, 0.5f)
    val animWindDirection by rememberAnimatedRotation(windDirection, 2.0f)

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "WEATHER MONITORING SYSTEM",
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = textPrimary,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WeatherVisualCard(
                title = "Temperature",
                icon = "🌡️",
                subtitle = "Thermometer",
                value = "${String.format(Locale.US, "%.1f", animTemp)} °C",
                modifier = Modifier.weight(1f),
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                dividerColor = dividerColor
            ) {
                ThermometerVisual(animTemp)
            }
            WeatherVisualCard(
                title = "Humidity",
                icon = "💧",
                subtitle = "Water Gauge",
                value = "${animHumidity.toInt()}%",
                modifier = Modifier.weight(1f),
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                dividerColor = dividerColor
            ) {
                WaterGaugeVisual(animHumidity)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WeatherVisualCard(
                title = "Wind Speed",
                icon = "🌬️",
                subtitle = "Speedometer",
                value = "${String.format(Locale.US, "%.1f", animWindSpeed)} km/h",
                modifier = Modifier.weight(1f),
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                dividerColor = dividerColor
            ) {
                SpeedometerVisual(animWindSpeed)
            }
            WeatherVisualCard(
                title = "Wind Dir.",
                icon = "🧭",
                subtitle = "Compass",
                value = getWindDirectionText(animWindDirection),
                modifier = Modifier.weight(1f),
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                dividerColor = dividerColor
            ) {
                CompassVisual(animWindDirection)
            }
        }
    }
}

@Composable
private fun WeatherVisualCard(
    title: String,
    icon: String,
    subtitle: String,
    value: String,
    modifier: Modifier = Modifier,
    textPrimary: Color,
    textSecondary: Color,
    dividerColor: Color,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = dividerColor.copy(alpha = 0.05f),
        border = BorderStroke(0.5.dp, dividerColor.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textPrimary)
            Text(icon, fontSize = 20.sp)
            Box(modifier = Modifier.height(60.dp), contentAlignment = Alignment.Center) {
                content()
            }
            Text(subtitle, fontSize = 9.sp, color = textSecondary)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = textPrimary)
        }
    }
}

@Composable
private fun ThermometerVisual(temp: Float) {
    val color = when {
        temp < 15f -> lerp(Color(0xFF60A5FA), Color(0xFF2DD4BF), (temp / 15f).coerceIn(0f, 1f))
        temp < 25f -> lerp(Color(0xFF2DD4BF), Color(0xFFFBBF24), ((temp - 15f) / 10f).coerceIn(0f, 1f))
        else -> lerp(Color(0xFFFBBF24), Color(0xFFFF6467), ((temp - 25f) / 15f).coerceIn(0f, 1f))
    }
    
    Canvas(modifier = Modifier.size(24.dp, 50.dp)) {
        val w = size.width
        val h = size.height
        val bulbRadius = w / 2.5f
        val tubeWidth = w / 4f
        val tubeHeight = h - bulbRadius * 2
        
        // Tube outline
        drawRoundRect(
            color = color.copy(alpha = 0.15f),
            topLeft = Offset((w - tubeWidth) / 2, 0f),
            size = Size(tubeWidth, h - bulbRadius),
            cornerRadius = CornerRadius(tubeWidth / 2)
        )
        
        // Bulb outline
        drawCircle(
            color = color.copy(alpha = 0.15f),
            radius = bulbRadius,
            center = Offset(w / 2, h - bulbRadius)
        )
        
        // Mercury Level
        val maxTemp = 50f
        val fillHeight = (temp.coerceIn(0f, maxTemp) / maxTemp) * tubeHeight
        
        drawRoundRect(
            color = color,
            topLeft = Offset((w - tubeWidth) / 2, tubeHeight - fillHeight),
            size = Size(tubeWidth, fillHeight + bulbRadius),
            cornerRadius = CornerRadius(tubeWidth / 2)
        )
        
        drawCircle(
            color = color,
            radius = bulbRadius * 0.8f,
            center = Offset(w / 2, h - bulbRadius)
        )
        
        // Glow effect
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.3f), Color.Transparent),
                center = Offset(w / 2, h - bulbRadius),
                radius = bulbRadius * 2.5f
            ),
            radius = bulbRadius * 2.5f,
            center = Offset(w / 2, h - bulbRadius)
        )
    }
}

@Composable
private fun WaterGaugeVisual(humidity: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "water")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart),
        label = "wave"
    )

    Canvas(modifier = Modifier.size(50.dp, 50.dp)) {
        val w = size.width
        val h = size.height
        
        // Container
        drawRoundRect(
            color = Color(0xFF60A5FA).copy(alpha = 0.1f),
            size = size,
            cornerRadius = CornerRadius(12.dp.toPx())
        )
        
        // Water Path
        val path = Path()
        val fillHeight = (humidity.coerceIn(0f, 100f) / 100f) * h
        val startY = h - fillHeight
        
        path.moveTo(0f, h)
        path.lineTo(0f, startY)
        
        val amplitude = 3.dp.toPx()
        for (x in 0..w.toInt()) {
            val relativeX = x.toFloat() / w
            val y = startY + sin(relativeX * 2 * PI.toFloat() + waveOffset) * amplitude
            path.lineTo(x.toFloat(), y)
        }
        
        path.lineTo(w, h)
        path.close()
        
        drawPath(path, color = Color(0xFF60A5FA).copy(alpha = 0.8f))
        
        // Bubbles
        val bubblePhase = (waveOffset / (2 * PI.toFloat()))
        for (i in 0..2) {
            val bX = w * (0.3f + i * 0.2f)
            val bY = h - ((fillHeight * (bubblePhase + i * 0.3f) % fillHeight))
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = 2.dp.toPx(),
                center = Offset(bX, bY)
            )
        }
    }
}

@Composable
private fun SpeedometerVisual(speed: Float) {
    Canvas(modifier = Modifier.size(60.dp, 40.dp)) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2, h)
        val radius = w / 2
        
        // Gauge Arc
        drawArc(
            color = Color(0xFF2DD4BF).copy(alpha = 0.2f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(0f, 0f),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        
        // Needle
        val maxSpeed = 40f // km/h
        val angle = 180f + (speed.coerceIn(0f, maxSpeed) / maxSpeed) * 180f
        val needleLen = radius * 0.85f
        
        rotate(angle, pivot = center) {
            drawLine(
                color = Color(0xFF2DD4BF),
                start = center,
                end = Offset(center.x + needleLen, center.y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        
        drawCircle(color = Color(0xFF2DD4BF), radius = 3.dp.toPx(), center = center)
    }
}

@Composable
private fun CompassVisual(rotation: Float) {
    Canvas(modifier = Modifier.size(50.dp, 50.dp)) {
        val center = center
        val radius = size.minDimension / 2
        
        // Ring
        drawCircle(
            color = Color(0xFF6366F1).copy(alpha = 0.15f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )
        
        // Needle
        rotate(rotation - 90f, pivot = center) {
            val needleW = 5.dp.toPx()
            val needleH = radius * 0.8f
            
            // North (Red)
            val nPath = Path().apply {
                moveTo(center.x + needleH, center.y)
                lineTo(center.x, center.y - needleW / 2)
                lineTo(center.x, center.y + needleW / 2)
                close()
            }
            drawPath(nPath, color = Color(0xFFFF4444))
            
            // South (Indigo)
            val sPath = Path().apply {
                moveTo(center.x - needleH, center.y)
                lineTo(center.x, center.y - needleW / 2)
                lineTo(center.x, center.y + needleW / 2)
                close()
            }
            drawPath(sPath, color = Color(0xFF6366F1))
        }
        
        drawCircle(color = Color.DarkGray, radius = 2.dp.toPx(), center = center)
    }
}

@Composable
private fun rememberAnimatedThresholdValue(target: Float, threshold: Float): State<Float> {
    val animatedValue = remember { Animatable(target) }
    LaunchedEffect(target) {
        if (abs(target - animatedValue.value) > threshold) {
            animatedValue.animateTo(target, animationSpec = tween(600, easing = FastOutSlowInEasing))
        }
    }
    return animatedValue.asState()
}

@Composable
private fun rememberAnimatedRotation(target: Float, threshold: Float): State<Float> {
    var lastTarget by remember { mutableStateOf(target) }
    var cumulativeRotation by remember { mutableStateOf(target) }
    
    LaunchedEffect(target) {
        if (abs(target - lastTarget) > threshold) {
            val diff = (target - lastTarget) % 360
            val shortestDiff = if (diff > 180) diff - 360 else if (diff < -180) diff + 360 else diff
            cumulativeRotation += shortestDiff
            lastTarget = target
        }
    }
    
    return animateFloatAsState(
        targetValue = cumulativeRotation,
        animationSpec = tween(1000, easing = LinearOutSlowInEasing),
        label = "rotation"
    )
}

private fun getWindDirectionText(degrees: Float): String {
    val directions = listOf("North", "North-East", "East", "South-East", "South", "South-West", "West", "North-West", "North")
    val normalized = ((degrees % 360) + 360) % 360
    val index = ((normalized + 22.5) / 45).toInt()
    return directions[index % 8]
}


