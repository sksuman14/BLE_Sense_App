package com.blesense.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.compose.material3.DropdownMenuItem

/**
 * MainScreen - The core Bluetooth Low Energy (BLE) scanning dashboard.
 *
 * Displays real-time discovered BLE devices with sensor-specific data previews.
 * Supports dark/light mode, permission handling, Bluetooth state monitoring,
 * and navigation to detailed advertising data screen.
 */
@SuppressLint("MissingPermission") // Permissions are checked before BLE operations
@Composable
fun MainScreen(navController: NavHostController, bluetoothViewModel: BluetoothScanViewModel<Any?>) {

    // Detect device orientation (useful for future responsive layouts)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Live data from ViewModel
    val bluetoothDevices by bluetoothViewModel.devices.collectAsState()
    val isScanning by bluetoothViewModel.isScanning.collectAsState()

    // Context & Activity reference
    val context = LocalContext.current
    val activity = context as ComponentActivity

    // Track if all required Bluetooth permissions are granted
    val isPermissionGranted = remember { mutableStateOf(checkBluetoothPermissions(context)) }

    // Dropdown state for sensor type selection
    var expanded by remember { mutableStateOf(false) }

    // List of supported sensor types (used for filtering and data parsing)
    val sensorTypes = listOf(
        "SHT40", "LIS2DH", "Lux Sensor", "Soil Sensor",
        "Speed Distance", "Ammonia Sensor", "DataLogger"
    )

    // Currently selected sensor type (affects preview display)
    var selectedSensor by remember { mutableStateOf(sensorTypes[0]) }

    // Toggle to show all devices or limit to first 4
    var showAllDevices by remember { mutableStateOf(false) }

    // Observe current theme from ThemeManager
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()

    // UI Text (Hardcoded - replace with string resources for localization)
    val appTitle = "BLE Sense"
    val nearbyDevices = "Nearby Devices"
    val bluetoothPermissionsRequired = "Bluetooth permissions required"
    val scanningForDevices = "Scanning for devices..."
    val noDevicesFound = "No devices found"
    val showMore = "Show More"
    val showLess = "Show Less"

    // Theme-aware colors
    val backgroundColor = if (isDarkMode) Color(0xFF121212) else Color(0xFFF5F5F5)
    val cardBackgroundColor = if (isDarkMode) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black
    val secondaryTextColor = if (isDarkMode) Color(0xFFB0B0B0) else Color(0xFF757575)
    val dividerColor = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFE0E0E0)

    // Bluetooth adapter instance
    val bluetoothAdapter = remember { BluetoothAdapter.getDefaultAdapter() }

    // Re-check permissions on first composition
    LaunchedEffect(Unit) {
        isPermissionGranted.value = checkBluetoothPermissions(context)
    }

    // Request missing permissions via custom handler
    BluetoothPermissionHandler(
        onPermissionsGranted = { isPermissionGranted.value = true }
    )

    // Listen for Bluetooth ON/OFF state changes
    val bluetoothStateReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_ON && isPermissionGranted.value && !isScanning) {
                        bluetoothViewModel.startPeriodicScan(activity)
                    }
                }
            }
        }
    }

    // Register receiver when composable enters composition
    DisposableEffect(Unit) {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(bluetoothStateReceiver, filter)
        onDispose { context.unregisterReceiver(bluetoothStateReceiver) }
    }

    // Automatically start scanning when conditions are met
    DisposableEffect(isPermissionGranted.value, bluetoothAdapter?.isEnabled) {
        if (isPermissionGranted.value && bluetoothAdapter?.isEnabled == true && !isScanning) {
            bluetoothViewModel.startPeriodicScan(activity)
        }
        onDispose { bluetoothViewModel.stopScan() }
    }

    // Main layout using Scaffold
    Scaffold(
        backgroundColor = backgroundColor,
        topBar = {
            TopAppBar(
                backgroundColor = cardBackgroundColor,
                elevation = 8.dp
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // Back button
                    IconButton(
                        onClick = { navController.navigate("intermediate_screen") },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = textColor)
                    }

                    // App title
                    Text(
                        text = appTitle,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        style = MaterialTheme.typography.h6,
                        textAlign = TextAlign.Center
                    )

                    // Dark/Light mode toggle
                    IconButton(
                        onClick = { ThemeManager.toggleDarkMode(!isDarkMode) },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = if (isDarkMode) Color(0xFF64B5F6) else Color(0xFF5D4037)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable list of discovered devices
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        elevation = 4.dp,
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = cardBackgroundColor
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header: Device count + controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$nearbyDevices (${bluetoothDevices.size})",
                                    style = MaterialTheme.typography.h6,
                                    color = textColor
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Manual refresh button
                                    IconButton(onClick = {
                                        if (isPermissionGranted.value && !isScanning) {
                                            if (bluetoothAdapter?.isEnabled == true) {
                                                bluetoothViewModel.startPeriodicScan(activity)
                                            } else {
                                                context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = textColor)
                                    }

                                    // Sensor type selection dropdown
                                    Box {
                                        IconButton(onClick = { expanded = true }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = textColor)
                                        }
                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false },
                                            modifier = Modifier.background(cardBackgroundColor)
                                        ) {
                                            sensorTypes.forEach { sensor ->
                                                DropdownMenuItem(
                                                    text = { Text(sensor, color = textColor) },
                                                    onClick = {
                                                        selectedSensor = sensor
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Conditional content based on state
                            when {
                                !isPermissionGranted.value -> {
                                    Text(
                                        bluetoothPermissionsRequired,
                                        textAlign = TextAlign.Center,
                                        color = textColor,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                bluetoothAdapter?.isEnabled != true -> {
                                    Text(
                                        "Please enable Bluetooth",
                                        textAlign = TextAlign.Center,
                                        color = textColor,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                bluetoothDevices.isEmpty() -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        if (isScanning) {
                                            CircularProgressIndicator(color = if (isDarkMode) Color(0xFF64B5F6) else Color(0xFF2196F3))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(scanningForDevices, color = textColor)
                                        } else {
                                            Text(noDevicesFound, color = textColor)
                                        }
                                    }
                                }
                                else -> {
                                    // Show limited or all devices
                                    val devicesToShow = if (showAllDevices) bluetoothDevices else bluetoothDevices.take(4)
                                    devicesToShow.forEach { device ->
                                        BluetoothDeviceItem(
                                            device = device,
                                            navController = navController,
                                            selectedSensor = selectedSensor,
                                            isDarkMode = isDarkMode
                                        )
                                        Divider(color = dividerColor)
                                    }

                                    // Show More / Show Less toggle
                                    if (bluetoothDevices.size > 4) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp)
                                                .clickable { showAllDevices = !showAllDevices }
                                                .background(
                                                    if (isDarkMode) Color(0xFF2A2A2A) else Color.LightGray,
                                                    RoundedCornerShape(8.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (showAllDevices) showLess else showMore,
                                                modifier = Modifier.padding(12.dp),
                                                color = textColor
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
    }
}

/**
 * Checks required Bluetooth permissions based on Android version.
 */
private fun checkBluetoothPermissions(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        ).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        ).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }
}

/**
 * Composable for displaying a single Bluetooth device in the list.
 * Shows name, address, RSSI, and sensor-specific live data preview.
 */
@Composable
fun BluetoothDeviceItem(
    device: BluetoothScanViewModel.BluetoothDevice,
    navController: NavHostController,
    selectedSensor: String,
    isDarkMode: Boolean
) {
    val textColor = if (isDarkMode) Color.White else Color.Black
    val secondaryTextColor = if (isDarkMode) Color(0xFFB0B0B0) else Color(0xFF757575)
    val iconBg = if (isDarkMode) Color(0xFF0D47A1) else Color(0xFFE3F2FD)
    val iconTint = if (isDarkMode) Color(0xFF64B5F6) else Color(0xFF2196F3)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable {
                // Navigate to detailed advertising data screen
                navController.navigate(
                    "advertising/${device.name.replace("/", "-")}/${device.address}/$selectedSensor/${device.deviceId}"
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bluetooth icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(iconBg, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.bluetooth),
                contentDescription = "Bluetooth Device",
                modifier = Modifier.size(24.dp),
                tint = iconTint
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(device.name, style = MaterialTheme.typography.subtitle1, color = textColor)
            Text("Address: ${device.address}", style = MaterialTheme.typography.caption, color = secondaryTextColor)
            Text("RSSI: ${device.rssi} dBm", style = MaterialTheme.typography.caption, color = secondaryTextColor)

            // Display sensor-specific preview if data exists
            device.sensorData?.let { data ->
                val displayText = when {
                    selectedSensor == "SHT40" && data is BluetoothScanViewModel.SensorData.SHT40Data ->
                        "Temp: ${data.temperature}°C, Humidity: ${data.humidity}%"
                    selectedSensor == "LIS2DH" && data is BluetoothScanViewModel.SensorData.LIS2DHData ->
                        "X: ${data.x}, Y: ${data.y}, Z: ${data.z}"
                    selectedSensor == "Lux Sensor" && data is BluetoothScanViewModel.SensorData.LuxSensorData ->
                        "Lux: ${data.lux}"
                    selectedSensor == "Soil Sensor" && data is BluetoothScanViewModel.SensorData.SoilSensorData ->
                        "N:${data.nitrogen} P:${data.phosphorus} K:${data.potassium} | Moisture:${data.moisture}%"
                    selectedSensor == "Speed Distance" && data is BluetoothScanViewModel.SensorData.SDTData ->
                        "Speed: ${data.speed}m/s, Distance: ${data.distance}m"
                    selectedSensor == "Ammonia Sensor" && data is BluetoothScanViewModel.SensorData.AmmoniaSensorData ->
                        "Ammonia: ${data.ammonia}"
                    selectedSensor == "DataLogger" && data is BluetoothScanViewModel.SensorData.DataLoggerData ->
                        "Packet ID: ${data.packetId} | Packets: ${data.payloadPackets.size}"
                    else -> "No preview available"
                }

                // Special preview box for DataLogger (larger data)
                if (selectedSensor == "DataLogger" && data is BluetoothScanViewModel.SensorData.DataLoggerData) {
                    DataLoggerPreview(
                        rawData = "Packet ID: ${data.packetId} | Packets: ${data.payloadPackets.size}",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }

                Text(
                    text = displayText,
                    style = MaterialTheme.typography.caption,
                    color = if (isDarkMode) Color(0xFF64B5F6) else MaterialTheme.colors.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } ?: Text("No data", color = secondaryTextColor)
        }
    }
}

/**
 * Dedicated preview card for DataLogger devices showing packet info.
 */
@Composable
fun DataLoggerPreview(rawData: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(80.dp)
            .background(
                if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color.White,
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = rawData,
            style = MaterialTheme.typography.caption,
            color = if (isSystemInDarkTheme()) Color(0xFF64B5F6) else Color(0xFF2196F3),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}