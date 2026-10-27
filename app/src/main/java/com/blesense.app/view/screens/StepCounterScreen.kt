package com.blesense.app.view.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.blesense.app.model.SensorData
import com.blesense.app.model.pedometer.StepResult
import com.blesense.app.ui.theme.BleSenseColors
import com.blesense.app.util.ThemeManager
import com.blesense.app.viewmodel.BluetoothScanViewModel
import java.util.Locale

@Composable
fun StepCounterScreen(
    navController: NavController,
    bluetoothViewModel: BluetoothScanViewModel?
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()

    // Ensure Step Counter scans completely independently on its own!
    LaunchedEffect(Unit) {
        bluetoothViewModel?.ensureScanning(activity)
    }

    // Collect Step Counter devices strictly
    val allDevices by (bluetoothViewModel?.stepCounterDevices?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
    val stepDevices = remember(allDevices) {
        allDevices.filter { dev ->
            dev.name.contains("Step", ignoreCase = true) || dev.sensorData is SensorData.StepCounterData
        }
    }

    val selectedAddress by (bluetoothViewModel?.selectedStepCounterAddress?.collectAsState() ?: remember { mutableStateOf(null) })
    val activeDevice = remember(stepDevices, selectedAddress) {
        if (selectedAddress != null) {
            stepDevices.find { it.address.equals(selectedAddress, ignoreCase = true) } ?: stepDevices.firstOrNull()
        } else {
            stepDevices.firstOrNull()
        }
    }

    val latestResult by (bluetoothViewModel?.latestStepResult?.collectAsState() ?: remember { mutableStateOf(null) })

    // Extract step data from either latestResult or activeDevice sensorData
    val activeStepData = (activeDevice?.sensorData as? SensorData.StepCounterData)
    val totalSteps = latestResult?.totalSteps ?: activeStepData?.totalSteps ?: 0
    val isWalking = latestResult?.isWalking ?: activeStepData?.isWalking ?: false
    val cadenceSpm = latestResult?.cadenceSpm ?: activeStepData?.cadenceSpm ?: 0
    val distanceMeters = latestResult?.distanceMeters ?: activeStepData?.distanceMeters ?: (totalSteps * 0.75f)
    val caloriesKcal = latestResult?.caloriesKcal ?: activeStepData?.caloriesKcal ?: (totalSteps * 0.04f)
    val magnitude = latestResult?.currentMagnitude ?: activeStepData?.magnitude ?: 0f
    val motion = latestResult?.currentMotion ?: activeStepData?.motion ?: 0f
    val xVal = activeStepData?.x ?: 0f
    val yVal = activeStepData?.y ?: 0f
    val zVal = activeStepData?.z ?: 0f

    var showResetDialog by remember { mutableStateOf(false) }
    var isTrackingPaused by remember { mutableStateOf(false) }

    val bgColor = if (isDarkMode) BleSenseColors.BackgroundDark else BleSenseColors.LightBackground
    val cardBg = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White
    val borderColor = if (isDarkMode) BleSenseColors.BorderDark else Color(0xFFE2EEF9)
    val textPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val textSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        backgroundColor = bgColor
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. Top Bar Header ─────────────────────────────────────────────
            StepCounterHeader(
                isDarkMode = isDarkMode,
                onBackPressed = { navController.popBackStack() },
                onToggleTheme = { ThemeManager.toggleDarkMode(context, !isDarkMode) },
                onResetClick = { showResetDialog = true }
            )

            // ── 2. Device Connectivity Status Card ────────────────────────────
            StepDeviceStatusCard(
                activeDevice = activeDevice,
                isDarkMode = isDarkMode,
                cardBg = cardBg,
                borderColor = borderColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )

            // ── 3. Main Circular Step Progress Ring ───────────────────────────
            CircularStepProgressMeter(
                totalSteps = totalSteps,
                stepGoal = 10000,
                isWalking = isWalking,
                isDarkMode = isDarkMode,
                cardBg = cardBg,
                borderColor = borderColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )

            // ── 4. Key Fitness Metrics Row (Cadence, Distance, Calories) ──────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Cadence
                MetricStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Cadence",
                    value = if (isWalking) "$cadenceSpm" else "0",
                    unit = "SPM",
                    icon = Icons.Default.Speed,
                    accentColor = Color(0xFF06B6D4),
                    cardBg = cardBg,
                    borderColor = borderColor,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )

                // Distance
                val distanceText = if (distanceMeters < 1000f) {
                    "${distanceMeters.toInt()}"
                } else {
                    String.format(Locale.US, "%.2f", distanceMeters / 1000f)
                }
                val distanceUnit = if (distanceMeters < 1000f) "meters" else "km"

                MetricStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Distance",
                    value = distanceText,
                    unit = distanceUnit,
                    icon = Icons.Default.Straighten,
                    accentColor = Color(0xFF6366F1),
                    cardBg = cardBg,
                    borderColor = borderColor,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )

                // Calories
                MetricStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Energy",
                    value = String.format(Locale.US, "%.1f", caloriesKcal),
                    unit = "kcal",
                    icon = Icons.Default.LocalFireDepartment,
                    accentColor = Color(0xFFF97316),
                    cardBg = cardBg,
                    borderColor = borderColor,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary
                )
            }

            // ── 5. LIS3DH Live Sensor Dynamics HUD ─────────────────────────────
            SensorDynamicsCard(
                magnitude = magnitude,
                motion = motion,
                x = xVal,
                y = yVal,
                z = zVal,
                isDarkMode = isDarkMode,
                cardBg = cardBg,
                borderColor = borderColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )

            // ── 6. Controls Bar (Pause/Resume, Reset) ──────────────────────────
            StepControlsRow(
                isPaused = isTrackingPaused,
                onTogglePause = {
                    isTrackingPaused = !isTrackingPaused
                    bluetoothViewModel?.toggleStepCounterPause(activeDevice?.address)
                },
                onReset = { showResetDialog = true },
                isDarkMode = isDarkMode
            )

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // ── Reset Confirmation Dialog ─────────────────────────────────────────────
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = "Reset Step Count?",
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            },
            text = {
                Text(
                    text = "This will reset your current steps, distance, and calories back to zero.",
                    color = textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        bluetoothViewModel?.resetStepCounter(activeDevice?.address)
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Reset", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = textSecondary)
                }
            },
            backgroundColor = cardBg,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// SUB-COMPONENTS
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun StepCounterHeader(
    isDarkMode: Boolean,
    onBackPressed: () -> Unit,
    onToggleTheme: () -> Unit,
    onResetClick: () -> Unit
) {
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color(0xFFA1A1AA) else Color(0xFF64748B)
    val iconBtnBg = if (isDarkMode) Color(0xFF1E293B).copy(alpha = 0.8f) else Color.White
    val iconBtnBorder = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color(0xFFE2EEF9)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBackPressed,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconBtnBg)
                    .border(1.dp, iconBtnBorder, CircleShape)
                    .shadow(if (isDarkMode) 0.dp else 2.dp, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = "Step Counter",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textPrimary,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "LIS3DH Real-time Pedometer",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = textSecondary
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Reset Button in Header
            IconButton(
                onClick = onResetClick,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconBtnBg)
                    .border(1.dp, iconBtnBorder, CircleShape)
                    .shadow(if (isDarkMode) 0.dp else 2.dp, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Theme Toggle
            IconButton(
                onClick = onToggleTheme,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconBtnBg)
                    .border(1.dp, iconBtnBorder, CircleShape)
                    .shadow(if (isDarkMode) 0.dp else 2.dp, CircleShape)
            ) {
                Icon(
                    imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Theme",
                    tint = if (isDarkMode) Color(0xFFFBBF24) else Color(0xFF475569),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun StepDeviceStatusCard(
    activeDevice: com.blesense.app.model.BluetoothDeviceModel?,
    isDarkMode: Boolean,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isDarkMode) 0.dp else 2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = cardBg,
        border = BorderStroke(1.dp, borderColor)
    ) {
        if (activeDevice != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF10B981), Color(0xFF059669))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = activeDevice.name.ifBlank { "Step Counter" },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Online blinking dot
                            PulsingDot(color = Color(0xFF10B981))
                        }
                        Text(
                            text = activeDevice.address,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = textSecondary
                        )
                    }
                }

                // RSSI Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isDarkMode) Color(0xFF064E3B) else Color(0xFFD1FAE5))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SignalCellularAlt,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${activeDevice.rssi} dBm",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669)
                        )
                    }
                }
            }
        } else {
            // Searching State
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFF10B981),
                    strokeWidth = 2.5.dp
                )
                Column {
                    Text(
                        text = "Searching for Step Counter pod...",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    Text(
                        text = "Scanning for nearby BLE accelerometer beacon",
                        fontSize = 11.sp,
                        color = textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun CircularStepProgressMeter(
    totalSteps: Int,
    stepGoal: Int,
    isWalking: Boolean,
    isDarkMode: Boolean,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val progress = (totalSteps.toFloat() / stepGoal.toFloat()).coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "stepProgress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isDarkMode) 0.dp else 4.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        backgroundColor = cardBg,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Circular Meter Canvas
            Box(
                modifier = Modifier.size(230.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 20.dp.toPx()
                    val arcSize = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                    // 1. Background Track Arc (270 degrees sweep)
                    drawArc(
                        color = if (isDarkMode) Color(0xFF334155).copy(alpha = 0.4f) else Color(0xFFE2E8F0),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(arcSize, arcSize),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // 2. Active Progress Arc with Gradient
                    val activeSweep = 270f * animatedProgress
                    if (activeSweep > 0f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color(0xFF10B981),
                                    Color(0xFF06B6D4),
                                    Color(0xFF3B82F6),
                                    Color(0xFF10B981)
                                )
                            ),
                            startAngle = 135f,
                            sweepAngle = activeSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(arcSize, arcSize),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                // Inner Content of the Dial
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                        tint = if (isWalking) Color(0xFF10B981) else textSecondary,
                        modifier = Modifier.size(32.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = String.format(Locale.US, "%,d", totalSteps),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textPrimary,
                        letterSpacing = (-1).sp
                    )

                    Text(
                        text = "STEPS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textSecondary,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Goal: ${String.format(Locale.US, "%,d", stepGoal)} (${(progress * 100).toInt()}%)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = textSecondary
                    )
                }
            }

            // Walking Status Badge
            AnimatedContent(
                targetState = isWalking,
                label = "walkingBadge"
            ) { walking ->
                if (walking) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(30.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF10B981), Color(0xFF059669))
                                )
                            )
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DirectionsRun,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "WALKING IN PROGRESS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(30.dp))
                            .background(if (isDarkMode) Color(0xFF334155) else Color(0xFFF1F5F9))
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessibilityNew,
                                contentDescription = null,
                                tint = textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "IDLE / RESTING",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textSecondary,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    accentColor: Color,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Card(
        modifier = modifier.shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = cardBg,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = textSecondary
            )

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textPrimary
                )
                Text(
                    text = unit,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun SensorDynamicsCard(
    magnitude: Float,
    motion: Float,
    x: Float,
    y: Float,
    z: Float,
    isDarkMode: Boolean,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isDarkMode) 0.dp else 2.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = cardBg,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "LIS3DH Telemetry",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                }

                Text(
                    text = "Magnitude: ${String.format(Locale.US, "%.2f", magnitude)} m/s²",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF10B981)
                )
            }

            // Magnitude Level Progress Bar (scale up to ~25 m/s²)
            val magnitudeProgress = (magnitude / 25f).coerceIn(0f, 1f)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Total Motion Amplitude",
                        fontSize = 11.sp,
                        color = textSecondary
                    )
                    Text(
                        text = "AC Motion: ${String.format(Locale.US, "%.2f", motion)} m/s² (Step Threshold: 0.45)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (motion > 0.45f) Color(0xFF10B981) else textSecondary
                    )
                }

                LinearProgressIndicator(
                    progress = magnitudeProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF10B981),
                    backgroundColor = if (isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0)
                )
            }

            // 3-Axis Accelerometer Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AxisChip(
                    modifier = Modifier.weight(1f),
                    label = "X-Axis",
                    value = String.format(Locale.US, "%+.2f", x),
                    color = Color(0xFF06B6D4),
                    isDarkMode = isDarkMode
                )
                AxisChip(
                    modifier = Modifier.weight(1f),
                    label = "Y-Axis",
                    value = String.format(Locale.US, "%+.2f", y),
                    color = Color(0xFF6366F1),
                    isDarkMode = isDarkMode
                )
                AxisChip(
                    modifier = Modifier.weight(1f),
                    label = "Z-Axis",
                    value = String.format(Locale.US, "%+.2f", z),
                    color = Color(0xFFF59E0B),
                    isDarkMode = isDarkMode
                )
            }
        }
    }
}

@Composable
private fun AxisChip(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color,
    isDarkMode: Boolean
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDarkMode) color.copy(alpha = 0.12f) else color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = "$value m/s²",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isDarkMode) Color.White else Color(0xFF0F172A),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StepControlsRow(
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onReset: () -> Unit,
    isDarkMode: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Pause/Resume Button
        Button(
            onClick = onTogglePause,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            border = BorderStroke(
                1.dp,
                if (isPaused) Color(0xFF10B981) else (if (isDarkMode) Color(0xFF475569) else Color(0xFFCBD5E1))
            ),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (isPaused) Color(0xFF10B981) else (if (isDarkMode) Color(0xFF1E293B) else Color.White)
            ),
            elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 2.dp)
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = null,
                tint = if (isPaused) Color.White else (if (isDarkMode) Color.White else Color(0xFF0F172A)),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isPaused) "Resume Tracking" else "Pause Tracking",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                color = if (isPaused) Color.White else (if (isDarkMode) Color.White else Color(0xFF0F172A))
            )
        }

        // Reset Button
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(
                backgroundColor = Color(0xFFEF4444).copy(alpha = 0.08f)
            )
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Reset Counter",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xFFEF4444)
            )
        }
    }
}
