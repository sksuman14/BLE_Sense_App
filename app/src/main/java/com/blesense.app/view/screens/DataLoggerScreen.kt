package com.blesense.app.view.screens

import kotlinx.coroutines.isActive
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.widget.Toast
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import no.nordicsemi.android.kotlin.ble.advertiser.BleAdvertiser
import no.nordicsemi.android.kotlin.ble.core.advertiser.BleAdvertisingConfig
import no.nordicsemi.android.kotlin.ble.core.advertiser.BleAdvertisingData
import no.nordicsemi.android.kotlin.ble.core.advertiser.BleAdvertisingInterval
import no.nordicsemi.android.kotlin.ble.core.advertiser.BleAdvertisingSettings
import no.nordicsemi.android.kotlin.ble.core.advertiser.BleTxPowerLevel
import no.nordicsemi.android.kotlin.ble.core.advertiser.ManufacturerData
import no.nordicsemi.android.kotlin.ble.core.data.util.DataByteArray
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeoutOrNull
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.blesense.app.model.*
import com.blesense.app.viewmodel.*
import com.blesense.app.repository.*
import com.blesense.app.util.*
import com.blesense.app.view.components.*
import com.blesense.app.repository.DataLoggerRepository
import com.blesense.app.model.DataLoggerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily.Companion.Monospace

import com.blesense.app.ui.theme.BleSenseColors

// ─── Theme Helpers ────────────────────────────────────────────────────────────

data class DataLoggerTheme(
    val isDarkMode: Boolean = false,
    val background: Color = Color(0xFFF2F5F9),
    val cardBackground: Color = Color(0xFFFFFFFF),
    val accentOrange: Color = Color(0xFF2563EB),
    val textPrimary: Color = Color(0xFF0F172A),
    val textSecondary: Color = Color(0xFF64748B),
    val successGreen: Color = Color(0xFF059669),
    val border: Color = Color(0xFFE2EEF9),
    val inactiveTick: Color = Color(0xFFCBD5E1),
    val microDotInactive: Color = Color(0xFF94A3B8),
    val codeBlockBg: Color = Color(0xFFF8FAFC),
    val codeBlockText: Color = Color(0xFF1E40AF),
    val buttonDisabledBg: Color = Color(0xFFE2E8F0),
    val buttonDisabledContent: Color = Color(0xFF94A3B8),
    val switchTrackUnchecked: Color = Color(0xFFCBD5E1),
    val badgeBg: Color = Color(0xFF059669).copy(alpha = 0.1f),
    val badgeText: Color = Color(0xFF059669),
    val badgeBorder: Color = Color(0xFF059669).copy(alpha = 0.3f)
) {
    companion object {
        fun create(isDarkMode: Boolean): DataLoggerTheme {
            return if (isDarkMode) {
                DataLoggerTheme(
                    isDarkMode = true,
                    background = BleSenseColors.BackgroundDark,
                    cardBackground = BleSenseColors.SurfaceDark,
                    accentOrange = Color(0xFF38BDF8),
                    textPrimary = BleSenseColors.TextPrimary,
                    textSecondary = BleSenseColors.TextSecondary,
                    successGreen = Color(0xFF10B981),
                    border = BleSenseColors.BorderDark,
                    inactiveTick = Color(0xFF4A4A5E), // High-contrast, crisp visibility in dark mode
                    microDotInactive = Color(0xFF5A5A70),
                    codeBlockBg = Color(0xFF121218),
                    codeBlockText = Color(0xFFE2E8F0),
                    buttonDisabledBg = Color(0xFF22222C),
                    buttonDisabledContent = Color(0xFF71717A),
                    switchTrackUnchecked = Color(0xFF3E3E4E),
                    badgeBg = Color(0xFF10B981).copy(alpha = 0.15f),
                    badgeText = Color(0xFF34D399),
                    badgeBorder = Color(0xFF10B981).copy(alpha = 0.4f)
                )
            } else {
                DataLoggerTheme(
                    isDarkMode = false,
                    background = BleSenseColors.LightBackground,
                    cardBackground = BleSenseColors.LightSurface,
                    accentOrange = Color(0xFF2563EB),
                    textPrimary = BleSenseColors.LightTextPrimary,
                    textSecondary = BleSenseColors.LightTextSecondary,
                    successGreen = Color(0xFF059669),
                    border = BleSenseColors.LightBorder,
                    inactiveTick = Color(0xFFCBD5E1),
                    microDotInactive = Color(0xFF94A3B8),
                    codeBlockBg = Color(0xFFF8FAFC),
                    codeBlockText = Color(0xFF1E40AF),
                    buttonDisabledBg = Color(0xFFE2E8F0),
                    buttonDisabledContent = Color(0xFF94A3B8),
                    switchTrackUnchecked = Color(0xFFCBD5E1),
                    badgeBg = Color(0xFF059669).copy(alpha = 0.1f),
                    badgeText = Color(0xFF059669),
                    badgeBorder = Color(0xFF059669).copy(alpha = 0.3f)
                )
            }
        }
    }
}


// ─── Parsed Packet Model ──────────────────────────────────────────────────────

enum class DataLoggerSyncState {
    IDLE,
    SYNCING,
    COMPLETE,
    FAILED
}

data class ParsedPacket(
    val packetIndex: Int,
    val deviceId: Int,
    val latestPacketId: Int,
    val currentPacketId: Int,
    val footer: Int,
    val points: List<Triple<Int, Int, Int>>,
    val rawHex: String
)

fun parseRawDataToPackets(bytes: ByteArray): List<ParsedPacket> {
    if (bytes.isEmpty()) return emptyList()

    val packetSize = 246
    val packets = mutableListOf<ParsedPacket>()

    for (offset in 0 until bytes.size step packetSize) {
        val remaining = bytes.size - offset
        if (remaining < packetSize) break

        val end = offset + packetSize
        val packet = bytes.sliceArray(offset until end)

        // Mfg0-1 = Packet ID
        val packetId = (packet[0].toInt() and 0xFF) or ((packet[1].toInt() and 0xFF) shl 8)
        
        // Mfg2 = Node ID / Device ID
        val nodeId = packet[2].toInt() and 0xFF
        
        val points = mutableListOf<Triple<Int, Int, Int>>()
        val payloadStart = 3
        val payloadEnd = 243
        for (i in payloadStart until payloadEnd step 3) {
            if (i + 2 >= payloadEnd) break
            points.add(Triple(packet[i].toInt(), packet[i + 1].toInt(), packet[i + 2].toInt()))
        }

        val pktIdx = offset / packetSize

        // Mfg243-244 = Total Packets
        val totalPackets = (packet[243].toInt() and 0xFF) or ((packet[244].toInt() and 0xFF) shl 8)
        
        // Mfg245 = Footer / Round
        val footer = packet[245].toInt() and 0xFF

        packets.add(ParsedPacket(
            packetIndex       = pktIdx,
            deviceId          = nodeId,
            latestPacketId    = packetId,
            currentPacketId   = totalPackets,
            footer            = footer,
            points            = points,
            rawHex            = packet.joinToString(" ") { "%02X".format(it) }
        ))
    }
    return packets
}






// ─── Components ───────────────────────────────────────────────────────────────

private fun getGaugeGradientColor(fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return when {
        f < 0.33f -> {
            val t = f / 0.33f
            lerp(Color(0xFF00D4A0), Color(0xFF38BDF8), t)
        }
        f < 0.66f -> {
            val t = (f - 0.33f) / 0.33f
            lerp(Color(0xFF38BDF8), Color(0xFF8B5CF6), t)
        }
        else -> {
            val t = (f - 0.66f) / 0.34f
            lerp(Color(0xFF8B5CF6), Color(0xFFFF2A6D), t)
        }
    }
}

@Composable
fun SyncGauge(percentage: Float, theme: DataLoggerTheme) {
    val animatedPercentage by animateFloatAsState(
        targetValue = percentage.coerceIn(0f, 100f),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "GaugeAnim"
    )

    val percentStr = when {
        animatedPercentage <= 0f -> "0%"
        animatedPercentage < 1f -> String.format(Locale.US, "%.1f%%", animatedPercentage)
        else -> "${animatedPercentage.toInt()}%"
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(240.dp)
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.width / 2f - 16.dp.toPx()
                val innerRadius = outerRadius - 16.dp.toPx()
                val microRadius = innerRadius - 8.dp.toPx()

                val totalTicks = 64
                val startAngle = 140f
                val sweepAngle = 260f
                val activeTicks = if (animatedPercentage > 0f) ((animatedPercentage / 100f) * totalTicks).toInt().coerceAtLeast(1) else 0

                for (i in 0..totalTicks) {
                    val tickFraction = i.toFloat() / totalTicks
                    val angleDeg = startAngle + (tickFraction * sweepAngle)
                    val angleRad = Math.toRadians(angleDeg.toDouble())

                    val cosA = cos(angleRad).toFloat()
                    val sinA = sin(angleRad).toFloat()

                    val isActive = i <= activeTicks
                    val isTip = i == activeTicks && activeTicks > 0

                    val tickColor = if (isActive) {
                        getGaugeGradientColor(tickFraction)
                    } else {
                        theme.inactiveTick
                    }

                    val tickStartR = innerRadius
                    val tickEndR = if (isTip) outerRadius + 6.dp.toPx() else outerRadius
                    val strokeW = if (isTip) 3.2.dp.toPx() else if (isActive) 2.2.dp.toPx() else 1.8.dp.toPx()

                    val p1 = Offset(center.x + tickStartR * cosA, center.y + tickStartR * sinA)
                    val p2 = Offset(center.x + tickEndR * cosA, center.y + tickEndR * sinA)

                    drawLine(
                        color = tickColor,
                        start = p1,
                        end = p2,
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )

                    if (isTip) {
                        drawLine(
                            color = Color(0xFFFF2A6D).copy(alpha = 0.5f),
                            start = p1,
                            end = p2,
                            strokeWidth = strokeW + 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    if (i % 2 == 0) {
                        val microP = Offset(center.x + microRadius * cosA, center.y + microRadius * sinA)
                        val microColor = if (isActive) tickColor.copy(alpha = 0.7f) else theme.microDotInactive
                        drawCircle(
                            color = microColor,
                            radius = if (isActive) 1.5.dp.toPx() else 1.2.dp.toPx(),
                            center = microP
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = percentStr,
                    color = theme.textPrimary,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = Monospace,
                    letterSpacing = (-1).sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "SYNC PROGRESS",
                    color = if (theme.isDarkMode) Color(0xFFA1A1AA) else theme.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}

@Composable
fun StatBox(label: String, value: String, modifier: Modifier, theme: DataLoggerTheme, color: Color = theme.textPrimary) {
    Surface(
        modifier = modifier,
        color = theme.cardBackground,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, theme.border)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = theme.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Monospace)
        }
    }
}

@Composable
fun ManifestItem(index: Int, id: Int, total: Int, time: String, rawData: String, theme: DataLoggerTheme) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (expanded) theme.cardBackground.copy(alpha = if (theme.isDarkMode) 0.6f else 0.5f) else Color.Transparent)
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = theme.accentOrange.copy(alpha = if (theme.isDarkMode) 0.2f else 0.1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("#$index", color = theme.accentOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Spacer(Modifier.width(16.dp))
                Text("id $id/$total", color = theme.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = Monospace)
            }
            Text(time, color = theme.textSecondary, fontSize = 12.sp, fontFamily = Monospace)
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = theme.codeBlockBg,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, theme.border)
            ) {
                Text(
                    text = rawData,
                    color = theme.codeBlockText,
                    fontSize = 11.sp,
                    fontFamily = Monospace,
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun BroadcastSummaryLine(title: String, subtitle: String, theme: DataLoggerTheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = theme.textPrimary,
            fontFamily = Monospace
        )
        Text(
            text = subtitle,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = theme.textPrimary,
            fontFamily = Monospace
        )
    }
}

// ─── DataLoggerScreen ─────────────────────────────────────────────────────────

@SuppressLint("MissingPermission")
@Composable
fun DataLoggerScreen(
    loggerId: String,
    deviceAddress: String? = null,
    navController: NavController,
    viewModel: BluetoothScanViewModel = viewModel(factory = BluetoothScanViewModelFactory(LocalContext.current))
) {
    val loggerConfig = remember(loggerId) {
        DataLoggerRepository.getLoggerById(loggerId) ?: DataLoggerRepository.loggers.first()
    }
    val context      = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val theme = remember(isDarkMode) { DataLoggerTheme.create(isDarkMode) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(loggerConfig) {
        viewModel.setSelectedDataLogger(loggerConfig.deviceId, loggerConfig.advertiserAddress)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.setSelectedDataLogger(null, null)
        }
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN))
        }
        val act = context as? Activity
        if (act != null && !act.isFinishing && !act.isDestroyed) {
            viewModel.startContinuousScan(act)
        }
    }

    var syncState by remember { mutableStateOf(DataLoggerSyncState.IDLE) }
    var isGettingData by remember { mutableStateOf(false) }
    var isResetting by remember { mutableStateOf(false) }
    val isUploadingToDashboard by viewModel.isUploadingToDashboard.collectAsState()
    
    val activeTimeoutJob = remember { mutableStateOf<Job?>(null) }

    val allPacketHistory by viewModel.dataLoggerPacketHistory.collectAsState()
    val packetHistory = remember(allPacketHistory, loggerConfig.deviceId) {
        allPacketHistory[loggerConfig.deviceId] ?: emptyList()
    }

    // --- Stats observed directly from ViewModel StateFlows to prevent UI freezing ---
    val capturedCount by viewModel.capturedCount.collectAsState()
    val expectedCount by viewModel.expectedCount.collectAsState()
    val r1Count by viewModel.r1Count.collectAsState()
    val r2Count by viewModel.r2Count.collectAsState()
    val r3Count by viewModel.r3Count.collectAsState()

    val syncProgressValue = remember(capturedCount, expectedCount) {
        if (expectedCount > 0) (capturedCount.toFloat() / expectedCount.toFloat() * 100f).coerceIn(0f, 100f) else 0f
    }







    // ─── UI Logic ─────────────────────────────────────────────────────────

    var firstReceivedBundleId by remember { mutableIntStateOf(-1) }
    var isFinalDataAvailable by remember { mutableStateOf(packetHistory.isNotEmpty()) }
    var visibleLimit by remember { mutableIntStateOf(15) }
    
    LaunchedEffect(capturedCount, expectedCount) {
        if (capturedCount > 0) {
            isFinalDataAvailable = true
            isGettingData = false // Stop loading on first packet
            if (firstReceivedBundleId == -1) {
                firstReceivedBundleId = 1
            }
            if (expectedCount > 0 && capturedCount >= expectedCount) {
                syncState = DataLoggerSyncState.COMPLETE
            } else {
                syncState = DataLoggerSyncState.SYNCING
            }
        }
    }

    Box(Modifier.fillMaxSize().background(theme.background), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            // ── Header ──────────────────────────────────────────────────────
            StandardScreenHeaderBar(
                title = loggerConfig.name,
                subtitle = "Device ID: ${loggerConfig.deviceId} • MAC: ${loggerConfig.advertiserAddress}",
                isDarkMode = isDarkMode,
                onBackClick = {
                    viewModel.stopScan()
                    navController.popBackStack()
                }
            )

            Spacer(Modifier.height(24.dp))

            // ── Action Buttons ──────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        visibleLimit = 15
                        activeTimeoutJob.value?.cancel()
                        viewModel.stopTrigger()
                        
                        firstReceivedBundleId = -1
                        isFinalDataAvailable = false
                        viewModel.clearDataLoggerHistory(loggerConfig.deviceId)
                        
                        isGettingData = true
                        isResetting = false

                        activeTimeoutJob.value = coroutineScope.launch {
                            // 1. Prime scanner: ensure BLE scan is 100% active and healthy in native HAL
                            viewModel.prepareForDataCapture()
                            delay(150L)

                            // 2. Pulse trigger for 750ms (~25-35 advertise packets)
                            // Stopping before 1.0s ensures phone radio Tx is completely OFF
                            // before the hardware finishes its 2.0s flash setup and blasts Bundle 1!
                            viewModel.startTrigger(loggerConfig.getDataCommand, loggerConfig.advertiserAddress, 750L)
                            
                            // Monitor for incoming data; if no packets after 3.0s, send another quick pulse
                            var elapsedMs = 0
                            while (isActive && elapsedMs < 15000) {
                                delay(200)
                                elapsedMs += 200

                                if (viewModel.capturedCount.value > 0) {
                                    viewModel.stopTrigger()
                                    break 
                                }

                                // Retry chirp pulse every 3 seconds if no data has started yet
                                if (elapsedMs % 3000 == 0 && viewModel.capturedCount.value == 0) {
                                    android.util.Log.d("BLE", "No response yet, sending retry trigger pulse...")
                                    viewModel.startTrigger(loggerConfig.getDataCommand, loggerConfig.advertiserAddress, 600L)
                                }
                            }
                            
                            // If no data received after 15s, stop loading
                            val finalHistory = viewModel.dataLoggerPacketHistory.value[loggerConfig.deviceId] ?: emptyList()
                            if (finalHistory.isEmpty() && viewModel.capturedCount.value == 0) {
                                isGettingData = false
                            }
                        }
                    },

                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.accentOrange,
                        contentColor = Color.White,
                        disabledContainerColor = theme.buttonDisabledBg,
                        disabledContentColor = theme.buttonDisabledContent
                    ),
                    modifier = Modifier.weight(1.3f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = (!isGettingData && !isResetting)
                ) {
                    if (isGettingData) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Get data", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                OutlinedButton(
                    onClick = {
                        visibleLimit = 15
                        activeTimeoutJob.value?.cancel()
                        viewModel.stopTrigger()
                        
                        firstReceivedBundleId = -1
                        isGettingData = false
                        isResetting = true
                        viewModel.refreshScan()
                        viewModel.startTrigger(loggerConfig.resetCommand, loggerConfig.advertiserAddress, 10000L)
                        activeTimeoutJob.value = coroutineScope.launch {
                            delay(10000)
                            isResetting = false
                            isFinalDataAvailable = false
                            syncState = DataLoggerSyncState.IDLE
                        }
                    },
                    border = BorderStroke(1.dp, theme.border),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = theme.cardBackground,
                        contentColor = theme.textPrimary,
                        disabledContainerColor = theme.cardBackground.copy(alpha = 0.5f),
                        disabledContentColor = theme.textSecondary.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isResetting
                ) {
                    if (isResetting) CircularProgressIndicator(Modifier.size(20.dp), color = theme.textPrimary, strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reset", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Progress Card ───────────────────────────────────────────────
            Surface(modifier = Modifier.fillMaxWidth(), color = theme.cardBackground, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, theme.border)) {
                val showContent = isGettingData || isFinalDataAvailable
                SyncGauge(percentage = if (!showContent) 0f else syncProgressValue, theme = theme)
            }

            Spacer(Modifier.height(16.dp))

            // ── Stats Row ───────────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val showContent = isGettingData || isFinalDataAvailable
                
                val capturedPackets = capturedCount
                val expectedPackets = expectedCount
                val pendingPackets = (expectedPackets - capturedPackets).coerceAtLeast(0)

                val dispCaptured = if (showContent) capturedPackets else 0
                val dispExpected = if (showContent) expectedPackets else 0
                val dispPending = if (showContent) pendingPackets else 0

                StatBox("CAPTURED", "$dispCaptured", Modifier.weight(1f), theme)
                StatBox("EXPECTED", "$dispExpected", Modifier.weight(1f), theme)
                StatBox("PENDING", "$dispPending", Modifier.weight(1f), theme, color = theme.accentOrange)
            }

            Spacer(Modifier.height(16.dp))

            // ── Rounds Card ─────────────────────────────────────────────────
            Surface(modifier = Modifier.fillMaxWidth(), color = theme.cardBackground, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, theme.border)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val showContent = isGettingData || isFinalDataAvailable
                    val dispR1 = if (showContent) r1Count else 0
                    val dispR2 = if (showContent) r2Count else 0
                    val dispR3 = if (showContent) r3Count else 0

                    Text("BROADCAST SUMMARY", color = theme.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(2.dp))
                    
                    RoundLine("Round 1 (First Blast)", "$dispR1 unique packets", theme)
                    RoundLine("Round 2 (Retry 1)", "$dispR2 unique packets", theme)
                    RoundLine("Round 3 (Retry 2)", "$dispR3 unique packets", theme)
                }
            }




            Spacer(Modifier.height(16.dp))

            // ── Dashboard Upload ───────────────────────────────────────────
            Button(
                onClick = { viewModel.uploadCapturedDataToDashboard(loggerConfig.deviceId, loggerConfig.advertiserAddress ?: "") },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.accentOrange,
                    contentColor = Color.White,
                    disabledContainerColor = theme.buttonDisabledBg,
                    disabledContentColor = theme.buttonDisabledContent
                ),
                enabled = (!isUploadingToDashboard && packetHistory.isNotEmpty())
            ) {
                if (isUploadingToDashboard) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Uploading…", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Upload to Dashboard", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Export ──────────────────────────────────────────────────────
            var isExporting by remember { mutableStateOf(false) }
            val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
                if (uri != null) {
                    isExporting = true
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            context.contentResolver.openOutputStream(uri)?.use { os ->
                                os.bufferedWriter(Charsets.UTF_8).use { writer ->
                                    writer.write("Timestamp,Device_ID,Packet_ID,Last_Packet_ID,Raw_Hex\n")
                                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                                    val snapshot = packetHistory.reversed()
                                    for (packet in snapshot) {
                                        writer.write(sdf.format(Date(packet.timestamp)))
                                        writer.write(",")
                                        writer.write(packet.deviceId)
                                        writer.write(",")
                                        writer.write(packet.lastPacketId.toString())
                                        writer.write(",")
                                        writer.write(packet.currentPacketId.toString())
                                        writer.write(",\"")
                                        writer.write(packet.rawDataHex)
                                        writer.write("\"\n")
                                    }
                                    writer.flush()
                                }
                            }
                        } catch (_: Exception) {} finally { withContext(Dispatchers.Main) { isExporting = false } }
                    }
                }
            }
            OutlinedButton(
                onClick = { if (!isExporting && packetHistory.isNotEmpty()) exportLauncher.launch("${loggerConfig.name}_Export.csv") },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (packetHistory.isNotEmpty()) theme.border else theme.border.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = theme.cardBackground,
                    contentColor = theme.textPrimary,
                    disabledContainerColor = theme.cardBackground.copy(alpha = 0.4f),
                    disabledContentColor = theme.textSecondary.copy(alpha = 0.5f)
                ),
                enabled = (!isExporting && packetHistory.isNotEmpty())
            ) {
                Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (isExporting) "Exporting…" else "Export CSV", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(32.dp))
            Spacer(Modifier.height(32.dp))

            // ── Manifest ────────────────────────────────────────────────────
            var showManifest by remember { mutableStateOf(true) }
            val showManifestContent = isGettingData || isFinalDataAvailable
            val activeHistory = if (showManifestContent) packetHistory else emptyList()

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val manifestCountLabel = if (activeHistory.isNotEmpty()) " (${activeHistory.size})" else ""
                Text("PACKET MANIFEST$manifestCountLabel", color = theme.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Switch(
                    checked = showManifest,
                    onCheckedChange = { showManifest = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = theme.accentOrange,
                        uncheckedThumbColor = if (theme.isDarkMode) Color(0xFF94A3B8) else Color.White,
                        uncheckedTrackColor = theme.switchTrackUnchecked
                    )
                )
            }

            if (showManifest) {
                Spacer(Modifier.height(16.dp))

                if (activeHistory.isEmpty()) {
                    Text(
                        text = "No active session data. Tap Get data to start sync.",
                        color = theme.textSecondary,
                        fontSize = 12.sp,
                        fontFamily = Monospace,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    val displayList = remember(activeHistory, visibleLimit) {
                        activeHistory.takeLast(visibleLimit).reversed()
                    }
                    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        displayList.forEachIndexed { i, p ->
                            ManifestItem(
                                index = activeHistory.size - i,
                                id = p.lastPacketId,
                                total = p.currentPacketId,
                                time = timeFormat.format(Date(p.timestamp)),
                                rawData = p.rawDataHex,
                                theme = theme
                            )
                        }

                        if (activeHistory.size > visibleLimit) {
                            TextButton(
                                onClick = { visibleLimit += 15 },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("Show More (+15 packets)", color = theme.accentOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
fun RoundLine(label: String, value: String, theme: DataLoggerTheme) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = theme.textSecondary, fontSize = 12.sp, fontFamily = Monospace)
        Text(value, color = theme.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Monospace, textAlign = TextAlign.End)
    }
}
