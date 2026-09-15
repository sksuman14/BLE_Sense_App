@file:Suppress("DEPRECATION", "UseCompatLoadingForDrawables")
package com.blesense.app.view.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.blesense.app.model.BluetoothDeviceModel as BluetoothDevice
import com.blesense.app.viewmodel.BluetoothScanViewModel
import com.blesense.app.ui.theme.BleSenseColors
import com.blesense.app.util.ThemeManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RawDataViewerScreen(
    navController: NavHostController,
    deviceAddress: String,
    viewModel: BluetoothScanViewModel
) {
    val devices by viewModel.devices.collectAsState()
    val device = devices.find { it.address == deviceAddress }

    val isScanning by viewModel.isScanning.collectAsState()

    // Get all historical raw packets
    val historyTrigger by viewModel.historyUpdateTrigger.collectAsState()
    val fullHistory = remember(historyTrigger, deviceAddress) {
        viewModel.getHistory(deviceAddress).reversed()
    }

    val isDarkMode by ThemeManager.isDarkMode.collectAsState()

    val darkBackground = if (isDarkMode) BleSenseColors.BackgroundDark else BleSenseColors.LightBackground
    val darkSurface = if (isDarkMode) BleSenseColors.SurfaceDark else BleSenseColors.LightSurface
    val cardColor = if (isDarkMode) BleSenseColors.SurfaceLight else BleSenseColors.LightSurface
    val textPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val textSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary
    val greenAccent = BleSenseColors.PrimaryGreen

    Scaffold(
        modifier = Modifier.systemBarsPadding(),
        backgroundColor = darkBackground,
        topBar = {
            TopAppBar(
                backgroundColor = darkSurface,
                title = {
                    Column {
                        Text(
                            text = device?.name ?: "Unknown Device",
                            style = MaterialTheme.typography.subtitle1,
                            color = textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = deviceAddress,
                            style = MaterialTheme.typography.caption,
                            color = textSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { /* Refresh could re-trigger scan if needed */ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = greenAccent)
                    }
                }
            )
        }
    ) { padding ->
        if (device == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Device not found in scan list", color = textSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    StatusCard(device, isScanning, textPrimary, textSecondary, greenAccent, cardColor)
                }



                if (fullHistory.isNotEmpty()) {
                    item {
                        SectionHeader("Raw Packet History (${fullHistory.size})", textPrimary)
                    }

                    items(fullHistory) { entry ->
                        val rawBytes = entry.rawData
                        if (rawBytes != null) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Received at: ${SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(entry.timestamp))}",
                                    fontSize = 11.sp,
                                    color = textSecondary,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                )
                                HexViewerCard(rawBytes, darkSurface, textPrimary, textSecondary)
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = greenAccent)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Waiting for advertising packets...", color = textSecondary)
                            }
                        }
                    }
                }

                // Basic AD segment parsing for the LATEST packet
                val latestBytes = device.scanRecordBytes
                if (latestBytes != null) {
                    item {
                        SectionHeader("Latest Packet Breakdown", textPrimary)
                    }

                    val segments = parseAdSegments(latestBytes)
                    items(segments) { segment ->
                        AdSegmentCard(segment, darkSurface, textPrimary, textSecondary, greenAccent)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusCard(
    device: BluetoothDevice,
    isScanning: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    greenAccent: Color,
    cardColor: Color = BleSenseColors.SurfaceLight
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        backgroundColor = cardColor,
        modifier = Modifier.fillMaxWidth(),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Signal Strength", fontSize = 12.sp, color = textSecondary)
                Text("${device.rssi} dBm", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textPrimary)
            }

            if (isScanning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(8.dp).background(greenAccent, RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LIVE Updates", fontSize = 12.sp, color = greenAccent, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun HexViewerCard(
    bytes: ByteArray,
    backgroundColor: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        backgroundColor = backgroundColor,
        modifier = Modifier.fillMaxWidth(),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val hexRows = bytes.toList().chunked(8)
            hexRows.forEachIndexed { index, row ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        text = String.format("%02X: ", index * 8),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = textSecondary,
                        modifier = Modifier.width(40.dp)
                    )
                    Text(
                        text = row.joinToString(" ") { String.format("%02X", it) },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = textPrimary,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, color: Color) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        color = color,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        letterSpacing = 1.sp
    )
}

data class AdSegment(
    val type: Int,
    val typeName: String,
    val data: ByteArray
)

fun parseAdSegments(bytes: ByteArray): List<AdSegment> {
    val segments = mutableListOf<AdSegment>()
    var i = 0
    while (i < bytes.size) {
        val length = bytes[i].toInt() and 0xFF
        if (length == 0) break
        if (i + length >= bytes.size) break

        val type = bytes[i + 1].toInt() and 0xFF
        val data = bytes.sliceArray((i + 2)..(i + length))

        segments.add(AdSegment(type, getAdTypeName(type), data))
        i += length + 1
    }
    return segments
}

fun getAdTypeName(type: Int): String = when (type) {
    0x01 -> "Flags"
    0x02, 0x03 -> "Service UUIDs"
    0x08, 0x09 -> "Local Name"
    0x0A -> "TX Power Level"
    0xFF -> "Manufacturer Data"
    else -> "Type 0x${String.format("%02X", type)}"
}

@Composable
fun AdSegmentCard(
    segment: AdSegment,
    backgroundColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    greenAccent: Color
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        backgroundColor = backgroundColor,
        modifier = Modifier.fillMaxWidth(),
        elevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = greenAccent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(segment.typeName, fontWeight = FontWeight.Bold, color = greenAccent, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = segment.data.joinToString(" ") { String.format("%02X", it) },
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = textPrimary
            )
        }
    }
}



