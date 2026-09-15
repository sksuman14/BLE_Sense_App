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
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.blesense.app.R
import com.blesense.app.model.*
import com.blesense.app.ui.theme.BleSenseColors
import com.blesense.app.util.ThemeManager
import com.blesense.app.view.components.ReferenceBottomNavBar
import com.blesense.app.viewmodel.BluetoothScanViewModel
import kotlinx.coroutines.launch

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
                    name.contains("Weather", true) ||
                    name.contains("Rain", true) ||
                    name.contains("Wind", true) ||
                    name.contains("Weather", true)

            val hasSensorData = sensorData != null

            (isKnownType || hasSensorData) &&
                    sensorData !is SensorData.AWSData &&
                    sensorData !is SensorData.DataLoggerData
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
                    "Weather" -> dev.sensorData is SensorData.WeatherData || dev.name.contains("Weather", true)
                    "Rain" -> dev.sensorData is SensorData.RainData || dev.name.contains("Rain", true)
                    "Wind" -> dev.sensorData is SensorData.WindData || dev.name.contains("Wind", true)
                    else -> true
                }
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = false
    )
    val coroutineScope = rememberCoroutineScope()

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
        onDispose { 
            // We no longer stop scanning here to allow background scanning 
            // and seamless transition between hub screens.
        }
    }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetBackgroundColor = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        sheetContent = {
            AdvertiserSheetContent(
                bluetoothAdapter = bluetoothAdapter,
                onDismiss = { coroutineScope.launch { sheetState.hide() } }
            )
        }
    ) {
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
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
            ) {
                item {
                    BluetoothHeaderBar(
                        isDarkMode = isDarkMode,
                        isScanning = isScanning,
                        bluetoothAdapter = bluetoothAdapter,
                        onBackClick = { navController.popBackStack() },
                        onBluetoothToggle = { isEnabled ->
                            if (isEnabled) {
                            } else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                        @Suppress("DEPRECATION")
                                        bluetoothAdapter?.disable()
                                    }
                                } else {
                                    @Suppress("DEPRECATION")
                                    bluetoothAdapter?.disable()
                                }
                            }
                        }
                    )
                }

                item {
                    ScanningHeroCard(
                        isDarkMode = isDarkMode,
                        isScanning = isScanning,
                        deviceCount = bluetoothDevices.size,
                        onScanToggle = {
                            if (isScanning) {
                                bluetoothViewModel.stopScan()
                            } else {
                                if (isPermissionGranted && bluetoothAdapter?.isEnabled == true) {
                                    bluetoothViewModel.startContinuousScan(activity)
                                } else {
                                    context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                }
                            }
                        }
                    )
                }

                item {
                    DeviceStatsRow(
                        isDarkMode = isDarkMode,
                        totalDiscovered = bluetoothDevices.size,
                        activeSensors = bluetoothDevices.count { it.sensorData != null },
                        isBtOn = bluetoothAdapter?.isEnabled == true
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
                            "Discovered BLE Sensors (${filteredDevices.size})",
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
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(16.dp)
                            )
                            Text("Rescan", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                        }
                    }
                }

                if (filteredDevices.isEmpty()) {
                    val mockDevice = if (selectedSensorTag != "All") com.blesense.app.util.MockSensorUtils.getMockDeviceForTag(selectedSensorTag) else null
                    if (mockDevice != null) {
                        item {
                            Text(
                                "Preview Mode (No real sensor found)",
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
}

@Composable
private fun BluetoothHeaderBar(
    isDarkMode: Boolean,
    isScanning: Boolean,
    bluetoothAdapter: BluetoothAdapter?,
    onBackClick: () -> Unit,
    onBluetoothToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val cardBg = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White
    val borderColor = if (isDarkMode) BleSenseColors.BorderDark else Color(0xFFE2EEF9)
    val textPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val textSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    var isBtEnabled by remember { mutableStateOf(bluetoothAdapter?.isEnabled == true) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    isBtEnabled = (state == BluetoothAdapter.STATE_ON)
                }
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(receiver, filter)
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (e: Exception) {}
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scan_dot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dot_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(34.dp)
                    .background(if (isDarkMode) BleSenseColors.SurfaceLight else Color(0xFFF1F5F9), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textPrimary, modifier = Modifier.size(18.dp))
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("BleSense Scanner", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                    if (isScanning) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(Color(0xFF10B981).copy(alpha = dotAlpha), CircleShape)
                        )
                    }
                }
                Text("Bluetooth LE Sensor Hub", fontSize = 10.sp, color = textSecondary)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (isBtEnabled) "BT ON" else "BT OFF",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isBtEnabled) Color(0xFF059669) else textSecondary
            )
            Switch(
                checked = isBtEnabled,
                onCheckedChange = { isChecked ->
                    if (isChecked) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            } else {
                                Toast.makeText(context, "Bluetooth Connect permission required", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        }
                    } else {
                        Toast.makeText(context, "Bluetooth can be disabled in Android Settings", Toast.LENGTH_SHORT).show()
                    }
                    onBluetoothToggle(isChecked)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF2563EB),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = if (isDarkMode) Color(0xFF3E3E4E) else Color(0xFFCBD5E1)
                )
            )
        }
    }
}

@Composable
private fun ScanningHeroCard(
    isDarkMode: Boolean,
    isScanning: Boolean,
    deviceCount: Int,
    onScanToggle: () -> Unit
) {
    val cardBg = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White
    val borderColor = if (isDarkMode) BleSenseColors.BorderDark else Color(0xFFE2EEF9)
    val textPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val textSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_ring")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.9f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "ring_scale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "ring_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isScanning) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .scale(ringScale)
                            .background(Color(0xFF2563EB).copy(alpha = ringAlpha), CircleShape)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isDarkMode) BleSenseColors.SurfaceLight else Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.BluetoothSearching,
                        contentDescription = "Radar Scan",
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column {
                Text("Bluetooth LE Scanner", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (isScanning) Color(0xFF10B981) else Color(0xFF94A3B8), CircleShape)
                    )
                    Text(
                        if (isScanning) "Continuous Scanning Active" else "Scan Paused",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isScanning) Color(0xFF059669) else textSecondary
                    )
                }
                Text("$deviceCount active sensor pod(s) found", fontSize = 10.sp, color = textSecondary)
            }
        }

        Row(
            modifier = Modifier
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(if (isScanning) Color(0xFFEF4444) else Color(0xFF2563EB))
                .clickable { onScanToggle() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                if (isScanning) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Text(
                if (isScanning) "Stop" else "Scan",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun DeviceStatsRow(
    isDarkMode: Boolean,
    totalDiscovered: Int,
    activeSensors: Int,
    isBtOn: Boolean
) {
    val cardBg = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White
    val borderColor = if (isDarkMode) BleSenseColors.BorderDark else Color(0xFFE2EEF9)
    val textSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .shadow(2.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Discovered", fontSize = 10.sp, color = textSecondary)
            Text("$totalDiscovered", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .shadow(2.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Active Pods", fontSize = 10.sp, color = textSecondary)
            Text("$activeSensors", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .shadow(2.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Adapter", fontSize = 10.sp, color = textSecondary)
            Text(if (isBtOn) "Ready" else "Off", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isBtOn) Color(0xFF0891B2) else Color(0xFFDC2626))
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
        "AHT20", "BME680", "TempLogger", "STS30", "STTS751", "Weather", "Rain", "Wind"
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
                    .shadow(if (isSelected) 3.dp else 1.dp, CircleShape)
                    .clip(CircleShape)
                    .background(animatedBg)
                    .border(
                        1.dp,
                        if (isSelected) Color(0xFF2563EB) else (if (isDarkMode) BleSenseColors.BorderDark else Color(0xFFE2EEF9)),
                        CircleShape
                    )
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
            // SHT40 Temperature & Humidity
            data is SensorData.SHT40Data || (data == null && name.contains("SHT", ignoreCase = true)) ->
                Triple(if (isDarkMode) Color(0xFF332014) else Color(0xFFFFF7ED), Color(0xFFEA580C), R.drawable.ic_thermometer)

            // SEN66 Environmental Air Quality
            data is SensorData.Sen66Data || (data == null && name.contains("sen66", ignoreCase = true)) ->
                Triple(if (isDarkMode) Color(0xFF143026) else Color(0xFFECFDF5), Color(0xFF059669), R.drawable.ic_air_quality)

            // Ammonia / NH3 Toxic Gas
            data is SensorData.AmmoniaSensorData || (data == null && (name.contains("NH", ignoreCase = true) || name.contains("Ammonia", ignoreCase = true))) ->
                Triple(if (isDarkMode) Color(0xFF331717) else Color(0xFFFEE2E2), Color(0xFFEF4444), R.drawable.ic_ammonia)

            // Soil Moisture & Nutrients
            data is SensorData.SoilSensorData || (data == null && name.contains("SOIL", ignoreCase = true)) ->
                Triple(if (isDarkMode) Color(0xFF162D1E) else Color(0xFFF0FDF4), Color(0xFF16A34A), R.drawable.icons_leaf)

            // VEML7700 Ambient Light / Lux
            data is SensorData.VEML7700Data || (data == null && name.contains("VEML", ignoreCase = true)) ->
                Triple(if (isDarkMode) Color(0xFF332D14) else Color(0xFFFEF9C3), Color(0xFFEAB308), R.drawable.ic_sun_lux)

            // VCNL4040 Proximity & IR
            data is SensorData.VCNL4040Data || (data == null && name.contains("VCNL", ignoreCase = true)) ->
                Triple(if (isDarkMode) Color(0xFF271A3B) else Color(0xFFF3E8FF), Color(0xFF8B5CF6), R.drawable.ic_proximity)

            // TempLogger Data Logging
            data is SensorData.TempLoggerData || (data == null && (name.contains("TempLogger", ignoreCase = true) || name.contains("TLOG", ignoreCase = true))) ->
                Triple(if (isDarkMode) Color(0xFF332014) else Color(0xFFFFF7ED), Color(0xFFF97316), R.drawable.ic_temp_logger)

            // AHT20 & BME680 Environmental Temperature/Pressure/Gas
            data is SensorData.AHT20Data || data is SensorData.BME680Data || (data == null && (name.contains("AHT", ignoreCase = true) || name.contains("BME", ignoreCase = true))) ->
                Triple(if (isDarkMode) Color(0xFF182238) else Color(0xFFEFF6FF), Color(0xFF2563EB), R.drawable.ic_thermometer)

            // LIS3DH Accelerometer / Activity
            data is SensorData.LIS3DHData || (data == null && (name.contains("Activity", ignoreCase = true) || name.contains("LIS3DH", ignoreCase = true))) ->
                Triple(if (isDarkMode) Color(0xFF261938) else Color(0xFFF5F3FF), Color(0xFF7C3AED), R.drawable.ic_accelerometer)

            // STS30 & STTS751 High-Precision Temperature
            data is SensorData.STS30Data || data is SensorData.STTS751Data || (data == null && (name.contains("STS", ignoreCase = true) || name.contains("STTS", ignoreCase = true))) ->
                Triple(if (isDarkMode) Color(0xFF332014) else Color(0xFFFFF7ED), Color(0xFFEA580C), R.drawable.ic_thermometer)

            // Rain Sensor (Precipitation)
            data is SensorData.RainData || (data == null && name.contains("Rain", ignoreCase = true)) ->
                Triple(if (isDarkMode) Color(0xFF182238) else Color(0xFFEFF6FF), Color(0xFF2563EB), R.drawable.ic_rain)

            // Wind Sensor (Anemometer)
            data is SensorData.WindData || (data == null && name.contains("Wind", ignoreCase = true)) ->
                Triple(if (isDarkMode) Color(0xFF162A36) else Color(0xFFE0F2FE), Color(0xFF0284C7), R.drawable.ic_wind)

            // Weather Multi-Station
            data is SensorData.WeatherData || (data == null && name.contains("Weather", ignoreCase = true)) ->
                Triple(if (isDarkMode) Color(0xFF182238) else Color(0xFFEFF6FF), Color(0xFF2563EB), R.drawable.ic_thermometer)

            else ->
                Triple(if (isDarkMode) Color(0xFF182238) else Color(0xFFEFF6FF), Color(0xFF2563EB), R.drawable.ic_bluetooth)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp))
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
                    device.sensorData is SensorData.WeatherData || device.name.contains("Weather", ignoreCase = true) -> "Weather"
                    device.sensorData is SensorData.RainData || device.name.contains("Rain", ignoreCase = true) -> "Rain"
                    device.sensorData is SensorData.WindData || device.name.contains("Wind", ignoreCase = true) -> "Wind"
                    device.sensorData is SensorData.LIS3DHData || device.name.contains("Activity", ignoreCase = true) || device.name.contains("LIS3DH", ignoreCase = true) -> "LIS3DH"
                    else -> "Generic"
                }
                val encName = Uri.encode(device.name.ifEmpty { "Unknown" })
                val encAddr = Uri.encode(device.address)
                navController.navigate("advertising/$encName/$encAddr/$sensorType/0")
            },
        backgroundColor = cardBg,
        elevation = 0.dp,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                .padding(14.dp)
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
                            .size(46.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
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
            .height(120.dp)
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.BluetoothSearching, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(28.dp))
            Text(
                if (isScanning) "Searching for nearby BLE sensors..." else "No BLE sensors discovered",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AdvertiserSheetContent(
    bluetoothAdapter: BluetoothAdapter?,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("BLE Advertiser", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        Text("Broadcast sensor telemetry over Bluetooth LE", fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Close")
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



