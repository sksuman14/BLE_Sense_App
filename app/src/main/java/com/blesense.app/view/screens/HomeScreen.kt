package com.blesense.app.view.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.blesense.app.R
import com.blesense.app.model.*
import com.blesense.app.ui.theme.BleSenseColors
import com.blesense.app.util.MockSensorUtils
import com.blesense.app.util.ThemeManager
import com.blesense.app.view.components.ReferenceBottomNavBar
import com.blesense.app.view.components.StandardScreenHeaderBar
import com.blesense.app.viewmodel.BluetoothScanViewModel

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MainScreen(
    navController: NavHostController,
    bluetoothViewModel: BluetoothScanViewModel
) {
    val bluetoothDevicesRaw by bluetoothViewModel.devices.collectAsState()
    val bluetoothDevices = remember(bluetoothDevicesRaw) {
        bluetoothDevicesRaw.filter { device ->
            val sensorData = device.sensorData
            val name = device.name ?: ""

            val isKnownType = name.contains("SHT", true) ||
                    name.contains("SOIL", true) ||
                    name.contains("Activity", true) ||
                    name.contains("LIS3DH", true) ||
                    name.contains("NH", true) ||
                    name.contains("Ammonia", true) ||
                    name.contains("sen66", true) ||
                    name.contains("VEML", true) ||
                    name.contains("VCNL", true) ||
                    name.contains("AHT", true) ||
                    name.contains("BME", true) ||
                    name.contains("TempLogger", true) ||
                    name.contains("TLOG", true) ||
                    name.contains("STS30", true) ||
                    name.contains("STTS751", true) ||
                    name.contains("ATRH", true) ||
                    name.contains("Rain", true) ||
                    name.contains("Wind", true)

            val hasSensorData = sensorData != null

            (isKnownType || hasSensorData) &&
                    sensorData !is SensorData.AWSData &&
                    sensorData !is SensorData.DataLoggerData &&
                    sensorData !is SensorData.StepCounterData &&
                    !name.contains("Step", ignoreCase = true) &&
                    !name.equals("test", ignoreCase = true)
        }
    }
    val isScanning by bluetoothViewModel.isScanning.collectAsState()
    val context = LocalContext.current
    val activity = context as ComponentActivity
    var isPermissionGranted by remember { mutableStateOf(checkBluetoothPermissions(context)) }
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val bluetoothAdapter = remember { BluetoothAdapter.getDefaultAdapter() }

    var selectedSensorTag by remember { mutableStateOf("All") }

    val filteredDevices = remember(bluetoothDevices, selectedSensorTag) {
        if (selectedSensorTag == "All") {
            bluetoothDevices
        } else {
            bluetoothDevices.filter { dev ->
                when (selectedSensorTag) {
                    "SHT40" -> dev.sensorData is SensorData.SHT40Data || dev.name.contains("SHT", true)
                    "LIS3DH" -> dev.sensorData is SensorData.LIS3DHData || dev.name.contains("Activity", true) || dev.name.contains("LIS3DH", true)
                    "Soil Sensor" -> dev.sensorData is SensorData.SoilSensorData || dev.name.contains("SOIL", true)
                    "Ammonia Sensor" -> dev.sensorData is SensorData.AmmoniaSensorData || dev.name.contains("NH", true) || dev.name.contains("Ammonia", true)
                    "sen66" -> dev.sensorData is SensorData.Sen66Data || dev.name.contains("sen66", true)
                    "VEML7700" -> dev.sensorData is SensorData.VEML7700Data || dev.name.contains("VEML", true)
                    "VCNL4040" -> dev.sensorData is SensorData.VCNL4040Data || dev.name.contains("VCNL", true)
                    "AHT20" -> dev.sensorData is SensorData.AHT20Data || dev.name.contains("AHT", true)
                    "BME680" -> dev.sensorData is SensorData.BME680Data || dev.name.contains("BME", true)
                    "TempLogger" -> dev.sensorData is SensorData.TempLoggerData || dev.name.contains("TempLogger", true) || dev.name.contains("TLOG", true)
                    "STS30" -> dev.sensorData is SensorData.STS30Data || dev.name.contains("STS30", true)
                    "STTS751" -> dev.sensorData is SensorData.STTS751Data || dev.name.contains("STTS", true)
                    "ATRH" -> dev.sensorData is SensorData.ATRHData || dev.name.contains("ATRH", true)
                    "Rain" -> dev.sensorData is SensorData.RainData || dev.name.contains("Rain", true)
                    "Wind" -> dev.sensorData is SensorData.WindData || dev.name.contains("Wind", true)
                    else -> true
                }
            }
        }
    }

    val bgColor = if (isDarkMode) BleSenseColors.BackgroundDark else BleSenseColors.LightBackground

    val bluetoothStateReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_ON && isPermissionGranted && !isScanning) {
                        bluetoothViewModel.startContinuousScan(activity)
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothStateReceiver, filter)
        onDispose { context.unregisterReceiver(bluetoothStateReceiver) }
    }

    DisposableEffect(isPermissionGranted, bluetoothAdapter?.isEnabled) {
        if (isPermissionGranted && bluetoothAdapter?.isEnabled == true && !isScanning) {
            bluetoothViewModel.startContinuousScan(activity)
        }
        onDispose { }
    }

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        backgroundColor = bgColor,
        bottomBar = {
            ReferenceBottomNavBar(
                navController = navController,
                currentRoute = "home_screen",
                isDarkMode = isDarkMode
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 20.dp)
        ) {
            item {
                StandardScreenHeaderBar(
                    title = "Sensor Hub",
                    subtitle = "Discover & monitor nearby BLE sensor pods",
                    isDarkMode = isDarkMode,
                    isScanning = isScanning,
                    onBackClick = { navController.popBackStack() }
                )
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                SensorHubHeroBanner(
                    isDarkMode = isDarkMode,
                    isScanning = isScanning,
                    totalDiscovered = bluetoothDevices.size,
                    activeSensors = bluetoothDevices.count { it.sensorData != null },
                    isBtOn = bluetoothAdapter?.isEnabled == true,
                    onScanToggle = {
                        if (isScanning) {
                            bluetoothViewModel.stopScan()
                        } else {
                            if (isPermissionGranted && bluetoothAdapter?.isEnabled == true) {
                                bluetoothViewModel.startContinuousScan(activity)
                            } else {
                                try {
                                    context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                } catch (e: SecurityException) {
                                    Log.e("BLE", "Permission denied to enable Bluetooth: ${e.message}")
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                )
            }

            item {
                SensorFilterRow(
                    isDarkMode = isDarkMode,
                    selectedTag = selectedSensorTag,
                    onTagSelected = { selectedSensorTag = it }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Discovered Sensors (${filteredDevices.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable {
                            if (isPermissionGranted && bluetoothAdapter?.isEnabled == true) {
                                bluetoothViewModel.startContinuousScan(activity)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Rescan",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2563EB)
                        )
                    }
                }
            }

            if (filteredDevices.isEmpty()) {
                val mockDevice = if (selectedSensorTag != "All") MockSensorUtils.getMockDeviceForTag(selectedSensorTag) else null
                if (mockDevice != null) {
                    item {
                        Text(
                            text = "Preview Mode (No real sensor found)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF6B7280),
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                        BluetoothDeviceCard(
                            device = mockDevice,
                            navController = navController,
                            isDarkMode = isDarkMode
                        )
                    }
                } else {
                    item {
                        EmptyDeviceCard(isDarkMode = isDarkMode, isScanning = isScanning)
                    }
                }
            } else {
                items(filteredDevices) { device ->
                    BluetoothDeviceCard(
                        device = device,
                        navController = navController,
                        isDarkMode = isDarkMode
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SensorHubHeroBanner(
    isDarkMode: Boolean,
    isScanning: Boolean,
    totalDiscovered: Int,
    activeSensors: Int,
    isBtOn: Boolean,
    onScanToggle: () -> Unit
) {
    val bgGradient = if (isDarkMode) listOf(Color(0xFF061428), Color(0xFF040E1E)) else listOf(Color.White, Color.White)
    val textMain = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val textSub = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val boxBg = if (isDarkMode) Color(0xFF0F172A).copy(alpha = 0.6f) else Color(0xFFF8FAFC)
    val boxBorder = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color(0xFFE2EEF9)
    val glow = if (isDarkMode) Color(0xFF6366F1) else Color(0xFF4F46E5)
    val onlineBg = if (isDarkMode) Color(0xFF1E1B4B).copy(alpha = 0.6f) else Color(0xFFE0E7FF)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_ring")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "ring_alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = glow.copy(alpha = if (isDarkMode) 0.3f else 0.1f),
                ambientColor = glow.copy(alpha = if (isDarkMode) 0.1f else 0.05f)
            )
            .border(1.dp, glow.copy(alpha = if (isDarkMode) 0.3f else 0.2f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        elevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(bgGradient))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Top Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, if (isScanning) Color(0xFF4ADE80).copy(alpha = 0.4f) else Color(0xFFFDE047).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .background(onlineBg)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (isScanning) Color(0xFF4ADE80).copy(alpha = dotAlpha) else Color(0xFFEAB308),
                                    CircleShape
                                )
                        )
                        Text(
                            text = if (isScanning) "SCANNING ACTIVE" else "SCANNER PAUSED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isScanning) Color(0xFF4ADE80) else Color(0xFFEAB308),
                            letterSpacing = 0.5.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isScanning) Color(0xFFEF4444) else Color(0xFF10B981))
                            .clickable { onScanToggle() }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isScanning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White
                            )
                            Text(
                                text = if (isScanning) "Stop" else "Start",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Discovered Box
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(boxBg, RoundedCornerShape(14.dp))
                            .border(1.dp, boxBorder, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(totalDiscovered.toString(), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = textMain)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Discovered", fontSize = 10.sp, color = textSub)
                    }

                    // Active Pods Box
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(boxBg, RoundedCornerShape(14.dp))
                            .border(1.dp, boxBorder, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(activeSensors.toString(), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = textMain)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Active Pods", fontSize = 10.sp, color = textSub)
                    }

                    // Bluetooth Box
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(boxBg, RoundedCornerShape(14.dp))
                            .border(1.dp, boxBorder, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(if (isBtOn) "ON" else "OFF", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = if (isBtOn) Color(0xFF4ADE80) else Color(0xFFEF4444))
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Bluetooth", fontSize = 10.sp, color = textSub)
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorFilterRow(
    isDarkMode: Boolean,
    selectedTag: String,
    onTagSelected: (String) -> Unit
) {
    val tags = listOf(
        "All", "SHT40", "LIS3DH", "Soil Sensor",
        "Ammonia Sensor", "sen66", "VEML7700", "VCNL4040",
        "AHT20", "BME680", "TempLogger", "STS30", "STTS751", "ATRH", "Rain", "Wind"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tags.forEach { tag ->
            val isSelected = (selectedTag == tag)
            val animatedBg by animateColorAsState(
                targetValue = if (isSelected) Color(0xFF2563EB) else (if (isDarkMode) BleSenseColors.SurfaceDark else Color.White),
                animationSpec = tween(250), label = "filter_bg"
            )
            val animatedText by animateColorAsState(
                targetValue = if (isSelected) Color.White else (if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary),
                animationSpec = tween(250), label = "filter_text"
            )

            Box(
                modifier = Modifier
                    .shadow(if (isSelected) 3.dp else 1.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.05f))
                    .clip(CircleShape)
                    .background(animatedBg)
                    .clickable { onTagSelected(tag) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tag, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = animatedText)
            }
        }
    }
}

@Composable
fun BluetoothDeviceCard(
    device: BluetoothDeviceModel,
    navController: NavHostController,
    isDarkMode: Boolean
) {
    val cardBg = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White
    val borderColor = if (isDarkMode) BleSenseColors.BorderDark else Color(0xFFE2EEF9)
    val textPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val textSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    val (iconBg, iconTint, iconRes) = remember(device.sensorData, device.name, isDarkMode) {
        val data = device.sensorData
        val name = device.name
        when {
            data is SensorData.SHT40Data || (data == null && name.contains("SHT", ignoreCase = true)) ->
                Triple(if (isDarkMode) Color(0xFF332014) else Color(0xFFFFF7ED), Color(0xFFEA580C), R.drawable.ic_thermometer)

            data is SensorData.Sen66Data || (data == null && name.contains("sen66", ignoreCase = true)) ->
                Triple(if (isDarkMode) Color(0xFF143026) else Color(0xFFECFDF5), Color(0xFF059669), R.drawable.ic_air_quality)

            data is SensorData.AmmoniaSensorData || (data == null && (name.contains("NH", ignoreCase = true) || name.contains("Ammonia", ignoreCase = true))) ->
                Triple(if (isDarkMode) Color(0xFF331717) else Color(0xFFFEE2E2), Color(0xFFEF4444), R.drawable.ic_ammonia)

            data is SensorData.SoilSensorData || (data == null && name.contains("SOIL", ignoreCase = true)) ->
                Triple(if (isDarkMode) Color(0xFF162D1E) else Color(0xFFF0FDF4), Color(0xFF16A34A), R.drawable.icons_leaf)

            data is SensorData.VEML7700Data || (data == null && name.contains("VEML", ignoreCase = true)) ->
                Triple(if (isDarkMode) Color(0xFF332D14) else Color(0xFFFEF9C3), Color(0xFFEAB308), R.drawable.ic_sun_lux)

            data is SensorData.VCNL4040Data || (data == null && name.contains("VCNL", ignoreCase = true)) ->
                Triple(if (isDarkMode) Color(0xFF271A3B) else Color(0xFFF3E8FF), Color(0xFF8B5CF6), R.drawable.ic_proximity)

            data is SensorData.TempLoggerData || (data == null && (name.contains("TempLogger", ignoreCase = true) || name.contains("TLOG", ignoreCase = true))) ->
                Triple(if (isDarkMode) Color(0xFF332014) else Color(0xFFFFF7ED), Color(0xFFF97316), R.drawable.ic_temp_logger)

            data is SensorData.AHT20Data || data is SensorData.BME680Data || (data == null && (name.contains("AHT", ignoreCase = true) || name.contains("BME", ignoreCase = true))) ->
                Triple(if (isDarkMode) Color(0xFF182238) else Color(0xFFEFF6FF), Color(0xFF2563EB), R.drawable.ic_thermometer)

            data is SensorData.LIS3DHData || (data == null && (name.contains("Activity", ignoreCase = true) || name.contains("LIS3DH", ignoreCase = true))) ->
                Triple(if (isDarkMode) Color(0xFF261938) else Color(0xFFF5F3FF), Color(0xFF7C3AED), R.drawable.ic_accelerometer)

            data is SensorData.STS30Data || data is SensorData.STTS751Data || (data == null && (name.contains("STS", ignoreCase = true) || name.contains("STTS", ignoreCase = true))) ->
                Triple(if (isDarkMode) Color(0xFF332014) else Color(0xFFFFF7ED), Color(0xFFEA580C), R.drawable.ic_thermometer)

            data is SensorData.RainData || (data == null && name.contains("Rain", ignoreCase = true)) ->
                Triple(if (isDarkMode) Color(0xFF182238) else Color(0xFFEFF6FF), Color(0xFF2563EB), R.drawable.ic_rain)

            data is SensorData.WindData || (data == null && name.contains("Wind", ignoreCase = true)) ->
                Triple(if (isDarkMode) Color(0xFF162A36) else Color(0xFFE0F2FE), Color(0xFF0284C7), R.drawable.ic_wind)

            data is SensorData.ATRHData || (data == null && name.contains("ATRH", ignoreCase = true)) ->
                Triple(if (isDarkMode) Color(0xFF182238) else Color(0xFFEFF6FF), Color(0xFF2563EB), R.drawable.ic_thermometer)

            else ->
                Triple(if (isDarkMode) Color(0xFF182238) else Color(0xFFEFF6FF), Color(0xFF2563EB), R.drawable.ic_bluetooth)
        }
    }

    val cardBgColor = if (isDarkMode) Color(0xFF061428) else Color.White
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = iconTint.copy(alpha = if (isDarkMode) 0.3f else 0.1f),
                ambientColor = iconTint.copy(alpha = if (isDarkMode) 0.1f else 0.05f)
            )
            .border(1.dp, iconTint.copy(alpha = if (isDarkMode) 0.3f else 0.15f), RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable {
                val sensorType = when {
                    device.sensorData is SensorData.SHT40Data || device.name.contains("SHT", ignoreCase = true) -> "SHT40"
                    device.sensorData is SensorData.Sen66Data || device.name.contains("sen66", ignoreCase = true) -> "SEN66"
                    device.sensorData is SensorData.AmmoniaSensorData || device.name.contains("NH", ignoreCase = true) || device.name.contains("Ammonia", ignoreCase = true) -> "Ammonia"
                    device.sensorData is SensorData.SoilSensorData || device.name.contains("SOIL", ignoreCase = true) -> "Soil"
                    device.sensorData is SensorData.VEML7700Data || device.name.contains("VEML", ignoreCase = true) -> "VEML7700"
                    device.sensorData is SensorData.VCNL4040Data || device.name.contains("VCNL", ignoreCase = true) -> "VCNL4040"
                    device.sensorData is SensorData.AHT20Data || device.name.contains("AHT", ignoreCase = true) -> "AHT20"
                    device.sensorData is SensorData.BME680Data || device.name.contains("BME", ignoreCase = true) -> "BME680"
                    device.sensorData is SensorData.TempLoggerData || device.name.contains("TempLogger", ignoreCase = true) || device.name.contains("TLOG", ignoreCase = true) -> "TempLogger"
                    device.sensorData is SensorData.STS30Data || device.name.contains("STS30", ignoreCase = true) -> "STS30"
                    device.sensorData is SensorData.STTS751Data || device.name.contains("STTS", ignoreCase = true) -> "STTS751"
                    device.sensorData is SensorData.ATRHData || device.name.contains("ATRH", ignoreCase = true) -> "ATRH"
                    device.sensorData is SensorData.RainData || device.name.contains("Rain", ignoreCase = true) -> "Rain"
                    device.sensorData is SensorData.WindData || device.name.contains("Wind", ignoreCase = true) -> "Wind"
                    device.sensorData is SensorData.LIS3DHData || device.name.contains("Activity", ignoreCase = true) || device.name.contains("LIS3DH", ignoreCase = true) -> "LIS3DH"
                    else -> "Generic"
                }
                val encName = Uri.encode(device.name.ifEmpty { "Unknown" })
                val encAddr = Uri.encode(device.address)
                navController.navigate("advertising/$encName/$encAddr/$sensorType/0")
            },
        backgroundColor = cardBgColor,
        elevation = 0.dp,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = iconTint
                        )
                    }
                    Column {
                        Text(device.name.ifEmpty { "BLE Sensor Pod" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textPrimary)
                        Text(device.address, fontSize = 10.sp, color = textSecondary)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(BleSenseColors.GreenBadgeBg, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("${device.rssi} dBm", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BleSenseColors.GreenBadgeText)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = textSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyDeviceCard(isDarkMode: Boolean, isScanning: Boolean) {
    val cardBg = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White
    val borderColor = if (isDarkMode) BleSenseColors.BorderDark else Color(0xFFE2EEF9)
    val textSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .shadow(
                elevation = if (isDarkMode) 0.dp else 4.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = Color.Black.copy(alpha = 0.05f)
            )
            .clip(RoundedCornerShape(22.dp))
            .background(cardBg)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.BluetoothSearching, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(26.dp))
            Text(
                if (isScanning) "Searching for nearby BLE sensors..." else "No BLE sensors found",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun checkBluetoothPermissions(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}
