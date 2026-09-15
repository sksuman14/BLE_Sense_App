package com.blesense.app.view.screens

import android.app.Activity
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.blesense.app.ui.theme.BleSenseColors
import com.blesense.app.model.*
import com.blesense.app.model.BluetoothDeviceModel as BluetoothDevice
import com.blesense.app.viewmodel.*
import com.blesense.app.repository.*
import com.blesense.app.util.*
import com.blesense.app.view.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BigAdvScannerScreen(
    navController: NavHostController,
    viewModel: BluetoothScanViewModel
) {
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val packetHistory by viewModel.dataLoggerPacketHistory.collectAsState()

    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        skipHalfExpanded = true
    )

    var selectedDeviceAddr by remember { mutableStateOf<String?>(null) }
    var selectedDeviceId   by remember { mutableStateOf<String?>(null) }
    
    // Stable list to prevent UI from "dancing" too fast
    var stableDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    
    // Throttle list updates to once every 1.5 seconds to prevent "dancing"
    LaunchedEffect(Unit) {
        while(true) {
            stableDevices = devices.sortedByDescending { it.lastSeen }
            delay(1500)
        }
    }
    val context = LocalContext.current
    val activity = context as? Activity

    // Automatic scanning management is now handled via the "Live Scan" switch and ViewModel lifecycle.
    // The previous onDispose block was too aggressive and stopped scans even when navigating to device details.


    // Automatically start scanning when entering this diagnostic screen is now removed 
    // for manual toggle control as requested.
    /*
    LaunchedEffect(Unit) {
        activity?.let {
            viewModel.startContinuousScan(it)
        }
    }
    */

    val isDarkMode by ThemeManager.isDarkMode.collectAsState()

    val darkBackground = if (isDarkMode) BleSenseColors.BackgroundDark else BleSenseColors.LightBackground
    val darkSurface = if (isDarkMode) BleSenseColors.SurfaceDark else BleSenseColors.LightSurface
    val darkCard = if (isDarkMode) BleSenseColors.SurfaceLight else BleSenseColors.LightSurface
    val greenAccent = BleSenseColors.PrimaryGreen
    val textPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val textSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetBackgroundColor = darkSurface,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetContent = {
            if (selectedDeviceAddr != null) {
                // Get history for the specific deviceId if it exists in the map
                val historyList = if (selectedDeviceId != null) {
                    packetHistory[selectedDeviceId!!] ?: emptyList()
                } else emptyList()

                PacketHistoryDetailSheet(
                    address = selectedDeviceAddr!!,
                    history = historyList,
                    onClose = { scope.launch { sheetState.hide() } },
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    accentColor = greenAccent,
                    cardColor = darkCard
                )
            } else {
                Box(Modifier.height(1.dp)) // Empty content
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.systemBarsPadding(),
            backgroundColor = darkBackground,
            topBar = {
                TopAppBar(
                    backgroundColor = darkSurface,
                    elevation = 0.dp,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(greenAccent, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Sensors, null, tint = BleSenseColors.PrimaryGreenDark, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("BigAdv Scanner", color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Extended Adv + Raw Payload", color = textSecondary, fontSize = 10.sp)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = textPrimary)
                        }
                    }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {

                val filteredDevices = stableDevices

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Nearby Devices (${filteredDevices.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        if (isScanning) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).background(greenAccent, RoundedCornerShape(3.dp)))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scanning Active", color = greenAccent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Text("Scanner Paused", color = textSecondary, fontSize = 10.sp)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Live Scan", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isScanning,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    activity?.let { viewModel.startContinuousScan(it) }
                                } else {
                                    viewModel.stopContinuousScan()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = greenAccent,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = darkCard
                            )
                        )
                    }
                }

                if (filteredDevices.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (devices.isEmpty()) {
                                CircularProgressIndicator(color = greenAccent)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Searching for BigAdv sensors...", color = textSecondary)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredDevices) { device ->
                            BigAdvDeviceItem(
                                device = device,
                                onClick = {
                                    navController.navigate("raw_data_viewer/${Uri.encode(device.address)}")
                                },
                                onShowHistory = {
                                    selectedDeviceAddr = device.address
                                    selectedDeviceId = (device.sensorData as? SensorData.DataLoggerData)?.deviceId
                                    scope.launch { sheetState.show() }
                                },
                                cardColor = darkCard,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                accentColor = greenAccent
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PacketHistoryDetailSheet(
    address: String,
    history: List<SensorData.DataLoggerData>,
    onClose: () -> Unit,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    cardColor: Color
) {
    val filteredHistory = remember(history, address) {
        // Filter history by address if available in DataLoggerData (assuming deviceId or similar match)
        // Since history is global, we show all for now or filter if we add address to DataLoggerData
        history.reversed()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Packet History: $address",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = textPrimary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredHistory.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No packets received yet", color = textSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredHistory) { packet ->
                    PacketDetailCard(
                        packet = packet,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = accentColor,
                        cardColor = cardColor
                    )
                }
            }
        }
    }
}

@Composable
fun PacketDetailCard(
    packet: SensorData.DataLoggerData,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    cardColor: Color
) {
    val df = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        backgroundColor = cardColor,
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Packet ID: ${packet.lastPacketId} • ID: ${packet.deviceId}",
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        df.format(Date(packet.timestamp)),
                        color = textSecondary,
                        fontSize = 11.sp
                    )
                }

                Surface(
                    color = accentColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "${(packet.rawData.size - 6) / 3} points",
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))

                // Accelerometer samples grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val points = packet.getParsedPoints()
                    itemsIndexed(points) { idx, triple ->
                        val x = triple.first.toByte().toInt()
                        val y = triple.second.toByte().toInt()
                        val z = triple.third.toByte().toInt()

                        val xScaled = "%.2f".format(x / 6.4f)
                        val yScaled = "%.2f".format(y / 6.4f)
                        val zScaled = "%.2f".format(z / 6.4f)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(textPrimary.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("#${idx + 1}", color = textSecondary, fontSize = 11.sp, modifier = Modifier.width(30.dp))
                            Text("X: $x ($xScaled)", color = Color(0xFFFF6B6B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Y: $y ($yScaled)", color = Color(0xFF4ECDC4), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Z: $z ($zScaled)", color = Color(0xFF95E1D3), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Text(
                    "Tap to view all ${(packet.rawData.size - 6) / 3} values",
                    color = accentColor,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 8.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun BigAdvDeviceItem(
    device: BluetoothDevice,
    onClick: () -> Unit,
    onShowHistory: () -> Unit,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        backgroundColor = cardColor,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (device.name == "N/A" || device.name.isBlank()) "N/A" else device.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Text(
                    text = device.address,
                    fontSize = 11.sp,
                    color = textSecondary
                )
                
                val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                Text(
                    text = "Last seen: ${timeFormat.format(Date(device.lastSeen))}",
                    fontSize = 10.sp,
                    color = textSecondary.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // RSSI Tag
                    Surface(color = accentColor.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = "RSSI: ${device.rssi}",
                            fontSize = 9.sp,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Scannable/Connectable Tags
                    if (device.isScannable) {
                        Surface(color = Color(0xFF4CAF50).copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                            Text(
                                text = "SCANNABLE",
                                fontSize = 9.sp,
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    if (device.isConnectable) {
                        Surface(color = Color(0xFF2196F3).copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                            Text(
                                text = "CONNECTABLE",
                                fontSize = 9.sp,
                                color = Color(0xFF2196F3),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Payload Size
                    val size = device.scanRecordBytes?.size ?: 0
                    Surface(color = textSecondary.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = "Data: $size B",
                            fontSize = 9.sp,
                            color = textSecondary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Show parsed DataLogger summary if available
                val sensorData = device.sensorData
                if (sensorData is SensorData.DataLoggerData) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(textPrimary.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .clickable { onShowHistory() }
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, null, tint = accentColor, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "DataLogger Active (View History)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                            }
                        }
                        Text(
                            "Packet ID: ${sensorData.currentPacketId} • ${sensorData.payloadAccel.size} points",
                            fontSize = 11.sp,
                            color = textPrimary
                        )
                    }
                }
            }

            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = "View Raw",
                    tint = textSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}


