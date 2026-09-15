package com.blesense.app.view.screens

import android.app.Activity
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.blesense.app.ui.theme.BleSenseColors
import com.blesense.app.model.*
import com.blesense.app.viewmodel.*
import com.blesense.app.repository.*
import com.blesense.app.util.*
import com.blesense.app.view.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Design tokens
private val GreenAccent     = Color(0xFF00BC7D)
private val GreenDark       = Color(0xFF0D542B)
private val BlueAccent      = Color(0xFF60A5FA)
private val PurpleAccent    = Color(0xFFA78BFA)

@Composable
fun AWSAdvertisingScreen(
    deviceAddress: String,
    deviceName: String,
    navController: NavController,
    viewModel: BluetoothScanViewModel
) {
    val context  = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(activity) {
        activity?.let { viewModel.startScan(it) }
    }

    val devices by viewModel.devices.collectAsState()
    val currentDevice by remember(devices, deviceAddress) {
        derivedStateOf { devices.find { it.address == deviceAddress } }
    }
    
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()

    val bgColor = if (isDarkMode) BleSenseColors.BackgroundDark else BleSenseColors.LightBackground
    val surfaceColor = if (isDarkMode) BleSenseColors.SurfaceDark else BleSenseColors.LightSurface
    val cardColor = if (isDarkMode) BleSenseColors.SurfaceDark else BleSenseColors.LightSurface
    val cardColor2 = if (isDarkMode) BleSenseColors.SurfaceLight else Color(0xFFF1F5F9)
    val dividerColor = if (isDarkMode) BleSenseColors.BorderDark else BleSenseColors.LightBorder
    val textPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val textSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    DisposableEffect(navController) {
        onDispose {
            // Preservation of scanning state on disposal to prevent UI interruptions
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Top Bar
            Surface(
                color     = surfaceColor,
                tonalElevation = 0.dp,
                modifier  = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textPrimary)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(GreenAccent.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Cloud, null, tint = GreenAccent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = deviceName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = deviceAddress,
                            fontSize = 10.sp,
                            color = textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    IconButton(onClick = { navController.navigate("aws_live_hub") }) {
                        Icon(Icons.Default.Analytics, contentDescription = "Analytics", tint = GreenAccent)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Info Section
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(16.dp),
                    color    = cardColor,
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.size(36.dp).background(BlueAccent.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Bluetooth, null, tint = BlueAccent, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text("Device Name", fontSize = 10.sp, color = textSecondary)
                                Text(deviceName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                            }
                        }
                        Divider(color = dividerColor, thickness = 0.5.dp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.size(36.dp).background(GreenAccent.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Tag, null, tint = GreenAccent, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text("Node ID", fontSize = 10.sp, color = textSecondary)
                                Text(deviceAddress, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                            }
                        }
                    }
                }

                // Data Grid
                val awsData = currentDevice?.sensorData as? SensorData.AWSData
                if (awsData != null) {
                    val coreMetrics = listOf(
                        "Temperature" to "${awsData.temperature}°C",
                        "Humidity" to "${awsData.humidity}%",
                        "Wind Speed" to "${awsData.windSpeed} m/s",
                        "Wind Direction" to "${awsData.windDirection}°",
                        "RF Cumulative" to awsData.rfCumulative,
                        "Battery Voltage" to "${awsData.batteryVoltage}V",
                        "Solar Voltage" to "${awsData.solarVoltage}V",
                        "Signal Strength" to "${awsData.signalStrength} dBm"
                    )

                    val activeErrors = listOf(
                        awsData.error1, awsData.error2, awsData.error3, awsData.error4,
                        awsData.error5, awsData.error6, awsData.error7, awsData.error8,
                        awsData.error9, awsData.error10, awsData.error11, awsData.error12,
                        awsData.error13, awsData.error14, awsData.error15, awsData.error16
                    ).filter { it != "None" }

                    // Check for "Not Ready" byte (99)
                    val isNotReady = currentDevice?.scanRecordBytes?.any { it.toInt() == 99 } ?: false
                    val totalErrorCount = awsData.totalErrors.toIntOrNull() ?: 0
                    var showRawPayload by remember { mutableStateOf(false) }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        
                        // Dynamic Device Status Message
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                isNotReady -> Color(0xFFFFF7ED)
                                totalErrorCount > 0 -> Color(0xFFFEF2F2)
                                else -> Color(0xFFECFDF5)
                            },
                            border = BorderStroke(1.dp, when {
                                isNotReady -> Color(0xFFFFEDD5)
                                totalErrorCount > 0 -> Color(0xFFFEE2E2)
                                else -> Color(0xFFD1FAE5)
                            })
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = when {
                                        isNotReady -> Icons.Default.Info
                                        totalErrorCount > 0 -> Icons.Default.Error
                                        else -> Icons.Default.CheckCircle
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        isNotReady -> Color(0xFFEA580C)
                                        totalErrorCount > 0 -> Color(0xFFDC2626)
                                        else -> Color(0xFF059669)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = when {
                                        isNotReady -> "Device = '$deviceName' is not ready"
                                        totalErrorCount > 0 -> "Device = '$deviceName' has $totalErrorCount errors"
                                        else -> "Device = '$deviceName' is ready and sending data"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isNotReady -> Color(0xFF9A3412)
                                        totalErrorCount > 0 -> Color(0xFF991B1B)
                                        else -> Color(0xFF065F46)
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // 1. Dynamic Error Diagnostics Section (MOVED ABOVE Core Metrics)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = cardColor,
                            shadowElevation = 2.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(
                                            imageVector = if (activeErrors.isEmpty()) Icons.Default.CheckCircle else Icons.Default.Error,
                                            contentDescription = null,
                                            tint = if (activeErrors.isEmpty()) Color(0xFF10B981) else Color(0xFFEF4444),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text("System Diagnostics", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (activeErrors.isEmpty()) Color(0xFFECFDF5) else Color(0xFFFEF2F2),
                                                CircleShape
                                            )
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Total Errors: ${awsData.totalErrors}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (activeErrors.isEmpty()) Color(0xFF059669) else Color(0xFFDC2626)
                                        )
                                    }
                                }

                                if (activeErrors.isNotEmpty()) {
                                    Spacer(Modifier.height(12.dp))
                                    activeErrors.forEach { error ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .background(Color(0xFFDC2626).copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                                .border(0.5.dp, Color(0xFFDC2626).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(Icons.Default.Warning, null, tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp))
                                            Text(error, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                        }
                                    }
                                } else {
                                    Spacer(Modifier.height(8.dp))
                                    Text("All sensors and systems operational.", fontSize = 11.sp, color = textSecondary)
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Text("Environmental & System Data", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)

                        // 2. Core Metrics Grid
                        coreMetrics.chunked(2).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { (label, value) ->
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        color = cardColor,
                                        border = BorderStroke(0.5.dp, dividerColor),
                                        shadowElevation = 1.dp
                                    ) {
                                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(label, fontSize = 10.sp, color = textSecondary)
                                            Text(value, fontSize = 15.sp, color = GreenAccent, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }

                    // Raw Payload
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = cardColor,
                        shadowElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Code, null, tint = PurpleAccent, modifier = Modifier.size(16.dp))
                                    Text("Raw Payload", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                                }
                                
                                Switch(
                                    checked = showRawPayload,
                                    onCheckedChange = { showRawPayload = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = PurpleAccent,
                                        uncheckedThumbColor = textSecondary,
                                        uncheckedTrackColor = dividerColor
                                    )
                                )
                            }
                            
                            if (showRawPayload) {
                                Surface(shape = RoundedCornerShape(8.dp), color = cardColor2) {
                                    Text(
                                        text = awsData.rawData,
                                        fontSize = 11.sp,
                                        color = PurpleAccent,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GreenAccent)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}


