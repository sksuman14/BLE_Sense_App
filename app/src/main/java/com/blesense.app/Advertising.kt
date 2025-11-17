package com.blesense.app

import android.app.Activity
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data class to hold text for the Advertising Data screen.
 */
data class AdvertisingText(
    val advertisingDataTitle: String = "Advertising Data",
    val deviceNameLabel: String = "Device Name",
    val nodeIdLabel: String = "Node ID",
    val downloadData: String = "DOWNLOAD DATA",
    val exportingData: String = "EXPORTING DATA...",
    val temperature: String = "Temperature",
    val humidity: String = "Humidity",
    val xAxis: String = "X-Axis",
    val yAxis: String = "Y-Axis",
    val zAxis: String = "Z-Axis",
    val nitrogen: String = "Nitrogen",
    val phosphorus: String = "Phosphorus",
    val potassium: String = "Potassium",
    val moisture: String = "Moisture",
    val electricConductivity: String = "Electric Conductivity",
    val pH: String = "pH",
    val lightIntensity: String = "Light Intensity",
    val speed: String = "Speed",
    val distance: String = "Distance",
    val objectDetected: String = "Object Detected",
    val steps: String = "Steps",
    val ammonia: String = "Ammonia",
    val resetSteps: String = "RESET STEPS",
    val warningTitle: String = "Warning",
    val warningMessage: String = "The %s has exceeded the threshold of %s!",
    val dismissButton: String = "Dismiss",
    val rawData: String = "Raw Data"
)

/**
 * Composable function to display the Advertising Data screen for a specific BLE device.
 */
@Composable
fun AdvertisingDataScreen(
    deviceAddress: String,
    deviceName: String,
    navController: NavController,
    deviceId: String,
    viewModel: BluetoothScanViewModel<Any?>
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val viewModel: BluetoothScanViewModel<Any> = viewModel(
        factory = remember { BluetoothScanViewModelFactory(context) }
    )

    // Launch BLE scan when activity is available
    LaunchedEffect(activity) {
        activity?.let { viewModel.startScan(it) }
    }

    // Initialize MediaPlayer as a state holder
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Initialize MediaPlayer only once
    LaunchedEffect(Unit) {
        mediaPlayer = MediaPlayer.create(context, R.raw.nuclear_alarm)?.apply {
            isLooping = true
        }
    }

    // Clean up MediaPlayer on disposal
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            }
            mediaPlayer = null
        }
    }

    // Collect UI state from ViewModel and managers
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val devices by viewModel.devices.collectAsState()

    val currentDevice by remember(devices, deviceAddress) {
        derivedStateOf { devices.find { it.address == deviceAddress } }
    }

    // State variables for threshold and alarm functionality
    var thresholdValue by remember { mutableStateOf("") }
    var isAlarmActive by remember { mutableStateOf(false) }
    var showAlertDialog by remember { mutableStateOf(false) }
    var parameterType by remember { mutableStateOf("Temperature") }
    var isThresholdSet by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Animate alarm blinking effect
    val isBlinking by remember(isAlarmActive) {
        derivedStateOf { isAlarmActive }
    }

    val blinkAlpha by animateFloatAsState(
        targetValue = if (isBlinking) 0.5f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkAlpha"
    )

    // Derive ammonia value from sensor data
    val ammoniaValue by remember(currentDevice?.sensorData) {
        derivedStateOf {
            when (val sensorData = currentDevice?.sensorData) {
                is BluetoothScanViewModel.SensorData.AmmoniaSensorData ->
                    sensorData.ammonia.replace(" ppm", "").toFloatOrNull() ?: 0f
                else -> 0f
            }
        }
    }

    // Debounce ammonia value updates
    var displayedAmmoniaValue by remember { mutableStateOf(0f) }
    val debouncedAmmoniaValue by remember(ammoniaValue) {
        derivedStateOf { ammoniaValue }
    }

    LaunchedEffect(debouncedAmmoniaValue) {
        displayedAmmoniaValue = debouncedAmmoniaValue
    }

    // Derive lux value from sensor data
    val luxValue by remember(currentDevice?.sensorData) {
        derivedStateOf {
            when (val sensorData = currentDevice?.sensorData) {
                is BluetoothScanViewModel.SensorData.LuxSensorData ->
                    sensorData.lux.toFloatOrNull() ?: 0f
                else -> 0f
            }
        }
    }

    // Handle threshold and alarm logic
    LaunchedEffect(currentDevice, thresholdValue, parameterType, isThresholdSet) {
        delay(500L)
        if (isThresholdSet) {
            val threshold = thresholdValue.toFloatOrNull()
            if (threshold != null) {
                when (val sensorData = currentDevice?.sensorData) {
                    is BluetoothScanViewModel.SensorData.SHT40Data -> {
                        val valueToCheck = when (parameterType) {
                            "Temperature" -> sensorData.temperature.toFloatOrNull()
                            "Humidity" -> sensorData.humidity.toFloatOrNull()
                            else -> null
                        }
                        isAlarmActive = valueToCheck != null && valueToCheck > threshold
                    }
                    is BluetoothScanViewModel.SensorData.AmmoniaSensorData -> {
                        isAlarmActive = parameterType == "Ammonia" && ammoniaValue > threshold
                    }
                    else -> isAlarmActive = false
                }

                if (isAlarmActive) {
                    showAlertDialog = true
                    mediaPlayer?.let { player ->
                        if (!player.isPlaying) {
                            try {
                                player.start()
                            } catch (e: IllegalStateException) {
                                // Recreate MediaPlayer if in invalid state
                                mediaPlayer = MediaPlayer.create(context, R.raw.nuclear_alarm)?.apply {
                                    isLooping = true
                                    start()
                                }
                            }
                        }
                    }
                } else {
                    mediaPlayer?.let { player ->
                        try {
                            if (player.isPlaying) {
                                player.stop()
                                player.prepare()
                            }
                        } catch (e: IllegalStateException) {
                            // Recreate MediaPlayer if in invalid state
                            mediaPlayer = MediaPlayer.create(context, R.raw.nuclear_alarm)?.apply {
                                isLooping = true
                            }
                        }
                    }
                    showAlertDialog = false
                }
            } else {
                isAlarmActive = false
                showAlertDialog = false
                mediaPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            player.stop()
                            player.prepare()
                        }
                    } catch (e: IllegalStateException) {
                        mediaPlayer = MediaPlayer.create(context, R.raw.nuclear_alarm)?.apply {
                            isLooping = true
                        }
                    }
                }
            }
        } else {
            isAlarmActive = false
            showAlertDialog = false
            mediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                        player.prepare()
                    }
                } catch (e: IllegalStateException) {
                    mediaPlayer = MediaPlayer.create(context, R.raw.nuclear_alarm)?.apply {
                        isLooping = true
                    }
                }
            }
        }
    }

    // Use fixed English text
    val advertisingText = AdvertisingText()

    // Derive display data based on sensor type
    val displayData by remember(currentDevice?.sensorData, advertisingText) {
        derivedStateOf {
            when (val sensorData = currentDevice?.sensorData) {
                is BluetoothScanViewModel.SensorData.SHT40Data -> listOf(
                    "Device ID" to sensorData.deviceId,
                    advertisingText.temperature to "${sensorData.temperature.takeIf { it.isNotEmpty() } ?: "0"}°C",
                    advertisingText.humidity to "${sensorData.humidity.takeIf { it.isNotEmpty() } ?: "0"}%"
                )
                is BluetoothScanViewModel.SensorData.SDTData -> listOf(
                    "Device ID" to sensorData.deviceId,
                    advertisingText.speed to "${sensorData.speed.takeIf { it.isNotEmpty() } ?: "0"} m/s",
                    advertisingText.distance to "${sensorData.distance.takeIf { it.isNotEmpty() } ?: "0"} m"
                )
                is BluetoothScanViewModel.SensorData.LIS2DHData -> listOf(
                    "Device ID" to sensorData.deviceId,
                    advertisingText.xAxis to "${sensorData.x.takeIf { it.isNotEmpty() } ?: "0"} m/s²",
                    advertisingText.yAxis to "${sensorData.y.takeIf { it.isNotEmpty() } ?: "0"} m/s²",
                    advertisingText.zAxis to "${sensorData.z.takeIf { it.isNotEmpty() } ?: "0"} m/s²"
                )
                is BluetoothScanViewModel.SensorData.SoilSensorData -> listOf(
                    "Device ID" to sensorData.deviceId,
                    advertisingText.nitrogen to "${sensorData.nitrogen.takeIf { it.isNotEmpty() } ?: "0"} mg/kg",
                    advertisingText.phosphorus to "${sensorData.phosphorus.takeIf { it.isNotEmpty() } ?: "0"} mg/kg",
                    advertisingText.potassium to "${sensorData.potassium.takeIf { it.isNotEmpty() } ?: "0"} mg/kg",
                    advertisingText.moisture to "${sensorData.moisture.takeIf { it.isNotEmpty() } ?: "0"}%",
                    advertisingText.temperature to "${sensorData.temperature.takeIf { it.isNotEmpty() } ?: "0"}°C",
                    advertisingText.electricConductivity to "${sensorData.ec.takeIf { it.isNotEmpty() } ?: "0"} mS/cm",
                    advertisingText.pH to "${sensorData.pH.takeIf { it.isNotEmpty() } ?: "0"}"
                )
                is BluetoothScanViewModel.SensorData.AmmoniaSensorData -> listOf(
                    "Device ID" to sensorData.deviceId,
                    advertisingText.ammonia to sensorData.ammonia,
                    advertisingText.rawData to sensorData.rawData
                )
                is BluetoothScanViewModel.SensorData.DataLoggerData -> listOf(
                    "Device ID" to sensorData.deviceId,
                    "Packet ID" to "${sensorData.packetId}",
                    "Packet Count" to "${sensorData.payloadPackets.size}",
                    advertisingText.rawData to sensorData.rawData
                )
                else -> emptyList()
            }
        }
    }

    // Define background gradient based on theme
    val backgroundGradient = if (isDarkMode) {
        Brush.verticalGradient(listOf(Color(0xFF1E1E1E), Color(0xFF424242)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF0A74DA), Color(0xFFADD8E6)))
    }

    val cardBackground = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFF2A9EE5)
    val textColor = if (isDarkMode) Color.White else Color.White
    val buttonColor = if (isDarkMode) Color(0xFF64B5F6) else Color(0xFF0A74DA)

    // Clean up resources on navigation change
    DisposableEffect(navController) {
        onDispose {
            viewModel.stopScan()
            viewModel.clearDevices()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .padding(WindowInsets.systemBars.asPaddingValues()),
        contentAlignment = Alignment.Center
    ) {
        if (isAlarmActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Red.copy(alpha = blinkAlpha))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WindowInsets.systemBars.asPaddingValues()),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HeaderSection(
                navController = navController,
                viewModel = viewModel,
                deviceAddress = deviceAddress,
                advertisingText = advertisingText,
                textColor = textColor
            )
            Spacer(modifier = Modifier.height(16.dp))

            DeviceInfoSection(
                deviceName = deviceName,
                deviceAddress = deviceAddress,
                deviceId = deviceId,
                advertisingText = advertisingText,
                cardBackground = cardBackground,
                cardGradient = Brush.verticalGradient(listOf(cardBackground, cardBackground.copy(alpha = 0.6f))),
                textColor = textColor
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (currentDevice?.sensorData is BluetoothScanViewModel.SensorData.LuxSensorData) {
                LuxSensorDisplay(
                    luxValue = luxValue,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ResponsiveDataCards(
                data = displayData,
                cardBackground = cardBackground,
                advertisingText = advertisingText,
                textColor = textColor
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (currentDevice?.sensorData is BluetoothScanViewModel.SensorData.SHT40Data ||
                currentDevice?.sensorData is BluetoothScanViewModel.SensorData.AmmoniaSensorData
            ) {
                ThresholdInputSection(
                    thresholdValue = thresholdValue,
                    onThresholdChange = { thresholdValue = it },
                    parameterType = parameterType,
                    onParameterChange = { parameterType = it },
                    isDarkMode = isDarkMode,
                    sensorData = currentDevice?.sensorData,
                    onConfirmThreshold = {
                        if (thresholdValue.toFloatOrNull() != null) {
                            isThresholdSet = true
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (currentDevice?.sensorData is BluetoothScanViewModel.SensorData.DataLoggerData) {
                val dataLoggerData = currentDevice!!.sensorData as BluetoothScanViewModel.SensorData.DataLoggerData
                DataLoggerDisplay(
                    rawData = dataLoggerData.rawData,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            DownloadButton(
                viewModel = viewModel,
                deviceAddress = deviceAddress,
                deviceName = deviceName,
                deviceId = deviceId,
                advertisingText = advertisingText
            )

            if (showAlertDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showAlertDialog = false
                        isAlarmActive = false
                        isThresholdSet = false
                        try {
                            mediaPlayer?.stop()
                            mediaPlayer?.prepare()
                        } catch (e: IllegalStateException) {
                            mediaPlayer?.reset()
                            MediaPlayer.create(context, R.raw.nuclear_alarm)?.let {
                                mediaPlayer?.release()
                                mediaPlayer = it
                            }
                        }
                    },
                    title = { Text(advertisingText.warningTitle) },
                    text = {
                        Text(
                            text = advertisingText.warningMessage.format(
                                parameterType,
                                thresholdValue
                            )
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showAlertDialog = false
                                isAlarmActive = false
                                isThresholdSet = false
                                try {
                                    mediaPlayer?.stop()
                                    mediaPlayer?.prepare()
                                } catch (e: IllegalStateException) {
                                    mediaPlayer?.reset()
                                    MediaPlayer.create(context, R.raw.nuclear_alarm)?.let {
                                        mediaPlayer?.release()
                                        mediaPlayer = it
                                    }
                                }
                            }
                        ) {
                            Text(advertisingText.dismissButton)
                        }
                    }
                )
            }
        }
    }
}

/**
 * Composable to display ammonia sensor data with an animated ring visualization.
 */
@Composable
fun AmmoniaSensorDisplay(
    ammoniaValue: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AmmoniaRingAnimation(ammoniaValue = ammoniaValue)
        Text(
            text = "%.1f ppm".format(ammoniaValue),
            style = MaterialTheme.typography.displayMedium,
            color = when {
                ammoniaValue > 50 -> Red
                ammoniaValue > 25 -> Color.Yellow
                else -> Color.Green
            }
        )
    }
}

/**
 * Animated ring visualization for ammonia sensor data.
 */
@Composable
fun AmmoniaRingAnimation(
    ammoniaValue: Float,
    modifier: Modifier = Modifier
) {
    val animatedFill by animateFloatAsState(
        targetValue = (ammoniaValue / 100f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 100f),
        label = "ammoniaFill"
    )

    val liquidColor by animateColorAsState(
        targetValue = when {
            ammoniaValue <= 25 -> Color(0xFF4CAF50)
            ammoniaValue <= 50 -> Color(0xFFFFC107)
            else -> Color(0xFFF44336)
        },
        animationSpec = tween(durationMillis = 300),
        label = "liquidColor"
    )

    Box(
        modifier = modifier
            .size(220.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension * 0.4f
            val ringWidth = size.minDimension * 0.1f

            drawArc(
                color = Color(0xFF333333).copy(alpha = 0.3f),
                startAngle = 270f,
                sweepAngle = 360f,
                useCenter = false,
                size = Size(radius * 2, radius * 2),
                topLeft = Offset(center.x - radius, center.y - radius),
                style = Stroke(width = ringWidth)
            )

            drawArc(
                color = liquidColor.copy(alpha = 0.7f),
                startAngle = 270f,
                sweepAngle = -360f * animatedFill,
                useCenter = false,
                size = Size(radius * 2, radius * 2),
                topLeft = Offset(center.x - radius, center.y - radius),
                style = Stroke(width = ringWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%.1f".format(ammoniaValue),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "ppm",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Composable to display lux sensor data with an animated ring visualization.
 */
@Composable
fun LuxSensorDisplay(
    luxValue: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LuxRingAnimation(luxValue = luxValue)
        Text(
            text = "%.0f LUX".format(luxValue),
            style = MaterialTheme.typography.displayMedium,
            color = when {
                luxValue > 10000 -> Red
                luxValue > 5000 -> Color.Yellow
                else -> Color.Green
            }
        )
    }
}

/**
 * Animated ring visualization for lux sensor data.
 */
@Composable
fun LuxRingAnimation(
    luxValue: Float,
    modifier: Modifier = Modifier
) {
    val animatedFill by animateFloatAsState(
        targetValue = (luxValue / 20000f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 100f),
        label = "luxFill"
    )

    val lightColor by animateColorAsState(
        targetValue = when {
            luxValue > 10000 -> Color(0xFFF44336)
            luxValue > 5000 -> Color(0xFFFFC107)
            else -> Color(0xFF4CAF50)
        },
        animationSpec = tween(durationMillis = 300),
        label = "lightColor"
    )

    Box(
        modifier = modifier
            .size(220.dp)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension * 0.4f
            val ringWidth = size.minDimension * 0.1f

            drawArc(
                color = Color(0xFF333333).copy(alpha = 0.3f),
                startAngle = 270f,
                sweepAngle = 360f,
                useCenter = false,
                size = Size(radius * 2, radius * 2),
                topLeft = Offset(center.x - radius, center.y - radius),
                style = Stroke(width = ringWidth)
            )

            drawArc(
                color = lightColor.copy(alpha = 0.7f),
                startAngle = 270f,
                sweepAngle = -360f * animatedFill,
                useCenter = false,
                size = Size(radius * 2, radius * 2),
                topLeft = Offset(center.x - radius, center.y - radius),
                style = Stroke(width = ringWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%.0f".format(luxValue),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "LUX",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Composable for inputting and confirming threshold values for sensor parameters.
 */
@Composable
private fun ThresholdInputSection(
    thresholdValue: String,
    onThresholdChange: (String) -> Unit,
    parameterType: String,
    onParameterChange: (String) -> Unit,
    isDarkMode: Boolean,
    sensorData: BluetoothScanViewModel.SensorData?,
    onConfirmThreshold: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val parameters = when (sensorData) {
                is BluetoothScanViewModel.SensorData.SHT40Data -> listOf("Temperature", "Humidity")
                is BluetoothScanViewModel.SensorData.AmmoniaSensorData -> listOf("Ammonia")
                else -> emptyList()
            }

            parameters.forEach { type ->
                Button(
                    onClick = { onParameterChange(type) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (parameterType == type) {
                            if (isDarkMode) Color(0xFF64B5F6) else Color(0xFF0A74DA)
                        } else {
                            if (isDarkMode) Color(0xFF424242) else Color(0xFFADD8E6)
                        }
                    )
                ) {
                    Text(type)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = thresholdValue,
            onValueChange = { onThresholdChange(it) },
            label = { Text("Enter $parameterType Threshold") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            isError = thresholdValue.isNotEmpty() && thresholdValue.toFloatOrNull() == null,
            supportingText = {
                if (thresholdValue.isNotEmpty() && thresholdValue.toFloatOrNull() == null) {
                    Text("Please enter a valid number")
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = if (isDarkMode) Color(0xFF2A2A2A) else Color.White,
                unfocusedContainerColor = if (isDarkMode) Color(0xFF2A2A2A) else Color.White,
                focusedTextColor = if (isDarkMode) Color.White else Color.Black,
                unfocusedTextColor = if (isDarkMode) Color.White else Color.Black,
                errorContainerColor = if (isDarkMode) Color(0xFF2A2A2A) else Color.White
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onConfirmThreshold,
            enabled = thresholdValue.isNotEmpty() && thresholdValue.toFloatOrNull() != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDarkMode) Color(0xFF64B5F6) else Color(0xFF0A74DA)
            )
        ) {
            Text("Confirm Threshold")
        }
    }
}

/**
 * Composable for the header section with navigation and graph icon.
 */
@Composable
private fun HeaderSection(
    navController: NavController,
    viewModel: BluetoothScanViewModel<Any>,
    deviceAddress: String,
    advertisingText: AdvertisingText,
    textColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                viewModel.stopScan()
                navController.popBackStack()
            }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = textColor
            )
        }

        Text(
            text = advertisingText.advertisingDataTitle,
            fontFamily = helveticaFont,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        IconButton(
            onClick = { navController.navigate("chart_screen/$deviceAddress") }
        ) {
            Image(
                painter = painterResource(id = R.drawable.graph),
                contentDescription = "Graph Icon",
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

/**
 * Composable for displaying device information cards.
 */
@Composable
private fun DeviceInfoSection(
    deviceName: String,
    deviceAddress: String,
    deviceId: String,
    advertisingText: AdvertisingText,
    cardBackground: Color,
    cardGradient: Brush,
    textColor: Color
) {
    InfoCard(
        text = "${advertisingText.deviceNameLabel}: $deviceName ($deviceAddress)",
        cardBackground = cardBackground,
        cardGradient = cardGradient,
        textColor = textColor
    )
    Spacer(modifier = Modifier.height(8.dp))
    InfoCard(
        text = "${advertisingText.nodeIdLabel}: $deviceId",
        cardBackground = cardBackground,
        cardGradient = cardGradient,
        textColor = textColor
    )
}

/**
 * Composable for individual info card display.
 */
@Composable
private fun InfoCard(
    text: String,
    cardBackground: Color,
    cardGradient: Brush,
    textColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 49.dp, max = 80.dp)
            .padding(horizontal = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = cardBackground
    ) {
        Box(
            modifier = Modifier
                .background(cardGradient)
                .padding(12.dp)
        ) {
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )
        }
    }
}

/**
 * Composable for initiating data download as a CSV file.
 */
@Composable
fun DownloadButton(
    viewModel: BluetoothScanViewModel<Any>,
    deviceAddress: String,
    deviceName: String,
    deviceId: String,
    advertisingText: AdvertisingText
) {
    val context = LocalContext.current
    var isExporting by remember { mutableStateOf(false) }
    var showToast by remember { mutableStateOf(false) }

    val isDarkMode by ThemeManager.isDarkMode.collectAsState()

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            isExporting = true
            exportDataToCSV(context, uri, viewModel, deviceAddress, deviceName, deviceId) {
                isExporting = false
                showToast = true
            }
        }
    }

    if (showToast) {
        LaunchedEffect(Unit) {
            showToast = false
        }
    }

    val buttonBackgroundColor = if (isDarkMode) Color(0xFFBB86FC) else Color(0xFF0A74DA)
    val buttonTextColor = if (isDarkMode) Color.Black else Color.White

    Button(
        onClick = {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "sensor_data_${deviceId}_$timestamp.csv"
            createDocumentLauncher.launch(filename)
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        enabled = !isExporting,
        colors = ButtonDefaults.buttonColors(containerColor = buttonBackgroundColor)
    ) {
        Text(
            text = if (isExporting) advertisingText.exportingData else advertisingText.downloadData,
            color = buttonTextColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Exports sensor data to a CSV file.
 */
private fun exportDataToCSV(
    context: Context,
    uri: Uri,
    viewModel: BluetoothScanViewModel<Any>,
    deviceAddress: String,
    deviceName: String,
    deviceId: String,
    onComplete: () -> Unit
) {
    MainScope().launch {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val historicalData = viewModel.getHistoricalDataForDevice(deviceAddress)

                    val headerBuilder = StringBuilder()
                    headerBuilder.append("Timestamp,Device Name,Device Address,Node ID,")

                    val sensorType = if (historicalData.isNotEmpty()) historicalData[0].sensorData else null

                    when (sensorType) {
                        is BluetoothScanViewModel.SensorData.SHT40Data ->
                            headerBuilder.append("Temperature (°C),Humidity (%)")
                        is BluetoothScanViewModel.SensorData.LIS2DHData ->
                            headerBuilder.append("X-Axis (m/s²),Y-Axis (m/s²),Z-Axis (m/s²)")
                        is BluetoothScanViewModel.SensorData.SoilSensorData ->
                            headerBuilder.append("Nitrogen (mg/kg),Phosphorus (mg/kg),Potassium (mg/kg),Moisture (%),Temperature (°C),Electric Conductivity (mS/cm),pH")
                        is BluetoothScanViewModel.SensorData.LuxSensorData ->
                            headerBuilder.append("Light Intensity (LUX)")
                        is BluetoothScanViewModel.SensorData.SDTData ->
                            headerBuilder.append("Speed (m/s),Distance (m)")
                        is BluetoothScanViewModel.SensorData.AmmoniaSensorData ->
                            headerBuilder.append("Ammonia (ppm)")
                        is BluetoothScanViewModel.SensorData.DataLoggerData ->
                            headerBuilder.append("Packet ID,Packet Count,Raw Data")
                        else -> {}
                    }

                    headerBuilder.append("\n")
                    outputStream.write(headerBuilder.toString().toByteArray())

                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

                    historicalData.forEach { entry ->
                        val dataBuilder = StringBuilder()
                        dataBuilder.append("${dateFormat.format(Date(entry.timestamp))},$deviceName,$deviceAddress,$deviceId,")

                        when (val sensorData = entry.sensorData) {
                            is BluetoothScanViewModel.SensorData.SHT40Data ->
                                dataBuilder.append("${sensorData.temperature},${sensorData.humidity}")
                            is BluetoothScanViewModel.SensorData.LIS2DHData ->
                                dataBuilder.append("${sensorData.x},${sensorData.y},${sensorData.z}")
                            is BluetoothScanViewModel.SensorData.SoilSensorData ->
                                dataBuilder.append("${sensorData.nitrogen},${sensorData.phosphorus},${sensorData.potassium},${sensorData.moisture},${sensorData.temperature},${sensorData.ec},${sensorData.pH}")
                            is BluetoothScanViewModel.SensorData.LuxSensorData ->
                                dataBuilder.append("${sensorData.lux}")
                            is BluetoothScanViewModel.SensorData.SDTData ->
                                dataBuilder.append("${sensorData.speed},${sensorData.distance}")
                            is BluetoothScanViewModel.SensorData.AmmoniaSensorData ->
                                dataBuilder.append("${sensorData.ammonia}")
                            is BluetoothScanViewModel.SensorData.DataLoggerData ->
                                dataBuilder.append("${sensorData.packetId},${sensorData.payloadPackets.size},\"${sensorData.rawData.replace("\"", "\"\"")}\"")
                            else -> {}
                        }

                        dataBuilder.append("\n")
                        outputStream.write(dataBuilder.toString().toByteArray())

                        if (historicalData.indexOf(entry) % 100 == 0) outputStream.flush()
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }
}

/**
 * Composable to display DataLogger data with toggle between continuous and large data modes.
 */
@Composable
fun DataLoggerDisplay(
    rawData: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "DataLogger Raw Data",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF2A9EE5).copy(alpha = 0.7f)
        ) {
            Text(
                text = rawData,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

/**
 * Composable for displaying responsive data cards based on sensor type.
 */
@Composable
private fun ResponsiveDataCards(
    data: List<Pair<String, String>>,
    cardBackground: Color,
    advertisingText: AdvertisingText,
    textColor: Color
) {
    val ammoniaData = data.find { it.first.contains("Ammonia", ignoreCase = true) }
    val rawData = data.find { it.first.contains("Raw Data", ignoreCase = true) }
    val otherData = data.filterNot {
        it.first.contains("Ammonia", ignoreCase = true) ||
                it.first.contains("Reflectance", ignoreCase = true) ||
                it.first.contains("Raw Data", ignoreCase = true)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ammoniaData?.let { (label, value) ->
            Column(
                modifier = Modifier.padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = label,
                    fontSize = 18.sp,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                AmmoniaRingAnimation(
                    ammoniaValue = value.replace(" ppm", "").toFloatOrNull() ?: 0f
                )
            }
        }

        rawData?.let { (label, value) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Raw Sensor Data",
                    style = MaterialTheme.typography.titleLarge,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = cardBackground.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        when (otherData.size) {
            0 -> Box(modifier = Modifier.height(100.dp))
            1 -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                DataCard(
                    label = otherData[0].first,
                    value = otherData[0].second,
                    cardBackground = cardBackground,
                    advertisingText = advertisingText,
                    textColor = textColor
                )
            }
            2 -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                otherData.forEach { (label, value) ->
                    DataCard(
                        label = label,
                        value = value,
                        cardBackground = cardBackground,
                        advertisingText = advertisingText,
                        textColor = textColor
                    )
                }
            }
            else -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                otherData.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowItems.forEach { (label, value) ->
                            DataCard(
                                label = label,
                                value = value,
                                cardBackground = cardBackground,
                                advertisingText = advertisingText,
                                textColor = textColor
                            )
                        }
                        if (rowItems.size == 1) Spacer(modifier = Modifier.width(141.dp))
                    }
                }
            }
        }
    }
}

/**
 * Composable for individual data card with dynamic coloring based on value.
 */
@Composable
fun DataCard(
    label: String,
    value: String,
    cardBackground: Color,
    advertisingText: AdvertisingText,
    textColor: Color
) {
    val numericValue = value.replace("[^0-9.]".toRegex(), "").toFloatOrNull() ?: 0f

    val dynamicColor = when {
        label == advertisingText.temperature -> when {
            numericValue <= 15f -> Color(0xFF2196F3)
            numericValue <= 30f -> Color(0xFF4CAF50)
            else -> Color(0xFFF44336)
        }
        label == advertisingText.humidity -> when {
            numericValue <= 40f -> Color(0xFF2196F3)
            numericValue <= 70f -> Color(0xFF4CAF50)
            else -> Color(0xFFF44336)
        }
        else -> cardBackground
    }

    val cardGradient = Brush.verticalGradient(
        colors = listOf(
            dynamicColor.copy(alpha = 0.8f),
            dynamicColor.copy(alpha = 0.6f)
        )
    )

    Surface(
        modifier = Modifier
            .height(95.dp)
            .width(141.dp),
        shape = RoundedCornerShape(16.dp),
        color = dynamicColor,
        tonalElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .background(cardGradient, RoundedCornerShape(16.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    fontSize = 18.sp,
                    color = textColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}