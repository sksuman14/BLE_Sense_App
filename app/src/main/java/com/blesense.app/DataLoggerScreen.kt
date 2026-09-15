package com.blesense.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.blesense.app.BluetoothScanViewModel
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.max
import java.util.Locale
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.composed
import androidx.compose.foundation.lazy.rememberLazyListState

// Class to send BLE commands using advertising (non-connectable)
class BleCommandSender(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val advertiser: BluetoothLeAdvertiser? = adapter?.bluetoothLeAdvertiser

    private val companyId = 0x0059  // Nordic default manufacturer ID

    private var currentCallback: AdvertiseCallback? = null

    // Check if app has BLE advertising permission (required for Android 12+)
    private fun hasAdvertisePermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }

    // Send a 2-byte command via BLE advertising
    fun sendCommand(command: ByteArray, durationMs: Long = 5000) {
        // Check permissions and validate command length
        if (!hasAdvertisePermission() || advertiser == null || command.size != 2) return

        stopAdvertising()

        // Create advertising data with manufacturer-specific data
        val data = AdvertiseData.Builder()
            .addManufacturerData(companyId, command)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        // Configure advertising settings for fast, non-connectable advertising
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        currentCallback = object : AdvertiseCallback() {}

        try {
            // Start advertising the command
            advertiser.startAdvertising(settings, data, currentCallback)

            // Stop advertising after specified duration
            MainScope().launch {
                delay(durationMs)
                stopAdvertising()
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    // Stop current advertising session
    fun stopAdvertising() {
        if (!hasAdvertisePermission()) return
        try {
            currentCallback?.let {
                advertiser?.stopAdvertising(it)
                currentCallback = null
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}

// Main screen for DataLogger device interaction
@SuppressLint("MissingPermission")
@Composable
fun DataLoggerScreen(
    deviceAddress: String,
    deviceName: String,
    navController: NavController,
    deviceId: String,
    viewModel: BluetoothScanViewModel<Any> = viewModel(factory = BluetoothScanViewModelFactory(LocalContext.current))
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()

    // Create command sender for BLE communication
    val commandSender = remember { BleCommandSender(context) }

    // Permission launcher for Android 12+ BLE permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    // Setup permissions and start BLE scanning when screen loads
    LaunchedEffect(Unit) {
        // Check and request BLE permissions for Android 12+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            val missing = permissions.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                permissionLauncher.launch(missing.toTypedArray())
            }
        }

        // Start continuous BLE scanning
        val act = context as? Activity
        if (act != null && !act.isFinishing && !act.isDestroyed) {
            viewModel.startContinuousScan(act)
        }
    }

    // State variables for UI control
    var connectedDevice by remember { mutableStateOf<BluetoothScanViewModel.BluetoothDevice?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isGettingData by remember { mutableStateOf(false) }
    var isResetting by remember { mutableStateOf(false) }
    var lastPacketCount by remember { mutableIntStateOf(0) }

    // Collect packet history from ViewModel
    val packetHistory by viewModel.dataLoggerPacketHistory.collectAsState()

    // Calculate lost packet IDs by comparing received IDs with expected sequence
    val lostPacketIds = remember(packetHistory) {
        val ids = mutableListOf<Int>()

        packetHistory.forEach { item ->
            if (item is BluetoothScanViewModel.SensorData.DataLoggerData) {
                ids.add(item.currentPacketId)
            }
        }

        if (ids.size < 2) {
            emptyList<Int>()
        } else {
            val presentIds = ids.toSet()
            val maxId = ids.maxOrNull() ?: 0
            val minId = ids.minOrNull() ?: 0

            // Find missing IDs in the sequence
            (minId..maxId).filter { it !in presentIds }
        }
    }

    // Collect discovered devices from ViewModel
    val devices by viewModel.devices.collectAsState()

    // Find the current DataLogger device
    val currentDevice by remember(devices, deviceAddress) {
        derivedStateOf {
            devices.find {
                it.address == deviceAddress && (
                        it.name.contains("DataLogger", ignoreCase = true) ||
                                it.name.contains("Data Logger", ignoreCase = true)
                        )
            } ?: devices.find {
                it.name.contains("DataLogger", ignoreCase = true) ||
                        it.name.contains("Data Logger", ignoreCase = true)
            }
        }
    }

    // Update connected device when devices list changes
    LaunchedEffect(devices) {
        val dataLoggerDevice = devices.find {
            it.name.contains("DataLogger", ignoreCase = true) ||
                    it.name.contains("Data Logger", ignoreCase = true)
        }
        dataLoggerDevice?.let { connectedDevice = it }
    }

    // Clean up BLE scanning when navigating away
    DisposableEffect(navController) {
        onDispose { viewModel.stopScan() }
    }

    // Stop advertising when new data arrives
    LaunchedEffect(packetHistory.size) {
        if (packetHistory.size > lastPacketCount) {
            commandSender.stopAdvertising()
            isGettingData = false
            isResetting = false
        }
    }

    // Reset state when packet history becomes empty
    LaunchedEffect(packetHistory.isEmpty()) {
        if (packetHistory.isEmpty()) {
            isResetting = false
        }
    }

    // Timeout for data collection and reset operations
    LaunchedEffect(isGettingData, isResetting) {
        if (isGettingData || isResetting) {
            delay(40000)
            isGettingData = false
            isResetting = false
        }
    }

    // Theme-aware background gradient
    val backgroundGradient = if (isDarkMode) {
        Brush.verticalGradient(listOf(Color(0xFF1E1E1E), Color(0xFF424242)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF0A74DA), Color(0xFFADD8E6)))
    }

    val textColor = Color.White

    // Main screen layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .padding(WindowInsets.systemBars.asPaddingValues()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with back button, title, and refresh button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    viewModel.stopScan()
                    navController.popBackStack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = textColor)
                }
                Text(
                    text = "Data Logger Data",
                    fontFamily = helveticaFont,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            if (isRefreshing) return@launch
                            isRefreshing = true
                            try {
                                viewModel.stopScan()
                                delay(1000)
                                val act = context as? Activity
                                if (act != null && !act.isFinishing && !act.isDestroyed) {
                                    viewModel.startScan(act)
                                }
                                delay(15000)
                            } catch (e: Exception) {
                                println("Refresh error: ${e.message}")
                            } finally {
                                isRefreshing = false
                            }
                        }
                    },
                    enabled = !isRefreshing
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = textColor, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, "Refresh", tint = textColor)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Device information display
            val displayDevice = connectedDevice ?: currentDevice
            Text("Device: Data Logger (${displayDevice?.address ?: deviceAddress})",
                fontSize = 16.sp, color = textColor, fontWeight = FontWeight.Bold)
            Text("Node ID: Data Logger", fontSize = 16.sp, color = textColor, fontWeight = FontWeight.Medium)

            // Connection status indicator
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                val connectionColor = if (connectedDevice != null) Color.Green else Color.Red
                val connectionText = if (connectedDevice != null) "Connected" else "Scanning..."
                Box(modifier = Modifier.size(12.dp).background(connectionColor, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(connectionText, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons for getting data and resetting device
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Get Data button - sends command to request data from device
                Button(
                    onClick = {
                        if (!isGettingData) {
                            isGettingData = true
                            lastPacketCount = packetHistory.size
                            commandSender.sendCommand(byteArrayOf(0xBB.toByte(), 0xCC.toByte()), 40000)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier.weight(1f),
                    enabled = !isGettingData
                ) {
                    if (isGettingData) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Get Data", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // Reset Device button - sends reset command to device
                Button(
                    onClick = {
                        if (!isResetting) {
                            isResetting = true
                            commandSender.sendCommand(byteArrayOf(0xFF.toByte(), 0xFF.toByte()), 40000)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                    modifier = Modifier.weight(1f),
                    enabled = !isResetting
                ) {
                    if (isResetting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Reset Device", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Statistics cards showing packet arrival and loss
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                // Total packets received card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Packets Arrival", color = Color.White, fontSize = 14.sp)
                            Text("${packetHistory.size}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }
                        if (packetHistory.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val receivedIds = packetHistory
                                .filterIsInstance<BluetoothScanViewModel.SensorData.DataLoggerData>()
                                .map { it.currentPacketId }
                                .distinct()
                                .sorted()

                            Text(
                                text = "Received IDs: ${receivedIds.joinToString(", ")}",
                                color = Color(0xFFB2FF59),
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Lost packets card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (lostPacketIds.isEmpty()) Color.White.copy(alpha = 0.1f) else Color(0x33FF5252)
                    ),
                    border = BorderStroke(1.dp, if (lostPacketIds.isEmpty()) Color.White.copy(alpha = 0.2f) else Color(0x66FF5252))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Packets Lost", color = Color.White, fontSize = 14.sp)
                            Text("${lostPacketIds.size}", color = if (lostPacketIds.isEmpty()) Color.White else Color(0xFFFF5252),
                                fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        if (lostPacketIds.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Lost IDs: ${lostPacketIds.joinToString(", ")}",
                                color = Color(0xFFFFCCCC),
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Export raw data button
            var isExporting by remember { mutableStateOf(false) }

            val createDocumentLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("text/csv")
            ) { uri: Uri? ->
                if (uri != null && packetHistory.isNotEmpty()) {
                    isExporting = true
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                    val dateFormat = SimpleDateFormat(
                                        "yyyy-MM-dd HH:mm:ss.SSS",
                                        Locale.getDefault()
                                    )

                                    // Write CSV header
                                    val header = "Timestamp,Packet_ID,Last_Packet_ID,Device_ID,Raw_Data_Bytes,Raw_Hex_String\n"
                                    outputStream.write(header.toByteArray())

                                    // Write each packet as CSV row
                                    packetHistory.reversed().forEach { packet ->
                                        val line = buildString {
                                            append(dateFormat.format(Date(packet.timestamp)))
                                            append(",")
                                            append(packet.currentPacketId)
                                            append(",")
                                            append(packet.lastPacketId)
                                            append(",")
                                            append(packet.deviceId)
                                            append(",")
                                            val byteCount = packet.rawData.split(" ").size
                                            append(byteCount)
                                            append(",")
                                            append("\"${packet.rawData}\"")
                                            append("\n")
                                        }
                                        outputStream.write(line.toByteArray())
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                withContext(Dispatchers.Main) {
                                    isExporting = false
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (packetHistory.isEmpty()) return@Button
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val filename = "DataLogger_Raw_${deviceId}_$timestamp.csv"
                    createDocumentLauncher.launch(filename)
                },
                enabled = packetHistory.isNotEmpty() && !isExporting,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isExporting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Exporting Raw Data...", color = Color.White)
                    }
                } else {
                    Text("Export All Raw Data as CSV", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // Display packet list or loading state
            val deviceToDisplay = connectedDevice ?: currentDevice
            val uniquePackets = packetHistory.distinctBy { it.currentPacketId }
            if (uniquePackets.isNotEmpty()) {
                val listState = rememberLazyListState()

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            items = packetHistory,
                            key = { index, packet -> "${packet.currentPacketId}_${packet.timestamp}_$index" }
                        ) { index, dataLoggerData ->
                            DataLoggerDisplay(
                                dataLoggerData = dataLoggerData,
                                viewModel = viewModel,
                                packetIndex = index
                            )
                        }
                    }

                    DraggableScrollbar(
                        state = listState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 2.dp, top = 20.dp, bottom = 20.dp)
                    )
                }
            } else {
                // Loading state when no packets available
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Device type: Data Logger",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Waiting for Data Packets...",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Found ${devices.size} BLE devices",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// Convert continuous FF values in accelerometer data to "NA" for display
fun mapContinuousFFtoNA(points: List<List<Int>>): List<Triple<String, String, String>> {
    return points.map { point ->
        if (point.size >= 3 && point[0] == 255 && point[1] == 255 && point[2] == 255) {
            Triple("--", "--", "--")
        } else {
            Triple(point[0].toString(), point[1].toString(), point[2].toString())
        }
    }
}

// Composable to display individual DataLogger packet
@Composable
fun DataLoggerDisplay(
    dataLoggerData: BluetoothScanViewModel.SensorData.DataLoggerData,
    viewModel: BluetoothScanViewModel<Any>,
    packetIndex: Int,
    modifier: Modifier = Modifier
) {
    // Process accelerometer data to ensure exactly 80 points
    val displayAccelPoints = remember(dataLoggerData.payloadAccel) {
        val original = dataLoggerData.payloadAccel

        val fixedList = when {
            original.size >= 80 -> original.take(80)
            original.isNotEmpty() -> {
                val filled = original.toMutableList()
                val last = original.last()
                repeat(80 - original.size) { filled.add(last) }
                filled
            }
            else -> List(80) { Triple(0, 0, 0) }
        }

        // Convert to unsigned values and handle FF values
        fixedList.map { triple ->
            val x = triple.first.toInt() and 0xFF
            val y = triple.second.toInt() and 0xFF
            val z = triple.third.toInt() and 0xFF

            if (x == 255 && y == 255 && z == 255) {
                Triple("--", "--", "--")
            } else {
                Triple(x.toString(), y.toString(), z.toString())
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title for first packet
        if (packetIndex == 0) {
            Text(
                text = "DataLogger - Large Data Packets",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // Packet ID display
        Text(
            text = "Packet ID: ${dataLoggerData.currentPacketId}",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF674414),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Timestamp display
        Text(
            text = "Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(dataLoggerData.timestamp))}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Accelerometer data section header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.Start)
        ) {
            Surface(color = Color(0xFF4CAF50), shape = RoundedCornerShape(8.dp)) {
                Text(
                    text = "80 Points",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            Text("Accelerometer Data", color = Color.Black.copy(0.7f), fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Accelerometer data list
        Surface(color = Color(0xFF0D1E0D), shape = RoundedCornerShape(8.dp)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(displayAccelPoints) { index, triple ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "#${(index + 1).toString().padStart(2, '0')}",
                            color = Color(0xFF888888),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.width(35.dp)
                        )
                        Text(
                            text = "X: ${triple.first.padStart(4, ' ')}",
                            color = if (triple.first == "--") Color.Gray else Color(0xFFFF6B6B),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Y: ${triple.second.padStart(4, ' ')}",
                            color = if (triple.second == "--") Color.Gray else Color(0xFF4ECDC4),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Z: ${triple.third.padStart(4, ' ')}",
                            color = if (triple.third == "--") Color.Gray else Color(0xFF95E1D3),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Raw data section header
        Text(
            text = "Raw Data (${dataLoggerData.rawData.split(" ").size} bytes):",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF5644A2),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Raw hex data display
        Surface(color = Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .padding(12.dp)
            ) {
                val chunks = dataLoggerData.rawData.chunked(64)
                items(chunks.size) { i ->
                    Text(
                        text = chunks[i],
                        color = Color(0xFF237C85),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    if (i < chunks.size - 1) Spacer(Modifier.height(4.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Total bytes summary
        Text(
            text = "Total payload: ${dataLoggerData.rawData.split(" ").size} bytes received",
            color = Color.Black,
            fontSize = 13.sp
        )
    }
}

// Custom draggable scrollbar for LazyColumn
@Composable
fun DraggableScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxHeight().width(50.dp)) {
        val localConstraints = this@BoxWithConstraints.constraints
        val maxHeightPx = localConstraints.maxHeight.toFloat()

        val totalItems = state.layoutInfo.totalItemsCount
        val visibleItems = state.layoutInfo.visibleItemsInfo

        // Only show scrollbar if there are more items than visible area
        if (totalItems > visibleItems.size && visibleItems.isNotEmpty()) {
            val thumbHeightPx = max(120f, maxHeightPx * (visibleItems.size.toFloat() / totalItems))
            val trackHeightPx = maxHeightPx - thumbHeightPx

            val scrollOffset = state.firstVisibleItemIndex.toFloat() / (totalItems - visibleItems.size).coerceAtLeast(1)
            val thumbOffsetY = with(LocalDensity.current) { (scrollOffset * trackHeightPx).toDp() }

            // Touch detection area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(totalItems, maxHeightPx) {
                        detectVerticalDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = { isDragging = false },
                            onVerticalDrag = { change, _ ->
                                val dragY = change.position.y
                                val newScrollOffset = (dragY / maxHeightPx).coerceIn(0f, 1f)
                                val itemToScroll = (newScrollOffset * totalItems).toInt()

                                coroutineScope.launch {
                                    state.scrollToItem(itemToScroll.coerceIn(0, totalItems - 1))
                                }
                            }
                        )
                    }
            ) {
                // Visual scrollbar element
                Box(
                    modifier = Modifier
                        .offset(y = thumbOffsetY)
                        .align(Alignment.TopEnd)
                        .width(10.dp)
                        .height(with(LocalDensity.current) { thumbHeightPx.toDp() })
                        .padding(end = 6.dp)
                        .background(
                            color = if (isDragging) Color.White else Color.White.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                )
            }
        }
    }
}