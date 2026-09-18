package com.blesense.app.view.screens

import android.app.Activity
import android.app.Application
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.blesense.app.model.*
import com.blesense.app.viewmodel.*
import com.blesense.app.repository.*
import com.blesense.app.util.*
import com.blesense.app.view.components.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*
import androidx.compose.ui.text.font.FontFamily.Companion.Monospace
import java.util.concurrent.ConcurrentLinkedQueue

/* -------------------------------------------------------------
   3-D helpers
   ------------------------------------------------------------- */
data class Point3D(val x: Float, val y: Float, val z: Float) {
    fun rotateX(a: Double) = Point3D(
        x,
        (y * cos(a) - z * sin(a)).toFloat(),
        (y * sin(a) + z * cos(a)).toFloat()
    )
    fun rotateY(a: Double) = Point3D(
        (x * cos(a) + z * sin(a)).toFloat(),
        y,
        (-x * sin(a) + z * cos(a)).toFloat()
    )
    fun rotateZ(a: Double) = Point3D(
        (x * cos(a) - y * sin(a)).toFloat(),
        (x * sin(a) + y * cos(a)).toFloat(),
        z
    )
}

/* -------------------------------------------------------------
   Main screen – English only
   ------------------------------------------------------------- */
@Composable
fun ChartScreen(
    navController: NavController,
    deviceAddress: String? = null,
    viewModel: BluetoothScanViewModel? = null,
    sensorType: String? = null
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as Application
    val vm: BluetoothScanViewModel = viewModel ?: run {
        val factory = remember { BluetoothScanViewModelFactory(app) }
        viewModel(factory = factory)
    }

    val dark by ThemeManager.isDarkMode.collectAsState()
    val devices by vm.devices.collectAsState()

    var liveSensorData by remember { mutableStateOf<SensorData?>(null) }

    val targetDevice = remember(devices, deviceAddress) {
        if (!deviceAddress.isNullOrBlank()) {
            devices.find { it.address.equals(deviceAddress, ignoreCase = true) }
        } else null
    }

    val mockData = remember(deviceAddress) {
        if (deviceAddress?.startsWith("AA:BB:CC:00") == true) {
            com.blesense.app.util.MockSensorUtils.getMockDataForAddress(deviceAddress)
        } else null
    }

    val sensorData = liveSensorData
        ?: targetDevice?.sensorData
        ?: mockData
        ?: if (deviceAddress.isNullOrBlank()) devices.firstOrNull { it.sensorData != null }?.sensorData else null

    val detectedType = remember(sensorData, targetDevice, sensorType, deviceAddress) {
        when {
            !sensorType.isNullOrBlank() && sensorType != "Generic" -> sensorType
            sensorData is SensorData.SHT40Data -> "SHT40"
            sensorData is SensorData.STS30Data -> "STS30"
            sensorData is SensorData.STTS751Data -> "STTS751"
            sensorData is SensorData.ATRHData -> "ATRH"
            sensorData is SensorData.RainData -> "Rain"
            sensorData is SensorData.WindData -> "Wind"
            sensorData is SensorData.LIS3DHData -> "LIS3DH"
            sensorData is SensorData.SoilSensorData -> "Soil"
            sensorData is SensorData.AmmoniaSensorData -> "Ammonia"
            sensorData is SensorData.VEML7700Data -> "VEML7700"
            sensorData is SensorData.VCNL4040Data -> "VCNL4040"
            sensorData is SensorData.AHT20Data -> "AHT20"
            sensorData is SensorData.BME680Data -> "BME680"
            sensorData is SensorData.TempLoggerData -> "TempLogger"
            sensorData is SensorData.Sen66Data -> "SEN66"
            sensorData is SensorData.DataLoggerData -> "LIS3DH"
            sensorData is SensorData.AWSData -> "AWS"
            targetDevice != null -> {
                val n = targetDevice.name
                when {
                    n.contains("SHT", true) -> "SHT40"
                    n.contains("STS30", true) -> "STS30"
                    n.contains("STTS", true) -> "STTS751"
                    n.contains("SOIL", true) -> "Soil"
                    n.contains("NH", true) || n.contains("Ammonia", true) -> "Ammonia"
                    n.contains("sen66", true) || n.contains("sen 66", true) -> "SEN66"
                    n.contains("VEML", true) -> "VEML7700"
                    n.contains("VCNL", true) -> "VCNL4040"
                    n.contains("AHT", true) -> "AHT20"
                    n.contains("BME", true) -> "BME680"
                    n.contains("TempLogger", true) || n.contains("TLOG", true) -> "TempLogger"
                    n.contains("Rain", true) -> "Rain"
                    n.contains("Wind", true) -> "Wind"
                    n.contains("ATRH", true) -> "ATRH"
                    n.contains("Activity", true) || n.contains("LIS3DH", true) || n.contains("DataLogger", true) || n.contains("DLOG", true) -> "LIS3DH"
                    n.contains("AWS", true) -> "AWS"
                    else -> "Generic"
                }
            }
            deviceAddress?.startsWith("AA:BB:CC:00") == true -> {
                when (deviceAddress) {
                    "AA:BB:CC:00:00:01" -> "SHT40"
                    "AA:BB:CC:00:00:02" -> "LIS3DH"
                    "AA:BB:CC:00:00:03" -> "Soil"
                    "AA:BB:CC:00:00:04" -> "Ammonia"
                    "AA:BB:CC:00:00:05" -> "SEN66"
                    "AA:BB:CC:00:00:06" -> "VEML7700"
                    "AA:BB:CC:00:00:07" -> "VCNL4040"
                    "AA:BB:CC:00:00:08" -> "AHT20"
                    "AA:BB:CC:00:00:09" -> "BME680"
                    "AA:BB:CC:00:00:10" -> "TempLogger"
                    "AA:BB:CC:00:00:11" -> "STS30"
                    "AA:BB:CC:00:00:12" -> "STTS751"
                    "AA:BB:CC:00:00:13" -> "ATRH"
                    "AA:BB:CC:00:00:14" -> "Rain"
                    "AA:BB:CC:00:00:15" -> "Wind"
                    else -> "Generic"
                }
            }
            else -> "Generic"
        }
    }

    // ---- raw sensor values ----
    val temp   = (sensorData as? SensorData.SHT40Data)?.temperature?.toFloatOrNull()
        ?: (sensorData as? SensorData.TempLoggerData)?.temperature?.toFloatOrNull()
        ?: (sensorData as? SensorData.Sen66Data)?.temperature?.toFloatOrNull()
        ?: (sensorData as? SensorData.SoilSensorData)?.temperature?.toFloatOrNull()
        ?: (sensorData as? SensorData.STS30Data)?.temperatureC?.toFloatOrNull()
        ?: (sensorData as? SensorData.STTS751Data)?.temperatureC?.toFloatOrNull()
        ?: (sensorData as? SensorData.ATRHData)?.temperature?.toFloatOrNull()
        ?: (sensorData as? SensorData.AHT20Data)?.temperature?.toFloatOrNull()
        ?: (sensorData as? SensorData.BME680Data)?.temperature?.toFloatOrNull()
        ?: (sensorData as? SensorData.AWSData)?.temperature?.toFloatOrNull()

    val hum    = (sensorData as? SensorData.SHT40Data)?.humidity?.toFloatOrNull()
        ?: (sensorData as? SensorData.TempLoggerData)?.humidity?.toFloatOrNull()
        ?: (sensorData as? SensorData.Sen66Data)?.humidity?.toFloatOrNull()
        ?: (sensorData as? SensorData.ATRHData)?.humidity?.toFloatOrNull()
        ?: (sensorData as? SensorData.AHT20Data)?.humidity?.toFloatOrNull()
        ?: (sensorData as? SensorData.BME680Data)?.humidity?.toFloatOrNull()
        ?: (sensorData as? SensorData.AWSData)?.humidity?.toFloatOrNull()

    val tempF  = (sensorData as? SensorData.STS30Data)?.temperatureF?.toFloatOrNull()
        ?: (sensorData as? SensorData.STTS751Data)?.temperatureF?.toFloatOrNull()

    val luxVeml = (sensorData as? SensorData.VEML7700Data)?.lux?.toFloatOrNull()
        ?: (sensorData as? SensorData.ATRHData)?.lux?.toFloatOrNull()
    val luxVcnl = (sensorData as? SensorData.VCNL4040Data)?.lux?.toFloatOrNull()

    val ahtTemp = (sensorData as? SensorData.AHT20Data)?.temperature?.toFloatOrNull()
    val ahtHum  = (sensorData as? SensorData.AHT20Data)?.humidity?.toFloatOrNull()
    val bmeTemp = (sensorData as? SensorData.BME680Data)?.temperature?.toFloatOrNull()
    val bmeHum  = (sensorData as? SensorData.BME680Data)?.humidity?.toFloatOrNull()
    val bmePres = (sensorData as? SensorData.BME680Data)?.pressure?.toFloatOrNull()
        ?: (sensorData as? SensorData.ATRHData)?.pressure?.toFloatOrNull()

    val rainfall = (sensorData as? SensorData.RainData)?.rainfall?.toFloatOrNull()
        ?: (sensorData as? SensorData.AWSData)?.rfCumulative?.toFloatOrNull()
    val windSpd  = (sensorData as? SensorData.WindData)?.windSpeed?.toFloatOrNull()
        ?: (sensorData as? SensorData.AWSData)?.windSpeed?.toFloatOrNull()
    val windDir  = (sensorData as? SensorData.WindData)?.windDirection?.toFloatOrNull()
        ?: (sensorData as? SensorData.AWSData)?.windDirection?.toFloatOrNull()

    val ammoniaVal = (sensorData as? SensorData.AmmoniaSensorData)?.ammonia
        ?.replace(" ppm", "")?.trim()?.toFloatOrNull()

    val sen66Data = sensorData as? SensorData.Sen66Data
    val pm1Val  = sen66Data?.pm1?.toFloatOrNull()
    val pm25Val = sen66Data?.pm25?.toFloatOrNull()
    val pm4Val  = sen66Data?.pm4?.toFloatOrNull()
    val pm10Val = sen66Data?.pm10?.toFloatOrNull()
    val co2Val  = sen66Data?.co2?.toFloatOrNull()
    val vocVal  = sen66Data?.voc?.toFloatOrNull()
    val noxVal  = sen66Data?.nox?.toFloatOrNull()

    val dataLoggerPoints = (sensorData as? SensorData.DataLoggerData)?.getParsedPoints()
    val lastDLPoint = dataLoggerPoints?.lastOrNull()

    val accX   = (sensorData as? SensorData.LIS3DHData)?.x?.toFloatOrNull()
        ?: lastDLPoint?.let { it.first.toByte().toFloat() / 6.4f }
    val accY   = (sensorData as? SensorData.LIS3DHData)?.y?.toFloatOrNull()
        ?: lastDLPoint?.let { it.second.toByte().toFloat() / 6.4f }
    val accZ   = (sensorData as? SensorData.LIS3DHData)?.z?.toFloatOrNull()
        ?: lastDLPoint?.let { it.third.toByte().toFloat() / 6.4f }

    val soilM  = (sensorData as? SensorData.SoilSensorData)?.moisture?.toFloatOrNull()
    val soilT  = (sensorData as? SensorData.SoilSensorData)?.temperature?.toFloatOrNull()
    val soilN  = (sensorData as? SensorData.SoilSensorData)?.nitrogen?.toFloatOrNull()
    val soilP  = (sensorData as? SensorData.SoilSensorData)?.phosphorus?.toFloatOrNull()
    val soilK  = (sensorData as? SensorData.SoilSensorData)?.potassium?.toFloatOrNull()
    val soilEC = (sensorData as? SensorData.SoilSensorData)?.ec?.toFloatOrNull()
    val soilPH = (sensorData as? SensorData.SoilSensorData)?.pH?.toFloatOrNull()

    var liveX by remember(deviceAddress) { mutableStateOf(0f) }
    var liveY by remember(deviceAddress) { mutableStateOf(0f) }
    var liveZ by remember(deviceAddress) { mutableStateOf(0f) }

    LaunchedEffect(accX, accY, accZ) {
        if (liveX == 0f) liveX = accX ?: 0f
        if (liveY == 0f) liveY = accY ?: 0f
        if (liveZ == 0f) liveZ = accZ ?: 0f
    }

    // ---- history buffers ----
    val tempH       = remember(deviceAddress) { mutableStateListOf<Float>() }
    val tempFH      = remember(deviceAddress) { mutableStateListOf<Float>() }
    val humH        = remember(deviceAddress) { mutableStateListOf<Float>() }
    val vemlLuxH    = remember(deviceAddress) { mutableStateListOf<Float>() }
    val vcnlLuxH    = remember(deviceAddress) { mutableStateListOf<Float>() }
    val ahtTempH    = remember(deviceAddress) { mutableStateListOf<Float>() }
    val ahtHumH     = remember(deviceAddress) { mutableStateListOf<Float>() }
    val bmeTempH    = remember(deviceAddress) { mutableStateListOf<Float>() }
    val bmeHumH     = remember(deviceAddress) { mutableStateListOf<Float>() }
    val bmePresH    = remember(deviceAddress) { mutableStateListOf<Float>() }
    val rainfallH   = remember(deviceAddress) { mutableStateListOf<Float>() }
    val windSpdH    = remember(deviceAddress) { mutableStateListOf<Float>() }
    val ammoniaH    = remember(deviceAddress) { mutableStateListOf<Float>() }
    val sen66Pm1H   = remember(deviceAddress) { mutableStateListOf<Float>() }
    val sen66Pm25H  = remember(deviceAddress) { mutableStateListOf<Float>() }
    val sen66Pm4H   = remember(deviceAddress) { mutableStateListOf<Float>() }
    val sen66Pm10H  = remember(deviceAddress) { mutableStateListOf<Float>() }
    val sen66Co2H   = remember(deviceAddress) { mutableStateListOf<Float>() }
    val sen66VocH   = remember(deviceAddress) { mutableStateListOf<Float>() }
    val sen66NoxH   = remember(deviceAddress) { mutableStateListOf<Float>() }
    val accXH       = remember(deviceAddress) { mutableStateListOf<Float>() }
    val accYH       = remember(deviceAddress) { mutableStateListOf<Float>() }
    val accZH       = remember(deviceAddress) { mutableStateListOf<Float>() }
    val soilMH      = remember(deviceAddress) { mutableStateListOf<Float>() }
    val soilTH      = remember(deviceAddress) { mutableStateListOf<Float>() }
    val soilNH      = remember(deviceAddress) { mutableStateListOf<Float>() }
    val soilPHist   = remember(deviceAddress) { mutableStateListOf<Float>() }
    val soilKHist   = remember(deviceAddress) { mutableStateListOf<Float>() }
    val soilECHist  = remember(deviceAddress) { mutableStateListOf<Float>() }
    val soilPHHist  = remember(deviceAddress) { mutableStateListOf<Float>() }
    val timestamps  = remember(deviceAddress) { mutableStateListOf<String>() }
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val titleGraphs            = "Graphs"
    val tempLabel              = "Temperature (°C)"
    val tempFLabel             = "Temperature (°F)"
    val humLabel               = "Humidity (%)"
    val luxLabel               = "Light Intensity (LUX)"
    val pressLabel             = "Pressure (hPa)"
    val rainLabel              = "Rainfall (mm)"
    val windSpdLabel           = "Wind Speed (m/s)"
    val xLabel                 = "X Axis (g)"
    val yLabel                 = "Y Axis (g)"
    val zLabel                 = "Z Axis (g)"
    val soilMoistLabel         = "Soil Moisture (%)"
    val soilTempLabel          = "Soil Temperature (°C)"
    val soilNLabel             = "Soil Nitrogen (ppm)"
    val soilPLabel             = "Soil Phosphorus (ppm)"
    val soilKLabel             = "Soil Potassium (ppm)"
    val soilECLabel            = "Soil EC (µS/cm)"
    val soilPHLabel            = "Soil pH"
    val currentTxt             = "Current"
    val naTxt                  = "N/A"
    val tabGraphs              = "Graphs"
    val tabSoilTable           = "Soil Data Table"

    val bgGrad = if (dark) Brush.verticalGradient(listOf(Color(0xFF121212), Color(0xFF424242)))
                 else Brush.verticalGradient(listOf(Color(0xFF0A74DA), Color(0xFFADD8E6)))
    val cardBg   = if (dark) Color(0xFF1E1E1E) else Color.White
    val txt      = if (dark) Color.White else Color.Black
    val txt2     = if (dark) Color(0xFFFFFFFF) else Color(0xFF2A2626)
    val accent   = if (dark) Color(0xFFBB86FC) else Color(0xFF0A74DA)
    val tabBg    = if (dark) Color(0xFF2A2A2A) else Color.Transparent
    val appBarBg = if (dark) Color(0xFF121212) else Color.White

    val receiving   = remember { mutableStateOf(false) }
    val hasSoil     = remember { mutableStateOf(false) }
    var tabIdx      by remember { mutableStateOf(0) }
    val tabs        = listOf(tabGraphs, tabSoilTable)
    val flowState   = remember { mutableStateOf("Waiting for BLE broadcast...") }

    LaunchedEffect(Unit) {
        val act = ctx as? Activity
        if (act != null) {
            vm.startScan(act)
            vm.startContinuousScan(act)
        }
    }

    val updateQueue = remember(deviceAddress) { ConcurrentLinkedQueue<SensorData>() }

    // Sync liveSensorData from targetDevice (device list flow) as dual-channel fallback
    LaunchedEffect(targetDevice?.sensorData) {
        targetDevice?.sensorData?.let { devData ->
            if (!deviceAddress.isNullOrBlank() && !deviceAddress.startsWith("AA:BB:CC:00")) {
                liveSensorData = devData
                updateQueue.add(devData)
            }
        }
    }

    LaunchedEffect(sensorData, detectedType, tempH.size, ammoniaH.size, rainfallH.size, windSpdH.size, accXH.size, vemlLuxH.size, vcnlLuxH.size, soilMH.size) {
        receiving.value = sensorData != null || 
            tempH.isNotEmpty() || humH.isNotEmpty() || tempFH.isNotEmpty() || 
            ammoniaH.isNotEmpty() || rainfallH.isNotEmpty() || windSpdH.isNotEmpty() || 
            accXH.isNotEmpty() || vemlLuxH.isNotEmpty() || vcnlLuxH.isNotEmpty() || 
            soilMH.isNotEmpty() || sen66Pm25H.isNotEmpty()
        hasSoil.value = detectedType == "Soil" || sensorData is SensorData.SoilSensorData || soilM != null || soilT != null || soilN != null
    }

    LaunchedEffect(deviceAddress) {
        if (deviceAddress.isNullOrBlank()) return@LaunchedEffect

        // 1. Initial State / History Loading
        if (deviceAddress.startsWith("AA:BB:CC:00")) {
            val mock = MockSensorUtils.getMockDataForAddress(deviceAddress)
            if (mock != null) {
                val updateMock = {
                    when (mock) {
                        is SensorData.SHT40Data -> {
                            updateHistory(tempH, mock.temperature.toFloat() + (Random().nextFloat() - 0.5f) * 2)
                            updateHistory(humH, mock.humidity.toFloat() + (Random().nextFloat() - 0.5f) * 5)
                        }
                        is SensorData.LIS3DHData -> {
                            val vx = mock.x.toFloat() + (Random().nextFloat() - 0.5f) * 0.5f
                            val vy = mock.y.toFloat() + (Random().nextFloat() - 0.5f) * 0.5f
                            val vz = mock.z.toFloat() + (Random().nextFloat() - 0.5f) * 0.5f
                            liveX = vx; liveY = vy; liveZ = vz
                            updateHistory(accXH, vx); updateHistory(accYH, vy); updateHistory(accZH, vz)
                        }
                        is SensorData.SoilSensorData -> {
                            if (timestamps.size >= 20) timestamps.removeAt(0)
                            timestamps.add(fmt.format(Date()))
                            updateHistory(soilMH, mock.moisture.toFloat() + (Random().nextFloat() - 0.5f) * 2)
                            updateHistory(soilTH, mock.temperature.toFloat() + (Random().nextFloat() - 0.5f) * 1)
                            updateHistory(soilNH, mock.nitrogen.toFloat() + (Random().nextFloat() - 0.5f) * 5)
                            updateHistory(soilPHist, mock.phosphorus.toFloat() + (Random().nextFloat() - 0.5f) * 5)
                            updateHistory(soilKHist, mock.potassium.toFloat() + (Random().nextFloat() - 0.5f) * 5)
                            updateHistory(soilECHist, mock.ec.toFloat() + (Random().nextFloat() - 0.5f) * 10)
                            updateHistory(soilPHHist, mock.pH.toFloat() + (Random().nextFloat() - 0.5f) * 0.2f)
                        }
                        is SensorData.AmmoniaSensorData -> {
                            val baseVal = mock.ammonia.replace(" ppm", "").trim().toFloatOrNull() ?: 12.5f
                            updateHistory(ammoniaH, baseVal + (Random().nextFloat() - 0.5f) * 1.5f)
                        }
                        is SensorData.Sen66Data -> {
                            updateHistory(tempH, mock.temperature.toFloat() + (Random().nextFloat() - 0.5f) * 2)
                            updateHistory(humH, mock.humidity.toFloat() + (Random().nextFloat() - 0.5f) * 5)
                            updateHistory(sen66Pm1H, mock.pm1.toFloat() + (Random().nextFloat() - 0.5f) * 0.5f)
                            updateHistory(sen66Pm25H, mock.pm25.toFloat() + (Random().nextFloat() - 0.5f) * 1f)
                            updateHistory(sen66Pm4H, mock.pm4.toFloat() + (Random().nextFloat() - 0.5f) * 1.5f)
                            updateHistory(sen66Pm10H, mock.pm10.toFloat() + (Random().nextFloat() - 0.5f) * 2f)
                            updateHistory(sen66Co2H, mock.co2.toFloat() + (Random().nextFloat() - 0.5f) * 20f)
                            updateHistory(sen66VocH, mock.voc.toFloat() + (Random().nextFloat() - 0.5f) * 10f)
                            updateHistory(sen66NoxH, mock.nox.toFloat() + (Random().nextFloat() - 0.5f) * 2f)
                        }
                        is SensorData.VEML7700Data -> updateHistory(vemlLuxH, mock.lux.toFloat() + (Random().nextFloat() - 0.5f) * 100)
                        is SensorData.VCNL4040Data -> updateHistory(vcnlLuxH, mock.lux.toFloat() + (Random().nextFloat() - 0.5f) * 100)
                        is SensorData.AHT20Data -> {
                            updateHistory(ahtTempH, mock.temperature.toFloat() + (Random().nextFloat() - 0.5f) * 2)
                            updateHistory(ahtHumH, mock.humidity.toFloat() + (Random().nextFloat() - 0.5f) * 5)
                        }
                        is SensorData.BME680Data -> {
                            updateHistory(bmeTempH, mock.temperature.toFloat() + (Random().nextFloat() - 0.5f) * 2)
                            updateHistory(bmeHumH, mock.humidity.toFloat() + (Random().nextFloat() - 0.5f) * 5)
                            updateHistory(bmePresH, mock.pressure.toFloat() + (Random().nextFloat() - 0.5f) * 10)
                        }
                        is SensorData.TempLoggerData -> {
                            updateHistory(tempH, mock.temperature.toFloat() + (Random().nextFloat() - 0.5f) * 2)
                            updateHistory(humH, mock.humidity.toFloat() + (Random().nextFloat() - 0.5f) * 5)
                        }
                        is SensorData.STS30Data -> {
                            updateHistory(tempH, mock.temperatureC.toFloat() + (Random().nextFloat() - 0.5f) * 2)
                            updateHistory(tempFH, mock.temperatureF.toFloat() + (Random().nextFloat() - 0.5f) * 3.6f)
                        }
                        is SensorData.STTS751Data -> {
                            updateHistory(tempH, mock.temperatureC.toFloat() + (Random().nextFloat() - 0.5f) * 2)
                            updateHistory(tempFH, mock.temperatureF.toFloat() + (Random().nextFloat() - 0.5f) * 3.6f)
                        }
                        is SensorData.ATRHData -> {
                            updateHistory(tempH, mock.temperature.toFloat() + (Random().nextFloat() - 0.5f) * 2)
                            updateHistory(humH, mock.humidity.toFloat() + (Random().nextFloat() - 0.5f) * 5)
                            updateHistory(vemlLuxH, mock.lux.toFloat() + (Random().nextFloat() - 0.5f) * 100)
                            updateHistory(bmePresH, mock.pressure.toFloat() + (Random().nextFloat() - 0.5f) * 10)
                        }
                        is SensorData.RainData -> updateHistory(rainfallH, mock.rainfall.toFloat() + (Random().nextFloat() * 0.5f))
                        is SensorData.WindData -> updateHistory(windSpdH, mock.windSpeed.toFloat() + (Random().nextFloat() - 0.5f) * 2)
                        else -> {}
                    }
                }
                repeat(20) { updateMock() }
                launch { while (isActive) { delay(2000); updateMock() } }
            }
        } else {
            val existingHistory = vm.getHistory(deviceAddress)
            if (existingHistory.isNotEmpty()) {
                existingHistory.forEach { entry ->
                    when (val data = entry.sensorData) {
                        is SensorData.SHT40Data -> { data.temperature.toFloatOrNull()?.let { updateHistory(tempH, it) }; data.humidity.toFloatOrNull()?.let { updateHistory(humH, it) } }
                        is SensorData.STS30Data -> { data.temperatureC.toFloatOrNull()?.let { updateHistory(tempH, it) }; data.temperatureF.toFloatOrNull()?.let { updateHistory(tempFH, it) } }
                        is SensorData.STTS751Data -> { data.temperatureC.toFloatOrNull()?.let { updateHistory(tempH, it) }; data.temperatureF.toFloatOrNull()?.let { updateHistory(tempFH, it) } }
                        is SensorData.ATRHData -> { data.temperature.toFloatOrNull()?.let { updateHistory(tempH, it) }; data.humidity.toFloatOrNull()?.let { updateHistory(humH, it) }; data.lux.toFloatOrNull()?.let { updateHistory(vemlLuxH, it) }; data.pressure.toFloatOrNull()?.let { updateHistory(bmePresH, it) } }
                        is SensorData.RainData -> data.rainfall.toFloatOrNull()?.let { updateHistory(rainfallH, it) }
                        is SensorData.WindData -> data.windSpeed.toFloatOrNull()?.let { updateHistory(windSpdH, it) }
                        is SensorData.AmmoniaSensorData -> data.ammonia.replace(" ppm", "").trim().toFloatOrNull()?.let { updateHistory(ammoniaH, it) }
                        is SensorData.LIS3DHData -> { 
                            val xV = data.x.toFloatOrNull() ?: 0f; val yV = data.y.toFloatOrNull() ?: 0f; val zV = data.z.toFloatOrNull() ?: 0f
                            liveX = xV; liveY = yV; liveZ = zV
                            updateHistory(accXH, xV); updateHistory(accYH, yV); updateHistory(accZH, zV) 
                        }
                        is SensorData.Sen66Data -> { 
                            data.temperature.toFloatOrNull()?.let { updateHistory(tempH, it) }
                            data.humidity.toFloatOrNull()?.let { updateHistory(humH, it) }
                            data.pm1.toFloatOrNull()?.let { updateHistory(sen66Pm1H, it) }
                            data.pm25.toFloatOrNull()?.let { updateHistory(sen66Pm25H, it) }
                            data.pm4.toFloatOrNull()?.let { updateHistory(sen66Pm4H, it) }
                            data.pm10.toFloatOrNull()?.let { updateHistory(sen66Pm10H, it) }
                            data.co2.toFloatOrNull()?.let { updateHistory(sen66Co2H, it) }
                            data.voc.toFloatOrNull()?.let { updateHistory(sen66VocH, it) }
                            data.nox.toFloatOrNull()?.let { updateHistory(sen66NoxH, it) }
                        }
                        is SensorData.TempLoggerData -> { data.temperature.toFloatOrNull()?.let { updateHistory(tempH, it) }; data.humidity.toFloatOrNull()?.let { updateHistory(humH, it) } }
                        is SensorData.VEML7700Data -> data.lux.toFloatOrNull()?.let { updateHistory(vemlLuxH, it) }
                        is SensorData.VCNL4040Data -> data.lux.toFloatOrNull()?.let { updateHistory(vcnlLuxH, it) }
                        is SensorData.AHT20Data -> { data.temperature.toFloatOrNull()?.let { updateHistory(ahtTempH, it) }; data.humidity.toFloatOrNull()?.let { updateHistory(ahtHumH, it) } }
                        is SensorData.BME680Data -> { data.temperature.toFloatOrNull()?.let { updateHistory(bmeTempH, it) }; data.humidity.toFloatOrNull()?.let { updateHistory(bmeHumH, it) }; data.pressure.toFloatOrNull()?.let { updateHistory(bmePresH, it) } }
                        is SensorData.SoilSensorData -> { data.moisture.toFloatOrNull()?.let { updateHistory(soilMH, it) }; data.temperature.toFloatOrNull()?.let { updateHistory(soilTH, it) }; data.nitrogen.toFloatOrNull()?.let { updateHistory(soilNH, it) }; data.phosphorus.toFloatOrNull()?.let { updateHistory(soilPHist, it) }; data.potassium.toFloatOrNull()?.let { updateHistory(soilKHist, it) }; data.ec.toFloatOrNull()?.let { updateHistory(soilECHist, it) }; data.pH.toFloatOrNull()?.let { updateHistory(soilPHHist, it) }; timestamps.add(fmt.format(Date(entry.timestamp))) }
                        is SensorData.AWSData -> {
                            data.temperature.toFloatOrNull()?.let { updateHistory(tempH, it) }
                            data.humidity.toFloatOrNull()?.let { updateHistory(humH, it) }
                            data.windSpeed.toFloatOrNull()?.let { updateHistory(windSpdH, it) }
                            data.rfCumulative.toFloatOrNull()?.let { updateHistory(rainfallH, it) }
                        }
                        else -> {}
                    }
                }
            }
        }

        // 2. Real-time Pipeline flusher
        launch {
            while (isActive) {
                delay(60) // 16Hz UI Refresh for smooth movement
                val batch = mutableListOf<SensorData>()
                var p = updateQueue.poll()
                while (p != null) {
                    batch.add(p)
                    p = updateQueue.poll()
                }

                if (batch.isNotEmpty()) {
                    flowState.value = "Data received at: ${SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())}"
                    
                    var lastData: SensorData? = null

                    batch.forEach { data ->
                        lastData = data
                        when (data) {
                            is SensorData.LIS3DHData -> {
                                val xVal = data.x.toFloatOrNull() ?: 0f
                                val yVal = data.y.toFloatOrNull() ?: 0f
                                val zVal = data.z.toFloatOrNull() ?: 0f
                                liveX = xVal
                                liveY = yVal
                                liveZ = zVal
                                updateHistory(accXH, xVal)
                                updateHistory(accYH, yVal)
                                updateHistory(accZH, zVal)
                            }
                            is SensorData.SHT40Data -> { updateHistory(tempH, data.temperature.toFloatOrNull()?:0f); updateHistory(humH, data.humidity.toFloatOrNull()?:0f) }
                            is SensorData.STS30Data -> { updateHistory(tempH, data.temperatureC.toFloatOrNull()?:0f); updateHistory(tempFH, data.temperatureF.toFloatOrNull()?:0f) }
                            is SensorData.STTS751Data -> { updateHistory(tempH, data.temperatureC.toFloatOrNull()?:0f); updateHistory(tempFH, data.temperatureF.toFloatOrNull()?:0f) }
                            is SensorData.ATRHData -> { updateHistory(tempH, data.temperature.toFloatOrNull()?:0f); updateHistory(humH, data.humidity.toFloatOrNull()?:0f); updateHistory(vemlLuxH, data.lux.toFloatOrNull()?:0f); updateHistory(bmePresH, data.pressure.toFloatOrNull()?:0f) }
                            is SensorData.RainData -> data.rainfall.toFloatOrNull()?.let { updateHistory(rainfallH, it) }
                            is SensorData.WindData -> data.windSpeed.toFloatOrNull()?.let { updateHistory(windSpdH, it) }
                            is SensorData.AmmoniaSensorData -> data.ammonia.replace(" ppm", "").trim().toFloatOrNull()?.let { updateHistory(ammoniaH, it) }
                            is SensorData.SoilSensorData -> { 
                                if (timestamps.size >= 20) timestamps.removeAt(0)
                                timestamps.add(fmt.format(Date()))
                                updateHistory(soilTH, data.temperature.toFloatOrNull()?:0f); updateHistory(soilMH, data.moisture.toFloatOrNull()?:0f); updateHistory(soilNH, data.nitrogen.toFloatOrNull()?:0f); updateHistory(soilPHist, data.phosphorus.toFloatOrNull()?:0f); updateHistory(soilKHist, data.potassium.toFloatOrNull()?:0f); updateHistory(soilECHist, data.ec.toFloatOrNull()?:0f); updateHistory(soilPHHist, data.pH.toFloatOrNull()?:0f) 
                            }
                            is SensorData.VEML7700Data -> data.lux.toFloatOrNull()?.let { updateHistory(vemlLuxH, it) }
                            is SensorData.VCNL4040Data -> data.lux.toFloatOrNull()?.let { updateHistory(vcnlLuxH, it) }
                            is SensorData.AHT20Data -> { updateHistory(ahtTempH, data.temperature.toFloatOrNull()?:0f); updateHistory(ahtHumH, data.humidity.toFloatOrNull()?:0f) }
                            is SensorData.BME680Data -> { updateHistory(bmeTempH, data.temperature.toFloatOrNull()?:0f); updateHistory(bmeHumH, data.humidity.toFloatOrNull()?:0f); updateHistory(bmePresH, data.pressure.toFloatOrNull()?:0f) }
                            is SensorData.Sen66Data -> { 
                                updateHistory(tempH, data.temperature.toFloatOrNull()?:0f); updateHistory(humH, data.humidity.toFloatOrNull()?:0f)
                                data.pm1.toFloatOrNull()?.let { updateHistory(sen66Pm1H, it) }; data.pm25.toFloatOrNull()?.let { updateHistory(sen66Pm25H, it) }; data.pm4.toFloatOrNull()?.let { updateHistory(sen66Pm4H, it) }; data.pm10.toFloatOrNull()?.let { updateHistory(sen66Pm10H, it) }; data.co2.toFloatOrNull()?.let { updateHistory(sen66Co2H, it) }; data.voc.toFloatOrNull()?.let { updateHistory(sen66VocH, it) }; data.nox.toFloatOrNull()?.let { updateHistory(sen66NoxH, it) }
                            }
                            is SensorData.TempLoggerData -> { updateHistory(tempH, data.temperature.toFloatOrNull()?:0f); updateHistory(humH, data.humidity.toFloatOrNull()?:0f) }
                            is SensorData.DataLoggerData -> { 
                                val points = data.getParsedPoints()
                                if (points.isNotEmpty()) { 
                                    val last = points.last()
                                    liveX = last.first.toByte().toFloat()/6.4f; liveY = last.second.toByte().toFloat()/6.4f; liveZ = last.third.toByte().toFloat()/6.4f 
                                }
                                updateHistoryBatch(accXH, points.map { it.first.toByte().toFloat()/6.4f }); updateHistoryBatch(accYH, points.map { it.second.toByte().toFloat()/6.4f }); updateHistoryBatch(accZH, points.map { it.third.toByte().toFloat()/6.4f }) 
                            }
                            is SensorData.AWSData -> {
                                data.temperature.toFloatOrNull()?.let { updateHistory(tempH, it) }; data.humidity.toFloatOrNull()?.let { updateHistory(humH, it) }; data.windSpeed.toFloatOrNull()?.let { updateHistory(windSpdH, it) }; data.rfCumulative.toFloatOrNull()?.let { updateHistory(rainfallH, it) }
                            }
                            else -> {}
                        }
                    }

                    // Flush UI state once per batch to avoid UI saturation
                    lastData?.let { liveSensorData = it }
                }
            }
        }

        // Collection loop (matching reference F:\)
        vm.sensorDataStream.collect { data ->
            val dataAddr = when (data) {
                is SensorData.SHT40Data -> data.deviceAddress
                is SensorData.STS30Data -> data.deviceAddress
                is SensorData.STTS751Data -> data.deviceAddress
                is SensorData.LIS3DHData -> data.deviceAddress
                is SensorData.SoilSensorData -> data.deviceAddress
                is SensorData.AmmoniaSensorData -> data.deviceAddress
                is SensorData.VEML7700Data -> data.deviceAddress
                is SensorData.VCNL4040Data -> data.deviceAddress
                is SensorData.AHT20Data -> data.deviceAddress
                is SensorData.BME680Data -> data.deviceAddress
                is SensorData.ATRHData -> data.deviceAddress
                is SensorData.RainData -> data.deviceAddress
                is SensorData.WindData -> data.deviceAddress
                is SensorData.Sen66Data -> data.deviceAddress
                is SensorData.TempLoggerData -> data.deviceAddress
                is SensorData.AWSData -> data.deviceAddress
                is SensorData.DataLoggerData -> data.deviceAddress
                else -> null
            }

            val isOur = if (deviceAddress.isNullOrBlank()) true
            else if (dataAddr == null) false
            else {
                dataAddr.equals(deviceAddress, true) ||
                deviceAddress.startsWith(dataAddr, true) ||
                dataAddr.startsWith(deviceAddress, true)
            }

            if (isOur) {
                updateQueue.add(data)
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(titleGraphs, fontFamily = Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = txt)
                        if (!deviceAddress.isNullOrBlank()) {
                            Text(deviceAddress, fontSize = 11.sp, color = txt.copy(alpha = 0.6f))
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if (dark) Color.White else Color.Black) } },
                backgroundColor = appBarBg, elevation = 2.dp
            )
        },
        backgroundColor = Color.Transparent
    ) { pad ->
        Box(modifier = Modifier.fillMaxSize().background(bgGrad).padding(pad)) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (hasSoil.value) {
                    TabRow(selectedTabIndex = tabIdx, backgroundColor = tabBg, contentColor = accent) {
                        tabs.forEachIndexed { i, t -> Tab(text = { Text(t, color = txt) }, selected = tabIdx == i, onClick = { tabIdx = i }) }
                    }
                }
                if (!hasSoil.value || tabIdx == 0) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Live stream status header badge
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                elevation = 2.dp,
                                backgroundColor = if (dark) Color(0xFF1E2630) else Color(0xFFE8F1FC)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(
                                                    if (receiving.value) Color(0xFF10B981) else Color(0xFFF59E0B),
                                                    CircleShape
                                                )
                                        )
                                        Text(
                                            text = if (receiving.value) "Live Stream Active" else "Listening for BLE Broadcast...",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (dark) Color.White else Color(0xFF1E293B)
                                        )
                                    }
                                    Text(
                                        text = flowState.value.replace("Data received at: ", ""),
                                        fontSize = 11.sp,
                                        color = (if (dark) Color.White else Color(0xFF1E293B)).copy(alpha = 0.65f),
                                        fontFamily = Monospace
                                    )
                                }
                            }
                        }

                        // Specific sensor cards by detected device type
                        when (detectedType) {
                            "SHT40" -> {
                                item { SensorGraphCard(tempLabel, temp, tempH, Color(0xFFE53935), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard(humLabel, hum, humH, Color(0xFF1976D2), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            }
                            "STS30", "STTS751" -> {
                                item { SensorGraphCard(tempLabel, temp, tempH, Color(0xFFE53935), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard(tempFLabel, tempF, tempFH, Color(0xFFFF9800), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            }
                            "Ammonia" -> {
                                item { SensorGraphCard("Ammonia (NH₃) Concentration", ammoniaVal, ammoniaH, Color(0xFF00BFA5), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            }
                            "SEN66" -> {
                                item { SensorGraphCard("PM2.5 (μg/m³)", pm25Val, sen66Pm25H, Color(0xFFE53935), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard("CO₂ (ppm)", co2Val, sen66Co2H, Color(0xFF8E24AA), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard("VOC Index", vocVal, sen66VocH, Color(0xFFFF6D00), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard("NOx Index", noxVal, sen66NoxH, Color(0xFF00ACC1), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard("PM1.0 (μg/m³)", pm1Val, sen66Pm1H, Color(0xFF43A047), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard("PM4.0 (μg/m³)", pm4Val, sen66Pm4H, Color(0xFF3949AB), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard("PM10 (μg/m³)", pm10Val, sen66Pm10H, Color(0xFFD81B60), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard(tempLabel, temp, tempH, Color(0xFFE53935), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard(humLabel, hum, humH, Color(0xFF1976D2), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            }
                            "Rain" -> {
                                item { SensorGraphCard(rainLabel, rainfall, rainfallH, Color(0xFF2196F3), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            }
                            "Wind" -> {
                                item { WindDirectionVisualizer(windDir ?: 0f, windSpd ?: 0f, cardBg, txt, dark) }
                                item { SensorGraphCard(windSpdLabel, windSpd, windSpdH, Color(0xFF009688), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            }
                            "ATRH" -> {
                                item { SensorGraphCard(tempLabel, temp, tempH, Color(0xFFE53935), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard(humLabel, hum, humH, Color(0xFF1976D2), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard(luxLabel, luxVeml, vemlLuxH, Color(0xFFFFB300), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard(pressLabel, bmePres, bmePresH, Color(0xFF7B1FA2), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            }
                            "VEML7700" -> {
                                item { LuxVisualizer(luxVeml, cardBg, txt, dark) }
                                item { SensorGraphCard(luxLabel, luxVeml, vemlLuxH, Color(0xFFFFB300), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            }
                            "VCNL4040" -> {
                                item { LuxVisualizer(luxVcnl, cardBg, txt, dark) }
                                item { SensorGraphCard(luxLabel, luxVcnl, vcnlLuxH, Color(0xFFFFB300), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            }
                            "AHT20" -> {
                                item { SensorGraphCard(tempLabel, temp ?: ahtTemp, if (ahtTempH.isNotEmpty()) ahtTempH else tempH, Color(0xFFE53935), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard(humLabel, hum ?: ahtHum, if (ahtHumH.isNotEmpty()) ahtHumH else humH, Color(0xFF1976D2), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            }
                            "BME680" -> {
                                item { BME680VisualDashboard(temp ?: bmeTemp, hum ?: bmeHum, bmePres, cardBg, txt) }
                                item { SensorGraphCard(tempLabel, temp ?: bmeTemp, if (bmeTempH.isNotEmpty()) bmeTempH else tempH, Color(0xFFE53935), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard(humLabel, hum ?: bmeHum, if (bmeHumH.isNotEmpty()) bmeHumH else humH, Color(0xFF1976D2), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard(pressLabel, bmePres, bmePresH, Color(0xFF7B1FA2), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            }
                            "TempLogger" -> {
                                item { SensorGraphCard(tempLabel, temp, tempH, Color(0xFFE53935), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard(humLabel, hum, humH, Color(0xFF1976D2), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            }
                            "LIS3DH" -> {
                                item { SensorGraphCard(xLabel, liveX, accXH, Color(0xFFE91E63), cardBg, txt, txt2, currentTxt, naTxt, dark, true) }
                                item { SensorGraphCard(yLabel, liveY, accYH, Color(0xFF9C27B0), cardBg, txt, txt2, currentTxt, naTxt, dark, true) }
                                item { SensorGraphCard(zLabel, liveZ, accZH, Color(0xFF009688), cardBg, txt, txt2, currentTxt, naTxt, dark, true) }
                                item { BleNodeVisualizer(liveX, liveY, liveZ, cardBg, txt, txt2, dark) }
                                item { AccelerometerAngleDisplay(liveX, liveY, liveZ, cardBg, txt, txt2, dark) }
                            }
                            "Soil" -> {
                                            item { SensorGraphCard(soilMoistLabel, soilM, soilMH, Color(0xFF6200EA), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard(soilTempLabel, soilT, soilTH, Color(0xFFFF6D00), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard(soilNLabel, soilN, soilNH, Color(0xFF00897B), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard(soilPLabel, soilP, soilPHist, Color(0xFFC2185B), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard(soilKLabel, soilK, soilKHist, Color(0xFF7B1FA2), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard(soilECLabel, soilEC, soilECHist, Color(0xFFF57C00), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard(soilPHLabel, soilPH, soilPHHist, Color(0xFFD32F2F), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            }
                            "AWS" -> {
                                item { SensorGraphCard(tempLabel, temp, tempH, Color(0xFFE53935), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard(humLabel, hum, humH, Color(0xFF1976D2), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard(windSpdLabel, windSpd, windSpdH, Color(0xFF009688), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                item { SensorGraphCard(rainLabel, rainfall, rainfallH, Color(0xFF2196F3), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                            }
                            else -> {
                                if (temp != null || hum != null || tempH.isNotEmpty() || humH.isNotEmpty()) {
                                    item { SensorGraphCard(tempLabel, temp, tempH, Color(0xFFE53935), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                    item { SensorGraphCard(humLabel, hum, humH, Color(0xFF1976D2), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                } else {
                                    item { SensorGraphCard("Sensor Live Stream", null, emptyList(), Color(0xFF2196F3), cardBg, txt, txt2, currentTxt, naTxt, dark) }
                                }
                            }
                        }
                    }
                } else if (hasSoil.value && tabIdx == 1) {
                    SoilSensorDataTable(soilMH, soilTH, soilNH, soilPHist, soilKHist, soilECHist, soilPHHist, timestamps, receiving.value, soilMoistLabel, soilTempLabel, soilNLabel, soilPLabel, soilKLabel, soilECLabel, soilPHLabel, "Waiting for soil data...", txt, txt2, cardBg)
                }
            }
        }
    }
}

/* -------------------------------------------------------------
   Visualizers
   ------------------------------------------------------------- */
@Composable
private fun BME680VisualDashboard(temp: Float?, hum: Float?, pres: Float?, cardBg: Color, txt: Color) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val targetTemp = temp ?: 0f; val targetHum = hum ?: 0f; val targetPres = pres ?: 1013f
    val animTemp by animateFloatAsState(targetTemp, tween(500), label = "t")
    val animHum by animateFloatAsState(targetHum, tween(500), label = "h")
    val animPres by animateFloatAsState(targetPres, tween(500), label = "p")
    val wavePhase by rememberInfiniteTransition(label = "w").animateFloat(0f, 2 * PI.toFloat(), infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "ph")
    Card(modifier = Modifier.fillMaxWidth().wrapContentHeight(), elevation = 10.dp, backgroundColor = cardBg, shape = RoundedCornerShape(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(85.dp)) {
                Text("Temp", color = txt.copy(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.size(70.dp, 160.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = size.width; val h = size.height; val stemW = w*0.25f; val bulbR = w*0.35f; val bulbC = Offset(w/2, h-bulbR-10f)
                        val stemTop = 10f; val stemBottom = bulbC.y-bulbR+10f; val fullH = stemBottom-stemTop
                        val glass = if (isDarkMode) Color.White.copy(0.15f) else Color.Black.copy(0.08f)
                        drawRoundRect(glass, Offset((w-stemW)/2, stemTop), Size(stemW, stemBottom-stemTop+20f), CornerRadius(stemW/2))
                        drawCircle(glass, bulbR, bulbC)
                        val frac = ((animTemp+10f)/70f).coerceIn(0f, 1f); val fluidH = fullH*frac
                        val col = when { animTemp<15f->Color(0xFF2196F3); animTemp<28f->Color(0xFF4CAF50); animTemp<38f->Color(0xFFFFA000); else->Color(0xFFF44336) }
                        drawCircle(col, bulbR*0.75f, bulbC)
                        drawRoundRect(col, Offset((w-stemW*0.5f)/2, stemBottom-fluidH), Size(stemW*0.5f, fluidH+15f), CornerRadius(stemW/4))
                    }
                }
                Text("%.1f°C".format(targetTemp), color = txt, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(85.dp)) {
                Text("Humidity", color = txt.copy(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.size(80.dp, 160.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = size.width; val h = size.height; val gaugeH = h-40f; val topY = 20f; val bottomY = topY+gaugeH
                        val path = Path().apply { moveTo(10f, topY); lineTo(10f, bottomY-20f); quadraticTo(10f, bottomY, 30f, bottomY); lineTo(w-30f, bottomY); quadraticTo(w-10f, bottomY, w-10f, bottomY-20f); lineTo(w-10f, topY) }
                        drawPath(path, if (isDarkMode) Color.White.copy(0.1f) else Color.Black.copy(0.05f))
                        clipPath(path) {
                            val levelY = bottomY - (gaugeH * (animHum/100f).coerceIn(0f,1f))
                            val wPath = Path(); wPath.moveTo(-10f, levelY)
                            for (x in 0..(w+20).toInt() step 5) { wPath.lineTo(x.toFloat(), levelY + 6f*sin(x*0.05f + wavePhase)) }
                            wPath.lineTo(w+10f, h); wPath.lineTo(-10f, h); wPath.close(); drawPath(wPath, Color(0xFF03A9F4).copy(0.7f))
                        }
                    }
                }
                Text("%.1f%%".format(targetHum), color = txt, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(100.dp)) {
                Text("Pressure", color = txt.copy(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.size(100.dp, 160.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val cx = size.width/2; val cy = size.height/2; val r = size.width*0.42f
                        drawCircle(if (isDarkMode) Color.White.copy(0.1f) else Color.Black.copy(0.05f), r, Offset(cx, cy), style = Stroke(18f))
                        drawArc(Color(0xFF00E676), 120f, (animPres/1084f).coerceIn(0f,1f)*300f, false, Offset(cx-r, cy-r), Size(r*2, r*2), style = Stroke(18f, cap = StrokeCap.Round))
                        drawCircle(if (isDarkMode) Color.Black.copy(0.3f) else Color.White.copy(0.3f), r-15f, Offset(cx, cy))
                        val p = android.graphics.Paint().apply { color = android.graphics.Color.argb((txt.alpha*255).toInt(), (txt.red*255).toInt(), (txt.green*255).toInt(), (txt.blue*255).toInt()); textSize = 28f; textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true }
                        drawContext.canvas.nativeCanvas.drawText("%.1f".format(animPres), cx, cy + 10f, p)
                    }
                }
                Text("hPa", color = txt.copy(0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private class Particle(var x: Float = 0f, var y: Float = 0f, var speed: Float = 0f, var alpha: Float = 0f, var size: Float = 0f, var active: Boolean = false)

@Composable
private fun LuxVisualizer(lux: Float?, cardBg: Color, txt: Color, isDarkMode: Boolean) {
    val targetLux = lux ?: 0f
    var lastKnownLux by remember { mutableFloatStateOf(0f) }
    val effectiveTarget = if (abs(targetLux - lastKnownLux) > 2f) { lastKnownLux = targetLux; targetLux } else lastKnownLux
    val animLux by animateFloatAsState(effectiveTarget, spring(0.8f, Spring.StiffnessLow), label = "l")
    val pulse by rememberInfiniteTransition(label = "p").animateFloat(0.97f, 1.03f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pl")
    val rayRot by rememberInfiniteTransition(label = "r").animateFloat(0f, 360f, infiniteRepeatable(tween(120000, easing = LinearEasing)), label = "rr")
    val wavePhase by rememberInfiniteTransition(label = "w").animateFloat(0f, 2 * PI.toFloat(), infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "wp")
    val particlePool = remember { List(50) { Particle() } }; var lastFrameTime by remember { mutableLongStateOf(0L) }
    val coreColor = Color(0xFF00B4D8)
    val bgOverlay by animateColorAsState(when { animLux < 1000f -> if (isDarkMode) Color(0xFF000814) else Color(0xFFE3F2FD); animLux < 10000f -> if (isDarkMode) Color(0xFF001D3D) else Color(0xFFBBDEFB); else -> if (isDarkMode) Color(0xFF003566) else Color(0xFF90CAF9) }, tween(1000), label = "bg")
    Card(modifier = Modifier.fillMaxWidth().height(400.dp), elevation = 12.dp, backgroundColor = if (isDarkMode) Color(0xFF000814) else Color.White, shape = RoundedCornerShape(28.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f; val cy = size.height / 2f; val maxDim = minOf(size.width, size.height)
                drawRect(bgOverlay)
                val baseR = (maxDim * 0.25f + (animLux / 5000f) * maxDim * 0.1f).coerceIn(maxDim * 0.2f, maxDim * 0.45f)
                val currR = baseR * pulse; val gAlpha = (0.2f + (animLux / 50000f)).coerceIn(0.2f, 0.9f)
                drawCircle(Brush.radialGradient(0f to coreColor.copy(gAlpha), 1f to Color.Transparent, center = Offset(cx, cy), radius = currR * 2.2f), currR * 2.2f, Offset(cx, cy))
                drawCircle(Brush.radialGradient(0f to Color.White.copy(0.3f), 0.6f to coreColor.copy(0.8f), 1f to if (isDarkMode) Color(0xFF03045E) else Color(0xFFE3F2FD), center = Offset(cx, cy), radius = currR), currR, Offset(cx, cy))
                if (animLux > 10f) {
                    repeat((4 + (animLux/5000f).toInt()).coerceAtMost(10)) { i ->
                        val path = Path(); val r = currR * (0.3f + i * 0.1f); val pts = 20
                        for (p in 0..pts) { val a = (p.toFloat()/pts) * 2 * PI.toFloat(); val w = sin(a*3+wavePhase+i).toFloat()*8f; val px = cx+(r+w)*cos(a).toFloat(); val py = cy+(r+w)*sin(a).toFloat(); if (p==0) path.moveTo(px, py) else path.lineTo(px, py) }
                        path.close(); drawPath(path, coreColor.copy(0.25f), style = Stroke(1.5f))
                    }
                }
                drawCircle(Color.White.copy(0.4f * pulse), currR, Offset(cx, cy), style = Stroke(2f))
                val rayCount = when { animLux < 100f -> 0; animLux < 1000f -> 12; animLux < 10000f -> 24; else -> 48 }
                if (rayCount > 0) {
                    val rayL = (currR*0.4f+(animLux/1000f)*15f).coerceAtMost(maxDim*0.5f); val rayO = (0.1f+(animLux/60000f)).coerceIn(0.1f, 0.5f)
                    repeat(rayCount) { i ->
                        val a = Math.toRadians((i * (360f/rayCount) + rayRot).toDouble()); val start = Offset(cx+currR*cos(a).toFloat(), cy+currR*sin(a).toFloat()); val end = Offset(cx+(currR+rayL)*cos(a).toFloat(), cy+(currR+rayL)*sin(a).toFloat())
                        drawLine(Brush.linearGradient(0f to coreColor.copy(rayO), 1f to Color.Transparent, start = start, end = end), start, end, 3f, StrokeCap.Round)
                    }
                }
                val now = System.currentTimeMillis(); val dt = if (lastFrameTime == 0L) 0f else (now - lastFrameTime) / 1000f; lastFrameTime = now
                val tPart = when { animLux < 10f -> 2; animLux < 100f -> 8; animLux < 1000f -> 15; animLux < 10000f -> 25; else -> 50 }
                particlePool.forEachIndexed { i, p ->
                    if (i < tPart) {
                        if (!p.active) { p.active = true; p.x = cx - currR + (Random().nextFloat() * currR * 2f); p.y = cy - currR + (Random().nextFloat() * currR * 2f); p.speed = 30f + (Random().nextFloat() * 50f); p.size = 2f + (Random().nextFloat() * 3f); p.alpha = 0f }
                        p.y -= p.speed * dt; p.x += sin(now / 400.0 + i).toFloat() * 1.5f
                        if (p.y < cy - currR * 1.5f || p.y > cy + currR * 1.5f) { p.y = cy + currR; p.x = cx - currR + (Random().nextFloat() * currR * 2f) }
                        val d = sqrt((p.x-cx).pow(2)+(p.y-cy).pow(2)); p.alpha = (1f - (d/(currR*1.5f))).coerceIn(0f, 1f)
                        drawCircle(Color.White.copy(p.alpha * 0.7f), p.size, Offset(p.x, p.y))
                    } else p.active = false
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("AMBIENT LIGHT", color = (if (isDarkMode) Color.Cyan else Color(0xFF00B4D8)).copy(0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("%.0f".format(animLux), color = if (isDarkMode) Color.White else Color(0xFF003566), fontSize = 56.sp, fontWeight = FontWeight.Black, fontFamily = Monospace)
                    Text(" LUX", color = if (isDarkMode) Color.Cyan else Color(0xFF0077B6), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp))
                }
                Text(when { animLux<50f->"Dim Environment"; animLux<500f->"Normal Indoor"; animLux<5000f->"Bright Workspace"; else->"High Intensity" }, color = if (isDarkMode) coreColor else Color(0xFF0077B6), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun SoilDashboardVisualizer(moisture: Float, temperature: Float, isDarkMode: Boolean) {
    val mAnim by animateFloatAsState(moisture, tween(1000), label = "m")
    val tAnim by animateFloatAsState(temperature, tween(1000), label = "t")
    Card(modifier = Modifier.fillMaxWidth().wrapContentHeight(), elevation = 8.dp, backgroundColor = if (isDarkMode) Color(0xFF1B262C) else Color(0xFFF0F8FF), shape = RoundedCornerShape(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Moisture", color = if (isDarkMode) Color.LightGray else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(if (isDarkMode) Color.White.copy(0.1f) else Color.Black.copy(0.05f), style = Stroke(12f))
                        drawArc(Color(0xFF3282B8), -90f, (mAnim/100f)*360f, false, style = Stroke(12f, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.WaterDrop, null, tint = Color(0xFF3282B8), modifier = Modifier.size(24.dp))
                        Text("${moisture.toInt()}%", fontSize = 18.sp, fontWeight = FontWeight.Black, color = if (isDarkMode) Color.White else Color.Black)
                    }
                }
            }
            Box(modifier = Modifier.width(1.dp).height(80.dp).background(if (isDarkMode) Color.White.copy(0.1f) else Color.Black.copy(0.1f)))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Soil Temp", color = if (isDarkMode) Color.LightGray else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(if (isDarkMode) Color.White.copy(0.1f) else Color.Black.copy(0.05f), style = Stroke(12f))
                        drawArc(if (temperature > 25) Color(0xFFFF4B2B) else Color(0xFF00D2FF), -90f, (tAnim/50f)*360f, false, style = Stroke(12f, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Thermostat, null, tint = if (temperature > 25) Color(0xFFFF4B2B) else Color(0xFF00D2FF), modifier = Modifier.size(24.dp))
                        Text("${"%.1f".format(temperature)}°C", fontSize = 18.sp, fontWeight = FontWeight.Black, color = if (isDarkMode) Color.White else Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun WindDirectionVisualizer(
    direction: Float,
    speed: Float,
    cardBg: Color,
    txt: Color,
    isDarkMode: Boolean
) {
    val animatedDirection by animateFloatAsState(
        targetValue = direction,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "graphWindDir"
    )
    val animatedSpeed by animateFloatAsState(
        targetValue = speed,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "graphWindSpd"
    )

    val speedMult = (animatedSpeed / 8f).coerceIn(0.2f, 4.0f)
    val infiniteTransition = rememberInfiniteTransition(label = "graphWindAnim")

    val windPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween((1400 / speedMult).toInt().coerceAtLeast(120), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "graphWindPhase"
    )

    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.80f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "graphPulse"
    )

    val (condition, conditionColor) = when {
        animatedSpeed <= 1.5f  -> "CALM" to if (isDarkMode) Color(0xFF00BC7D) else Color(0xFF059669)
        animatedSpeed <= 5.5f  -> "BREEZY" to if (isDarkMode) Color(0xFF38BDF8) else Color(0xFF0284C7)
        animatedSpeed <= 10.8f -> "WINDY" to if (isDarkMode) Color(0xFFFBBF24) else Color(0xFFD97706)
        else                   -> "STORM" to if (isDarkMode) Color(0xFFFF5252) else Color(0xFFDC2626)
    }

    val fromDir = getCardinalDirection(animatedDirection)
    val kmhSpeed = animatedSpeed * 3.6f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = 6.dp,
        backgroundColor = cardBg,
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Air,
                        contentDescription = null,
                        tint = conditionColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "WIND COMPASS & RADAR",
                        color = txt.copy(0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = conditionColor.copy(alpha = if (isDarkMode) 0.2f else 0.12f)
                ) {
                    Text(
                        text = condition,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = conditionColor,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            // Main Content Row: Compass Gauge on Left, Telemetry Stats on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Attractive Precision Aeronautical Compass Dial (Image 2 Style)
                val dialSize = 168.dp
                Box(
                    modifier = Modifier.size(dialSize),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(dialSize)) {
                        val w = size.width
                        val h = size.height
                        val center = Offset(w / 2f, h / 2f)
                        val dialRadius = (size.minDimension / 2f) - 6.dp.toPx()
                        val innerRingRadius = dialRadius * 0.76f

                        // Dial Face Background (Deep slate navy in dark mode, subtle aero slate depth in light mode)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = if (isDarkMode) listOf(
                                    Color(0xFF1E2D42),
                                    Color(0xFF142032),
                                    Color(0xFF0D1522)
                                ) else listOf(
                                    Color(0xFFF8FAFC),
                                    Color(0xFFF1F5F9),
                                    Color(0xFFE2E8F0)
                                ),
                                radius = dialRadius,
                                center = center
                            ),
                            radius = dialRadius,
                            center = center
                        )

                        // Outer Cyan Bezel Rim (Image 2 Style)
                        val rimColor = if (isDarkMode) Color(0xFF00E5FF) else Color(0xFF00B4D8)
                        drawCircle(
                            color = rimColor,
                            radius = dialRadius,
                            center = center,
                            style = Stroke(width = 2.4.dp.toPx())
                        )
                        drawCircle(
                            color = rimColor.copy(alpha = 0.35f),
                            radius = dialRadius - 2.5.dp.toPx(),
                            center = center,
                            style = Stroke(width = 0.8.dp.toPx())
                        )

                        // Inner Concentric Ring
                        drawCircle(
                            color = rimColor.copy(alpha = 0.30f),
                            radius = innerRingRadius,
                            center = center,
                            style = Stroke(width = 1.0.dp.toPx())
                        )

                        // Precision Chronograph Ticks around Rim (Matching Image 2)
                        for (deg in 0 until 360 step 10) {
                            val rad = Math.toRadians(deg.toDouble())
                            val isCardinal = deg % 90 == 0
                            val isMajor = deg % 30 == 0
                            val tickLen = when {
                                isCardinal -> 9.dp.toPx()
                                isMajor    -> 5.5.dp.toPx()
                                else       -> 3.0.dp.toPx()
                            }
                            val tickColor = when {
                                isCardinal -> rimColor
                                isMajor    -> rimColor.copy(alpha = 0.70f)
                                else       -> rimColor.copy(alpha = 0.35f)
                            }
                            val strokeW = when {
                                isCardinal -> 2.2.dp.toPx()
                                isMajor    -> 1.4.dp.toPx()
                                else       -> 0.8.dp.toPx()
                            }
                            val pStart = Offset(
                                center.x + (dialRadius - tickLen) * sin(rad).toFloat(),
                                center.y - (dialRadius - tickLen) * cos(rad).toFloat()
                            )
                            val pEnd = Offset(
                                center.x + dialRadius * sin(rad).toFloat(),
                                center.y - dialRadius * cos(rad).toFloat()
                            )
                            drawLine(
                                color = tickColor,
                                start = pStart,
                                end = pEnd,
                                strokeWidth = strokeW,
                                cap = StrokeCap.Round
                            )
                        }

                        // High-Precision Aeronautical Compass Needle (Image 2 Style)
                        withTransform({
                            rotate(animatedDirection, center)
                        }) {
                            val hubRadiusPx = 8.dp.toPx()
                            val arrowReach = dialRadius - 16.dp.toPx()

                            // ── North / Heading Arrow (Bright Orange-Red / Coral) ──
                            val redTipY = center.y - arrowReach
                            val redBarbY = redTipY + 18.dp.toPx()
                            val redBarbW = 7.5.dp.toPx()
                            val redBorderColor = if (isDarkMode) Color(0xFF450A0A) else Color(0xFF7F1D1D)

                            // Red Stem Dark Border (Light mode crisp definition)
                            if (!isDarkMode) {
                                drawLine(
                                    color = redBorderColor,
                                    start = Offset(center.x, center.y - hubRadiusPx),
                                    end = Offset(center.x, redBarbY),
                                    strokeWidth = 3.8.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }

                            // Red Stem
                            drawLine(
                                color = Color(0xFFFF453A),
                                start = Offset(center.x, center.y - hubRadiusPx),
                                end = Offset(center.x, redBarbY),
                                strokeWidth = 2.4.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // Red Arrowhead
                            val redArrowPath = Path().apply {
                                moveTo(center.x, redTipY)
                                lineTo(center.x + redBarbW, redBarbY)
                                lineTo(center.x - redBarbW, redBarbY)
                                close()
                            }
                            drawPath(redArrowPath, color = Color(0xFFFF453A))
                            if (!isDarkMode) {
                                drawPath(
                                    path = redArrowPath,
                                    color = redBorderColor,
                                    style = Stroke(width = 1.4.dp.toPx(), join = StrokeJoin.Round)
                                )
                            }

                            // ── South / Tail Arrow (Image 2 Outlined White / Silver Needle - Clearly Visible in Light & Dark Mode) ──
                            val tailBorderColor = if (isDarkMode) Color(0xFF0F172A) else Color(0xFF1E293B)
                            val tailFillColor = if (isDarkMode) Color(0xFFF8FAFC) else Color(0xFFFFFFFF)

                            val whiteTipY = center.y + arrowReach
                            val whiteBarbY = whiteTipY - 18.dp.toPx()
                            val whiteBarbW = 7.5.dp.toPx()

                            // Stem Outer Border (Guarantees razor-sharp visibility on light mode)
                            drawLine(
                                color = tailBorderColor,
                                start = Offset(center.x, center.y + hubRadiusPx),
                                end = Offset(center.x, whiteBarbY),
                                strokeWidth = 4.0.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // Stem Inner White Core
                            drawLine(
                                color = tailFillColor,
                                start = Offset(center.x, center.y + hubRadiusPx),
                                end = Offset(center.x, whiteBarbY),
                                strokeWidth = 2.2.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // White Arrowhead
                            val whiteArrowPath = Path().apply {
                                moveTo(center.x, whiteTipY)
                                lineTo(center.x + whiteBarbW, whiteBarbY)
                                lineTo(center.x - whiteBarbW, whiteBarbY)
                                close()
                            }
                            // 1. Pure White Fill
                            drawPath(whiteArrowPath, color = tailFillColor)
                            // 2. Crisp Dark Slate Outline (Matches Image 2 outlined arrow & visible on any background)
                            drawPath(
                                path = whiteArrowPath,
                                color = tailBorderColor,
                                style = Stroke(width = 2.0.dp.toPx(), join = StrokeJoin.Round)
                            )

                            // ── Center Bullseye Pivot (◎ as seen in Image 2) ──
                            drawCircle(
                                color = rimColor,
                                radius = 6.5.dp.toPx(),
                                center = center,
                                style = Stroke(width = 2.2.dp.toPx())
                            )
                            drawCircle(
                                color = if (isDarkMode) Color(0xFF142032) else Color(0xFFF1F5F9),
                                radius = 5.dp.toPx(),
                                center = center
                            )
                            drawCircle(
                                color = rimColor,
                                radius = 2.0.dp.toPx(),
                                center = center
                            )
                        }
                    }

                    // 4 Cardinal Markers (N in distinctive box, E, S, W as seen in Image 2)
                    val cardinalRadius = 55.dp

                    // N (with small blue/cyan rectangular badge as in Image 2)
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = -cardinalRadius)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = Color(0xFF00E5FF).copy(alpha = 0.20f),
                            border = BorderStroke(0.8.dp, Color(0xFF00E5FF))
                        ) {
                            Text(
                                text = "N",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFF453A),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp)
                            )
                        }
                    }

                    // S
                    Text(
                        text = "S",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) Color(0xFFE2E8F0) else Color(0xFF0F172A),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = cardinalRadius)
                    )

                    // E
                    Text(
                        text = "E",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) Color(0xFFE2E8F0) else Color(0xFF0F172A),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = cardinalRadius)
                    )

                    // W
                    Text(
                        text = "W",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) Color(0xFFE2E8F0) else Color(0xFF0F172A),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = -cardinalRadius)
                    )
                }

                // 2. Right Side Telemetry Stats
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Velocity Readout
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format(Locale.US, "%.1f", animatedSpeed),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            color = txt,
                            lineHeight = 34.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "m/s",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = conditionColor,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    // Secondary Speed in km/h
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = txt.copy(0.6f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.1f", kmhSpeed)} km/h",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = txt.copy(0.6f)
                        )
                    }

                    // Direction / Compass Heading Card
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDarkMode) Color(0xFF131F33) else Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFFFF453A), CircleShape)
                            )
                            Column {
                                Text(
                                    text = "WIND DIRECTION",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = txt.copy(alpha = 0.6f),
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "${direction.toInt()}° $fromDir",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isDarkMode) Color.White else Color(0xFF0F172A)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun updateHistory(list: MutableList<Float>, v: Float) {
    if (list.size >= 100) list.removeAt(0)
    list.add(v)
}

private fun updateHistoryBatch(list: MutableList<Float>, values: List<Float>) {
    if (values.isEmpty()) return
    val total = list.size + values.size
    if (total > 100) { repeat((total-100).coerceAtMost(list.size)) { list.removeAt(0) } }
    list.addAll(if (values.size > 100) values.takeLast(100) else values)
}

@Composable
fun SensorGraphCard(title: String, cur: Float?, hist: List<Float>, lineCol: Color, cardBg: Color, txtCol: Color, txt2Col: Color, curLabel: String, naLabel: String, dark: Boolean, isAccelerometer: Boolean = false) {
    var tapPos by remember { mutableStateOf<Offset?>(null) }
    var tapVal by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(tapPos) { if (tapPos != null) { delay(1500); tapPos = null; tapVal = null } }
    Card(modifier = Modifier.fillMaxWidth().height(250.dp), elevation = 4.dp, shape = RoundedCornerShape(16.dp), backgroundColor = cardBg) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = txtCol, textAlign = TextAlign.Center)
            Text("$curLabel: ${cur?.let { "%.2f".format(it) } ?: naLabel}", fontSize = 14.sp, color = txt2Col.copy(alpha = 0.8f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { tapPos = it } }) {
                    val lm = 50f; val rm = 16f; val tm = 20f; val bm = 32f
                    val w = size.width - lm - rm; val h = size.height - tm - bm
                    val sx = lm; val sy = tm; val ex = sx + w; val ey = sy + h
                    
                    val yMin: Float
                    val yMax: Float
                    if (isAccelerometer) { 
                        yMin = -20f; yMax = 20f 
                    } else if (hist.isNotEmpty()) { 
                        val dMin = hist.minOrNull() ?: 0f
                        val dMax = hist.maxOrNull() ?: 100f
                        val rng = dMax - dMin
                        val pad = if (rng == 0f) 10f else rng * 0.15f
                        yMin = dMin - pad; yMax = dMax + pad 
                    } else {
                        yMin = 0f; yMax = 100f
                    }
                    val yRng = if (yMax - yMin == 0f) 1f else yMax - yMin
                    val stepX = w / 99f

                    drawRect(if (dark) Color(0x0AFFFFFF) else Color(0x0A000000), Offset(sx, sy), Size(w, h))
                    val grid = txt2Col.copy(0.12f)
                    val lCount = if (isAccelerometer) 8 else 4
                    val lStep = yRng / lCount.toFloat()
                    for (i in 0..lCount) { 
                        val v = yMin + i * lStep
                        val y = sy + h * (1 - (v - yMin) / yRng)
                        drawLine(grid, Offset(sx, y), Offset(ex, y), 0.8f) 
                    }
                    for (i in 0..100 step 10) { 
                        val x = sx + i * stepX
                        drawLine(grid, Offset(x, sy), Offset(x, ey), 0.8f) 
                    }
                    if (0f in yMin..yMax) { 
                        val zY = sy + h * (1 - (0f - yMin) / yRng)
                        drawLine(txt2Col.copy(0.5f), Offset(sx, zY), Offset(ex, zY), 2f) 
                    }
                    drawRect(txt2Col.copy(0.35f), Offset(sx, sy), Size(w, h), style = Stroke(1.2f))
                    
                    val paint = android.graphics.Paint().apply { 
                        color = android.graphics.Color.argb((txt2Col.alpha*255).toInt(), (txt2Col.red*255).toInt(), (txt2Col.green*255).toInt(), (txt2Col.blue*255).toInt())
                        textSize = 18f; textAlign = android.graphics.Paint.Align.RIGHT 
                    }
                    for (i in 0..lCount) { 
                        val v = yMin + i * lStep
                        val y = sy + h * (1 - (v - yMin) / yRng)
                        drawContext.canvas.nativeCanvas.drawText("%.0f".format(v), sx - 8f, y + 6f, paint) 
                    }
                    val xPaint = android.graphics.Paint().apply { 
                        color = android.graphics.Color.argb((txt2Col.alpha*255).toInt(), (txt2Col.red*255).toInt(), (txt2Col.green*255).toInt(), (txt2Col.blue*255).toInt())
                        textSize = 16f; textAlign = android.graphics.Paint.Align.CENTER 
                    }
                    listOf(0, 25, 50, 75, 100).forEach { idx -> 
                        val x = sx + (idx.toFloat()/100f)*w
                        drawContext.canvas.nativeCanvas.drawText(idx.toString(), x, ey + 18f, xPaint) 
                    }

                    if (hist.isNotEmpty()) {
                        val path = Path().apply { 
                            moveTo(sx, sy + h * (1 - (hist[0] - yMin) / yRng))
                            for (i in 1 until hist.size) lineTo(sx + i * stepX, sy + h * (1 - (hist[i] - yMin) / yRng)) 
                        }
                        drawPath(path, lineCol, style = Stroke(3.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawCircle(lineCol, 5.5f, Offset(sx + (hist.size-1) * stepX, sy + h * (1 - (hist.last() - yMin) / yRng)))
                        tapPos?.let { tp ->
                            val idx = ((tp.x - lm) / (w / (hist.size.coerceAtLeast(2) - 1).toFloat())).toInt().coerceIn(0, hist.size - 1)
                            val tx = sx + idx * stepX
                            val ty = sy + h * (1 - (hist[idx] - yMin) / yRng)
                            tapVal = hist[idx]
                            drawLine(lineCol.copy(0.6f), Offset(tx, sy), Offset(tx, ey), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                            drawCircle(lineCol, 8f, Offset(tx, ty))
                            val tPaint = android.graphics.Paint().apply { 
                                color = android.graphics.Color.argb((lineCol.alpha*255).toInt(), (lineCol.red*255).toInt(), (lineCol.green*255).toInt(), (lineCol.blue*255).toInt())
                                textSize = 23f; textAlign = android.graphics.Paint.Align.CENTER; setShadowLayer(5f, 2f, 2f, android.graphics.Color.BLACK) 
                            }
                            drawContext.canvas.nativeCanvas.drawText("%.2f".format(hist[idx]), tx, ty - 22f, tPaint)
                        }
                    } else {
                        val midY = sy + h * 0.5f
                        drawLine(lineCol.copy(0.35f), Offset(sx, midY), Offset(ex, midY), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                    }
                }
                if (hist.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(if (dark) Color(0xCC1E1E1E) else Color(0xEEFFFFFF), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Waiting for live data points...", color = txtCol.copy(0.75f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            tapVal?.let { Text("Touched: %.2f".format(it), fontSize = 13.sp, color = lineCol, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
fun SoilSensorDataTable(soilMH: List<Float>, soilTH: List<Float>, soilNH: List<Float>, soilPHist: List<Float>, soilKHist: List<Float>, soilECHist: List<Float>, soilPHHist: List<Float>, timestamps: List<String>, isReceiving: Boolean, soilML: String, soilTL: String, soilNL: String, soilPL: String, soilKL: String, soilECL: String, soilPHL: String, wait: String, textColor: Color, secondaryColor: Color, cardBg: Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp), elevation = 4.dp, backgroundColor = cardBg) {
        Column(Modifier.padding(16.dp)) {
            if (isReceiving) {
                LazyColumn {
                    items(timestamps.size) { i ->
                        Column {
                            Text("Timestamp: ${timestamps.getOrNull(i) ?: "-"}", color = textColor, fontWeight = FontWeight.Bold)
                            Text("$soilML: ${soilMH.getOrNull(i) ?: "-"}", color = secondaryColor)
                            Text("$soilTL: ${soilTH.getOrNull(i) ?: "-"}", color = secondaryColor)
                            Text("$soilNL: ${soilNH.getOrNull(i) ?: "-"}", color = secondaryColor)
                            Text("$soilPL: ${soilPHist.getOrNull(i) ?: "-"}", color = secondaryColor)
                            Text("$soilKL: ${soilKHist.getOrNull(i) ?: "-"}", color = secondaryColor)
                            Text("$soilECL: ${soilECHist.getOrNull(i) ?: "-"}", color = secondaryColor)
                            Text("$soilPHL: ${soilPHHist.getOrNull(i) ?: "-"}", color = secondaryColor)
                            Divider(color = secondaryColor.copy(0.2f), thickness = 1.dp)
                        }
                    }
                }
            } else Text(wait, modifier = Modifier.padding(vertical = 32.dp), color = textColor)
        }
    }
}

@Composable
fun Accelerometer3DVisualization(xAxis: Float?, yAxis: Float?, zAxis: Float?, cardBg: Color, textColor: Color, isDarkMode: Boolean) {
    val x = xAxis ?: 0f; val y = yAxis ?: 0f; val z = zAxis ?: 0f
    Card(modifier = Modifier.fillMaxWidth().height(380.dp), elevation = 4.dp, backgroundColor = cardBg) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("3D Orientation Visualizer", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Canvas(Modifier.fillMaxWidth().height(280.dp).padding(8.dp)) {
                val cx = size.width/2f; val cy = size.height/2f; val scale = minOf(size.width, size.height)*0.25f; val axisL = 300f; val diag = axisL/sqrt(2f); val edge = if (isDarkMode) Color.White else Color.Black
                val verts = arrayOf(floatArrayOf(-1f,-1f,-1f), floatArrayOf(1f,-1f,-1f), floatArrayOf(1f,1f,-1f), floatArrayOf(-1f,1f,-1f), floatArrayOf(-1f,-1f,1f), floatArrayOf(1f,-1f,1f), floatArrayOf(1f,1f,1f), floatArrayOf(-1f,1f,1f))
                val edges = arrayOf(intArrayOf(0,1), intArrayOf(1,2), intArrayOf(2,3), intArrayOf(3,0), intArrayOf(4,5), intArrayOf(5,6), intArrayOf(6,7), intArrayOf(7,4), intArrayOf(0,4), intArrayOf(1,5), intArrayOf(2,6), intArrayOf(3,7))
                fun rot(x: Float, y: Float, z: Float): FloatArray { val rx = Math.toRadians(20.0).toFloat(); val ry = Math.toRadians(25.0).toFloat(); val rz = Math.toRadians(5.0).toFloat()
                    val y1 = y*cos(rx)-z*sin(rx); val z1 = y*sin(rx)+z*cos(rx); val z2 = z1*cos(ry)-x*sin(ry); val x2 = z1*sin(ry)+x*cos(ry); val x3 = x2*cos(rz)-y1*sin(rz); val y3 = x2*sin(rz)+y1*cos(rz); return floatArrayOf(x3, y3, z2) }
                fun proj(x: Float, y: Float, z: Float) = Offset(cx + x*scale*(5f/(5f-z)), cy - y*scale*(5f/(5f-z)))
                val projected = verts.map { val r = rot(it[0], it[1], it[2]); proj(r[0], r[1], r[2]) }
                edges.forEach { (a, b) -> drawLine(edge, projected[a], projected[b], 3f) }; projected.forEach { drawCircle(edge, 4f, it) }
                val xE = Offset(cx+axisL, cy); val yE = Offset(cx, cy-axisL); val zE = Offset(cx-diag, cy+diag)
                drawLine(Color.Red, Offset(cx, cy), xE, 3f); drawLine(Color.Green, Offset(cx, cy), yE, 3f); drawLine(Color.Cyan, Offset(cx, cy), zE, 3f)
                drawContext.canvas.nativeCanvas.apply { val p = android.graphics.Paint().apply { textSize = 36f; isAntiAlias = true }; p.color = android.graphics.Color.RED; drawText("X", xE.x+10f, xE.y, p); p.color = android.graphics.Color.GREEN; drawText("Y", yE.x+10f, yE.y, p); p.color = android.graphics.Color.CYAN; drawText("Z", zE.x+20f, zE.y+30f, p) }
                val rS = rot((x/10f).coerceIn(-1f,1f), (y/10f).coerceIn(-1f,1f), (z/10f).coerceIn(-1f,1f)); drawCircle(Color.Magenta, 10f, proj(rS[0], rS[1], rS[2]))
            }
            Text("X: %.2f, Y: %.2f, Z: %.2f".format(x, y, z), color = textColor, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun AccelerometerAngleDisplay(xAxis: Float?, yAxis: Float?, zAxis: Float?, cardBg: Color, textColor: Color, secondaryColor: Color, isDarkMode: Boolean) {
    val x = xAxis ?: 0f; val y = yAxis ?: 0f; val z = zAxis ?: 0f
    val roll = Math.toDegrees(atan2(y.toDouble(), z.toDouble())).toFloat(); val pitch = Math.toDegrees(atan2((-x).toDouble(), sqrt((y*y+z*z).toDouble()))).toFloat(); val yaw = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
    Card(modifier = Modifier.fillMaxWidth().wrapContentHeight(), elevation = 4.dp, backgroundColor = cardBg) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Tilt / Angle Monitor", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { AngleValueBox("Roll", roll.coerceIn(-180f, 180f), Color(0xFFE91E63), textColor, secondaryColor); AngleValueBox("Pitch", pitch.coerceIn(-90f, 90f), Color(0xFF2196F3), textColor, secondaryColor); AngleValueBox("Yaw~", yaw, Color(0xFF4CAF50), textColor, secondaryColor) }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Canvas(Modifier.weight(1f).fillMaxHeight()) { drawAngleGauge(this, roll.coerceIn(-180f,180f), "Roll", Color(0xFFE91E63), if (isDarkMode) Color(0x1AFFFFFF) else Color(0x1A000000), textColor, -180f, 180f) }
                Canvas(Modifier.weight(1f).fillMaxHeight()) { drawAngleGauge(this, pitch.coerceIn(-90f,90f), "Pitch", Color(0xFF2196F3), if (isDarkMode) Color(0x1AFFFFFF) else Color(0x1A000000), textColor, -90f, 90f) }
            }
            Text("Note: Yaw (~) is approximate without magnetometer", color = secondaryColor, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun AngleValueBox(name: String, value: Float, color: Color, textColor: Color, secondaryColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, fontSize = 13.sp, color = secondaryColor, fontWeight = FontWeight.Medium)
        Box(modifier = Modifier.background(color.copy(0.15f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text("%.1f°".format(value), color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun drawAngleGauge(scope: DrawScope, angle: Float, label: String, color: Color, bgColor: Color, textColor: Color, minV: Float, maxV: Float) {
    with(scope) {
        val cx = size.width/2f; val cy = size.height*0.72f; val r = minOf(size.width, size.height)*0.42f
        drawArc(bgColor, 180f, 180f, false, Offset(cx-r, cy-r), Size(r*2, r*2), style = Stroke(18f, cap = StrokeCap.Round))
        val sweep = ((angle - minV)/(maxV-minV))*180f
        if (sweep > 0f) drawArc(color, 180f, sweep.coerceIn(0f, 180f), false, Offset(cx-r, cy-r), Size(r*2, r*2), style = Stroke(18f, cap = StrokeCap.Round))
        val aRad = Math.toRadians((180f + ((angle-minV)/(maxV-minV))*180f).toDouble())
        drawLine(color, Offset(cx, cy), Offset(cx+(r*cos(aRad)).toFloat(), cy+(r*sin(aRad)).toFloat()), 4f, StrokeCap.Round)
        drawCircle(color, 7f, Offset(cx, cy))
        val p = android.graphics.Paint().apply { textSize = 22f; isAntiAlias = true; this.color = android.graphics.Color.argb((textColor.alpha*255).toInt(), (textColor.red*255).toInt(), (textColor.green*255).toInt(), (textColor.blue*255).toInt()); textAlign = android.graphics.Paint.Align.CENTER }
        drawContext.canvas.nativeCanvas.apply { drawText("${minV.toInt()}°", cx-r-4f, cy+26f, p); drawText("${maxV.toInt()}°", cx+r+4f, cy+26f, p); val bp = android.graphics.Paint().apply { textSize = 34f; isAntiAlias = true; isFakeBoldText = true; this.color = android.graphics.Color.argb((color.alpha*255).toInt(), (color.red*255).toInt(), (color.green*255).toInt(), (color.blue*255).toInt()); textAlign = android.graphics.Paint.Align.CENTER }; drawText("%.1f°".format(angle), cx, cy+52f, bp); val lp = android.graphics.Paint().apply { textSize = 24f; isAntiAlias = true; this.color = p.color; textAlign = android.graphics.Paint.Align.CENTER }; drawText(label, cx, cy-r-10f, lp) }
    }
}

@Composable
fun BleNodeVisualizer(xAxis: Float?, yAxis: Float?, zAxis: Float?, cardBg: Color, textColor: Color, secondaryColor: Color, isDarkMode: Boolean) {
    val x = xAxis ?: 0f; val y = yAxis ?: 0f; val z = zAxis ?: 0f
    val rA by animateFloatAsState(Math.toDegrees(atan2(y.toDouble(), z.toDouble())).toFloat(), spring(0.6f, 80f), label = "r")
    val pA by animateFloatAsState(Math.toDegrees(atan2((-x).toDouble(), sqrt((y*y+z*z).toDouble()))).toFloat(), spring(0.6f, 80f), label = "p")
    val yA by animateFloatAsState(Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat(), spring(0.6f, 80f), label = "y")
    val inf = rememberInfiniteTransition(label = "i"); val pS by inf.animateFloat(0.6f, 1.4f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing)), label = "s"); val pAl by inf.animateFloat(0.6f, 0f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "a"); val bAl by inf.animateFloat(1f, 0.2f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "b")
    val accB = Color(0xFF2979FF); val accC = Color(0xFF00E5FF); val nB = if (isDarkMode) Color(0xFF1A237E) else Color(0xFF1565C0); val nS = if (isDarkMode) Color(0xFF283593) else Color(0xFF1976D2)
    Card(modifier = Modifier.fillMaxWidth().height(420.dp), elevation = 6.dp, shape = RoundedCornerShape(16.dp), backgroundColor = cardBg) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(10.dp).background(accB.copy(bAl), CircleShape)); Spacer(Modifier.width(8.dp)); Text("BLE Node Orientation", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            Canvas(Modifier.fillMaxWidth().height(300.dp)) {
                val cx = size.width/2f; val cy = size.height/2f; val rR = Math.toRadians(rA.toDouble()); val pR = Math.toRadians(pA.toDouble()); val yR = Math.toRadians(yA.toDouble())
                fun proj(px: Float, py: Float, pz: Float): Offset {
                    val y1 = (py*cos(pR)-pz*sin(pR)).toFloat(); val z1 = (py*sin(pR)+pz*cos(pR)).toFloat(); val x2 = (px*cos(rR)-y1*sin(rR)).toFloat(); val y2 = (px*sin(rR)+y1*cos(rR)).toFloat()
                    val x3 = (x2*cos(yR)+z1*sin(yR)).toFloat(); val z3 = (-x2*sin(yR)+z1*cos(yR)).toFloat(); val persp = 5f/(5f-z3*0.5f); return Offset(cx+x3*130f*persp, cy-y2*130f*persp)
                }
                repeat(3) { i -> drawCircle(accB.copy(pAl*(1f-i*0.25f)), 38f+(i+1)*22f*pS, Offset(cx, cy), style = Stroke(2.5f)) }
                drawOval(Color.Black.copy(0.18f), Offset(cx-80f, cy+85f), Size(160f, 28f))
                val bW = 1.35f; val bH = 0.09f; val bD = 0.9f
                val bBot = listOf(proj(-bW,-bH,-bD), proj(bW,-bH,-bD), proj(bW,-bH,bD), proj(-bW,-bH,bD)); val bTop = listOf(proj(-bW,bH,-bD), proj(bW,bH,-bD), proj(bW,bH,bD), proj(-bW,bH,bD))
                drawPath(Path().apply { moveTo(bBot[0].x, bBot[0].y); bBot.drop(1).forEach { lineTo(it.x, it.y) }; close() }, nB)
                val tP = Path().apply { moveTo(bTop[0].x, bTop[0].y); bTop.drop(1).forEach { lineTo(it.x, it.y) }; close() }; drawPath(tP, if (isDarkMode) Color(0xFF1B5E20) else Color(0xFF2E7D32)); drawPath(tP, accC.copy(0.12f), style = Stroke(1.5f))
                for (i in 0..3) { val next = (i+1)%4; drawPath(Path().apply { moveTo(bBot[i].x, bBot[i].y); lineTo(bBot[next].x, bBot[next].y); lineTo(bTop[next].x, bTop[next].y); lineTo(bTop[i].x, bTop[i].y); close() }, nS.copy(0.7f)) }
                for (i in -3..3) drawLine(accC.copy(0.25f), proj(i*0.2f, bH+0.005f, -bD+0.05f), proj(i*0.2f, bH+0.005f, bD-0.05f), 1f)
                for (i in -2..2) drawLine(accC.copy(0.25f), proj(-bW+0.05f, bH+0.005f, i*0.28f), proj(bW-0.05f, bH+0.005f, i*0.28f), 1f)
                val cC = listOf(proj(-0.38f, bH+0.01f, -0.24f), proj(0.38f, bH+0.01f, -0.24f), proj(0.38f, bH+0.01f, 0.24f), proj(-0.38f, bH+0.01f, 0.24f))
                val cP = Path().apply { moveTo(cC[0].x, cC[0].y); cC.drop(1).forEach { lineTo(it.x, it.y) }; close() }; drawPath(cP, if (isDarkMode) Color(0xFF212121) else Color(0xFF37474F)); drawPath(cP, accC.copy(0.3f), style = Stroke(1.5f))
                drawLine(accC.copy(0.4f), Offset((cC[0].x+cC[1].x)/2f, (cC[0].y+cC[1].y)/2f), Offset((cC[2].x+cC[3].x)/2f, (cC[2].y+cC[3].y)/2f), 1f); drawLine(accC.copy(0.4f), Offset((cC[0].x+cC[3].x)/2f, (cC[0].y+cC[3].y)/2f), Offset((cC[1].x+cC[2].x)/2f, (cC[1].y+cC[2].y)/2f), 1f)
                val c2C = listOf(proj(-1.05f, bH+0.01f, -0.18f), proj(-0.6f, bH+0.01f, -0.18f), proj(-0.6f, bH+0.01f, 0.18f), proj(-1.05f, bH+0.01f, 0.18f)); drawPath(Path().apply { moveTo(c2C[0].x, c2C[0].y); c2C.drop(1).forEach { lineTo(it.x, it.y) }; close() }, if (isDarkMode) Color(0xFF263238) else Color(0xFF455A64))
                listOf(Triple(0.6f, bH+0.01f, 0.55f), Triple(0.85f, bH+0.01f, 0.55f), Triple(0.6f, bH+0.01f, 0.7f), Triple(0.85f, bH+0.01f, 0.7f)).forEach { (cx2, cy2, cz2) -> val capC = listOf(proj(cx2-0.06f, cy2, cz2-0.05f), proj(cx2+0.06f, cy2, cz2-0.05f), proj(cx2+0.06f, cy2, cz2+0.05f), proj(cx2-0.06f, cy2, cz2+0.05f)); drawPath(Path().apply { moveTo(capC[0].x, capC[0].y); capC.drop(1).forEach { lineTo(it.x, it.y) }; close() }, Color(0xFFB8860B)) }
                val lP = proj(0.88f, bH+0.02f, -0.62f); drawCircle(Color(0xFF76FF03).copy(0.85f+0.15f*pAl), 7f, lP); drawCircle(Color(0xFF76FF03).copy(0.3f), 13f, lP, style = Stroke(2f))
                val aB = proj(1.1f, bH, -0.75f); val aT = proj(1.1f, bH+0.7f, -0.75f); drawLine(accC.copy(0.9f), aB, aT, 3.5f, StrokeCap.Round); drawCircle(accC, 6f, aT)
                repeat(3) { i -> drawCircle(accB.copy((pAl*(1f-(i+1)*0.28f)).coerceIn(0f, 1f)), 12f+(i+1)*16f*pS*0.5f, aT, style = Stroke(2f)) }
                repeat(6) { i -> val pX = -bW+0.12f+i*0.22f; val pT = proj(pX, bH+0.01f, bD-0.04f); val pB = proj(pX, bH+0.01f, bD+0.12f); drawLine(Color(0xFFB0BEC5), pT, pB, 3f, StrokeCap.Round); drawCircle(Color(0xFFCFD8DC), 4f, pB) }
                val oP = proj(0f,0f,0f); val xT = proj(1.6f,0f,0f); val yT = proj(0f,1.6f,0f); val zT = proj(0f,0f,1.6f)
                drawLine(Color.Red.copy(0.85f), oP, xT, 2.5f, StrokeCap.Round); drawLine(Color.Green.copy(0.85f), oP, yT, 2.5f, StrokeCap.Round); drawLine(Color.Cyan.copy(0.85f), oP, zT, 2.5f, StrokeCap.Round)
                drawContext.canvas.nativeCanvas.apply { val p = android.graphics.Paint().apply { textSize = 26f; isAntiAlias = true; isFakeBoldText = true; textAlign = android.graphics.Paint.Align.CENTER }; p.color = android.graphics.Color.RED; drawText("X", xT.x+14f, xT.y, p); p.color = android.graphics.Color.GREEN; drawText("Y", yT.x+14f, yT.y, p); p.color = android.graphics.Color.CYAN; drawText("Z", zT.x, zT.y-10f, p) }
            }
            Row(modifier = Modifier.fillMaxWidth().background(if (isDarkMode) Color(0xFF0D1B2A) else Color(0xFFE3F2FD), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(Triple("Roll", rA, Color(0xFFE91E63)), Triple("Pitch", pA, Color(0xFF2196F3)), Triple("Yaw~", yA, Color(0xFF4CAF50))).forEach { (n, v, c) -> Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(n, fontSize = 11.sp, color = secondaryColor); Text("%.1f°".format(v), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c) } }
            }
        }
    }
}
