@file:Suppress("DEPRECATION", "UseCompatLoadingForDrawables")
package com.blesense.app.view.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.blesense.app.model.BluetoothDeviceModel as BluetoothDevice
import com.blesense.app.model.HistoricalDataEntry
import com.blesense.app.ui.theme.BleSenseColors
import com.blesense.app.util.ThemeManager
import com.blesense.app.view.components.StandardScreenHeaderBar
import com.blesense.app.viewmodel.BluetoothScanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RawDataViewerScreen(
    navController: NavHostController,
    deviceAddress: String,
    viewModel: BluetoothScanViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val devices by viewModel.devices.collectAsState()
    val decodedAddress = remember(deviceAddress) {
        try { android.net.Uri.decode(deviceAddress) } catch (_: Exception) { deviceAddress }
    }
    val device = devices.find {
        it.address.equals(deviceAddress, ignoreCase = true) ||
        it.address.equals(decodedAddress, ignoreCase = true)
    }

    val isScanning by viewModel.isScanning.collectAsState()

    val historyTrigger by viewModel.historyUpdateTrigger.collectAsState()
    val historyList = remember(historyTrigger, deviceAddress, decodedAddress) {
        val direct = viewModel.getHistory(deviceAddress)
        if (direct.isNotEmpty()) direct else viewModel.getHistory(decodedAddress)
    }

    // Get the single newest/latest packet
    val latestEntry = historyList.lastOrNull()
    val rawOtaBytes = latestEntry?.rawData ?: device?.scanRecordBytes
    val actualPayload = remember(rawOtaBytes) { extractActualDevicePayload(rawOtaBytes) }
    val latestTimestamp = latestEntry?.timestamp ?: device?.lastSeen ?: System.currentTimeMillis()
    val latestRssi = device?.rssi ?: "--"
    val deviceName = if (!device?.name.isNullOrBlank() && device?.name != "N/A") device!!.name else "test"

    val isDarkMode by ThemeManager.isDarkMode.collectAsState()

    val darkBackground = if (isDarkMode) BleSenseColors.BackgroundDark else BleSenseColors.LightBackground
    val darkSurface = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White
    val cardColor = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White
    val borderColor = if (isDarkMode) BleSenseColors.BorderDark else Color(0xFFE2EEF9)
    val textPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val textSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary
    val greenAccent = BleSenseColors.PrimaryGreen

    // CSV Download Launcher
    var isExporting by remember { mutableStateOf(false) }
    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            isExporting = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val packetsToExport = if (historyList.isNotEmpty()) {
                        historyList
                    } else if (rawOtaBytes != null) {
                        listOf(HistoricalDataEntry(latestTimestamp, device?.sensorData, rawOtaBytes))
                    } else {
                        emptyList()
                    }

                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        val header = "Index,Timestamp_Epoch_Ms,DateTime,Device_Name,Device_Address,RSSI_dBm,Payload_Size_Bytes,Raw_Hex,Raw_Integer\n"
                        os.write(header.toByteArray(Charsets.UTF_8))

                        packetsToExport.forEachIndexed { index, entry ->
                            val fullRaw = entry.rawData ?: ByteArray(0)
                            val payload = extractActualDevicePayload(fullRaw)
                            val payloadHex = payload.joinToString(" ") { String.format("%02X", it) }
                            val payloadInts = payload.map { it.toInt() and 0xFF }.joinToString(", ")
                            val dtStr = dateFormat.format(Date(entry.timestamp))

                            val line = "${index + 1},${entry.timestamp},\"$dtStr\",\"${deviceName.replace("\"", "\"\"")}\",\"$deviceAddress\",\"$latestRssi\",${payload.size},\"$payloadHex\",\"$payloadInts\"\n"
                            os.write(line.toByteArray(Charsets.UTF_8))
                        }
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Downloaded ${packetsToExport.size} raw packets with raw integer to CSV!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isExporting = false
                    }
                }
            }
        }
    }

    fun triggerCsvDownload() {
        if (isExporting) return
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val safeName = deviceName.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val filename = "${safeName}_actual_payload_$timeStamp.csv"
        exportCsvLauncher.launch(filename)
    }

    Scaffold(
        modifier = Modifier.systemBarsPadding(),
        backgroundColor = darkBackground,
        topBar = {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                StandardScreenHeaderBar(
                    title = deviceName,
                    subtitle = deviceAddress,
                    isDarkMode = isDarkMode,
                    isScanning = isScanning,
                    onBackClick = { navController.popBackStack() },
                    actions = {
                        IconButton(onClick = {
                            val activity = context as? Activity
                            viewModel.refreshScan(activity)
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = textSecondary)
                        }
                    }
                )
            }
        }
    ) { padding ->
        if (device == null && rawOtaBytes == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Device not found in scan list", color = textSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)
            ) {
                // 1. Status Card with Signal Strength & Buffer Status
                item {
                    StatusCard(
                        rssi = latestRssi,
                        isScanning = isScanning,
                        bufferCount = historyList.size,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        greenAccent = greenAccent,
                        cardColor = cardColor,
                        borderColor = borderColor
                    )
                }

                // 2. Download CSV Button Card
                item {
                    DownloadCsvCard(
                        isExporting = isExporting,
                        packetCount = historyList.size,
                        onClick = { triggerCsvDownload() },
                        cardColor = cardColor,
                        borderColor = borderColor,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        greenAccent = greenAccent
                    )
                }

                // 3. Single Live Raw Hex Card
                if (actualPayload.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionHeader("Raw Hex", textPrimary)
                            Surface(
                                color = greenAccent.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${actualPayload.size} Bytes",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = greenAccent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Received at: ${SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(latestTimestamp))}",
                                fontSize = 11.sp,
                                color = textSecondary,
                                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                            )
                            HexViewerCard(actualPayload, darkSurface, textPrimary, borderColor)
                        }
                    }

                    // 4. Raw Integer Below Raw Hex
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SectionHeader("Raw Integer", textPrimary)
                            Spacer(modifier = Modifier.height(4.dp))
                            IntegerConversionViewerCard(actualPayload, darkSurface, textPrimary, borderColor)
                        }
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = greenAccent)
                                Spacer(modifier = Modifier.height(14.dp))
                                Text("Waiting for live sensor payload...", color = textSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusCard(
    rssi: String,
    isScanning: Boolean,
    bufferCount: Int,
    textPrimary: Color,
    textSecondary: Color,
    greenAccent: Color,
    cardColor: Color = BleSenseColors.SurfaceLight,
    borderColor: Color = BleSenseColors.BorderDark
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        backgroundColor = cardColor,
        elevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Signal Strength", fontSize = 11.sp, color = textSecondary)
                Text("$rssi dBm", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textPrimary)
            }

            Column(horizontalAlignment = Alignment.End) {
                if (isScanning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(greenAccent, RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("LIVE Updates", fontSize = 11.sp, color = greenAccent, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("Paused", fontSize = 11.sp, color = textSecondary)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "History: $bufferCount / 1000",
                    fontSize = 11.sp,
                    color = textSecondary
                )
            }
        }
    }
}

@Composable
fun DownloadCsvCard(
    isExporting: Boolean,
    packetCount: Int,
    onClick: () -> Unit,
    cardColor: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    greenAccent: Color
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        backgroundColor = cardColor,
        elevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(18.dp))
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    text = "Raw Packet History",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (packetCount > 0) "$packetCount packets (includes raw integer)" else "Collecting packets...",
                    fontSize = 11.sp,
                    color = textSecondary
                )
            }

            Button(
                onClick = onClick,
                enabled = !isExporting && packetCount > 0,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = greenAccent,
                    contentColor = Color.White,
                    disabledBackgroundColor = greenAccent.copy(alpha = 0.4f),
                    disabledContentColor = Color.White.copy(alpha = 0.6f)
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Saving...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    borderColor: Color
) {
    val hexString = remember(bytes) {
        bytes.joinToString(" ") { String.format("%02X", it) }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        backgroundColor = backgroundColor,
        elevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = hexString,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = textPrimary,
                letterSpacing = 1.sp,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun IntegerConversionViewerCard(
    bytes: ByteArray,
    backgroundColor: Color,
    textPrimary: Color,
    borderColor: Color
) {
    val uint8List = remember(bytes) { bytes.map { it.toInt() and 0xFF } }

    Card(
        shape = RoundedCornerShape(16.dp),
        backgroundColor = backgroundColor,
        elevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = uint8List.joinToString(", "),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = textPrimary,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, color: Color) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
    )
}

/**
 * Extracts the actual device sensor payload from the raw BLE advertising frame.
 * If Manufacturer Specific Data (Type 0xFF) exists, returns its payload bytes.
 * Otherwise falls back to the full byte array.
 */
fun extractActualDevicePayload(bytes: ByteArray?): ByteArray {
    if (bytes == null || bytes.isEmpty()) return ByteArray(0)
    var i = 0
    while (i < bytes.size) {
        val length = bytes[i].toInt() and 0xFF
        if (length == 0 || i + length >= bytes.size) break
        val type = bytes[i + 1].toInt() and 0xFF
        if (type == 0xFF && length >= 2) {
            return bytes.sliceArray((i + 2)..(i + length))
        }
        i += length + 1
    }
    return bytes
}
