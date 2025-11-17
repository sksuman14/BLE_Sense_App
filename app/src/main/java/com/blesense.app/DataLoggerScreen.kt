package com.blesense.app

import android.annotation.SuppressLint
import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Composable function for the DataLoggerScreen, responsible for displaying data from a Bluetooth Data Logger device
@SuppressLint("MissingPermission")
@Composable
fun DataLoggerScreen(
    deviceAddress: String, // MAC address of the target Bluetooth device
    deviceName: String, // Name of the Bluetooth device
    navController: NavController, // Navigation controller for handling back navigation
    deviceId: String, // Unique identifier for the device
    viewModel: BluetoothScanViewModel<Any> = viewModel(factory = BluetoothScanViewModelFactory(LocalContext.current)) // ViewModel for managing Bluetooth scanning and data
) {
    // Obtain the current Android context for accessing Activity and other resources
    val context = LocalContext.current
    // Cast context to Activity for lifecycle-aware operations
    val activity = context as? Activity
    // Create a coroutine scope for launching asynchronous tasks
    val coroutineScope = rememberCoroutineScope()
    // Observe the dark mode state from ThemeManager to adjust UI styling
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()

    // State to track the currently connected Bluetooth device, initially null
    var connectedDevice by remember { mutableStateOf<BluetoothScanViewModel.BluetoothDevice?>(null) }
    // State to indicate whether a refresh operation is in progress
    var isRefreshing by remember { mutableStateOf(false) }
    // Observe the packet history from the ViewModel, containing received Data Logger packets
    val packetHistory by viewModel.dataLoggerPacketHistory.collectAsState()
    // Observe the list of discovered Bluetooth devices
    val devices by viewModel.devices.collectAsState()

    // Filter devices to find Data Logger devices based on name and address
    val currentDevice by remember(devices, deviceAddress) {
        derivedStateOf {
            // First, try to find a device matching both the address and "DataLogger" in its name
            devices.find {
                it.address == deviceAddress && (
                        it.name.contains("DataLogger", ignoreCase = true) ||
                                it.name.contains("Data Logger", ignoreCase = true)
                        )
            } ?: devices.find {
                // Fallback to any device with "DataLogger" in its name if no exact match is found
                it.name.contains("DataLogger", ignoreCase = true) ||
                        it.name.contains("Data Logger", ignoreCase = true)
            }
        }
    }

    // Automatically connect to a Data Logger device when one is found in the devices list
    LaunchedEffect(devices) {
        // Search for a device with "DataLogger" in its name
        val dataLoggerDevice = devices.find {
            it.name.contains("DataLogger", ignoreCase = true) ||
                    it.name.contains("Data Logger", ignoreCase = true)
        }
        // Update the connectedDevice state if a Data Logger device is found
        dataLoggerDevice?.let { connectedDevice = it }
    }

    // Ensure the Bluetooth scan is stopped when the composable is disposed to prevent resource leaks
    DisposableEffect(navController) {
        onDispose { viewModel.stopScan() }
    }

    // Define the background gradient based on dark mode state
    val backgroundGradient = if (isDarkMode) {
        Brush.verticalGradient(listOf(Color(0xFF1E1E1E), Color(0xFF424242))) // Dark mode gradient (dark gray shades)
    } else {
        Brush.verticalGradient(listOf(Color(0xFF0A74DA), Color(0xFFADD8E6))) // Light mode gradient (blue shades)
    }
    // Define the text color for UI elements
    val textColor = Color.White

    // Main UI container with a gradient background and system bar padding
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient) // Apply the gradient background
            .padding(WindowInsets.systemBars.asPaddingValues()), // Adjust for system bars (status/navigation)
        contentAlignment = Alignment.Center
    ) {
        // Main column layout for organizing UI elements
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp), // Add 16dp padding around the content
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header row containing back button, title, and refresh button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween, // Distribute elements across the row
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button to stop scanning and navigate back
                IconButton(onClick = {
                    viewModel.stopScan() // Stop any ongoing Bluetooth scan
                    navController.popBackStack() // Navigate back to the previous screen
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back", // Accessibility description
                        tint = textColor // White icon color
                    )
                }

                // Title text for the screen
                Text(
                    text = "Data Logger Data",
                    fontFamily = helveticaFont, // Use custom Helvetica font
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor // White text color
                )

                // Refresh button to rescan for Bluetooth devices
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            // Prevent multiple refresh operations from running concurrently
                            if (isRefreshing) return@launch
                            isRefreshing = true // Set refreshing state to true

                            try {
                                // Stop any ongoing scan to avoid conflicts
                                viewModel.stopScan()
                                delay(300) // Brief delay to ensure scan stops properly

                                // Restart scan only if the Activity context is valid
                                val act = context as? Activity
                                if (act != null && !act.isFinishing && !act.isDestroyed) {
                                    viewModel.startScan(act) // Start a new Bluetooth scan
                                } else {
                                    println("⚠️ Skipping scan restart: invalid Activity context")
                                }

                                // Wait 2 seconds to allow devices to be discovered
                                delay(2000)
                            } catch (e: Exception) {
                                // Log any errors during the refresh process
                                println("⚠️ Refresh crashed: ${e.message}")
                            } finally {
                                isRefreshing = false // Reset refreshing state
                            }
                        }
                    },
                    enabled = !isRefreshing // Disable button while refreshing
                ) {
                    if (isRefreshing) {
                        // Show a progress indicator during refresh
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = textColor, // White progress indicator
                            strokeWidth = 2.dp
                        )
                    } else {
                        // Show refresh icon when not refreshing
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh", // Accessibility description
                            tint = textColor // White icon color
                        )
                    }
                }
            }

            // Add spacing below the header
            Spacer(modifier = Modifier.height(16.dp))

            // Display device information (address or fallback to provided address)
            val displayDevice = connectedDevice ?: currentDevice
            Text(
                text = "Device: Data Logger (${displayDevice?.address ?: deviceAddress})",
                fontSize = 16.sp,
                color = textColor, // White text color
                fontWeight = FontWeight.Bold
            )

            // Display node ID (hardcoded as "Data Logger")
            Text(
                text = "Node ID: Data Logger",
                fontSize = 16.sp,
                color = textColor, // White text color
                fontWeight = FontWeight.Medium
            )

            // Connection status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                // Determine color and text based on connection status
                val connectionColor = if (connectedDevice != null) Color.Green else Color.Red
                val connectionText = if (connectedDevice != null) "Connected" else "Scanning..."

                // Circular indicator for connection status
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(connectionColor, CircleShape) // Green for connected, red for scanning
                )
                Spacer(modifier = Modifier.width(8.dp)) // Spacing between dot and text
                Text(
                    text = connectionText,
                    color = textColor, // White text color
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            //Fetch Stored Data button
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    coroutineScope.launch @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) {
                   //     viewModel.sendAdvertiseCommandToSensor(deviceAddress)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF237C85))
            ) {
                Text("Get Data", color = Color.White, fontWeight = FontWeight.Bold)
            }


            // Add spacing before data display section
            Spacer(modifier = Modifier.height(24.dp))

            // Determine which device to display data for
            val deviceToDisplay = connectedDevice ?: currentDevice

            // Display Data Logger-specific data if available
            val sensorData = deviceToDisplay?.sensorData
            if (sensorData is BluetoothScanViewModel.SensorData.DataLoggerData) {
                if (packetHistory.isNotEmpty()) {
                    // Display packet history in a scrollable list
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(packetHistory) { index, dataLoggerData ->
                            // Render each packet using DataLoggerDisplay composable
                            DataLoggerDisplay(
                                dataLoggerData = dataLoggerData,
                                viewModel = viewModel,
                                packetIndex = index
                            )
                        }
                    }
                } else {
                    // Show message if no packets have been received
                    Text("No DataLogger packets received yet", color = textColor)
                }
            } else {
                // Show loading state when no Data Logger data is available
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Device type: Data Logger",
                        color = textColor, // White text color
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    // Show loading indicator while searching for devices
                    CircularProgressIndicator(color = textColor, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Searching for DataLogger devices...",
                        color = textColor.copy(alpha = 0.7f), // Slightly transparent white
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Found ${devices.size} BLE devices",
                        color = textColor.copy(alpha = 0.6f), // More transparent white
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// Utility function to convert continuous FF (255) triplets to "--" for better readability
fun mapContinuousFFtoNA(points: List<List<Int>>): List<Triple<String, String, String>> {
    // Initialize result list to store mapped triplets
    val result = mutableListOf<Triple<String, String, String>>()
    var i = 0
    while (i < points.size) {
        // Check if the current point is an FF triplet (255, 255, 255)
        val isFF = points[i].size >= 3 && points[i][0] == 255 && points[i][1] == 255 && points[i][2] == 255
        if (isFF) {
            // Track the start of consecutive FF triplets
            val start = i
            // Skip all consecutive FF triplets
            while (i < points.size && points[i].size >= 3 &&
                points[i][0] == 255 && points[i][1] == 255 && points[i][2] == 255
            ) {
                i++
            }
            // Replace each FF triplet with "--" for readability
            for (j in start until i) result.add(Triple("--", "--", "--"))
        } else {
            // Convert valid data points to strings
            result.add(Triple(points[i][0].toString(), points[i][1].toString(), points[i][2].toString()))
            i++
        }
    }
    return result
}

// Composable function to display individual Data Logger data packets
@Composable
fun DataLoggerDisplay(
    dataLoggerData: BluetoothScanViewModel.SensorData.DataLoggerData, // Data for the specific packet
    viewModel: BluetoothScanViewModel<Any>, // ViewModel for accessing additional data
    packetIndex: Int, // Index of the packet in the history list
    modifier: Modifier = Modifier // Optional modifier for customization
) {
    // Column layout for organizing packet data display
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp), // Add padding around the content
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Show a header only for the first packet in the list
        if (packetIndex == 0) {
            Text(
                text = "DataLogger - Large Data Packets",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Display the packet ID
        Text(
            text = "Device ID: ${dataLoggerData.packetId}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Green, // Green for device ID
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Display raw data in chunks within a scrollable list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp, max = 200.dp) // Constrain height between 80dp and 200dp
                .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp)) // Dark background with rounded corners
                .padding(8.dp)
        ) {
            // Split raw data into 64-byte chunks for display
            val chunks = dataLoggerData.rawData.chunked(64)
            items(chunks) { chunk ->
                Text(
                    text = chunk,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF237C85), // Custom teal color for raw data
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Add spacing after raw data
        Spacer(modifier = Modifier.height(12.dp))

        // Display payload size information
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Payload: ${dataLoggerData.rawData.length} bytes (${dataLoggerData.payloadPackets.size} triplets)",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF5644A2), // Custom purple color for payload info
                fontWeight = FontWeight.Medium
            )
        }

        // Handle case where no payload packets are available
        if (dataLoggerData.payloadPackets.isEmpty()) {
            Text("No large data received yet", color = Color.Red)
            return@Column // Exit early if no data to display
        }

        // Display parsed data points in a scrollable list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp) // Constrain maximum height to 500dp
        ) {
            // Header for parsed data points
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "-------- Parsed Data Points --------",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Yellow,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
            }

            // Map FF triplets to "--" for display
            val mappedPoints = mapContinuousFFtoNA(dataLoggerData.payloadPackets)
            items(mappedPoints) { dataPoint ->
                // Display each data point (X, Y, Z coordinates)
                Text(
                    text = "Point: X=${dataPoint.first}, Y=${dataPoint.second}, Z=${dataPoint.third}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f), // Slightly transparent white
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}