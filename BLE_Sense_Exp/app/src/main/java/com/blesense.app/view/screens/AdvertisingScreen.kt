package com.blesense.app.view.screens

import android.app.Activity
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.blesense.app.R
import com.blesense.app.model.BluetoothDeviceModel as BluetoothDevice
import com.blesense.app.model.HistoricalDataEntry
import com.blesense.app.model.SensorData
import com.blesense.app.viewmodel.BluetoothScanViewModel
import com.blesense.app.util.ThemeManager
import com.blesense.app.ui.theme.BleSenseColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

// ── BleSense unified design tokens ─────────────────────────────────────────────
private val GreenAccent     = Color(0xFF00BC7D)
private val GreenDark       = Color(0xFF0D542B)
private val GreenMuted      = Color(0x2600BC7D)
private val BlueAccent      = Color(0xFF60A5FA)
private val YellowAccent    = Color(0xFFFBBF24)
private val OrangeAccent    = Color(0xFFFB923C)
private val PurpleAccent    = Color(0xFFA78BFA)
private val TealAccent      = Color(0xFF2DD4BF)
private val RedAccent       = Color(0xFFFF6467)

/**
 * Localization-ready text strings for the Advertising Data screen.
 */
data class AdvertisingText(
    val advertisingDataTitle: String = "Advertising Data",
    val deviceNameLabel: String = "Device Name",
    val nodeIdLabel: String = "Device Address",
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
    val salinity: String = "Salinity",
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

// ══════════════════════════════════════════════════════════════════════════════
// ADVERTISING DATA SCREEN
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun AdvertisingDataScreen(
    deviceAddress: String,
    deviceName: String,
    navController: NavController,
    deviceId: String,
    viewModel: BluetoothScanViewModel
) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val BgDark = if (isDarkMode) BleSenseColors.BackgroundDark else BleSenseColors.LightBackground
    val SurfaceDark = if (isDarkMode) BleSenseColors.SurfaceDark else BleSenseColors.LightSurface
    val CardDark = if (isDarkMode) BleSenseColors.CardDark else BleSenseColors.LightSurface
    val CardDark2 = if (isDarkMode) BleSenseColors.CardDarkElevated else BleSenseColors.LightBackground
    val DividerDark = if (isDarkMode) BleSenseColors.BorderDark else BleSenseColors.LightBorder
    val TextPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val TextSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    val context  = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(activity) {
        activity?.let { viewModel.startScan(it) }
    }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    LaunchedEffect(Unit) {
        mediaPlayer = MediaPlayer.create(context, R.raw.nuclear_alarm)?.apply { isLooping = true }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.let { p -> if (p.isPlaying) p.stop(); p.reset(); p.release() }
            mediaPlayer = null
        }
    }

    val devices      by viewModel.devices.collectAsState()

    var liveSensorData by remember { mutableStateOf<SensorData?>(null) }

    LaunchedEffect(deviceAddress) {
        if (deviceAddress.startsWith("AA:BB:CC:00")) {
            val mock = com.blesense.app.util.MockSensorUtils.getMockDataForAddress(deviceAddress)
            if (mock != null) {
                launch {
                    val rand = java.util.Random()
                    var currentMock: SensorData = mock
                    while (isActive) {
                        val updated: SensorData = when (val m = currentMock) {
                            is SensorData.SHT40Data -> {
                                val curTemp = m.temperature.replace(",", ".").toFloatOrNull() ?: 24.5f
                                val curHum = m.humidity.replace(",", ".").toFloatOrNull() ?: 48.2f
                                m.copy(
                                    temperature = String.format(Locale.US, "%.2f", (curTemp + (rand.nextFloat() - 0.48f) * 0.4f).coerceIn(15f, 40f)),
                                    humidity = String.format(Locale.US, "%.2f", (curHum + (rand.nextFloat() - 0.48f) * 1.0f).coerceIn(20f, 90f))
                                )
                            }
                            is SensorData.LIS3DHData -> {
                                val curX = m.x.replace(",", ".").toFloatOrNull() ?: 0.01f
                                val curY = m.y.replace(",", ".").toFloatOrNull() ?: 0.02f
                                val curZ = m.z.replace(",", ".").toFloatOrNull() ?: 1.00f
                                m.copy(
                                    x = String.format(Locale.US, "%.2f", curX + (rand.nextFloat() - 0.5f) * 0.15f),
                                    y = String.format(Locale.US, "%.2f", curY + (rand.nextFloat() - 0.5f) * 0.15f),
                                    z = String.format(Locale.US, "%.2f", curZ + (rand.nextFloat() - 0.5f) * 0.15f)
                                )
                            }
                            is SensorData.SoilSensorData -> {
                                val curMoist = m.moisture.replace(",", ".").toFloatOrNull() ?: 34f
                                val curTemp = m.temperature.replace(",", ".").toFloatOrNull() ?: 24f
                                val curEc = m.ec.replace(",", ".").toFloatOrNull() ?: 450f
                                val curPh = m.pH.replace(",", ".").toFloatOrNull() ?: 6.8f
                                val curSal = m.salinity.replace(",", ".").toFloatOrNull() ?: 120f
                                val curN = m.nitrogen.replace(",", ".").toFloatOrNull() ?: 45f
                                val curP = m.phosphorus.replace(",", ".").toFloatOrNull() ?: 38f
                                val curK = m.potassium.replace(",", ".").toFloatOrNull() ?: 110f

                                m.copy(
                                    moisture = String.format(Locale.US, "%.1f", (curMoist + (rand.nextFloat() - 0.48f) * 1.5f).coerceIn(15f, 70f)),
                                    temperature = String.format(Locale.US, "%.1f", (curTemp + (rand.nextFloat() - 0.5f) * 0.6f).coerceIn(15f, 38f)),
                                    ec = String.format(Locale.US, "%.0f", (curEc + (rand.nextFloat() - 0.5f) * 25f).coerceIn(150f, 950f)),
                                    pH = String.format(Locale.US, "%.1f", (curPh + (rand.nextFloat() - 0.5f) * 0.1f).coerceIn(5.5f, 8.5f)),
                                    salinity = String.format(Locale.US, "%.0f", (curSal + (rand.nextFloat() - 0.5f) * 8f).coerceIn(50f, 320f)),
                                    nitrogen = String.format(Locale.US, "%.0f", (curN + (rand.nextFloat() - 0.5f) * 4f).coerceIn(10f, 95f)),
                                    phosphorus = String.format(Locale.US, "%.0f", (curP + (rand.nextFloat() - 0.5f) * 3f).coerceIn(10f, 85f)),
                                    potassium = String.format(Locale.US, "%.0f", (curK + (rand.nextFloat() - 0.5f) * 6f).coerceIn(40f, 220f))
                                )
                            }
                            is SensorData.VEML7700Data -> {
                                val curLux = m.lux.replace(",", ".").toFloatOrNull() ?: 850f
                                m.copy(lux = String.format(Locale.US, "%.0f", (curLux + (rand.nextFloat() - 0.5f) * 80f).coerceAtLeast(0f)))
                            }
                            is SensorData.VCNL4040Data -> {
                                val curLux = m.lux.replace(",", ".").toFloatOrNull() ?: 1200f
                                m.copy(lux = String.format(Locale.US, "%.0f", (curLux + (rand.nextFloat() - 0.5f) * 100f).coerceAtLeast(0f)))
                            }
                            is SensorData.AHT20Data -> {
                                val curTemp = m.temperature.replace(",", ".").toFloatOrNull() ?: 22.1f
                                val curHum = m.humidity.replace(",", ".").toFloatOrNull() ?: 55.4f
                                m.copy(
                                    temperature = String.format(Locale.US, "%.2f", (curTemp + (rand.nextFloat() - 0.48f) * 0.4f).coerceIn(15f, 40f)),
                                    humidity = String.format(Locale.US, "%.2f", (curHum + (rand.nextFloat() - 0.48f) * 1.0f).coerceIn(20f, 90f))
                                )
                            }
                            is SensorData.BME680Data -> {
                                val curTemp = m.temperature.replace(",", ".").toFloatOrNull() ?: 28.3f
                                val curHum = m.humidity.replace(",", ".").toFloatOrNull() ?: 42.1f
                                val curPress = m.pressure.replace(",", ".").toFloatOrNull() ?: 1013f
                                m.copy(
                                    temperature = String.format(Locale.US, "%.2f", (curTemp + (rand.nextFloat() - 0.48f) * 0.4f).coerceIn(15f, 40f)),
                                    humidity = String.format(Locale.US, "%.2f", (curHum + (rand.nextFloat() - 0.48f) * 1.0f).coerceIn(20f, 90f)),
                                    pressure = String.format(Locale.US, "%.1f", (curPress + (rand.nextFloat() - 0.5f) * 2f).coerceIn(950f, 1050f))
                                )
                            }
                            is SensorData.WeatherData -> {
                                val curTemp = m.temperature.replace(",", ".").toFloatOrNull() ?: 24.0f
                                val curHum = m.humidity.replace(",", ".").toFloatOrNull() ?: 50.0f
                                val curLux = m.lux.replace(",", ".").toFloatOrNull() ?: 1500f
                                val curPress = m.pressure.replace(",", ".").toFloatOrNull() ?: 1015f
                                m.copy(
                                    temperature = String.format(Locale.US, "%.2f", (curTemp + (rand.nextFloat() - 0.48f) * 0.4f).coerceIn(15f, 40f)),
                                    humidity = String.format(Locale.US, "%.2f", (curHum + (rand.nextFloat() - 0.48f) * 1.0f).coerceIn(20f, 90f)),
                                    lux = String.format(Locale.US, "%.0f", (curLux + (rand.nextFloat() - 0.5f) * 120f).coerceAtLeast(0f)),
                                    pressure = String.format(Locale.US, "%.1f", (curPress + (rand.nextFloat() - 0.5f) * 2f).coerceIn(950f, 1050f))
                                )
                            }
                            is SensorData.STS30Data -> {
                                val curC = m.temperatureC.replace(",", ".").toFloatOrNull() ?: 25.0f
                                val nextC = (curC + (rand.nextFloat() - 0.48f) * 0.4f).coerceIn(15f, 40f)
                                m.copy(
                                    temperatureC = String.format(Locale.US, "%.2f", nextC),
                                    temperatureF = String.format(Locale.US, "%.2f", nextC * 1.8f + 32f)
                                )
                            }
                            is SensorData.STTS751Data -> {
                                val curC = m.temperatureC.replace(",", ".").toFloatOrNull() ?: 26.0f
                                val nextC = (curC + (rand.nextFloat() - 0.48f) * 0.4f).coerceIn(15f, 40f)
                                m.copy(
                                    temperatureC = String.format(Locale.US, "%.2f", nextC),
                                    temperatureF = String.format(Locale.US, "%.2f", nextC * 1.8f + 32f)
                                )
                            }
                            is SensorData.RainData -> {
                                val curRain = m.rainfall.replace(",", ".").toFloatOrNull() ?: 12.5f
                                m.copy(rainfall = String.format(Locale.US, "%.2f", (curRain + rand.nextFloat() * 0.5f).coerceIn(0f, 100f)))
                            }
                            is SensorData.WindData -> {
                                val curSpeed = m.windSpeed.replace(",", ".").toFloatOrNull() ?: 4.2f
                                val curDir = m.windDirection.replace(",", ".").toFloatOrNull() ?: 180f
                                val nextSpeed = (curSpeed + (rand.nextFloat() - 0.48f) * 1.2f).coerceIn(0.4f, 16.5f)
                                val nextDir = (curDir + 12f + rand.nextFloat() * 16f) % 360f
                                m.copy(
                                    windSpeed = String.format(Locale.US, "%.1f", nextSpeed),
                                    windDirection = String.format(Locale.US, "%.0f", nextDir)
                                )
                            }
                            is SensorData.AmmoniaSensorData -> {
                                val curVal = m.ammonia.replace(" ppm", "").replace(",", ".").toFloatOrNull() ?: 12.5f
                                m.copy(ammonia = String.format(Locale.US, "%.2f ppm", (curVal + (rand.nextFloat() - 0.5f) * 0.4f).coerceAtLeast(0f)))
                            }
                            is SensorData.Sen66Data -> {
                                val curPm1 = m.pm1.replace(",", ".").toFloatOrNull() ?: 2.1f
                                val curPm25 = m.pm25.replace(",", ".").toFloatOrNull() ?: 5.4f
                                val curPm4 = m.pm4.replace(",", ".").toFloatOrNull() ?: 8.2f
                                val curPm10 = m.pm10.replace(",", ".").toFloatOrNull() ?: 12.5f
                                val curTemp = m.temperature.replace(",", ".").toFloatOrNull() ?: 23.4f
                                val curHum = m.humidity.replace(",", ".").toFloatOrNull() ?: 45.1f
                                val curCo2 = m.co2.replace(",", ".").toFloatOrNull() ?: 410f
                                val curVoc = m.voc.replace(",", ".").toFloatOrNull() ?: 150f
                                val curNox = m.nox.replace(",", ".").toFloatOrNull() ?: 12f

                                m.copy(
                                    pm1 = String.format(Locale.US, "%.1f", (curPm1 + (rand.nextFloat() - 0.48f) * 0.7f).coerceIn(0.5f, 35f)),
                                    pm25 = String.format(Locale.US, "%.1f", (curPm25 + (rand.nextFloat() - 0.48f) * 1.2f).coerceIn(1.0f, 65f)),
                                    pm4 = String.format(Locale.US, "%.1f", (curPm4 + (rand.nextFloat() - 0.48f) * 1.6f).coerceIn(2.0f, 85f)),
                                    pm10 = String.format(Locale.US, "%.1f", (curPm10 + (rand.nextFloat() - 0.48f) * 2.0f).coerceIn(4.0f, 110f)),
                                    temperature = String.format(Locale.US, "%.1f", (curTemp + (rand.nextFloat() - 0.5f) * 0.4f).coerceIn(15f, 38f)),
                                    humidity = String.format(Locale.US, "%.1f", (curHum + (rand.nextFloat() - 0.5f) * 1.0f).coerceIn(20f, 85f)),
                                    co2 = String.format(Locale.US, "%.0f", (curCo2 + (rand.nextFloat() - 0.5f) * 15f).coerceIn(380f, 1600f)),
                                    voc = String.format(Locale.US, "%.0f", (curVoc + (rand.nextFloat() - 0.5f) * 10f).coerceIn(50f, 400f)),
                                    nox = String.format(Locale.US, "%.0f", (curNox + (rand.nextFloat() - 0.5f) * 2f).coerceIn(1f, 60f))
                                )
                            }
                            else -> m
                        }
                        currentMock = updated
                        liveSensorData = updated
                        delay(2000)
                    }
                }
            }
        }

        viewModel.sensorDataStream.collect { data ->
            val isOurDevice = when (data) {
                is SensorData.SHT40Data -> data.deviceAddress.equals(deviceAddress, true) || deviceAddress.startsWith(data.deviceAddress, true) || data.deviceAddress.startsWith(deviceAddress, true)
                is SensorData.LIS3DHData -> data.deviceAddress.equals(deviceAddress, true) || deviceAddress.startsWith(data.deviceAddress, true) || data.deviceAddress.startsWith(deviceAddress, true)
                is SensorData.SoilSensorData -> data.deviceAddress.equals(deviceAddress, true) || deviceAddress.startsWith(data.deviceAddress, true) || data.deviceAddress.startsWith(deviceAddress, true)
                is SensorData.AmmoniaSensorData -> data.deviceAddress.equals(deviceAddress, true) || deviceAddress.startsWith(data.deviceAddress, true) || data.deviceAddress.startsWith(deviceAddress, true)
                is SensorData.VEML7700Data -> data.deviceAddress.equals(deviceAddress, true) || deviceAddress.startsWith(data.deviceAddress, true) || data.deviceAddress.startsWith(deviceAddress, true)
                is SensorData.VCNL4040Data -> data.deviceAddress.equals(deviceAddress, true) || deviceAddress.startsWith(data.deviceAddress, true) || data.deviceAddress.startsWith(deviceAddress, true)
                is SensorData.AHT20Data -> data.deviceAddress.equals(deviceAddress, true) || deviceAddress.startsWith(data.deviceAddress, true) || data.deviceAddress.startsWith(deviceAddress, true)
                is SensorData.STS30Data -> data.deviceAddress.equals(deviceAddress, true) || deviceAddress.startsWith(data.deviceAddress, true) || data.deviceAddress.startsWith(deviceAddress, true)
                is SensorData.STTS751Data -> data.deviceAddress.equals(deviceAddress, true) || deviceAddress.startsWith(data.deviceAddress, true) || data.deviceAddress.startsWith(deviceAddress, true)
                is SensorData.BME680Data -> data.deviceAddress.equals(deviceAddress, true) || deviceAddress.startsWith(data.deviceAddress, true) || data.deviceAddress.startsWith(deviceAddress, true)
                is SensorData.TempLoggerData -> data.deviceAddress.equals(deviceAddress, true) || deviceAddress.startsWith(data.deviceAddress, true) || data.deviceAddress.startsWith(deviceAddress, true)
                is SensorData.Sen66Data -> data.deviceAddress.equals(deviceAddress, true) || deviceAddress.startsWith(data.deviceAddress, true) || data.deviceAddress.startsWith(deviceAddress, true)
                is SensorData.RainData -> data.deviceAddress.equals(deviceAddress, true) || deviceAddress.startsWith(data.deviceAddress, true) || data.deviceAddress.startsWith(deviceAddress, true)
                is SensorData.WindData -> data.deviceAddress.equals(deviceAddress, true) || deviceAddress.startsWith(data.deviceAddress, true) || data.deviceAddress.startsWith(deviceAddress, true)
                is SensorData.WeatherData -> data.deviceAddress.equals(deviceAddress, true) || deviceAddress.startsWith(data.deviceAddress, true) || data.deviceAddress.startsWith(deviceAddress, true)
                else -> false
            }
            if (isOurDevice) {
                liveSensorData = data
            }
        }
    }

    val currentDevice by remember(devices, deviceAddress) {
        derivedStateOf { devices.find { it.address.equals(deviceAddress, ignoreCase = true) } }
    }

    // Automatically sync liveSensorData whenever currentDevice is updated from real BLE scan
    LaunchedEffect(currentDevice?.sensorData) {
        currentDevice?.sensorData?.let { devData ->
            if (!deviceAddress.startsWith("AA:BB:CC:00")) {
                liveSensorData = devData
            }
        }
    }

    // Effectively use live stream data for immediate UI response
    val activeSensorData = liveSensorData ?: currentDevice?.sensorData

    var thresholdValue   by remember { mutableStateOf("") }
    var isAlarmActive    by remember { mutableStateOf(false) }
    var showAlertDialog  by remember { mutableStateOf(false) }
    var parameterType    by remember { mutableStateOf("Temperature") }
    var isThresholdSet   by remember { mutableStateOf(false) }

    val blinkAlpha by animateFloatAsState(
        targetValue = if (isAlarmActive) 0.5f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkAlpha"
    )

    val ammoniaValue by remember(activeSensorData) {
        derivedStateOf {
            (activeSensorData as? SensorData.AmmoniaSensorData)
                ?.ammonia?.replace(" ppm", "")?.toFloatOrNull() ?: 0f
        }
    }

    var displayedAmmoniaValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(ammoniaValue) { displayedAmmoniaValue = ammoniaValue }

    val luxValue by remember(activeSensorData) {
        derivedStateOf {
            when (val sd = activeSensorData) {
                is SensorData.VEML7700Data -> sd.lux.toFloatOrNull() ?: 0f
                is SensorData.VCNL4040Data -> sd.lux.toFloatOrNull() ?: 0f
                is SensorData.WeatherData  -> sd.lux.toFloatOrNull() ?: 0f
                else -> 0f
            }
        }
    }

    // Threshold monitoring
    LaunchedEffect(activeSensorData, thresholdValue, parameterType, isThresholdSet) {
        delay(500L)
        if (isThresholdSet) {
            val threshold = thresholdValue.toFloatOrNull()
            if (threshold != null) {
                when (val sd = activeSensorData) {
                    is SensorData.SHT40Data -> {
                        val v = when (parameterType) {
                            "Temperature" -> sd.temperature.toFloatOrNull()
                            "Humidity"    -> sd.humidity.toFloatOrNull()
                            else -> null
                        }
                        isAlarmActive = v != null && v > threshold
                    }
                    is SensorData.AmmoniaSensorData ->
                        isAlarmActive = parameterType == "Ammonia" && ammoniaValue > threshold
                    else -> isAlarmActive = false
                }
                if (isAlarmActive) {
                    showAlertDialog = true
                    mediaPlayer?.let { p -> if (!p.isPlaying) try { p.start() } catch (_: IllegalStateException) {
                        mediaPlayer = MediaPlayer.create(context, R.raw.nuclear_alarm)?.apply { isLooping = true; start() }
                    }}
                } else {
                    mediaPlayer?.let { p -> try { if (p.isPlaying) { p.stop(); p.prepare() } } catch (_: IllegalStateException) {
                        mediaPlayer = MediaPlayer.create(context, R.raw.nuclear_alarm)?.apply { isLooping = true }
                    }}
                    showAlertDialog = false
                }
            } else {
                isAlarmActive = false; showAlertDialog = false
                mediaPlayer?.let { p -> try { if (p.isPlaying) { p.stop(); p.prepare() } } catch (_: IllegalStateException) {
                    mediaPlayer = MediaPlayer.create(context, R.raw.nuclear_alarm)?.apply { isLooping = true }
                }}
            }
        } else {
            isAlarmActive = false; showAlertDialog = false
            mediaPlayer?.let { p -> try { if (p.isPlaying) { p.stop(); p.prepare() } } catch (_: IllegalStateException) {
                mediaPlayer = MediaPlayer.create(context, R.raw.nuclear_alarm)?.apply { isLooping = true }
            }}
        }
    }

    if (luxValue > 0) {
        // Log to avoid unused warning
        Log.d("Advertising", "Lux value: $luxValue")
    }

    val advertisingText = AdvertisingText()

    val displayData by remember(activeSensorData, advertisingText) {
        derivedStateOf {
            when (val sd = activeSensorData) {
                is SensorData.SHT40Data -> listOf(
                    "Device ID" to sd.deviceId,
                    advertisingText.temperature to "${sd.temperature.ifEmpty { "0" }}°C",
                    advertisingText.humidity    to "${sd.humidity.ifEmpty { "0" }}%"
                )

                is SensorData.LIS3DHData -> listOf(
                    "Device ID" to sd.deviceId,
                    advertisingText.xAxis to "${sd.x.ifEmpty { "0" }} m/s²",
                    advertisingText.yAxis to "${sd.y.ifEmpty { "0" }} m/s²",
                    advertisingText.zAxis to "${sd.z.ifEmpty { "0" }} m/s²"
                )
                is SensorData.SoilSensorData -> listOf(
                    "Device ID" to sd.deviceId,
                    advertisingText.nitrogen            to "${sd.nitrogen.ifEmpty { "0" }} mg/kg",
                    advertisingText.phosphorus          to "${sd.phosphorus.ifEmpty { "0" }} mg/kg",
                    advertisingText.potassium           to "${sd.potassium.ifEmpty { "0" }} mg/kg",
                    advertisingText.moisture            to "${sd.moisture.ifEmpty { "0" }}%",
                    advertisingText.temperature         to "${sd.temperature.ifEmpty { "0" }}°C",
                    advertisingText.electricConductivity to "${sd.ec.ifEmpty { "0" }} µS/cm",
                    advertisingText.pH                  to sd.pH.ifEmpty { "0" },
                    advertisingText.salinity            to "${sd.salinity.ifEmpty { "0" }} mg/L"
                )
                is SensorData.TempLoggerData -> listOf(
                    "Device ID" to sd.deviceId,
                    advertisingText.temperature to "${sd.temperature}°C",
                    advertisingText.humidity    to "${sd.humidity}%",
                    advertisingText.rawData     to sd.rawData
                )
                is SensorData.AmmoniaSensorData -> listOf(
                    advertisingText.ammonia to sd.ammonia
                )
                is SensorData.VEML7700Data -> listOf(
                    advertisingText.lightIntensity to "${sd.lux} LUX"
                )
                is SensorData.VCNL4040Data -> listOf(
                    advertisingText.lightIntensity to "${sd.lux} LUX"
                )
                is SensorData.AHT20Data -> listOf(
                    "Device ID" to sd.deviceId,
                    advertisingText.temperature to "${sd.temperature}°C",
                    advertisingText.humidity    to "${sd.humidity}%"
                )
                is SensorData.BME680Data -> listOf(
                    "Device ID" to sd.deviceId,
                    advertisingText.temperature to "${sd.temperature}°C",
                    advertisingText.humidity    to "${sd.humidity}%",
                    "Pressure"                  to "${sd.pressure} hPa"
                )
                is SensorData.Sen66Data -> listOf(
                    "Device ID" to sd.deviceId,
                    "PM1.0"     to "${sd.pm1.ifEmpty { "0" }} μg/m³",
                    "PM2.5"     to "${sd.pm25.ifEmpty { "0" }} μg/m³",
                    "PM4.0"     to "${sd.pm4.ifEmpty { "0" }} μg/m³",
                    "PM10"      to "${sd.pm10.ifEmpty { "0" }} μg/m³",
                    advertisingText.temperature to "${sd.temperature.ifEmpty { "0" }}°C",
                    advertisingText.humidity    to "${sd.humidity.ifEmpty { "0" }}%",
                    "CO₂"       to "${sd.co2.ifEmpty { "0" }} ppm",
                    "VOC"       to sd.voc.ifEmpty { "0" },
                    "NOx"       to sd.nox.ifEmpty { "0" },
                    "Air Quality" to sd.airQualityIndex.ifEmpty { "0" }
                )
                is SensorData.WeatherData -> listOf(
                    "Device ID" to sd.deviceId,
                    advertisingText.temperature to "${sd.temperature}°C",
                    advertisingText.humidity    to "${sd.humidity}%",
                    "Pressure"                  to "${sd.pressure} hPa",
                    advertisingText.lightIntensity to "${sd.lux} LUX"
                )
                is SensorData.STS30Data -> listOf(
                    "Device ID" to sd.deviceId,
                    "Temp (C)" to "${sd.temperatureC}°C",
                    "Temp (F)" to "${sd.temperatureF}°F"
                )
                is SensorData.STTS751Data -> listOf(
                    "Device ID" to sd.deviceId,
                    "Temp (C)" to "${sd.temperatureC}°C",
                    "Temp (F)" to "${sd.temperatureF}°F"
                )
                is SensorData.RainData -> listOf(
                    "Device ID" to sd.deviceId,
                    "Rainfall"  to "${sd.rainfall} mm"
                )
                is SensorData.WindData -> {
                    val dirFloat = sd.windDirection.replace(",", ".").toFloatOrNull() ?: 0f
                    val cardinal = getCardinalDirection(dirFloat)
                    listOf(
                        "Device ID" to sd.deviceId,
                        "Wind Speed" to "${sd.windSpeed} m/s",
                        "Wind Direction" to "${sd.windDirection}° ($cardinal)"
                    )
                }
                else -> emptyList()
            }
        }
    }

    DisposableEffect(navController) {
        onDispose { viewModel.stopScan(); viewModel.clearDevices() }
    }

    // ── Root layout ────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .systemBarsPadding()
    ) {
        // Alarm blink overlay
        if (isAlarmActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RedAccent.copy(alpha = blinkAlpha * 0.4f))
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top App Bar ────────────────────────────────────────────────
            AdvertisingTopBar(
                deviceName   = deviceName,
                deviceAddress = deviceAddress,
                isAlarmActive = isAlarmActive,
                navController = navController,
                viewModel     = viewModel
            )

            // ── Scrollable body ────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Device info cards
                AdvertisingDeviceInfoSection(
                    deviceName    = deviceName,
                    deviceAddress = deviceAddress,
                    deviceId      = deviceId,
                    advertisingText = advertisingText
                )

                // 1. General sensor data cards (Device ID, etc.) - suppressed for SEN66, Soil, and Weather
                if (activeSensorData !is SensorData.Sen66Data && activeSensorData !is SensorData.SoilSensorData && activeSensorData !is SensorData.WeatherData) {
                    ResponsiveDataCards(
                        data            = displayData,
                        advertisingText = advertisingText,
                        forceLuxAsCard  = activeSensorData is SensorData.WeatherData
                    )
                }

                // ── SPECIALIZED VISUALIZERS ──

                // 2. Rain Gauge Visualizer
                if (activeSensorData is SensorData.RainData) {
                    RainVisualizer(
                        rainfall = activeSensorData.rainfall.toFloatOrNull() ?: 0f
                    )
                }

                // 3. Wind Station Visualizer
                if (activeSensorData is SensorData.WindData) {
                    val rawSpeed = activeSensorData.windSpeed.replace(",", ".").toFloatOrNull() ?: 0f
                    val rawDir = activeSensorData.windDirection.replace(",", ".").toFloatOrNull() ?: 0f
                    WindVisualizer(
                        windSpeed = rawSpeed,
                        windDirection = rawDir
                    )
                }

                // 4. SEN66 360° Environmental Intelligence Orbit
                if (activeSensorData is SensorData.Sen66Data) {
                    Sen66SensorDisplay(
                        sensorData = activeSensorData as SensorData.Sen66Data
                    )
                }

                // 5. SOIL 360° Agronomic Intelligence Orbit
                if (activeSensorData is SensorData.SoilSensorData) {
                    SoilSensorDisplay(
                        sensorData = activeSensorData as SensorData.SoilSensorData
                    )
                }

                // 6. Weather Station Creative Modern Deck
                if (activeSensorData is SensorData.WeatherData) {
                    WeatherStationDisplay(
                        sensorData = activeSensorData as SensorData.WeatherData
                    )
                }

                // 7. TempLogger specific
                if (activeSensorData is SensorData.TempLoggerData) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(600.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = CardDark,
                        tonalElevation = 0.dp
                    ) {
                        TempLoggerDisplay(
                            viewModel     = viewModel,
                            deviceAddress = deviceAddress,
                            deviceId      = deviceId,
                            deviceName    = deviceName
                        )
                    }
                }

                // 7. Threshold section
                if (activeSensorData is SensorData.SHT40Data) {
                    ThresholdInputSection(
                        thresholdValue    = thresholdValue,
                        onThresholdChange = { thresholdValue = it },
                        parameterType     = parameterType,
                        onParameterChange = { parameterType = it },
                        sensorData        = activeSensorData,
                        onConfirmThreshold = {
                            if (thresholdValue.toFloatOrNull() != null) isThresholdSet = true
                        }
                    )
                }

                // 8. Download button
                AdvertisingDownloadButton(
                    viewModel     = viewModel,
                    deviceAddress = deviceAddress,
                    deviceName    = deviceName,
                    deviceId      = deviceId,
                    advertisingText = advertisingText,
                    currentSensorData = activeSensorData
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Alarm dialog
        if (showAlertDialog) {
            AlertDialog(
                onDismissRequest = { dismissAlarm(showAlertDialog = { showAlertDialog = false }, isAlarmActive = { isAlarmActive = false }, isThresholdSet = { isThresholdSet = false }, mediaPlayer = mediaPlayer) },
                containerColor   = CardDark,
                title = { Text(advertisingText.warningTitle, color = TextPrimary, fontWeight = FontWeight.Bold) },
                text  = { Text(advertisingText.warningMessage.format(parameterType, thresholdValue), color = TextSecondary) },
                confirmButton = {
                    Button(
                        onClick = { dismissAlarm(showAlertDialog = { showAlertDialog = false }, isAlarmActive = { isAlarmActive = false }, isThresholdSet = { isThresholdSet = false }, mediaPlayer = mediaPlayer) },
                        colors  = ButtonDefaults.buttonColors(containerColor = GreenAccent)
                    ) {
                        Text(advertisingText.dismissButton, color = GreenDark, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Top App Bar
// ══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvertisingTopBar(
    deviceName: String,
    deviceAddress: String,
    isAlarmActive: Boolean,
    navController: NavController,
    viewModel: BluetoothScanViewModel
) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val BgDark = if (isDarkMode) BleSenseColors.BackgroundDark else BleSenseColors.LightBackground
    val SurfaceDark = if (isDarkMode) BleSenseColors.SurfaceDark else BleSenseColors.LightSurface
    val TextPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val TextSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    Surface(
        color     = SurfaceDark,
        tonalElevation = 0.dp,
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(onClick = { viewModel.stopScan(); navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Device icon + title
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(GreenMuted, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Sensors, null, tint = GreenAccent, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deviceName.ifEmpty { "Unknown Device" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = deviceAddress,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Alarm indicator
            if (isAlarmActive) {
                Box(
                    modifier = Modifier
                        .background(RedAccent.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = RedAccent, modifier = Modifier.size(12.dp))
                        Text("ALARM", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RedAccent)
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Graph button
            IconButton(onClick = { navController.navigate("chart_screen/${Uri.encode(deviceAddress)}") }) {
                Icon(Icons.Default.BarChart, contentDescription = "Graph", tint = GreenAccent)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Device info section
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun AdvertisingDeviceInfoSection(
    deviceName: String,
    deviceAddress: String,
    deviceId: String,
    advertisingText: AdvertisingText
) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val CardDark = if (isDarkMode) BleSenseColors.CardDark else BleSenseColors.LightSurface
    val DividerDark = if (isDarkMode) BleSenseColors.BorderDark else BleSenseColors.LightBorder
    val TextPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val TextSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        color    = CardDark,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(BlueAccent.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Bluetooth, null, tint = BlueAccent, modifier = Modifier.size(18.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(advertisingText.deviceNameLabel, fontSize = 10.sp, color = TextSecondary)
                    Text(
                        deviceName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            HorizontalDivider(color = DividerDark, thickness = 0.5.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(GreenMuted, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Tag, null, tint = GreenAccent, modifier = Modifier.size(18.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(advertisingText.nodeIdLabel, fontSize = 10.sp, color = TextSecondary)
                    Text(deviceAddress, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Threshold Input Section
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ThresholdInputSection(
    thresholdValue: String,
    onThresholdChange: (String) -> Unit,
    parameterType: String,
    onParameterChange: (String) -> Unit,
    sensorData: SensorData?,
    onConfirmThreshold: () -> Unit
) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val CardDark = if (isDarkMode) BleSenseColors.CardDark else BleSenseColors.LightSurface
    val DividerDark = if (isDarkMode) BleSenseColors.BorderDark else BleSenseColors.LightBorder
    val TextPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val TextSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        color    = CardDark,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(RedAccent.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.NotificationsActive, null, tint = RedAccent, modifier = Modifier.size(16.dp))
                }
                Text("Threshold Alarm", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }

            // Parameter selector chips
            val parameters = when (sensorData) {
                is SensorData.SHT40Data         -> listOf("Temperature", "Humidity")
                is SensorData.AmmoniaSensorData -> listOf("Ammonia")
                else -> emptyList()
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                parameters.forEach { type ->
                    val selected = parameterType == type
                    Box(
                        modifier = Modifier
                            .background(
                                if (selected) GreenAccent else DividerDark,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { onParameterChange(type) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            type,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) GreenDark else TextSecondary
                        )
                    }
                }
            }

            // Threshold input
            OutlinedTextField(
                value         = thresholdValue,
                onValueChange = onThresholdChange,
                label         = { Text("Enter $parameterType Threshold", color = TextSecondary, fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier      = Modifier.fillMaxWidth(),
                isError       = thresholdValue.isNotEmpty() && thresholdValue.toFloatOrNull() == null,
                singleLine    = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = GreenAccent,
                    unfocusedBorderColor = DividerDark,
                    focusedTextColor     = TextPrimary,
                    unfocusedTextColor   = TextPrimary,
                    errorBorderColor     = RedAccent,
                    cursorColor          = GreenAccent
                ),
                supportingText = {
                    if (thresholdValue.isNotEmpty() && thresholdValue.toFloatOrNull() == null) {
                        Text("Please enter a valid number", color = RedAccent, fontSize = 11.sp)
                    }
                }
            )

            // Confirm button
            Button(
                onClick  = onConfirmThreshold,
                enabled  = thresholdValue.isNotEmpty() && thresholdValue.toFloatOrNull() != null,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = GreenAccent,
                    disabledContainerColor = DividerDark
                )
            ) {
                Text("Set Alarm Threshold", fontWeight = FontWeight.Bold, color = GreenDark)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Download Button
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun AdvertisingDownloadButton(
    viewModel: BluetoothScanViewModel,
    deviceAddress: String,
    deviceName: String,
    deviceId: String,
    advertisingText: AdvertisingText,
    currentSensorData: SensorData?
) {
    val context     = LocalContext.current
    var isExporting by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            isExporting = true
            exportDataToCSV(context, uri, viewModel, deviceAddress, deviceName, deviceId, currentSensorData) {
                isExporting = false
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = {
                val ts  = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val isPreview = deviceAddress.startsWith("AA:BB:CC:00")
                val fileName = if (isPreview) {
                    "PREVIEW_${deviceName.replace(" ", "_")}_$ts.csv"
                } else {
                    "sensor_data_${deviceId}_$ts.csv"
                }
                createDocumentLauncher.launch(fileName)
            },
            modifier = Modifier.weight(1f).height(48.dp),
            shape    = RoundedCornerShape(12.dp),
            enabled  = !isExporting,
            colors   = ButtonDefaults.buttonColors(containerColor = GreenAccent)
        ) {
            Icon(Icons.Default.Download, null, tint = GreenDark, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                if (isExporting) advertisingText.exportingData else advertisingText.downloadData,
                color      = GreenDark,
                fontWeight = FontWeight.Bold,
                fontSize   = 12.sp
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Responsive Data Cards
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ResponsiveDataCards(
    data: List<Pair<String, String>>,
    advertisingText: AdvertisingText,
    forceLuxAsCard: Boolean = false
) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val CardDark = if (isDarkMode) BleSenseColors.CardDark else BleSenseColors.LightSurface
    val CardDark2 = if (isDarkMode) BleSenseColors.CardDarkElevated else BleSenseColors.LightBackground
    val TextPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val TextSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    val ammoniaData = data.find { it.first.contains("Ammonia", ignoreCase = true) }
    val luxData     = if (forceLuxAsCard) null else data.find { it.first.contains(advertisingText.lightIntensity, ignoreCase = true) }
    val rawDataItem = data.find { it.first.contains("Raw Data", ignoreCase = true) }
    val otherData   = data.filterNot {
        it.first.contains("Ammonia", ignoreCase = true) ||
                (!forceLuxAsCard && it.first.contains(advertisingText.lightIntensity, ignoreCase = true)) ||
                it.first.contains("Raw Data", ignoreCase = true) ||
                listOf("PM1.0","PM2.5","PM4.0","PM10","CO₂","VOC","NOx","Air Quality")
                    .any { kw -> it.first.contains(kw, ignoreCase = true) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Ammonia ring
        ammoniaData?.let { (label, value) ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                color    = CardDark,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    AmmoniaRingAnimation(ammoniaValue = value.replace(" ppm", "").toFloatOrNull() ?: 0f)
                }
            }
        }

        // Lux ring
        luxData?.let { (label, value) ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                color    = CardDark,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    LuxRingAnimation(luxValue = value.replace(" LUX", "").replace(",", "").toFloatOrNull() ?: 0f)
                }
            }
        }

        // Raw data
        rawDataItem?.let { (_, value) ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                color    = CardDark,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Code, null, tint = PurpleAccent, modifier = Modifier.size(16.dp))
                        Text("Raw Sensor Data", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CardDark2
                    ) {
                        Text(
                            text     = value,
                            fontSize = 11.sp,
                            color    = PurpleAccent,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Sensor value cards
        if (otherData.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                color    = CardDark,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (otherData.size) {
                        1 -> DataCard(
                            label = otherData[0].first,
                            value = otherData[0].second,
                            advertisingText = advertisingText
                        )
                        else -> {
                            otherData.chunked(2).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    row.forEach { (label, value) ->
                                        DataCard(
                                            label  = label,
                                            value  = value,
                                            advertisingText = advertisingText,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Individual Data Card
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun DataCard(
    label: String,
    value: String,
    advertisingText: AdvertisingText,
    modifier: Modifier = Modifier
) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val TextSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    val numericValue = value.replace("[^0-9.]".toRegex(), "").toFloatOrNull() ?: 0f

    val accentColor = when {
        label == advertisingText.temperature -> when {
            numericValue <= 15f -> BlueAccent
            numericValue <= 30f -> GreenAccent
            else               -> RedAccent
        }
        label == advertisingText.humidity -> when {
            numericValue <= 40f -> BlueAccent
            numericValue <= 70f -> GreenAccent
            else               -> RedAccent
        }
        label.contains("pH", ignoreCase = true)     -> TealAccent
        label.contains("lux", ignoreCase = true)    -> YellowAccent
        label.contains("speed", ignoreCase = true)  -> PurpleAccent
        label.contains("direction", ignoreCase = true) -> TealAccent
        label.contains("nitrogen", ignoreCase = true) ||
                label.contains("phosphorus", ignoreCase = true) ||
                label.contains("potassium", ignoreCase = true) -> OrangeAccent
        else -> GreenAccent
    }

    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(12.dp),
        color    = accentColor.copy(alpha = 0.10f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text      = label,
                fontSize  = 10.sp,
                color     = TextSecondary,
                fontWeight = FontWeight.Medium,
                textAlign  = TextAlign.Center
            )
            Text(
                text      = value,
                fontSize  = 18.sp,
                color     = accentColor,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                maxLines   = 2,
                lineHeight = 22.sp
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Ammonia Ring Animation (unchanged logic, colours updated)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun AmmoniaRingAnimation(ammoniaValue: Float, modifier: Modifier = Modifier) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val DividerDark = if (isDarkMode) BleSenseColors.BorderDark else BleSenseColors.LightBorder
    val TextPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val TextSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    val animatedFill by animateFloatAsState(
        targetValue = (ammoniaValue / 100f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 100f),
        label = "ammoniaFill"
    )
    val liquidColor by animateColorAsState(
        targetValue = when {
            ammoniaValue <= 25 -> GreenAccent
            ammoniaValue <= 50 -> YellowAccent
            else               -> RedAccent
        },
        animationSpec = tween(300),
        label = "liquidColor"
    )
    Box(
        modifier = modifier.size(220.dp).padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center    = Offset(size.width / 2, size.height / 2)
            val radius    = size.minDimension * 0.4f
            val ringWidth = size.minDimension * 0.1f
            drawArc(color = DividerDark, startAngle = 270f, sweepAngle = 360f, useCenter = false,
                size = Size(radius*2, radius*2), topLeft = Offset(center.x-radius, center.y-radius),
                style = Stroke(width = ringWidth))
            drawArc(color = liquidColor.copy(alpha = 0.8f), startAngle = 270f, sweepAngle = -360f * animatedFill,
                useCenter = false, size = Size(radius*2, radius*2), topLeft = Offset(center.x-radius, center.y-radius),
                style = Stroke(width = ringWidth, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%.1f".format(ammoniaValue), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("ppm", fontSize = 14.sp, color = TextSecondary)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Lux Ring Animation (colours updated)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun LuxRingAnimation(luxValue: Float, modifier: Modifier = Modifier) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val DividerDark = if (isDarkMode) BleSenseColors.BorderDark else BleSenseColors.LightBorder
    val TextPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val TextSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    val animatedFill by animateFloatAsState(
        targetValue = (luxValue / 20000f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 100f),
        label = "luxFill"
    )
    val lightColor by animateColorAsState(
        targetValue = when {
            luxValue > 10000 -> RedAccent
            luxValue > 5000  -> YellowAccent
            else             -> GreenAccent
        },
        animationSpec = tween(300),
        label = "lightColor"
    )
    Box(modifier = modifier.size(220.dp).padding(16.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width/2, size.height/2)
            val radius = size.minDimension * 0.4f
            val ring   = size.minDimension * 0.1f
            drawArc(color = DividerDark, startAngle = 270f, sweepAngle = 360f, useCenter = false,
                size = Size(radius*2, radius*2), topLeft = Offset(center.x-radius, center.y-radius),
                style = Stroke(width = ring))
            drawArc(color = lightColor.copy(alpha = 0.8f), startAngle = 270f, sweepAngle = -360f * animatedFill,
                useCenter = false, size = Size(radius*2, radius*2), topLeft = Offset(center.x-radius, center.y-radius),
                style = Stroke(width = ring, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%.0f".format(luxValue), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("LUX", fontSize = 14.sp, color = TextSecondary)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SEN66 360° ENVIRONMENTAL INTELLIGENCE ORBIT DISPLAY
// ══════════════════════════════════════════════════════════════════════════════

private data class Sen66OrbitNode(
    val id: String,
    val label: String,
    val value: String,
    val unit: String,
    val angleDeg: Float,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun Sen66SensorDisplay(sensorData: SensorData.Sen66Data, modifier: Modifier = Modifier) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val CardDark = if (isDarkMode) BleSenseColors.CardDark else BleSenseColors.LightSurface
    val CardDark2 = if (isDarkMode) BleSenseColors.CardDarkElevated else BleSenseColors.LightBackground
    val TextPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val TextSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    // Composite Air Quality Score (0 - 100) and evaluation status
    val (airIndexScore, statusText, statusColor) = remember(
        sensorData.pm25, sensorData.co2, sensorData.voc, sensorData.nox
    ) {
        val pm25 = sensorData.pm25.replace(",", ".").toFloatOrNull() ?: 0f
        val co2  = sensorData.co2.replace(",", ".").toFloatOrNull()  ?: 0f
        val voc  = sensorData.voc.replace(",", ".").toFloatOrNull()  ?: 0f
        val nox  = sensorData.nox.replace(",", ".").toFloatOrNull()  ?: 0f

        var score = 100f
        // PM2.5 penalty: 0-5μg/m³ pristine; 5-12μg/m³ mild; >12 moderate/unhealthy
        score -= when {
            pm25 <= 5f  -> (pm25 / 5f) * 1.5f
            pm25 <= 12f -> 1.5f + (pm25 - 5f) * 0.5f
            pm25 <= 35f -> 5f + (pm25 - 12f) * 1.2f
            pm25 <= 55f -> 32.6f + (pm25 - 35f) * 1.5f
            else        -> 62.6f + (pm25 - 55f) * 0.5f
        }
        // CO2 penalty: 400-500 fresh; 500-800 normal indoor; >1000 stale
        score -= when {
            co2 <= 420f  -> 0f
            co2 <= 800f  -> ((co2 - 420f) / 380f) * 5.5f
            co2 <= 1200f -> 5.5f + ((co2 - 800f) / 400f) * 12f
            co2 <= 2000f -> 17.5f + ((co2 - 1200f) / 800f) * 20f
            else         -> 37.5f + ((co2 - 2000f) / 1000f) * 25f
        }
        // VOC Index penalty: 0-100 normal; >100 elevated
        score -= when {
            voc <= 100f -> (voc / 100f) * 2f
            voc <= 200f -> 2f + ((voc - 100f) / 100f) * 10f
            else        -> 12f + ((voc - 200f) / 300f) * 25f
        }
        // NOx Index penalty: 0-1 normal; >1 elevated
        score -= when {
            nox <= 1f   -> 0f
            nox <= 50f  -> (nox / 50f) * 8f
            else        -> 8f + ((nox - 50f) / 450f) * 30f
        }

        val finalScore = score.coerceIn(0f, 100f).roundToInt()
        val (status, color) = when {
            finalScore >= 90 -> "EXCELLENT" to Color(0xFF00BC7D)
            finalScore >= 75 -> "GOOD" to Color(0xFF2DD4BF)
            finalScore >= 50 -> "MODERATE" to Color(0xFFFBBF24)
            finalScore >= 30 -> "POOR" to Color(0xFFFB923C)
            else             -> "VERY POOR" to Color(0xFFFF6467)
        }
        Triple(finalScore, status, color)
    }

    val animatedAirScore by animateIntAsState(
        targetValue = airIndexScore,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "animatedAirScore"
    )

    // 9 floating nodes ordered clockwise starting at 12 o'clock (-90°)
    // Color variants adapt dynamically: bright neons for dark mode, rich saturated jewel tones for light mode
    val nodes = remember(sensorData, isDarkMode) {
        listOf(
            Sen66OrbitNode(
                id = "temp",
                label = "TEMP",
                value = sensorData.temperature.ifBlank { "--" },
                unit = "°C",
                angleDeg = -90f, // 12 o'clock
                color = if (isDarkMode) Color(0xFFFF5252) else Color(0xFFE11D48),
                icon = Icons.Default.Thermostat
            ),
            Sen66OrbitNode(
                id = "humidity",
                label = "HUMIDITY",
                value = sensorData.humidity.ifBlank { "--" },
                unit = "%",
                angleDeg = -50f, // ~1:20
                color = if (isDarkMode) Color(0xFF38BDF8) else Color(0xFF0284C7),
                icon = Icons.Default.WaterDrop
            ),
            Sen66OrbitNode(
                id = "co2",
                label = "CO2 GAS",
                value = sensorData.co2.ifBlank { "--" },
                unit = "ppm",
                angleDeg = -10f, // ~2:40
                color = if (isDarkMode) Color(0xFF10B981) else Color(0xFF059669),
                icon = Icons.Default.Cloud
            ),
            Sen66OrbitNode(
                id = "voc",
                label = "VOC",
                value = sensorData.voc.ifBlank { "--" },
                unit = "Idx",
                angleDeg = 30f, // ~4:00
                color = if (isDarkMode) Color(0xFF2DD4BF) else Color(0xFF0D9488),
                icon = Icons.Default.BubbleChart
            ),
            Sen66OrbitNode(
                id = "nox",
                label = "NOX",
                value = sensorData.nox.ifBlank { "--" },
                unit = "Idx",
                angleDeg = 70f, // ~5:20
                color = if (isDarkMode) Color(0xFFEF4444) else Color(0xFFDC2626),
                icon = Icons.Default.DirectionsCar
            ),
            Sen66OrbitNode(
                id = "pm1",
                label = "PM 1.0",
                value = sensorData.pm1.ifBlank { "--" },
                unit = "µg",
                angleDeg = 110f, // ~6:40
                color = if (isDarkMode) Color(0xFF14B8A6) else Color(0xFF0F766E),
                icon = Icons.Default.Grain
            ),
            Sen66OrbitNode(
                id = "pm25",
                label = "PM 2.5",
                value = sensorData.pm25.ifBlank { "--" },
                unit = "µg",
                angleDeg = 150f, // ~8:00
                color = if (isDarkMode) Color(0xFF4ADE80) else Color(0xFF16A34A),
                icon = Icons.Default.Grain
            ),
            Sen66OrbitNode(
                id = "pm4",
                label = "PM 4.0",
                value = sensorData.pm4.ifBlank { "--" },
                unit = "µg",
                angleDeg = 190f, // ~9:20
                color = if (isDarkMode) Color(0xFFFBBF24) else Color(0xFFD97706),
                icon = Icons.Default.Air
            ),
            Sen66OrbitNode(
                id = "pm10",
                label = "PM 10",
                value = sensorData.pm10.ifBlank { "--" },
                unit = "µg",
                angleDeg = 230f, // ~10:40
                color = if (isDarkMode) Color(0xFFFB923C) else Color(0xFFEA580C),
                icon = Icons.Default.Layers
            )
        )
    }

    // Outer Orbit Card Container - tailored for Dark and Light modes
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (isDarkMode) Color(0xFF0F141A) else Color.White,
        border = BorderStroke(
            1.dp,
            if (isDarkMode) {
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF2DD4BF).copy(alpha = 0.35f),
                        Color(0xFF1E293B).copy(alpha = 0.5f),
                        Color(0xFF00BC7D).copy(alpha = 0.25f)
                    )
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF00BC7D).copy(alpha = 0.35f),
                        Color(0xFFE2E8F0),
                        Color(0xFF2DD4BF).copy(alpha = 0.25f)
                    )
                )
            }
        ),
        shadowElevation = if (isDarkMode) 4.dp else 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 18.dp, start = 10.dp, end = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Top Header Section ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (isDarkMode) Color(0xFFF59E0B) else Color(0xFFD97706),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "SEN66 360° ENVIRONMENTAL INTELLIGENCE ORBIT",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                        color = if (isDarkMode) Color.White else Color(0xFF0F172A),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Real-Time Air Quality Index Core with 9 Floating Physical Parameter Symbols",
                    fontSize = 10.sp,
                    color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // ── 360° Circular Orbit Visualizer ──
            Sen66OrbitVisualizer(
                airIndexScore = animatedAirScore,
                statusText = statusText,
                statusColor = statusColor,
                nodes = nodes,
                isDarkMode = isDarkMode
            )
        }
    }
}

@Composable
private fun Sen66OrbitVisualizer(
    airIndexScore: Int,
    statusText: String,
    statusColor: Color,
    nodes: List<Sen66OrbitNode>,
    isDarkMode: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbitPulse")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    val dashPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dashPhase"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(390.dp),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val maxAvailable = minOf(maxWidth, maxHeight)

        // Compute orbit radius dynamically so nodes stay within view bounds
        val orbitRadiusDp = (maxAvailable * 0.36f).coerceIn(115.dp, 138.dp)
        val orbitRadiusPx = with(density) { orbitRadiusDp.toPx() }

        // 1. Orbit Canvas: dashed circular track & atmospheric glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)

            // Faint atmospheric glow field
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isDarkMode) listOf(
                        Color(0xFF2DD4BF).copy(alpha = 0.08f * glowPulse),
                        Color(0xFF00BC7D).copy(alpha = 0.03f),
                        Color.Transparent
                    ) else listOf(
                        Color(0xFF00BC7D).copy(alpha = 0.06f * glowPulse),
                        Color(0xFF2DD4BF).copy(alpha = 0.02f),
                        Color.Transparent
                    ),
                    radius = orbitRadiusPx * 1.25f,
                    center = center
                )
            )

            // Dashed orbital track ring
            val strokeWidth = 1.6.dp.toPx()
            val dashEffect = PathEffect.dashPathEffect(
                floatArrayOf(6.dp.toPx(), 6.dp.toPx()),
                phase = dashPhase
            )
            drawCircle(
                color = if (isDarkMode) Color(0xFF2DD4BF).copy(alpha = 0.50f) else Color(0xFF0D9488).copy(alpha = 0.65f),
                radius = orbitRadiusPx,
                center = center,
                style = Stroke(width = strokeWidth, pathEffect = dashEffect)
            )
        }

        // 2. Central Air Quality Index Core
        Box(
            modifier = Modifier
                .size(138.dp)
                .align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            // Outer glowing ring
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 2.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = if (isDarkMode) listOf(
                            Color(0xFF00E5A3).copy(alpha = 0.30f * glowPulse),
                            Color(0xFF00E5A3).copy(alpha = 0.06f),
                            Color.Transparent
                        ) else listOf(
                            Color(0xFF059669).copy(alpha = 0.22f * glowPulse),
                            Color(0xFF00BC7D).copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        radius = size.minDimension / 2f
                    )
                )
                drawCircle(
                    color = if (isDarkMode) Color(0xFF00E5A3) else Color(0xFF059669),
                    radius = (size.minDimension / 2f) - strokeW,
                    style = Stroke(width = strokeW)
                )
            }

            // Inner circular disc
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = if (isDarkMode) listOf(
                                Color(0xFF0E231C),
                                Color(0xFF091712),
                                Color(0xFF040A08)
                            ) else listOf(
                                Color(0xFFF0FDF4),
                                Color(0xFFE6F4EA),
                                Color(0xFFDCFCE7)
                            )
                        ),
                        shape = CircleShape
                    )
                    .border(
                        width = if (isDarkMode) 0.dp else 1.dp,
                        color = if (isDarkMode) Color.Transparent else Color(0xFF059669).copy(alpha = 0.25f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = "AIR INDEX",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = if (isDarkMode) Color(0xFF2DD4BF) else Color(0xFF047857)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = airIndexScore.toString(),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDarkMode) Color.White else Color(0xFF0F172A),
                        lineHeight = 38.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Evaluation pill badge
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = statusColor,
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = statusText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 0.4.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. 9 Floating Physical Parameter Nodes positioned around the 360° circle
        nodes.forEach { node ->
            val rad = Math.toRadians(node.angleDeg.toDouble())
            val offsetX = with(density) { (orbitRadiusPx * cos(rad)).toFloat().toDp() }
            val offsetY = with(density) { (orbitRadiusPx * sin(rad)).toFloat().toDp() }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = offsetX, y = offsetY),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.wrapContentSize()
                ) {
                    // Icon Bubble
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                color = if (isDarkMode) Color(0xFF0F172A).copy(alpha = 0.94f) else Color.White,
                                shape = CircleShape
                            )
                            .border(
                                width = if (isDarkMode) 1.5.dp else 1.8.dp,
                                color = node.color,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(node.color.copy(alpha = if (isDarkMode) 0.15f else 0.10f), CircleShape)
                        )
                        Icon(
                            imageVector = node.icon,
                            contentDescription = node.label,
                            tint = node.color,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Parameter Name Tag Pill
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isDarkMode) Color(0xFF1E293B).copy(alpha = 0.95f) else Color(0xFFF1F5F9),
                        border = BorderStroke(0.6.dp, node.color.copy(alpha = if (isDarkMode) 0.45f else 0.60f)),
                        shadowElevation = if (isDarkMode) 0.dp else 1.dp
                    ) {
                        Text(
                            text = node.label,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color.White else Color(0xFF0F172A),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(1.dp))

                    // Value & Unit text
                    Text(
                        text = "${node.value} ${node.unit}".trim(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) Color.White else Color(0xFF0F172A),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Preview(name = "SEN66 360 Orbit - Dark Mode", showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
private fun Sen66SensorDisplayDarkPreview() {
    MaterialTheme {
        Sen66SensorDisplay(
            sensorData = SensorData.Sen66Data(
                deviceId = "5",
                pm1 = "4.8",
                pm25 = "5.2",
                pm4 = "5.4",
                pm10 = "5.5",
                temperature = "22.5",
                humidity = "67.3",
                co2 = "582",
                voc = "0",
                nox = "0"
            )
        )
    }
}

@Preview(name = "SEN66 360 Orbit - Light Mode", showBackground = true, backgroundColor = 0xFFF8FAFC)
@Composable
private fun Sen66SensorDisplayLightPreview() {
    MaterialTheme {
        Sen66SensorDisplay(
            sensorData = SensorData.Sen66Data(
                deviceId = "5",
                pm1 = "4.8",
                pm25 = "5.2",
                pm4 = "5.4",
                pm10 = "5.5",
                temperature = "22.5",
                humidity = "67.3",
                co2 = "582",
                voc = "0",
                nox = "0"
            )
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SOIL 360° AGRONOMIC INTELLIGENCE ORBIT DISPLAY
// ══════════════════════════════════════════════════════════════════════════════

private data class SoilOrbitNode(
    val id: String,
    val label: String,
    val value: String,
    val unit: String,
    val angleDeg: Float,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun SoilSensorDisplay(sensorData: SensorData.SoilSensorData, modifier: Modifier = Modifier) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val CardDark = if (isDarkMode) BleSenseColors.CardDark else BleSenseColors.LightSurface

    // Dynamic Soil Health Index (0 - 100) and evaluation status
    val (soilIndexScore, statusText, statusColor) = remember(
        sensorData.moisture, sensorData.pH, sensorData.temperature,
        sensorData.ec, sensorData.salinity, sensorData.nitrogen,
        sensorData.phosphorus, sensorData.potassium
    ) {
        val moisture = sensorData.moisture.replace(",", ".").toFloatOrNull() ?: 0f
        val temp     = sensorData.temperature.replace(",", ".").toFloatOrNull() ?: 0f
        val ph       = sensorData.pH.replace(",", ".").toFloatOrNull() ?: 7.0f
        val ec       = sensorData.ec.replace(",", ".").toFloatOrNull() ?: 0f
        val salinity = sensorData.salinity.replace(",", ".").toFloatOrNull() ?: 0f
        val n        = sensorData.nitrogen.replace(",", ".").toFloatOrNull() ?: 0f
        val p        = sensorData.phosphorus.replace(",", ".").toFloatOrNull() ?: 0f
        val k        = sensorData.potassium.replace(",", ".").toFloatOrNull() ?: 0f

        var score = 100f

        // Moisture penalty (ideal 25 - 60%)
        score -= when {
            moisture == 0f -> 10f
            moisture in 25f..60f -> 0f
            moisture < 25f -> ((25f - moisture) / 25f) * 15f
            else -> ((moisture - 60f) / 40f) * 15f
        }.coerceAtMost(15f)

        // pH penalty (ideal 6.0 - 7.5)
        score -= when {
            ph in 6.0f..7.5f -> 0f
            ph < 6.0f -> ((6.0f - ph) / 3f) * 20f
            else -> ((ph - 7.5f) / 3f) * 20f
        }.coerceAtMost(20f)

        // Temperature penalty (ideal 18 - 28°C)
        score -= when {
            temp == 0f -> 5f
            temp in 18f..28f -> 0f
            temp < 18f -> ((18f - temp) / 18f) * 10f
            else -> ((temp - 28f) / 20f) * 10f
        }.coerceAtMost(10f)

        // EC penalty (ideal 150 - 1200 µS/cm)
        score -= when {
            ec == 0f -> 8f
            ec in 150f..1200f -> 0f
            ec < 150f -> 4f
            else -> ((ec - 1200f) / 1000f) * 14f
        }.coerceAtMost(14f)

        // Salinity penalty (ideal < 250 mg/L)
        score -= when {
            salinity <= 250f -> 0f
            else -> ((salinity - 250f) / 500f) * 12f
        }.coerceAtMost(12f)

        // Nutrients (N, P, K) fertility check
        if (n in 0.1f..19.9f) score -= 5f
        if (p in 0.1f..14.9f) score -= 5f
        if (k in 0.1f..39.9f) score -= 5f

        val finalScore = score.coerceIn(0f, 100f).roundToInt()
        val (status, color) = when {
            finalScore >= 85 -> "OPTIMAL" to Color(0xFF00BC7D)
            finalScore >= 70 -> "FERTILE" to Color(0xFF2DD4BF)
            finalScore >= 50 -> "MODERATE" to Color(0xFFFBBF24)
            finalScore >= 30 -> "DEPLETED" to Color(0xFFFB923C)
            else             -> "CRITICAL" to Color(0xFFFF6467)
        }
        Triple(finalScore, status, color)
    }

    val animatedSoilScore by animateIntAsState(
        targetValue = soilIndexScore,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "animatedSoilScore"
    )

    // 8 floating physical nodes spaced at 45° intervals around 360° circle
    val nodes = remember(sensorData, isDarkMode) {
        listOf(
            SoilOrbitNode(
                id = "moisture",
                label = "MOISTURE",
                value = sensorData.moisture.ifBlank { "--" },
                unit = "%",
                angleDeg = -90f, // 12 o'clock
                color = if (isDarkMode) Color(0xFF38BDF8) else Color(0xFF0284C7),
                icon = Icons.Default.WaterDrop
            ),
            SoilOrbitNode(
                id = "temp",
                label = "TEMP",
                value = sensorData.temperature.ifBlank { "--" },
                unit = "°C",
                angleDeg = -45f, // 1:30
                color = if (isDarkMode) Color(0xFFFF5252) else Color(0xFFE11D48),
                icon = Icons.Default.Thermostat
            ),
            SoilOrbitNode(
                id = "ec",
                label = "EC COND",
                value = sensorData.ec.ifBlank { "--" },
                unit = "µS/cm",
                angleDeg = 0f, // 3 o'clock
                color = if (isDarkMode) Color(0xFFFBBF24) else Color(0xFFD97706),
                icon = Icons.Default.Bolt
            ),
            SoilOrbitNode(
                id = "salinity",
                label = "SALINITY",
                value = sensorData.salinity.ifBlank { "--" },
                unit = "mg/L",
                angleDeg = 45f, // 4:30
                color = if (isDarkMode) Color(0xFFA78BFA) else Color(0xFF7C3AED),
                icon = Icons.Default.Grain
            ),
            SoilOrbitNode(
                id = "ph",
                label = "SOIL pH",
                value = sensorData.pH.ifBlank { "--" },
                unit = "pH",
                angleDeg = 90f, // 6 o'clock
                color = if (isDarkMode) Color(0xFF2DD4BF) else Color(0xFF0D9488),
                icon = Icons.Default.Science
            ),
            SoilOrbitNode(
                id = "nitrogen",
                label = "NITROGEN",
                value = sensorData.nitrogen.ifBlank { "--" },
                unit = "mg/kg",
                angleDeg = 135f, // 7:30
                color = if (isDarkMode) Color(0xFF4ADE80) else Color(0xFF16A34A),
                icon = Icons.Default.Eco
            ),
            SoilOrbitNode(
                id = "phosphorus",
                label = "PHOSPHORUS",
                value = sensorData.phosphorus.ifBlank { "--" },
                unit = "mg/kg",
                angleDeg = 180f, // 9 o'clock
                color = if (isDarkMode) Color(0xFFFB923C) else Color(0xFFEA580C),
                icon = Icons.Default.Spa
            ),
            SoilOrbitNode(
                id = "potassium",
                label = "POTASSIUM",
                value = sensorData.potassium.ifBlank { "--" },
                unit = "mg/kg",
                angleDeg = 225f, // 10:30
                color = if (isDarkMode) Color(0xFF818CF8) else Color(0xFF4F46E5),
                icon = Icons.Default.Layers
            )
        )
    }

    // Outer Orbit Card Container
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (isDarkMode) Color(0xFF0F141A) else Color.White,
        border = BorderStroke(
            1.dp,
            if (isDarkMode) {
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF00BC7D).copy(alpha = 0.35f),
                        Color(0xFF1E293B).copy(alpha = 0.5f),
                        Color(0xFF2DD4BF).copy(alpha = 0.25f)
                    )
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF00BC7D).copy(alpha = 0.35f),
                        Color(0xFFE2E8F0),
                        Color(0xFF2DD4BF).copy(alpha = 0.25f)
                    )
                )
            }
        ),
        shadowElevation = if (isDarkMode) 4.dp else 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 18.dp, start = 10.dp, end = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Top Header Section ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Eco,
                        contentDescription = null,
                        tint = if (isDarkMode) Color(0xFF00BC7D) else Color(0xFF059669),
                        modifier = Modifier.size(19.dp)
                    )
                    Text(
                        text = "SOIL 360° AGRONOMIC INTELLIGENCE ORBIT",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                        color = if (isDarkMode) Color.White else Color(0xFF0F172A),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Real-Time Soil Health Index Core with 8 Floating Physical Parameter Symbols",
                    fontSize = 10.sp,
                    color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // ── 360° Circular Orbit Visualizer ──
            SoilOrbitVisualizer(
                soilIndexScore = animatedSoilScore,
                statusText = statusText,
                statusColor = statusColor,
                nodes = nodes,
                isDarkMode = isDarkMode
            )
        }
    }
}

@Composable
private fun SoilOrbitVisualizer(
    soilIndexScore: Int,
    statusText: String,
    statusColor: Color,
    nodes: List<SoilOrbitNode>,
    isDarkMode: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SoilOrbitPulse")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    val dashPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dashPhase"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(390.dp),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val maxAvailable = minOf(maxWidth, maxHeight)

        // Compute orbit radius dynamically so nodes stay within view bounds
        val orbitRadiusDp = (maxAvailable * 0.36f).coerceIn(115.dp, 138.dp)
        val orbitRadiusPx = with(density) { orbitRadiusDp.toPx() }

        // 1. Orbit Canvas: dashed circular track & atmospheric glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)

            // Faint agronomic glow field
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isDarkMode) listOf(
                        Color(0xFF00BC7D).copy(alpha = 0.08f * glowPulse),
                        Color(0xFF2DD4BF).copy(alpha = 0.03f),
                        Color.Transparent
                    ) else listOf(
                        Color(0xFF00BC7D).copy(alpha = 0.06f * glowPulse),
                        Color(0xFF059669).copy(alpha = 0.02f),
                        Color.Transparent
                    ),
                    radius = orbitRadiusPx * 1.25f,
                    center = center
                )
            )

            // Dashed orbital track ring
            val strokeWidth = 1.6.dp.toPx()
            val dashEffect = PathEffect.dashPathEffect(
                floatArrayOf(6.dp.toPx(), 6.dp.toPx()),
                phase = dashPhase
            )
            drawCircle(
                color = if (isDarkMode) Color(0xFF00BC7D).copy(alpha = 0.50f) else Color(0xFF059669).copy(alpha = 0.65f),
                radius = orbitRadiusPx,
                center = center,
                style = Stroke(width = strokeWidth, pathEffect = dashEffect)
            )
        }

        // 2. Central Soil Health Index Core
        Box(
            modifier = Modifier
                .size(138.dp)
                .align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            // Outer glowing ring
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 2.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = if (isDarkMode) listOf(
                            Color(0xFF00BC7D).copy(alpha = 0.30f * glowPulse),
                            Color(0xFF00BC7D).copy(alpha = 0.06f),
                            Color.Transparent
                        ) else listOf(
                            Color(0xFF059669).copy(alpha = 0.22f * glowPulse),
                            Color(0xFF00BC7D).copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        radius = size.minDimension / 2f
                    )
                )
                drawCircle(
                    color = if (isDarkMode) Color(0xFF00BC7D) else Color(0xFF059669),
                    radius = (size.minDimension / 2f) - strokeW,
                    style = Stroke(width = strokeW)
                )
            }

            // Inner circular disc
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = if (isDarkMode) listOf(
                                Color(0xFF0A2318),
                                Color(0xFF061710),
                                Color(0xFF030A07)
                            ) else listOf(
                                Color(0xFFF0FDF4),
                                Color(0xFFE6F4EA),
                                Color(0xFFDCFCE7)
                            )
                        ),
                        shape = CircleShape
                    )
                    .border(
                        width = if (isDarkMode) 0.dp else 1.dp,
                        color = if (isDarkMode) Color.Transparent else Color(0xFF059669).copy(alpha = 0.25f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = "SOIL INDEX",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = if (isDarkMode) Color(0xFF2DD4BF) else Color(0xFF047857)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = soilIndexScore.toString(),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDarkMode) Color.White else Color(0xFF0F172A),
                        lineHeight = 38.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Evaluation pill badge
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = statusColor,
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = statusText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 0.4.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. 8 Floating Physical Parameter Nodes positioned around the 360° circle
        nodes.forEach { node ->
            val rad = Math.toRadians(node.angleDeg.toDouble())
            val offsetX = with(density) { (orbitRadiusPx * cos(rad)).toFloat().toDp() }
            val offsetY = with(density) { (orbitRadiusPx * sin(rad)).toFloat().toDp() }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = offsetX, y = offsetY),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.wrapContentSize()
                ) {
                    // Icon Bubble
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                color = if (isDarkMode) Color(0xFF0F172A).copy(alpha = 0.94f) else Color.White,
                                shape = CircleShape
                            )
                            .border(
                                width = if (isDarkMode) 1.5.dp else 1.8.dp,
                                color = node.color,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(node.color.copy(alpha = if (isDarkMode) 0.15f else 0.10f), CircleShape)
                        )
                        Icon(
                            imageVector = node.icon,
                            contentDescription = node.label,
                            tint = node.color,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Parameter Name Tag Pill
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isDarkMode) Color(0xFF1E293B).copy(alpha = 0.95f) else Color(0xFFF1F5F9),
                        border = BorderStroke(0.6.dp, node.color.copy(alpha = if (isDarkMode) 0.45f else 0.60f)),
                        shadowElevation = if (isDarkMode) 0.dp else 1.dp
                    ) {
                        Text(
                            text = node.label,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color.White else Color(0xFF0F172A),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(1.dp))

                    // Value & Unit text
                    Text(
                        text = "${node.value} ${node.unit}".trim(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) Color.White else Color(0xFF0F172A),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Preview(name = "SOIL 360 Orbit - Dark Mode", showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
private fun SoilSensorDisplayDarkPreview() {
    MaterialTheme {
        SoilSensorDisplay(
            sensorData = SensorData.SoilSensorData(
                deviceId = "2",
                nitrogen = "45",
                phosphorus = "38",
                potassium = "110",
                moisture = "34.2",
                temperature = "24.1",
                ec = "450",
                pH = "6.8",
                salinity = "120"
            )
        )
    }
}

@Preview(name = "SOIL 360 Orbit - Light Mode", showBackground = true, backgroundColor = 0xFFF8FAFC)
@Composable
private fun SoilSensorDisplayLightPreview() {
    MaterialTheme {
        SoilSensorDisplay(
            sensorData = SensorData.SoilSensorData(
                deviceId = "2",
                nitrogen = "45",
                phosphorus = "38",
                potassium = "110",
                moisture = "34.2",
                temperature = "24.1",
                ec = "450",
                pH = "6.8",
                salinity = "120"
            )
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// WEATHER STATION CREATIVE MODERN DECK (Atmospheric Vista & Bento Grid Deck)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun WeatherStationDisplay(
    sensorData: SensorData.WeatherData,
    modifier: Modifier = Modifier
) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()

    // Smooth numerical animations
    val rawTemp = sensorData.temperature.replace(",", ".").toFloatOrNull() ?: 24.0f
    val rawHum = sensorData.humidity.replace(",", ".").toFloatOrNull() ?: 50.0f
    val rawPress = sensorData.pressure.replace(",", ".").toFloatOrNull() ?: 1013.25f
    val rawLux = sensorData.lux.replace(",", ".").toFloatOrNull() ?: 1500f

    val animatedTemp by animateFloatAsState(
        targetValue = rawTemp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "weatherTemp"
    )
    val animatedHum by animateFloatAsState(
        targetValue = rawHum,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "weatherHum"
    )
    val animatedPress by animateFloatAsState(
        targetValue = rawPress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "weatherPress"
    )
    val animatedLux by animateFloatAsState(
        targetValue = rawLux,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "weatherLux"
    )

    // Fluid wave and ambient glow phase animations
    val infiniteTransition = rememberInfiniteTransition(label = "weatherWaveTransition")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )
    val ambientPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "weatherPulse"
    )

    // ── Meteorological Analytics Calculations ──
    val tempF = animatedTemp * 1.8f + 32f

    // Magnus-Tetens Dew Point approximation
    val safeHum = (animatedHum / 100f).coerceIn(0.01f, 1f)
    val alpha = ((17.27f * animatedTemp) / (237.7f + animatedTemp)) + kotlin.math.ln(safeHum)
    val dewPoint = (237.7f * alpha) / (17.27f - alpha)

    // Apparent Temperature / Feels Like
    val vaporPres = safeHum * 6.105f * kotlin.math.exp((17.27f * animatedTemp) / (237.7f + animatedTemp))
    val feelsLike = animatedTemp + 0.33f * vaporPres - 4.0f

    // Biometeorological Comfort Index (0 - 100%)
    val tempDev = kotlin.math.abs(animatedTemp - 22.5f) / 15f
    val humDev = kotlin.math.abs(animatedHum - 50f) / 40f
    val comfortScore = ((1f - (tempDev * 0.5f + humDev * 0.5f).coerceIn(0f, 1f)) * 100f).roundToInt().coerceIn(15, 100)

    // Dynamic Weather Condition Synthesis
    val (conditionTitle, conditionColor, conditionIcon) = when {
        animatedHum >= 85f && animatedPress < 1008f -> Triple("STORMY / RAIN RISK", Color(0xFF6366F1), Icons.Default.Cloud)
        animatedHum >= 75f                           -> Triple("HUMID & OVERCAST",   Color(0xFF38BDF8), Icons.Default.Cloud)
        animatedLux >= 10000f                         -> Triple("BRILLIANT SUNSHINE", Color(0xFFF59E0B), Icons.Default.WbSunny)
        animatedLux >= 2000f                          -> Triple("CLEAR & SUNNY",      Color(0xFFFBBF24), Icons.Default.WbSunny)
        animatedTemp >= 30f                          -> Triple("WARM / HEAT INDEX",  Color(0xFFFF5252), Icons.Default.WbSunny)
        animatedTemp <= 14f                          -> Triple("CRISP & CHILLY",     Color(0xFF00D2FF), Icons.Default.AcUnit)
        else                                         -> Triple("OPTIMAL AMBIENCE",   Color(0xFF10B981), Icons.Default.WbSunny)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Atmospheric Vista Hero Deck
        WeatherHeroVistaCard(
            tempC = animatedTemp,
            tempF = tempF,
            feelsLike = feelsLike,
            dewPoint = dewPoint,
            comfortScore = comfortScore,
            conditionTitle = conditionTitle,
            conditionColor = conditionColor,
            conditionIcon = conditionIcon,
            deviceId = sensorData.deviceId,
            ambientPulse = ambientPulse,
            isDarkMode = isDarkMode
        )

        // 2. 2x2 Bento Grid: 4 Bespoke Environmental Pillars
        WeatherBentoGrid(
            tempC = animatedTemp,
            tempF = tempF,
            humidity = animatedHum,
            pressure = animatedPress,
            lux = animatedLux,
            wavePhase = wavePhase,
            isDarkMode = isDarkMode
        )

        // 3. Biometeorology & Ambience Summary Deck
        AtmosphericAnalyticsDeck(
            comfortScore = comfortScore,
            vaporPres = vaporPres,
            pressure = animatedPress,
            dewPoint = dewPoint,
            isDarkMode = isDarkMode
        )
    }
}

/**
 * 1. Top Atmospheric Vista Hero Card: Big typographic temperature, sky ambient vista, and biometeorology chips.
 */
@Composable
private fun WeatherHeroVistaCard(
    tempC: Float,
    tempF: Float,
    feelsLike: Float,
    dewPoint: Float,
    comfortScore: Int,
    conditionTitle: String,
    conditionColor: Color,
    conditionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    deviceId: String,
    ambientPulse: Float,
    isDarkMode: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (isDarkMode) Color(0xFF0F172A) else Color(0xFFFFFFFF),
        border = BorderStroke(
            1.2.dp,
            if (isDarkMode) Color(0xFF1E293B) else Color(0xFFE2E8F0)
        ),
        shadowElevation = if (isDarkMode) 6.dp else 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.radialGradient(
                        colors = if (isDarkMode) listOf(
                            conditionColor.copy(alpha = 0.20f * ambientPulse),
                            Color(0xFF131D2E),
                            Color(0xFF0F172A)
                        ) else listOf(
                            conditionColor.copy(alpha = 0.12f * ambientPulse),
                            Color(0xFFF8FAFC),
                            Color(0xFFFFFFFF)
                        ),
                        radius = 450f,
                        center = Offset(700f, 100f)
                    )
                )
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row: Station Status & Device Tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = conditionColor.copy(alpha = if (isDarkMode) 0.22f else 0.15f),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = conditionIcon,
                                    contentDescription = null,
                                    tint = conditionColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = "ATMOSPHERIC VISTA",
                            color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        )
                    }

                    // Device Tag Badge
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                        border = BorderStroke(
                            0.8.dp,
                            if (isDarkMode) Color(0xFF334155) else Color(0xFFCBD5E1)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(conditionColor, CircleShape)
                            )
                            Text(
                                text = "NODE #${deviceId.ifEmpty { "WTR" }}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDarkMode) Color(0xFFE2E8F0) else Color(0xFF334155)
                            )
                        }
                    }
                }

                // Main Reading Row: Giant Temperature & Radiant Weather Glyph
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = String.format(Locale.US, "%.1f", tempC),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isDarkMode) Color.White else Color(0xFF0F172A),
                                lineHeight = 48.sp
                            )
                            Text(
                                text = "°C",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = conditionColor,
                                modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                            )
                        }

                        // Secondary pill: Fahrenheit & Condition text
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                border = BorderStroke(0.6.dp, if (isDarkMode) Color(0xFF334155) else Color(0xFFCBD5E1))
                            ) {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", tempF)}°F",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = conditionColor.copy(alpha = if (isDarkMode) 0.20f else 0.12f),
                                border = BorderStroke(0.8.dp, conditionColor.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = conditionTitle,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = conditionColor,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                )
                            }
                        }
                    }

                    // Radiant Celestial Weather Glyph
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                color = conditionColor.copy(alpha = (if (isDarkMode) 0.12f else 0.08f) * ambientPulse),
                                shape = CircleShape
                            )
                            .border(
                                width = 1.5.dp,
                                color = conditionColor.copy(alpha = 0.40f * ambientPulse),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = conditionIcon,
                            contentDescription = conditionTitle,
                            tint = conditionColor,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                // Bottom Biometeorology Chips: Feels Like, Dew Point, Comfort Score
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BiometeorologyChip(
                        label = "Feels",
                        value = "${String.format(Locale.US, "%.1f", feelsLike)}°C",
                        color = Color(0xFFFF7043),
                        isDarkMode = isDarkMode,
                        modifier = Modifier.weight(1f)
                    )
                    BiometeorologyChip(
                        label = "Dew Pt",
                        value = "${String.format(Locale.US, "%.1f", dewPoint)}°C",
                        color = Color(0xFF29B6F6),
                        isDarkMode = isDarkMode,
                        modifier = Modifier.weight(1f)
                    )
                    BiometeorologyChip(
                        label = "Comfort",
                        value = "$comfortScore%",
                        color = Color(0xFF10B981),
                        isDarkMode = isDarkMode,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BiometeorologyChip(
    label: String,
    value: String,
    color: Color,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isDarkMode) Color(0xFF1E293B).copy(alpha = 0.75f) else Color(0xFFF8FAFC),
        border = BorderStroke(
            0.8.dp,
            if (isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0)
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label.uppercase(Locale.US),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
    }
}

/**
 * 2. 2x2 Bento Matrix: 4 Bespoke Pillars (Thermal, Hydrometric, Barometric, Photometric).
 */
@Composable
private fun WeatherBentoGrid(
    tempC: Float,
    tempF: Float,
    humidity: Float,
    pressure: Float,
    lux: Float,
    wavePhase: Float,
    isDarkMode: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Row 1: Thermal Dynamics (Temp) & Hydrometric Waveform (Humidity)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ThermalDynamicsCard(
                tempC = tempC,
                tempF = tempF,
                isDarkMode = isDarkMode,
                modifier = Modifier.weight(1f)
            )
            HydrometricWaveCard(
                humidity = humidity,
                wavePhase = wavePhase,
                isDarkMode = isDarkMode,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Barometric Isobar (Pressure) & Solar Photometric (LUX)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BarometricIsobarCard(
                pressure = pressure,
                isDarkMode = isDarkMode,
                modifier = Modifier.weight(1f)
            )
            SolarLuminescenceCard(
                lux = lux,
                isDarkMode = isDarkMode,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Pillar 1: Thermal Dynamics Card (Temperature with Graduated Thermometer Track).
 */
@Composable
private fun ThermalDynamicsCard(
    tempC: Float,
    tempF: Float,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = Color(0xFFFF5252)
    val statusText = when {
        tempC < 16f -> "Cool"
        tempC in 18f..26f -> "Comfort Ideal"
        tempC in 26.1f..32f -> "Warm"
        else -> "Extreme Heat"
    }

    Surface(
        modifier = modifier.height(180.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (isDarkMode) Color(0xFF0F172A) else Color(0xFFFFFFFF),
        border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
        shadowElevation = if (isDarkMode) 4.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Thermostat,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "THERMAL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                        letterSpacing = 0.8.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = accent.copy(alpha = if (isDarkMode) 0.18f else 0.10f)
                ) {
                    Text(
                        text = statusText,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                    )
                }
            }

            // Value
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(Locale.US, "%.1f", tempC),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isDarkMode) Color.White else Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "°C",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Text(
                    text = "${String.format(Locale.US, "%.1f", tempF)}°F",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDarkMode) Color(0xFF64748B) else Color(0xFF94A3B8)
                )
            }

            // Graduated Thermometer Track Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            ) {
                val w = size.width
                val h = size.height
                val trackY = h / 2f
                val trackHeight = 6.dp.toPx()

                // Background track (-10°C to 50°C)
                drawRoundRect(
                    color = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                    topLeft = Offset(0f, trackY - trackHeight / 2f),
                    size = Size(w, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                )

                // Comfort Zone bracket (18°C to 26°C)
                val minT = -10f
                val maxT = 50f
                val comfortStart = ((18f - minT) / (maxT - minT)).coerceIn(0f, 1f) * w
                val comfortEnd = ((26f - minT) / (maxT - minT)).coerceIn(0f, 1f) * w
                drawRoundRect(
                    color = Color(0xFF10B981).copy(alpha = if (isDarkMode) 0.35f else 0.25f),
                    topLeft = Offset(comfortStart, trackY - trackHeight / 2f),
                    size = Size(comfortEnd - comfortStart, trackHeight),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                )

                // Current Temperature fill
                val currentNorm = ((tempC - minT) / (maxT - minT)).coerceIn(0.04f, 1f)
                val fillW = currentNorm * w
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFFFF8A80), accent),
                        startX = 0f,
                        endX = fillW
                    ),
                    topLeft = Offset(0f, trackY - trackHeight / 2f),
                    size = Size(fillW, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)
                )

                // Indicator Thumb Node
                drawCircle(
                    color = accent,
                    radius = 5.dp.toPx(),
                    center = Offset(fillW, trackY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.5.dp.toPx(),
                    center = Offset(fillW, trackY)
                )
            }

            // Reference labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "-10°",
                    fontSize = 8.sp,
                    color = if (isDarkMode) Color(0xFF64748B) else Color(0xFF94A3B8)
                )
                Text(
                    text = "Ideal (18-26°)",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )
                Text(
                    text = "50°",
                    fontSize = 8.sp,
                    color = if (isDarkMode) Color(0xFF64748B) else Color(0xFF94A3B8)
                )
            }
        }
    }
}

/**
 * Pillar 2: Hydrometric Waveform Card (Animated Fluid Wave Inside Glass Capsule).
 */
@Composable
private fun HydrometricWaveCard(
    humidity: Float,
    wavePhase: Float,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = if (isDarkMode) Color(0xFF00D2FF) else Color(0xFF0284C7)
    val statusText = when {
        humidity < 35f -> "Dry"
        humidity in 40f..60f -> "Optimal"
        humidity in 60.1f..75f -> "Muggy"
        else -> "Very Humid"
    }

    Surface(
        modifier = modifier.height(180.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (isDarkMode) Color(0xFF0F172A) else Color(0xFFFFFFFF),
        border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
        shadowElevation = if (isDarkMode) 4.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "HUMIDITY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                        letterSpacing = 0.8.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = accent.copy(alpha = if (isDarkMode) 0.18f else 0.10f)
                ) {
                    Text(
                        text = statusText,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                    )
                }
            }

            // Value
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(Locale.US, "%.1f", humidity),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isDarkMode) Color.White else Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Text(
                    text = "Relative RH",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDarkMode) Color(0xFF64748B) else Color(0xFF94A3B8)
                )
            }

            // Fluid Wave Capsule Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                val w = size.width
                val h = size.height
                val fillFraction = (humidity / 100f).coerceIn(0.12f, 0.95f)
                val baseWaterY = h * (1f - fillFraction)

                // Capsule outline
                drawRoundRect(
                    color = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )

                // Fluid wave path
                val wavePath = Path()
                wavePath.moveTo(0f, h)
                wavePath.lineTo(0f, baseWaterY)

                val segments = 24
                val waveAmp = 2.2.dp.toPx()
                for (i in 0..segments) {
                    val x = (i / segments.toFloat()) * w
                    val y = baseWaterY + sin((i / segments.toFloat() * 2f * PI) + wavePhase).toFloat() * waveAmp
                    wavePath.lineTo(x, y)
                }
                wavePath.lineTo(w, h)
                wavePath.close()

                drawPath(
                    path = wavePath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            accent.copy(alpha = if (isDarkMode) 0.65f else 0.50f),
                            accent.copy(alpha = if (isDarkMode) 0.25f else 0.18f)
                        ),
                        startY = baseWaterY,
                        endY = h
                    )
                )

                // 50% ideal dashed line
                val midY = h * 0.50f
                drawLine(
                    color = if (isDarkMode) Color(0xFF38BDF8).copy(0.40f) else Color(0xFF0284C7).copy(0.35f),
                    start = Offset(4.dp.toPx(), midY),
                    end = Offset(w - 4.dp.toPx(), midY),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Subtitle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0%", fontSize = 8.sp, color = if (isDarkMode) Color(0xFF64748B) else Color(0xFF94A3B8))
                Text("50% Ideal", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = accent)
                Text("100%", fontSize = 8.sp, color = if (isDarkMode) Color(0xFF64748B) else Color(0xFF94A3B8))
            }
        }
    }
}

/**
 * Pillar 3: Barometric Isobaric Capsule Card (Pressure with Sea-Level Baseline).
 */
@Composable
private fun BarometricIsobarCard(
    pressure: Float,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = if (isDarkMode) Color(0xFF818CF8) else Color(0xFF4F46E5)
    val diffFromSea = pressure - 1013.25f
    val diffString = if (diffFromSea >= 0) "+${String.format(Locale.US, "%.1f", diffFromSea)}" else String.format(Locale.US, "%.1f", diffFromSea)

    val statusText = when {
        pressure >= 1020f -> "High Press"
        pressure >= 1010f -> "Standard"
        else -> "Low Press"
    }

    Surface(
        modifier = modifier.height(180.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (isDarkMode) Color(0xFF0F172A) else Color(0xFFFFFFFF),
        border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
        shadowElevation = if (isDarkMode) 4.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "PRESSURE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                        letterSpacing = 0.8.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = accent.copy(alpha = if (isDarkMode) 0.18f else 0.10f)
                ) {
                    Text(
                        text = statusText,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                    )
                }
            }

            // Value
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(Locale.US, "%.1f", pressure),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isDarkMode) Color.White else Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "hPa",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Text(
                    text = "$diffString vs 1013 hPa",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDarkMode) Color(0xFF64748B) else Color(0xFF94A3B8)
                )
            }

            // Isobaric Barometer Scale Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            ) {
                val w = size.width
                val h = size.height
                val trackY = h / 2f
                val trackH = 6.dp.toPx()

                // Baseline track (970 to 1050 hPa)
                drawRoundRect(
                    color = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                    topLeft = Offset(0f, trackY - trackH / 2f),
                    size = Size(w, trackH),
                    cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
                )

                // 1013.25 Standard Sea Level Reference Tick
                val seaNorm = ((1013.25f - 970f) / (1050f - 970f)).coerceIn(0f, 1f)
                val seaX = seaNorm * w
                drawLine(
                    color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                    start = Offset(seaX, trackY - 7.dp.toPx()),
                    end = Offset(seaX, trackY + 7.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx()
                )

                // Current Pointer
                val currentNorm = ((pressure - 970f) / (1050f - 970f)).coerceIn(0.05f, 0.95f)
                val currX = currentNorm * w

                // Fill connecting center to current
                val fillLeft = min(seaX, currX)
                val fillW = abs(currX - seaX).coerceAtLeast(2.dp.toPx())
                drawRoundRect(
                    color = accent.copy(alpha = 0.6f),
                    topLeft = Offset(fillLeft, trackY - trackH / 2f),
                    size = Size(fillW, trackH),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )

                // Needle Head
                drawCircle(color = accent, radius = 5.dp.toPx(), center = Offset(currX, trackY))
                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(currX, trackY))
            }

            // Scale Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("970", fontSize = 8.sp, color = if (isDarkMode) Color(0xFF64748B) else Color(0xFF94A3B8))
                Text("1013 Std", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = accent)
                Text("1050", fontSize = 8.sp, color = if (isDarkMode) Color(0xFF64748B) else Color(0xFF94A3B8))
            }
        }
    }
}

/**
 * Pillar 4: Solar Photometric Luminescence Card (Radiant Multi-Segment Meter).
 */
@Composable
private fun SolarLuminescenceCard(
    lux: Float,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = Color(0xFFF59E0B)
    val statusText = when {
        lux >= 10000f -> "Sunlight"
        lux >= 1000f  -> "Daylight"
        lux >= 300f   -> "Indoor"
        else          -> "Low Light"
    }

    Surface(
        modifier = modifier.height(180.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (isDarkMode) Color(0xFF0F172A) else Color(0xFFFFFFFF),
        border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
        shadowElevation = if (isDarkMode) 4.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LightMode,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "LIGHT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                        letterSpacing = 0.8.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = accent.copy(alpha = if (isDarkMode) 0.18f else 0.10f)
                ) {
                    Text(
                        text = statusText,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                    )
                }
            }

            // Value
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = lux.roundToInt().toString(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isDarkMode) Color.White else Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "LUX",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Text(
                    text = "Illuminance",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDarkMode) Color(0xFF64748B) else Color(0xFF94A3B8)
                )
            }

            // Luminous 8-Segment Energy Scale Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            ) {
                val w = size.width
                val h = size.height
                val totalBars = 8
                val barGap = 3.dp.toPx()
                val barW = (w - (barGap * (totalBars - 1))) / totalBars.toFloat()
                val logFraction = (kotlin.math.log10(lux + 1f) / 4.5f).coerceIn(0.08f, 1f)
                val activeCount = (logFraction * totalBars).roundToInt().coerceIn(1, totalBars)

                for (i in 0 until totalBars) {
                    val x = i * (barW + barGap)
                    val isActive = i < activeCount
                    val barH = (h * (0.35f + (i / totalBars.toFloat()) * 0.65f))
                    val topY = h - barH

                    drawRoundRect(
                        color = if (isActive) {
                            accent.copy(alpha = 0.50f + (i / totalBars.toFloat()) * 0.50f)
                        } else {
                            if (isDarkMode) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                        },
                        topLeft = Offset(x, topY),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }
            }

            // Scale Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0 lx", fontSize = 8.sp, color = if (isDarkMode) Color(0xFF64748B) else Color(0xFF94A3B8))
                Text("Daylight", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = accent)
                Text("20k lx", fontSize = 8.sp, color = if (isDarkMode) Color(0xFF64748B) else Color(0xFF94A3B8))
            }
        }
    }
}

/**
 * 3. Bottom Biometeorology & Atmospheric Summary Deck.
 */
@Composable
private fun AtmosphericAnalyticsDeck(
    comfortScore: Int,
    vaporPres: Float,
    pressure: Float,
    dewPoint: Float,
    isDarkMode: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isDarkMode) Color(0xFF0F172A) else Color(0xFFFFFFFF),
        border = BorderStroke(1.dp, if (isDarkMode) Color(0xFF1E293B) else Color(0xFFE2E8F0)),
        shadowElevation = if (isDarkMode) 3.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BIOMETEOROLOGY & ATMOSPHERIC STABILITY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B),
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (comfortScore >= 80) "OPTIMAL\nCOMFORT" else "MODERATE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (comfortScore >= 80) Color(0xFF10B981) else Color(0xFFF59E0B),
                    textAlign = TextAlign.End,
                    lineHeight = 11.sp
                )
            }

            // Multi-segment Comfort Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val totalBars = 10
                val activeBars = (comfortScore / 10f).roundToInt().coerceIn(1, totalBars)
                for (i in 1..totalBars) {
                    val isActive = i <= activeBars
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .background(
                                color = if (isActive) Color(0xFF10B981) else if (isDarkMode) Color(0xFF1E293B) else Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }

            // 3 Analytic Metric Columns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "VAPOR PRESSURE",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) Color(0xFF64748B) else Color(0xFF94A3B8)
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", vaporPres)} hPa",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) Color(0xFFE2E8F0) else Color(0xFF1E293B)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "DEW SPREAD",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) Color(0xFF64748B) else Color(0xFF94A3B8)
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", dewPoint)}°C",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "ISOBARIC STATE",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) Color(0xFF64748B) else Color(0xFF94A3B8)
                    )
                    Text(
                        text = if (pressure >= 1013.25f) "Stable / Fair" else "Depressive / Rain",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pressure >= 1013.25f) Color(0xFF10B981) else Color(0xFFF59E0B)
                    )
                }
            }
        }
    }
}

@Preview(name = "Weather Station - Dark Mode", showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
private fun WeatherStationDisplayDarkPreview() {
    MaterialTheme {
        WeatherStationDisplay(
            sensorData = SensorData.WeatherData(
                deviceId = "13",
                temperature = "24.00",
                humidity = "50.00",
                lux = "1500",
                pressure = "1015"
            )
        )
    }
}

@Preview(name = "Weather Station - Light Mode", showBackground = true, backgroundColor = 0xFFF8FAFC)
@Composable
private fun WeatherStationDisplayLightPreview() {
    MaterialTheme {
        WeatherStationDisplay(
            sensorData = SensorData.WeatherData(
                deviceId = "13",
                temperature = "24.00",
                humidity = "50.00",
                lux = "1500",
                pressure = "1015"
            )
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// TempLogger display (unchanged logic, dark theme applied)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun TempLoggerDisplay(viewModel: BluetoothScanViewModel, deviceAddress: String, deviceId: String, deviceName: String) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val CardDark = if (isDarkMode) BleSenseColors.CardDark else BleSenseColors.LightSurface
    val CardDark2 = if (isDarkMode) BleSenseColors.CardDarkElevated else BleSenseColors.LightBackground
    val TextPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val TextSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    LaunchedEffect(Unit) {
        println("🔍 TEMPLOGGER DEBUG: Device=$deviceName Address=$deviceAddress")
    }
    val allPacketsMap       by viewModel.tempLoggerPacketHistory.collectAsState()
    val allLatestPacketsMap by viewModel.latestTempLoggerPacket.collectAsState()

    val deviceSpecificPackets = remember(allPacketsMap, deviceAddress) { allPacketsMap[deviceAddress] ?: emptyList() }
    val latestPacketForThis   = remember(allLatestPacketsMap, deviceAddress) { allLatestPacketsMap[deviceAddress] }

    val largePackets = remember(deviceSpecificPackets) {
        deviceSpecificPackets.filter { p ->
            p.rawData.split(" ").filter { it.isNotBlank() }.count { it.isNotEmpty() && it != " " } >= 224
        }
    }
    val latestLargePacket = remember(latestPacketForThis) {
        latestPacketForThis?.takeIf { p ->
            p.rawData.split(" ").filter { it.isNotBlank() }.count { it.isNotEmpty() && it != " " } >= 224
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Device ${deviceAddress.takeLast(8)} Large Packets (${largePackets.size})", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (latestLargePacket != null) {
                Box(modifier = Modifier.background(GreenMuted, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("LIVE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = GreenAccent)
                }
            }
        }
        Text("ID: $deviceId | ${deviceAddress.takeLast(8)}", color = TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))

        if (largePackets.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                Text("No large packets (224 bytes) for ${deviceAddress.takeLast(8)}", color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            return
        }

        val tempVals = largePackets.map { it.temperature.toFloatOrNull() ?: 0f }
        val humVals  = largePackets.map { it.humidity.toFloatOrNull()    ?: 0f }
        if (tempVals.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(12.dp), color = CardDark2, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Statistics (${largePackets.size} packets)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("Avg Temp",  "${String.format(Locale.US, "%.1f", tempVals.average())}°C", GreenAccent)
                        StatItem("Min Temp",  "${String.format(Locale.US, "%.1f", tempVals.minOrNull()?:0f)}°C", BlueAccent)
                        StatItem("Max Temp",  "${String.format(Locale.US, "%.1f", tempVals.maxOrNull()?:0f)}°C", RedAccent)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("Avg Hum",   "${String.format(Locale.US, "%.1f", humVals.average())}%",  GreenAccent)
                        StatItem("Min Hum",   "${String.format(Locale.US, "%.1f", humVals.minOrNull()?:0f)}%",  BlueAccent)
                        StatItem("Max Hum",   "${String.format(Locale.US, "%.1f", humVals.maxOrNull()?:0f)}%",  RedAccent)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(largePackets.reversed()) { packet ->
                TempLoggerPacketCard(packet = packet, index = largePackets.indexOf(packet)+1, isLatest = packet == latestLargePacket, deviceName = deviceName)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val TextSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = TextSecondary)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun TempLoggerPacketCard(packet: SensorData.TempLoggerData, index: Int, isLatest: Boolean, deviceName: String) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val CardDark = if (isDarkMode) BleSenseColors.CardDark else BleSenseColors.LightSurface
    val CardDark2 = if (isDarkMode) BleSenseColors.CardDarkElevated else BleSenseColors.LightBackground
    val TextSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    var expanded       by remember { mutableStateOf(false) }
    var showByteGroups by remember { mutableStateOf(true) }

    val actualByteCount = remember(packet.rawData) {
        packet.rawData.split(" ").filter { it.isNotBlank() }.count { it.isNotEmpty() && it != " " }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        color    = if (isLatest) CardDark else CardDark2,
        border   = if (isLatest) BorderStroke(1.dp, GreenAccent.copy(alpha = 0.4f)) else null,
        tonalElevation = if (isLatest) 6.dp else 0.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("$deviceName · Packet #$index", color = BlueAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (isLatest) {
                        Box(modifier = Modifier.background(GreenMuted, RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("LATEST", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = GreenAccent)
                        }
                    }
                }
                Text("Device ${packet.deviceId}", color = TextSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                shape    = RoundedCornerShape(10.dp),
                color    = CardDark2
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (expanded) "Hide Raw Data" else "Show Raw Data", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text(if (actualByteCount >= 224) "224 bytes (7×32)" else "$actualByteCount bytes", color = TextSecondary.copy(alpha = 0.6f), fontSize = 10.sp)
                    }
                    if (expanded) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !showByteGroups, onClick = { showByteGroups = false }, label = { Text("Raw Hex", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BlueAccent, selectedLabelColor = Color.White))
                            FilterChip(selected = showByteGroups, onClick = { showByteGroups = true }, label = { Text("32-Byte Groups", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GreenAccent, selectedLabelColor = GreenDark))
                        }
                        Spacer(Modifier.height(10.dp))
                        if (!showByteGroups) {
                            Text(packet.rawData, color = PurpleAccent, fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
                        } else {
                            val groups = parseTempLoggerRawDataIntoByteGroups(packet.rawData)
                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(groups) { idx, group ->
                                    TempLoggerByteGroupItem(groupNumber = idx+1, bytes = group, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// TempLogger byte group item (dark theme applied)
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun TempLoggerByteGroupItem(groupNumber: Int, bytes: List<String>, modifier: Modifier = Modifier) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val CardDark = if (isDarkMode) BleSenseColors.CardDark else BleSenseColors.LightSurface
    val CardDark2 = if (isDarkMode) BleSenseColors.CardDarkElevated else BleSenseColors.LightBackground
    val DividerDark = if (isDarkMode) BleSenseColors.BorderDark else BleSenseColors.LightBorder
    val TextSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    val displayBytes    = bytes.take(32)
    val hasValidData    = displayBytes.any { it != "00" && it != "--" }
    val isEmptyGroup    = displayBytes.all { it == "--" }
    val (temperature, humidity) = remember(displayBytes) { extractTempHumidityFromGroup(displayBytes) }

    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp),
        color  = if (hasValidData) CardDark else CardDark2,
        border = BorderStroke(1.dp, if (hasValidData) DividerDark else CardDark2)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (isEmptyGroup) "Group $groupNumber (Empty)" else "Group $groupNumber",
                        color = if (hasValidData) BlueAccent else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (hasValidData && temperature != "--" && humidity != "--") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("🌡️ $temperature", color = RedAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("💧 $humidity",    color = BlueAccent,  fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (!isEmptyGroup) {
                        Text("Bytes ${(groupNumber-1)*32+1}–${groupNumber*32}", color = TextSecondary, fontSize = 9.sp)
                    }
                }
                val seqNum = displayBytes.getOrNull(31)?.toIntOrNull(16) ?: groupNumber
                if (!isEmptyGroup) {
                    Box(modifier = Modifier.background(
                        if (seqNum == groupNumber) GreenAccent.copy(0.15f) else YellowAccent.copy(0.15f),
                        CircleShape
                    ).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("Seq: ${String.format("%02X", seqNum)}",
                            color = if (seqNum == groupNumber) GreenAccent else YellowAccent,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (hasValidData) {
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(8), modifier = Modifier.height(140.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(displayBytes) { index, byte ->
                        val isHeader = index < 4 && byte != "00" && byte != "--"
                        val isSeq    = index == 31
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .background(when { isHeader->BlueAccent.copy(0.1f); isSeq->GreenAccent.copy(0.1f); else->Color.Transparent }, RoundedCornerShape(4.dp))
                                .border(1.dp, when { isHeader->BlueAccent.copy(0.3f); isSeq->GreenAccent.copy(0.3f); else->DividerDark.copy(0.3f) }, RoundedCornerShape(4.dp))
                                .padding(4.dp)
                        ) {
                            Text("B${index+1}", color = when { isHeader->BlueAccent; isSeq->GreenAccent; else->TextSecondary.copy(0.6f) }, fontSize = 7.sp)
                            Text(byte, color = when { byte=="--"->TextSecondary.copy(0.4f); isHeader->BlueAccent; isSeq->GreenAccent; byte=="00"->TextSecondary.copy(0.5f); else->GreenAccent }, fontSize = 11.sp, fontWeight = if (isHeader||isSeq) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                    Text("No data in this group", color = TextSecondary, fontSize = 11.sp, fontStyle = FontStyle.Italic)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Helpers (logic identical to original)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun RainVisualizer(
    rainfall: Float
) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val cardDark = if (isDarkMode) BleSenseColors.CardDark else BleSenseColors.LightSurface
    val borderDark = if (isDarkMode) BleSenseColors.BorderDark else BleSenseColors.LightBorder
    val textPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val textSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary
    val waterDarkBlue = if (isDarkMode) Color(0xFF38BDF8) else Color(0xFF1E40AF)
    val waterMediumBlue = if (isDarkMode) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
    val waterDeepNavy = if (isDarkMode) Color(0xFF0284C7) else Color(0xFF172554)

    val coroutineScope = rememberCoroutineScope()

    // ── TIPPING PHYSICS ──
    // GDKG PS-3150 calibration: 1 tip = 0.5 mm of rain
    val baseTipCount = (rainfall / 0.5f).toInt().coerceAtLeast(0)
    var manualTipTrigger by remember { mutableIntStateOf(0) }
    val tipCount = baseTipCount + manualTipTrigger

    // Alternating tip direction:
    // Odd tips (1, 3, 5...)  -> Left side tips down (-13.5°)
    // Even tips (2, 4, 6...) -> Right side tips down (+13.5°)
    // Zero tips (no rain yet) -> Balanced horizontal (0°)
    val targetAngle = if (tipCount == 0) 0f else if (tipCount % 2 == 1) -13.5f else 13.5f
    val seesawRotation = remember { Animatable(targetAngle) }

    // ── SPLASH STATE ──
    val splashProgress = remember { Animatable(0f) }
    var prevTipCount by remember { mutableIntStateOf(tipCount) }

    // ── REALISTIC MECHANICAL TIPPING ANIMATION ──
    // Triggered whenever new rain data (0.5 mm) arrives or manual test tip occurs.
    // Tips one time left down, next time right down, alternating seamlessly.
    LaunchedEffect(tipCount) {
        if (tipCount != prevTipCount && tipCount > 0) {
            // Trigger water splash at moment of discharge into hood cavity
            launch {
                delay(340) // Synchronized with impact against the mechanical stopper
                splashProgress.snapTo(0f)
                splashProgress.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
            }

            val start = seesawRotation.value
            val diff = targetAngle - start

            seesawRotation.animateTo(
                targetValue = targetAngle,
                animationSpec = keyframes {
                    durationMillis = 700
                    // Stage 1: Water accumulation overcomes equilibrium
                    (start + diff * 0.08f) at 100 using FastOutLinearInEasing
                    // Stage 2: Rapid gravity swing through center
                    (start + diff * 0.88f) at 340 using LinearEasing
                    // Stage 3: Strikes stopper screw with slight overshoot
                    val overshoot = if (targetAngle > start) targetAngle + 1.2f else targetAngle - 1.2f
                    overshoot at 460 using LinearEasing
                    // Stage 4: Damped metallic rebound
                    val rebound = targetAngle - (overshoot - targetAngle) * 0.35f
                    rebound at 570 using LinearOutSlowInEasing
                    // Stage 5: Firm rest on stopper
                    targetAngle at 700 using LinearOutSlowInEasing
                }
            )
        } else if (tipCount == 0) {
            seesawRotation.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
        }
        prevTipCount = tipCount
    }

    // ── RAINFALL INTENSITY ──
    val fillLevel = (rainfall / 40f).coerceIn(0f, 1f)
    val animatedFill by animateFloatAsState(
        targetValue = fillLevel,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "rainFill"
    )
    val intensityLabel = when {
        rainfall <= 0f -> "No Precipitation"
        rainfall <= 2.5f -> "Light Rain"
        rainfall <= 7.5f -> "Moderate Rain"
        rainfall <= 15f -> "Heavy Rain"
        else -> "Very Heavy Rain"
    }
    val intensityColor = when {
        rainfall <= 0f -> textSecondary
        rainfall <= 2.5f -> Color(0xFF60A5FA)
        rainfall <= 7.5f -> Color(0xFF38BDF8)
        rainfall <= 15f -> Color(0xFFFBBF24)
        else -> Color(0xFFEF4444)
    }
    val rainIntensity = (rainfall / 20f).coerceIn(0f, 1f)

    // ── AMBIENT ANIMATIONS ──
    val infiniteTransition = rememberInfiniteTransition(label = "rainAmbient")
    val rainPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (2200 / (1f + rainIntensity * 1.5f)).toInt(),
                easing = LinearEasing
            )
        ),
        label = "rainPhase"
    )
    val spiritPulse by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "spiritPulse"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cardDark,
        border = BorderStroke(1.dp, borderDark)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {

            // ── AMBIENT WATER-LEVEL IN CONTAINER (Dark, High-Vis Reservoir) ──
            Canvas(modifier = Modifier.matchParentSize()) {
                val barH = 14.dp.toPx() + (size.height * 0.22f * animatedFill)
                val waterTop = size.height - barH

                // Dark water reservoir fill
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = if (isDarkMode) {
                            listOf(
                                Color(0xFF0284C7).copy(alpha = 0.35f * animatedFill),
                                Color(0xFF0369A1).copy(alpha = 0.65f * animatedFill)
                            )
                        } else {
                            listOf(
                                waterMediumBlue.copy(alpha = 0.45f * animatedFill),
                                waterDeepNavy.copy(alpha = 0.75f * animatedFill)
                            )
                        },
                        startY = waterTop,
                        endY = size.height
                    ),
                    topLeft = Offset(0f, waterTop),
                    size = Size(size.width, barH)
                )

                // High-visibility crisp waterline on top of reservoir
                if (animatedFill > 0.01f) {
                    drawLine(
                        color = if (isDarkMode) Color(0xFF38BDF8).copy(alpha = 0.75f * animatedFill)
                                else waterDarkBlue.copy(alpha = 0.90f * animatedFill),
                        start = Offset(0f, waterTop),
                        end = Offset(size.width, waterTop),
                        strokeWidth = 2.5.dp.toPx()
                    )
                }
            }

            // ── RAIN PARTICLE SYSTEM (Dark, High-Vis Falling Rain) ──
            if (rainfall > 0f) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val particleCount = (14 + rainIntensity * 22).toInt()
                    val dropLen = 14.dp.toPx() + rainIntensity * 16.dp.toPx()
                    for (i in 0 until particleCount) {
                        val seed = i * 137.508f
                        val xFrac = (seed % 97f) / 97f
                        val yOffset = ((seed * 0.618f) % 1f)
                        val yFrac = (rainPhase + yOffset) % 1f
                        val x = xFrac * size.width
                        val y = yFrac * size.height
                        val alpha = (0.50f + rainIntensity * 0.40f) *
                            (1f - (yFrac * 0.25f))
                        drawLine(
                            color = waterDarkBlue.copy(alpha = alpha),
                            start = Offset(x, y),
                            end = Offset(x - 0.8f, y + dropLen),
                            strokeWidth = 2.5f + rainIntensity * 1.2f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── HEADER ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Cloud, null,
                        tint = if (rainfall > 0f) Color(0xFF38BDF8) else BlueAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        "Rain",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                }

                Spacer(Modifier.height(6.dp))

                // ── RAINFALL VALUE ──
                Text(
                    text = "${"%.1f".format(rainfall)} mm",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = textPrimary
                )

                // ── INTENSITY BADGE ──
                Surface(
                    modifier = Modifier.padding(top = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = intensityColor.copy(alpha = 0.12f),
                    border = BorderStroke(0.5.dp, intensityColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = intensityLabel,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = intensityColor
                    )
                }

                Spacer(Modifier.height(10.dp))

                // ══════════════════════════════════════════════════════════
                // RAIN GAUGE VIEWPORT (Clean, transparent background)
                // 3-Layer Physical Simulation:
                // Layer 1: Static base & rear hood walls
                // Layer 2: Tipping seesaw (swings inside the hollow hood cavities)
                // Layer 3: Static foreground (front curved rims, pillar, calibration screws)
                // Layer 4: Spirit level glow
                // Layer 4.5: Water dripping into tipping bucket
                // Layer 5: Discharged water splash ripple
                // ══════════════════════════════════════════════════════════
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            // Tap to simulate a rain tip: alternates one time left down, next time right down
                            manualTipTrigger++
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // ── INNER 1536:950 ASPECT RATIO GAUGE VIEWPORT ──
                    // Locking aspect ratio guarantees the TransformOrigin pivot (0.5000f, 0.27895f)
                    // aligns precisely with the physical bearing shaft with zero letterbox drift.
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1536f / 950f),
                        contentAlignment = Alignment.Center
                    ) {
                        // ── LAYER 1: STATIC BASE (Rear walls of hoods, base plate, spirit level) ──
                        Image(
                            painter = painterResource(R.drawable.rain_gauge_static),
                            contentDescription = "Rain gauge base and rear hoods",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )

                        // ── LAYER 2: TIPPING BUCKET SEESAW ──
                        // Pivots on the shaft bearing (X = 0.5000f, Y = 0.27895f).
                        // Tips one time left down (-13.5°), other time right down (+13.5°) on each 0.5 mm rain.
                        Image(
                            painter = painterResource(R.drawable.rain_gauge_seesaw),
                            contentDescription = "Tipping bucket seesaw",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationZ = seesawRotation.value
                                    transformOrigin = TransformOrigin(0.5000f, 0.27895f)
                                },
                            contentScale = ContentScale.FillBounds
                        )

                        // ── LAYER 3: STATIC FOREGROUND (Curved front lips of hoods, pillar, hex screws) ──
                        // Drawn on top so the bucket tips move seamlessly inside the hood cavities!
                        Image(
                            painter = painterResource(R.drawable.rain_gauge_foreground),
                            contentDescription = "Rain gauge foreground – curved hood rims, pillar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )

                        // ── LAYER 4: SPIRIT LEVEL GLOW ──
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val glowAlpha = 0.10f + spiritPulse * 0.18f
                            val glowRadius = 18.dp.toPx() + spiritPulse * 6.dp.toPx()
                            val spiritCx = size.width * 0.5f
                            val spiritCy = size.height * 0.646f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFCDFF57).copy(alpha = glowAlpha),
                                        Color(0xFF84CC16).copy(alpha = glowAlpha * 0.5f),
                                        Color.Transparent
                                    ),
                                    center = Offset(spiritCx, spiritCy),
                                    radius = glowRadius
                                ),
                                center = Offset(spiritCx, spiritCy),
                                radius = glowRadius
                            )
                        }

                        // ── LAYER 4.5: WATER DRIPPING INTO TIPPING BUCKET ──
                        if (rainfall > 0f) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val dripPhase = (rainPhase * 2.8f) % 1f
                                val dripY = size.height * (0.04f + dripPhase * 0.23f)
                                val dripX = size.width * 0.50f
                                drawCircle(
                                    color = waterDarkBlue.copy(alpha = 0.90f * (1f - dripPhase * 0.25f)),
                                    center = Offset(dripX, dripY),
                                    radius = 3.dp.toPx()
                                )
                                val trailPhase = ((rainPhase * 2.8f) + 0.5f) % 1f
                                val trailY = size.height * (0.04f + trailPhase * 0.23f)
                                drawCircle(
                                    color = waterMediumBlue.copy(alpha = 0.80f * (1f - trailPhase * 0.25f)),
                                    center = Offset(dripX, trailY),
                                    radius = 2.5.dp.toPx()
                                )
                            }
                        }

                        // ── LAYER 5: WATER SPLASH AT DISCHARGE (Dark, High-Vis Droplets) ──
                        if (splashProgress.value > 0f && splashProgress.value < 1f) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val progress = splashProgress.value
                                val splashAlpha = (1f - progress) * 0.95f
                                val splashCx = if (seesawRotation.value < 0f) size.width * 0.27f else size.width * 0.73f
                                val splashCy = size.height * 0.38f

                                drawCircle(
                                    color = waterDarkBlue.copy(alpha = splashAlpha * 0.6f),
                                    center = Offset(splashCx, splashCy),
                                    radius = 8.dp.toPx() + progress * 26.dp.toPx(),
                                    style = Stroke(width = 2.5.dp.toPx() * (1f - progress))
                                )
                                val dropletCount = 8
                                for (d in 0 until dropletCount) {
                                    val angle = (d.toFloat() / dropletCount) * 2f * PI.toFloat()
                                    val dist = progress * 22.dp.toPx()
                                    val dx = cos(angle) * dist
                                    val dy = sin(angle) * dist * 0.6f + progress * 12.dp.toPx()
                                    drawCircle(
                                        color = waterDeepNavy.copy(alpha = splashAlpha),
                                        center = Offset(splashCx + dx, splashCy + dy),
                                        radius = 2.8.dp.toPx() * (1f - progress * 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// WIND 360° AERODYNAMIC RADAR COMPASS (IMAGE 2 PARADIGM - PURE CIRCULAR INSTRUMENT)
// ══════════════════════════════════════════════════════════════════════════════

fun getBeaufortScale(speedMs: Float): Pair<Int, String> = when {
    speedMs < 0.3f  -> 0 to "Calm"
    speedMs < 1.6f  -> 1 to "Light Air"
    speedMs < 3.4f  -> 2 to "Light Breeze"
    speedMs < 5.5f  -> 3 to "Gentle Breeze"
    speedMs < 8.0f  -> 4 to "Moderate Breeze"
    speedMs < 10.8f -> 5 to "Fresh Breeze"
    speedMs < 13.9f -> 6 to "Strong Breeze"
    speedMs < 17.2f -> 7 to "High Wind"
    speedMs < 20.8f -> 8 to "Gale"
    speedMs < 24.5f -> 9 to "Strong Gale"
    speedMs < 28.5f -> 10 to "Storm"
    speedMs < 32.7f -> 11 to "Violent Storm"
    else            -> 12 to "Hurricane"
}

@Composable
fun WindVisualizer(
    windSpeed: Float,
    windDirection: Float,
    modifier: Modifier = Modifier
) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()

    // Smoothly animate speed and direction so real BLE updates and preview ticks transition fluidly
    val animatedSpeed by animateFloatAsState(
        targetValue = windSpeed,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "animatedWindSpeed"
    )
    val animatedDirection by animateFloatAsState(
        targetValue = windDirection,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "animatedWindDirection"
    )

    val speedMult = (animatedSpeed / 8f).coerceIn(0.2f, 4.0f)
    val infiniteTransition = rememberInfiniteTransition(label = "windAnim")

    // Streamline flow & anemometer rotation phase
    val windPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween((1400 / speedMult).toInt().coerceAtLeast(120), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "windPhase"
    )

    // Pulsing radar glow
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.80f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )

    val (condition, conditionColor) = when {
        animatedSpeed <= 1.5f  -> "CALM" to if (isDarkMode) Color(0xFF00BC7D) else Color(0xFF059669)
        animatedSpeed <= 5.5f  -> "BREEZY" to if (isDarkMode) Color(0xFF10B981) else Color(0xFF10B981)
        animatedSpeed <= 10.8f -> "WINDY" to if (isDarkMode) Color(0xFFF59E0B) else Color(0xFFF59E0B)
        else                   -> "STORM" to if (isDarkMode) Color(0xFFEF4444) else Color(0xFFEF4444)
    }

    val kmhSpeed = animatedSpeed * 3.6f

    // ── Pure Circular Aerodynamic Radar Compass Dial (Image 2 Paradigm) ──
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val dialSize = 320.dp

        // Outer Circular Dial Housing with subtle 3D bevel & soft shadow
        Surface(
            modifier = Modifier.size(dialSize),
            shape = CircleShape,
            color = if (isDarkMode) Color(0xFF0C121D) else Color(0xFFFFFFFF),
            border = BorderStroke(
                1.5.dp,
                if (isDarkMode) Color(0xFF334155) else Color(0xFFD6E0EA)
            ),
            shadowElevation = if (isDarkMode) 6.dp else 12.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // 1. Radar Canvas: Radial background, 3D bezel rim, guide arcs, precision ticks, streamlines, and 3D arrow
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val center = Offset(w / 2f, h / 2f)
                    val outerRadius = (size.minDimension / 2f) - 10.dp.toPx()
                    val tickBaseRadius = outerRadius - 1.dp.toPx()
                    val guideArcRadius = outerRadius * 0.70f

                    // Soft ambient gradient wash inside dial
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = if (isDarkMode) listOf(
                                Color(0xFF131D2E),
                                Color(0xFF0F1726),
                                Color(0xFF090D15)
                            ) else listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFF6F9FD),
                                Color(0xFFEBF1F7)
                            ),
                            radius = outerRadius,
                            center = center
                        ),
                        radius = outerRadius,
                        center = center
                    )

                    // Outer Rim Bezel Strokes
                    if (isDarkMode) {
                        drawCircle(
                            color = Color(0xFF22D3EE).copy(alpha = 0.20f * corePulse),
                            radius = outerRadius + 2.dp.toPx(),
                            center = center,
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawCircle(
                            color = Color(0xFF334155),
                            radius = outerRadius,
                            center = center,
                            style = Stroke(width = 1.8.dp.toPx())
                        )
                    } else {
                        drawCircle(
                            color = Color(0xFFCBD5E1),
                            radius = outerRadius,
                            center = center,
                            style = Stroke(width = 1.8.dp.toPx())
                        )
                        drawCircle(
                            color = Color(0xFFF1F5F9),
                            radius = outerRadius - 1.5.dp.toPx(),
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    // Concentric Aeronautical Guide Arcs (as seen in Image 2)
                    // Cyan Arc in SW Quadrant (around 195° to 250°)
                    val guideArcColor = Color(0xFF00A3A6)
                    drawArc(
                        color = if (isDarkMode) guideArcColor.copy(alpha = 0.85f) else guideArcColor.copy(alpha = 0.70f),
                        startAngle = 195f,
                        sweepAngle = 55f,
                        useCenter = false,
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = Offset(center.x - guideArcRadius, center.y - guideArcRadius),
                        size = Size(guideArcRadius * 2f, guideArcRadius * 2f)
                    )

                    // Complementary Soft Arc in SE Quadrant (around 120° to 165°)
                    drawArc(
                        color = if (isDarkMode) guideArcColor.copy(alpha = 0.45f) else guideArcColor.copy(alpha = 0.35f),
                        startAngle = 120f,
                        sweepAngle = 45f,
                        useCenter = false,
                        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = Offset(center.x - guideArcRadius, center.y - guideArcRadius),
                        size = Size(guideArcRadius * 2f, guideArcRadius * 2f)
                    )

                    // ── Precision Chronograph Tick Marks (Every 3 degrees, 120 ticks) ──
                    for (deg in 0 until 360 step 3) {
                        val rad = Math.toRadians(deg.toDouble())
                        val isNorth = deg == 0
                        val isMajor = deg % 30 == 0
                        val isInter = deg % 15 == 0

                        val tickLen = when {
                            isNorth -> 15.dp.toPx()
                            isMajor -> 9.5.dp.toPx()
                            isInter -> 6.5.dp.toPx()
                            else    -> 3.5.dp.toPx()
                        }

                        val tickColor = when {
                            isNorth -> if (isDarkMode) Color(0xFFFF3B30) else Color(0xFFDC2626)
                            isMajor -> if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF475569)
                            isInter -> if (isDarkMode) Color(0xFF64748B) else Color(0xFF94A3B8)
                            else    -> if (isDarkMode) Color(0xFF334155) else Color(0xFFCBD5E1)
                        }

                        val strokeW = when {
                            isNorth -> 2.8.dp.toPx()
                            isMajor -> 1.8.dp.toPx()
                            isInter -> 1.2.dp.toPx()
                            else    -> 0.8.dp.toPx()
                        }

                        val pStart = Offset(
                            center.x + (tickBaseRadius - tickLen) * sin(rad).toFloat(),
                            center.y - (tickBaseRadius - tickLen) * cos(rad).toFloat()
                        )
                        val pEnd = Offset(
                            center.x + tickBaseRadius * sin(rad).toFloat(),
                            center.y - tickBaseRadius * cos(rad).toFloat()
                        )

                        drawLine(
                            color = tickColor,
                            start = pStart,
                            end = pEnd,
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                    }

                    // ── Dynamic Aerodynamic Streamlines & Wind Direction Vector ──
                    withTransform({
                        rotate(animatedDirection, center)
                    }) {
                        val flowColor = Color(0xFF00A3A6)
                        val flowPhase = windPhase

                        // Coordinates along the flow axis
                        // Top: Wind Direction pointing arrowhead (pointing OUTWARD at animatedDirection)
                        val tipY = center.y - (outerRadius - 12.dp.toPx())
                        val barbY = center.y - (outerRadius - 38.dp.toPx())
                        val notchY = center.y - (outerRadius - 28.dp.toPx())
                        val barbW = 11.5.dp.toPx()

                        // Bottom: Directly opposite 180° counter-vector fin & flow intake
                        val tailTipY = center.y + (outerRadius - 14.dp.toPx())
                        val tailBarbY = center.y + (outerRadius - 26.dp.toPx())
                        val tailW = 7.dp.toPx()

                        // 1. Translucent Vector Needle Beam connecting opposite side to the arrowhead
                        val beamY1 = notchY + 2.dp.toPx()
                        val beamY2 = tailBarbY - 2.dp.toPx()
                        drawLine(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF00A3A6).copy(alpha = 0.75f),
                                    Color(0xFF0284C7).copy(alpha = 0.20f),
                                    Color(0xFF00A3A6).copy(alpha = 0.50f)
                                ),
                                startY = beamY1,
                                endY = beamY2
                            ),
                            start = Offset(center.x, beamY1),
                            end = Offset(center.x, beamY2),
                            strokeWidth = 4.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // 2. 4 Curving Aerodynamic Streamlines (flowing from opposite side toward the arrowhead)
                        val streamLines = listOf(
                            Pair(-1f, 16.dp.toPx()),
                            Pair(-1f, 32.dp.toPx()),
                            Pair(1f, 16.dp.toPx()),
                            Pair(1f, 32.dp.toPx())
                        )

                        streamLines.forEachIndexed { idx, (side, rOffset) ->
                            val phaseOffset = (flowPhase + (idx * 0.25f)) % 1f
                            val alphaPulse = (sin(phaseOffset * Math.PI).toFloat()).coerceIn(0.20f, 1f)

                            val path = Path()
                            val startX = center.x + side * (34.dp.toPx() + rOffset * 0.4f)
                            val startY = center.y + 78.dp.toPx()  // Starts from opposite side
                            val ctrlX = center.x + side * (82.dp.toPx() + rOffset)
                            val ctrlY = center.y
                            val endX = center.x + side * (38.dp.toPx() + rOffset * 0.4f)
                            val endY = center.y - 78.dp.toPx()    // Flows TOWARD the wind direction arrow!

                            path.moveTo(startX, startY)
                            path.quadraticTo(ctrlX, ctrlY, endX, endY)

                            drawPath(
                                path = path,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        flowColor.copy(alpha = 0.12f),
                                        flowColor.copy(alpha = (if (isDarkMode) 0.90f else 0.80f) * alphaPulse),
                                        flowColor.copy(alpha = 0.18f)
                                    ),
                                    start = Offset(startX, startY),
                                    end = Offset(endX, endY)
                                ),
                                style = Stroke(
                                    width = 2.0.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(24.dp.toPx(), 12.dp.toPx()),
                                        phase = phaseOffset * 36.dp.toPx()
                                    )
                                )
                            )

                            // Direction chevron along the flow pointing TOWARD the arrowhead
                            if (idx == 1 || idx == 2) {
                                val arrowTip = Offset(endX, endY)
                                val arrowAngle = Math.atan2((endY - ctrlY).toDouble(), (endX - ctrlX).toDouble())
                                val arrowSz = 5.5.dp.toPx()
                                val aL = Offset(
                                    arrowTip.x - arrowSz * cos(arrowAngle - 0.45).toFloat(),
                                    arrowTip.y - arrowSz * sin(arrowAngle - 0.45).toFloat()
                                )
                                val aR = Offset(
                                    arrowTip.x - arrowSz * cos(arrowAngle + 0.45).toFloat(),
                                    arrowTip.y - arrowSz * sin(arrowAngle + 0.45).toFloat()
                                )
                                val aHead = Path().apply {
                                    moveTo(arrowTip.x, arrowTip.y)
                                    lineTo(aL.x, aL.y)
                                    lineTo(aR.x, aR.y)
                                    close()
                                }
                                drawPath(aHead, color = flowColor.copy(alpha = 0.85f * alphaPulse))
                            }
                        }

                        // 3. Main Pointing Arrowhead: 3D Faceted Chevron pointing directly at Wind Direction!
                        // Left Facet (Teal highlight)
                        val leftFacet = Path().apply {
                            moveTo(center.x, tipY)
                            lineTo(center.x - barbW, barbY)
                            lineTo(center.x, notchY)
                            close()
                        }
                        drawPath(leftFacet, color = Color(0xFF00A3A6))

                        // Right Facet (Deep cyan/blue shadow)
                        val rightFacet = Path().apply {
                            moveTo(center.x, tipY)
                            lineTo(center.x + barbW, barbY)
                            lineTo(center.x, notchY)
                            close()
                        }
                        drawPath(rightFacet, color = Color(0xFF0284C7))

                        // Arrowhead Crisp Outline
                        val fullArrowPath = Path().apply {
                            moveTo(center.x, tipY)
                            lineTo(center.x + barbW, barbY)
                            lineTo(center.x, notchY)
                            lineTo(center.x - barbW, barbY)
                            close()
                        }
                        drawPath(
                            fullArrowPath,
                            color = if (isDarkMode) Color(0xFF0F172A) else Color(0xFF0369A1),
                            style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Forward wake ripples radiating forward in the pointing direction
                        for (w in 1..2) {
                            val wPhase = (flowPhase + w * 0.5f) % 1f
                            val wY = tipY + 2.dp.toPx() - wPhase * 15.dp.toPx()
                            if (wY > center.y - outerRadius) {
                                val wAlpha = (1f - wPhase) * 0.70f
                                val wRadius = 6.5.dp.toPx() * (0.5f + wPhase * 0.7f)
                                drawArc(
                                    color = Color(0xFF00A3A6).copy(alpha = wAlpha),
                                    startAngle = 205f,
                                    sweepAngle = 130f,
                                    useCenter = false,
                                    topLeft = Offset(center.x - wRadius, wY - wRadius),
                                    size = Size(wRadius * 2f, wRadius * 2f),
                                    style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }

                        // 4. Counter-Vector Fin & Flow Intake at the Directly Opposite Side
                        val tailPath = Path().apply {
                            moveTo(center.x, tailTipY)
                            lineTo(center.x + tailW, tailBarbY)
                            lineTo(center.x + tailW * 0.3f, tailBarbY + 4.dp.toPx())
                            lineTo(center.x, tailTipY + 2.dp.toPx())
                            lineTo(center.x - tailW * 0.3f, tailBarbY + 4.dp.toPx())
                            lineTo(center.x - tailW, tailBarbY)
                            close()
                        }
                        drawPath(tailPath, color = Color(0xFF00A3A6).copy(alpha = 0.85f))

                        // Flow chevrons streaming from opposite side toward center
                        for (c in 0..1) {
                            val cPhase = (flowPhase + c * 0.5f) % 1f
                            val cY = tailBarbY - cPhase * 18.dp.toPx()
                            val cAlpha = (sin(cPhase * Math.PI).toFloat()).coerceIn(0.15f, 0.85f)
                            val cW = 6.dp.toPx()
                            val cH = 4.dp.toPx()
                            val cPath = Path().apply {
                                moveTo(center.x - cW, cY + cH)
                                lineTo(center.x, cY)
                                lineTo(center.x + cW, cY + cH)
                            }
                            drawPath(
                                cPath,
                                color = flowColor.copy(alpha = cAlpha),
                                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                }

                // 2. All 8 Cardinal & Intercardinal Points (N in RED, NE, E, SE, S, SW, W, NW)
                val cardinalPoints = listOf(
                    0 to ("N" to true),
                    45 to ("NE" to false),
                    90 to ("E" to false),
                    135 to ("SE" to false),
                    180 to ("S" to false),
                    225 to ("SW" to false),
                    270 to ("W" to false),
                    315 to ("NW" to false)
                )
                val cardinalRadius = 115.dp
                cardinalPoints.forEach { (deg, info) ->
                    val (label, isNorth) = info
                    val rad = Math.toRadians(deg.toDouble())
                    val xOffset = (cardinalRadius.value * sin(rad)).dp
                    val yOffset = (-cardinalRadius.value * cos(rad)).dp

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = xOffset, y = yOffset)
                    ) {
                        Text(
                            text = label,
                            fontSize = if (isNorth) 16.sp else if (deg % 90 == 0) 14.5.sp else 12.sp,
                            fontWeight = if (isNorth) FontWeight.ExtraBold else FontWeight.Bold,
                            color = when {
                                isNorth -> if (isDarkMode) Color(0xFFFF3B30) else Color(0xFFDC2626)
                                deg % 90 == 0 -> if (isDarkMode) Color.White else Color(0xFF0F172A)
                                else -> if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF334155)
                            }
                        )
                    }
                }

                // 3. Central Elevated Frosted Glassmorphism Disc (Core)
                Surface(
                    modifier = Modifier.size(154.dp),
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(
                        1.5.dp,
                        if (isDarkMode) Color(0xFF22D3EE).copy(alpha = 0.5f) else Color(0xFFCBD5E1)
                    ),
                    shadowElevation = if (isDarkMode) 6.dp else 10.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.radialGradient(
                                    colors = if (isDarkMode) listOf(
                                        Color(0xFF142032).copy(alpha = 0.94f),
                                        Color(0xFF0E1624).copy(alpha = 0.92f),
                                        Color(0xFF070B12).copy(alpha = 0.90f)
                                    ) else listOf(
                                        Color(0xFFFFFFFF).copy(alpha = 0.95f),
                                        Color(0xFFF8FAFC).copy(alpha = 0.92f),
                                        Color(0xFFE2E8F0).copy(alpha = 0.88f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            // Dynamic Spinning Anemometer 3-Cup Rotor
                            AnemometerRotorIcon(
                                rotationAngle = (windPhase * 360f * speedMult) % 360f,
                                tint = if (isDarkMode) Color(0xFF38BDF8) else Color(0xFF1E293B)
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            // Primary Velocity Readout (e.g., "4.2 m/s")
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = String.format(Locale.US, "%.1f", animatedSpeed),
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkMode) Color.White else Color(0xFF0F172A),
                                    lineHeight = 40.sp
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "m/s",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF334155),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            // Secondary Velocity in km/h (e.g., "⏱ 15.1 km/h")
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF475569),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", kmhSpeed)} km/h",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF475569)
                                )
                            }

                            Spacer(modifier = Modifier.height(5.dp))

                            // Status Condition Pill Badge (e.g. "BREEZY" in Green Pill as seen in Image 2)
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = conditionColor,
                                shadowElevation = 2.dp
                            ) {
                                Text(
                                    text = condition,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 2.5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

    }
}

/**
 * High-fidelity physical 3-cup anemometer rotor rendered with vector canvas precision.
 */
@Composable
private fun AnemometerRotorIcon(
    rotationAngle: Float,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(26.dp, 20.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val mastTopY = h * 0.40f
        val mastBottomY = h * 0.95f

        // Anemometer mast post
        drawLine(
            color = tint.copy(alpha = 0.85f),
            start = Offset(cx, mastTopY),
            end = Offset(cx, mastBottomY),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Base foot
        drawLine(
            color = tint.copy(alpha = 0.85f),
            start = Offset(cx - 3.5.dp.toPx(), mastBottomY),
            end = Offset(cx + 3.5.dp.toPx(), mastBottomY),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Dynamic spinning rotor cups
        withTransform({
            rotate(rotationAngle, pivot = Offset(cx, mastTopY))
        }) {
            val armLen = 8.5.dp.toPx()
            val cupR = 2.4.dp.toPx()

            // Rotor crossarm
            drawLine(
                color = tint,
                start = Offset(cx - armLen, mastTopY),
                end = Offset(cx + armLen, mastTopY),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Left cup
            drawCircle(
                color = tint,
                radius = cupR,
                center = Offset(cx - armLen, mastTopY),
                style = Stroke(width = 1.3.dp.toPx())
            )

            // Right cup
            drawCircle(
                color = tint,
                radius = cupR,
                center = Offset(cx + armLen, mastTopY),
                style = Stroke(width = 1.3.dp.toPx())
            )

            // Center bearing cap
            drawCircle(
                color = tint,
                radius = 1.8.dp.toPx(),
                center = Offset(cx, mastTopY)
            )
        }
    }
}

@Preview(name = "Wind Radar - Dark Mode", showBackground = true, backgroundColor = 0xFF0D1117)
@Composable
private fun WindVisualizerDarkPreview() {
    MaterialTheme {
        WindVisualizer(
            windSpeed = 4.2f,
            windDirection = 240f
        )
    }
}

@Preview(name = "Wind Radar - Light Mode", showBackground = true, backgroundColor = 0xFFF8FAFC)
@Composable
private fun WindVisualizerLightPreview() {
    MaterialTheme {
        WindVisualizer(
            windSpeed = 4.2f,
            windDirection = 240f
        )
    }
}

@Composable
fun WeatherVisualSummary(temp: Float, hum: Float, press: Float, lux: Float) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val CardDark = if (isDarkMode) BleSenseColors.CardDark else BleSenseColors.LightSurface
    val DividerDark = if (isDarkMode) BleSenseColors.BorderDark else BleSenseColors.LightBorder
    val TextPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary

    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = CardDark, border = BorderStroke(1.dp, DividerDark)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CloudQueue, null, tint = BlueAccent, modifier = Modifier.size(20.dp))
                Text("Station Overview", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                WeatherMetric("🌡️", "${temp.toInt()}°C", "Temp")
                WeatherMetric("💧", "${hum.toInt()}%", "Hum")
                WeatherMetric("⏲️", "${press.toInt()}hPa", "Press")
                WeatherMetric("☀️", "${lux.toInt()}lx", "Lux")
            }
        }
    }
}

@Composable
private fun WeatherMetric(icon: String, value: String, label: String) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val TextPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val TextSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 20.sp)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        Text(label, fontSize = 9.sp, color = TextSecondary)
    }
}

private fun dismissAlarm(
    showAlertDialog: () -> Unit,
    isAlarmActive: () -> Unit,
    isThresholdSet: () -> Unit,
    mediaPlayer: MediaPlayer?
) {
    showAlertDialog(); isAlarmActive(); isThresholdSet()
    try { mediaPlayer?.stop(); mediaPlayer?.prepare() }
    catch (_: IllegalStateException) { mediaPlayer?.reset() }
}

private fun parseTempLoggerRawDataIntoByteGroups(rawData: String?): List<List<String>> {
    if (rawData.isNullOrBlank()) return createEmptyGroupsWithDashes()
    try {
        val bytes  = rawData.split(" ").filter { it.isNotBlank() }.map { it.trim() }.filter { it.isNotEmpty() }
        val result = mutableListOf<List<String>>()
        for (chunk in bytes.chunked(32)) {
            if (chunk.all { it.equals("FF", ignoreCase = true) }) continue
            val hasReal = chunk.any { it != "00" && !it.equals("FF", ignoreCase = true) && it.isNotEmpty() }
            if (hasReal) {
                val padded = chunk.toMutableList().apply { while (size < 32) add("00") }
                result.add(padded.take(32))
            } else {
                result.add(List(32) { i -> if (i < chunk.size) { val b = chunk[i]; if (b.equals("FF", ignoreCase = true)) "--" else b } else "--" })
            }
            if (result.size >= 7) break
        }
        if (result.isEmpty()) return createEmptyGroupsWithDashes()
        while (result.size < 7) result.add(List(32) { "--" })
        return result.take(7)
    } catch (_: Exception) { return createEmptyGroupsWithDashes() }
}

private fun createEmptyGroupsWithDashes(): List<List<String>> = List(7) { List(32) { "--" } }

private fun extractTempHumidityFromGroup(bytes: List<String>): Pair<String, String> {
    if (bytes.size < 4 || bytes.any { it == "--" }) return "--" to "--"
    return try {
        val b1 = bytes[0].toIntOrNull(16) ?: 0; val b2 = bytes[1].toIntOrNull(16) ?: 0
        val b3 = bytes[2].toIntOrNull(16) ?: 0; val b4 = bytes[3].toIntOrNull(16) ?: 0
        val b2f = if (b2 == 0 && bytes[1] != "00") bytes[1].toIntOrNull(16) ?: 0 else b2
        val b4f = if (b4 == 0 && bytes[3] != "00") bytes[3].toIntOrNull(16) ?: 0 else b4
        "${String.format(Locale.US, "%.2f", b1 + b2f / 100.0)}°C" to "${String.format(Locale.US, "%.2f", b3 + b4f / 100.0)}%"
    } catch (_: Exception) { "--" to "--" }
}

private fun extractExactMfgData(rawData: ByteArray?, expectedLength: Int): ByteArray? {
    if (rawData == null || rawData.isEmpty()) return null
    var i = 0
    while (i < rawData.size - 1) {
        val len = rawData[i].toInt() and 0xFF
        if (len == 0) break
        if (i + 1 + len > rawData.size) break
        val type = rawData[i + 1].toInt() and 0xFF
        if (type == 0xFF) {
            val dataStart = i + 4
            if (dataStart < rawData.size) {
                val actualDataLen = len - 2
                val toCopy = minOf(actualDataLen, expectedLength)
                val result = ByteArray(expectedLength)
                System.arraycopy(rawData, dataStart, result, 0, minOf(toCopy, rawData.size - dataStart))
                return result
            }
        }
        i += len + 1
    }
    return null
}

private fun exportDataToCSV(context: Context, uri: Uri, viewModel: BluetoothScanViewModel, deviceAddress: String, deviceName: String, deviceId: String, currentSensorData: SensorData?, onComplete: () -> Unit) {
    MainScope().launch {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    val history = viewModel.getHistory(deviceAddress).toMutableList()
                    if (history.isEmpty()) {
                        val fallbackData = currentSensorData ?: viewModel.devices.value.find { it.address == deviceAddress }?.sensorData
                        fallbackData?.let {
                            history.add(HistoricalDataEntry(System.currentTimeMillis(), it))
                        }
                    }
                    if (history.isEmpty()) return@use
                    val header = StringBuilder("Timestamp,Device Name,Device Address,Node ID,")
                    when (history.first().sensorData) {
                        is SensorData.SHT40Data         -> header.append("Temperature (°C),Humidity (%)")
                        is SensorData.LIS3DHData       -> header.append("X-Axis (m/s²),Y-Axis (m/s²),Z-Axis (m/s²)")
                        is SensorData.SoilSensorData    -> header.append("Nitrogen,Phosphorus,Potassium,Moisture (%),Temperature (°C),EC (mS/cm),pH,Salinity (mg/L)")
                        is SensorData.AmmoniaSensorData -> header.append("Ammonia (ppm)")
                        is SensorData.Sen66Data         -> header.append("PM1.0,PM2.5,PM4.0,PM10,Temperature,Humidity,CO₂,VOC,NOx,Air Quality")
                        is SensorData.TempLoggerData    -> header.append("Temperature (°C),Humidity (%)")
                        is SensorData.AHT20Data         -> header.append("Temperature (°C),Humidity (%)")
                        is SensorData.BME680Data        -> header.append("Temperature (°C),Humidity (%),Pressure (hPa)")
                        is SensorData.VEML7700Data      -> header.append("Light (LUX)")
                        is SensorData.VCNL4040Data      -> header.append("Light (LUX)")
                        is SensorData.WeatherData       -> header.append("Temperature (°C),Humidity (%),Light (LUX),Pressure (hPa)")
                        is SensorData.RainData          -> header.append("Rainfall (mm)")
                        is SensorData.WindData          -> header.append("Wind Speed (m/s),Wind Direction (°)")
                        else -> {}
                    }
                    header.append(",Raw Data\n")
                    os.write(header.toString().toByteArray())
                    val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                    history.forEachIndexed { i, entry ->
                        val row = StringBuilder("${df.format(Date(entry.timestamp))},$deviceName,$deviceAddress,$deviceId,")
                        var mfgPayload: ByteArray? = null
                        when (val sd = entry.sensorData) {
                            is SensorData.SHT40Data         -> { row.append("${sd.temperature},${sd.humidity}"); mfgPayload = extractExactMfgData(entry.rawData, 8) }
                            is SensorData.LIS3DHData        -> { row.append("${sd.x},${sd.y},${sd.z}"); mfgPayload = extractExactMfgData(entry.rawData, 10) }
                            is SensorData.SoilSensorData    -> { row.append("${sd.nitrogen},${sd.phosphorus},${sd.potassium},${sd.moisture},${sd.temperature},${sd.ec},${sd.pH},${sd.salinity}"); mfgPayload = extractExactMfgData(entry.rawData, 18) }
                            is SensorData.AmmoniaSensorData -> { row.append(sd.ammonia); mfgPayload = extractExactMfgData(entry.rawData, 8) }
                            is SensorData.Sen66Data         -> { row.append("${sd.pm1},${sd.pm25},${sd.pm4},${sd.pm10},${sd.temperature},${sd.humidity},${sd.co2},${sd.voc},${sd.nox},${sd.airQualityIndex}"); mfgPayload = extractExactMfgData(entry.rawData, 21) }
                            is SensorData.TempLoggerData    -> { row.append("${sd.temperature},${sd.humidity}"); mfgPayload = entry.rawData ?: sd.rawData.split(" ").map { it.toInt(16).toByte() }.toByteArray() }
                            is SensorData.AHT20Data         -> { row.append("${sd.temperature},${sd.humidity}"); mfgPayload = extractExactMfgData(entry.rawData, 8) }
                            is SensorData.BME680Data        -> { row.append("${sd.temperature},${sd.humidity},${sd.pressure}"); mfgPayload = extractExactMfgData(entry.rawData, 9) }
                            is SensorData.VEML7700Data      -> { row.append("${sd.lux}"); mfgPayload = extractExactMfgData(entry.rawData, 8) }
                            is SensorData.VCNL4040Data      -> { row.append("${sd.lux}"); mfgPayload = extractExactMfgData(entry.rawData, 8) }
                            is SensorData.WeatherData       -> { row.append("${sd.temperature},${sd.humidity},${sd.lux},${sd.pressure}"); mfgPayload = extractExactMfgData(entry.rawData, 12) }
                            is SensorData.RainData          -> { row.append(sd.rainfall); mfgPayload = extractExactMfgData(entry.rawData, 8) }
                            is SensorData.WindData          -> { row.append("${sd.windSpeed},${sd.windDirection}"); mfgPayload = extractExactMfgData(entry.rawData, 8) }
                            else -> {}
                        }
                        val rawHex = mfgPayload?.joinToString(" ") { "%02X".format(it) } ?: ""
                        row.append(",\"$rawHex\"\n")
                        os.write(row.toString().toByteArray())
                        if (i % 100 == 0) os.flush()
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { withContext(Dispatchers.Main) { onComplete() } }
        }
    }
}

fun getCardinalDirection(angle: Float): String {
    val d = (angle % 360 + 360) % 360
    return when {
        d >= 337.5 || d < 22.5 -> "N"
        d >= 22.5 && d < 67.5 -> "NE"
        d >= 67.5 && d < 112.5 -> "E"
        d >= 112.5 && d < 157.5 -> "SE"
        d >= 157.5 && d < 202.5 -> "S"
        d >= 202.5 && d < 247.5 -> "SW"
        d >= 247.5 && d < 292.5 -> "W"
        d >= 292.5 && d < 337.5 -> "NW"
        else -> "N"
    }
}
