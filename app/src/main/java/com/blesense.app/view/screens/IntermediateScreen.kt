package com.blesense.app.view.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.blesense.app.model.DashboardCardItem
import com.blesense.app.model.SensorData
import com.blesense.app.ui.theme.BleSenseColors
import com.blesense.app.util.ThemeManager
import com.blesense.app.view.components.ReferenceBottomNavBar
import com.blesense.app.viewmodel.BluetoothScanViewModel

@Composable
fun IntermediateScreen(
    navController: NavHostController,
    bluetoothViewModel: BluetoothScanViewModel? = null,
    isDarkMode: Boolean,
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    // Automatically ensure continuous scan is active on the dashboard independently
    LaunchedEffect(Unit) {
        bluetoothViewModel?.ensureScanning(activity)
    }

    val bluetoothDevicesRaw = bluetoothViewModel?.devices?.collectAsState()?.value ?: emptyList()

    // 1. Sensor Hub Devices (Strict isolation matching HomeScreen)
    val sensorHubDevices = remember(bluetoothDevicesRaw) {
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
                    name.contains("ATRH", true) ||
                    name.contains("Rain", true) ||
                    name.contains("Wind", true)

            val hasSensorData = sensorData != null

            (isKnownType || hasSensorData) &&
                    sensorData !is SensorData.AWSData &&
                    sensorData !is SensorData.DataLoggerData &&
                    sensorData !is SensorData.StepCounterData &&
                    !name.contains("Step", ignoreCase = true) &&
                    !name.contains("DataLogger", ignoreCase = true) &&
                    !name.contains("DLOG", ignoreCase = true) &&
                    !name.contains("AWS", ignoreCase = true) &&
                    !name.equals("test", ignoreCase = true)
        }
    }

    // 2. Step Counter Devices
    val stepCounterDevices = remember(bluetoothDevicesRaw) {
        bluetoothDevicesRaw.filter { device ->
            (device.name.contains("Step", ignoreCase = true) ||
                    device.sensorData is SensorData.StepCounterData) &&
                    !device.name.equals("test", ignoreCase = true)
        }
    }

    // 3. LiveStock / DataLogger Devices
    val dataLoggerDevices = remember(bluetoothDevicesRaw) {
        bluetoothDevicesRaw.filter { device ->
            (device.name.contains("DataLogger", ignoreCase = true) ||
                    device.name.contains("DLOG", ignoreCase = true) ||
                    device.sensorData is SensorData.DataLoggerData) &&
                    !device.name.equals("test", ignoreCase = true)
        }
    }

    // 4. AWS Station Devices
    val awsDevices = remember(bluetoothDevicesRaw) {
        bluetoothDevicesRaw.filter { device ->
            (device.name.contains("AWS", ignoreCase = true) ||
                    device.address.equals("DE:AD:BE:AF:BA:11", ignoreCase = true) ||
                    device.sensorData is SensorData.AWSData) &&
                    !device.name.equals("test", ignoreCase = true)
        }
    }

    // System-wide discovered & active totals across all distinct physical hardware
    val allTrackedDevices = remember(sensorHubDevices, stepCounterDevices, dataLoggerDevices, awsDevices) {
        (sensorHubDevices + stepCounterDevices + dataLoggerDevices + awsDevices).distinctBy { it.address }
    }
    val totalDiscovered = allTrackedDevices.size
    val totalActiveDevices = allTrackedDevices.count { it.sensorData != null }

    val bgColor = if (isDarkMode) BleSenseColors.BackgroundDark else BleSenseColors.LightBackground

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        backgroundColor = bgColor,
        bottomBar = {
            ReferenceBottomNavBar(
                navController = navController,
                currentRoute = "intermediate_screen",
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
            contentPadding = PaddingValues(top = 24.dp, bottom = 20.dp)
        ) {
            // 1. Top Bar Header
            item {
                DashboardTopHeader(
                    isDarkMode = isDarkMode,
                    onToggleTheme = { ThemeManager.toggleDarkMode(context, !isDarkMode) }
                )
            }

            // 2. Compact System Metrics Banner
            item {
                IntegratedHeroCard(
                    totalDiscovered = totalDiscovered,
                    activeSensors = totalActiveDevices,
                    isDarkMode = isDarkMode
                )
            }

            item {
                PrimaryFeaturedCard(
                    item = DashboardCardItem(
                        vectorIcon = Icons.Default.Bluetooth,
                        title = "Sensor Hub",
                        subtitle = "Discover, connect & monitor nearby BLE sensor pods",
                        badgeText = if (sensorHubDevices.isNotEmpty()) "${sensorHubDevices.size} Pods" else "Active Scan",
                        badgeBg = Color(0xFFEFF6FF),
                        badgeTextTint = Color(0xFF2563EB),
                        iconBg = Color(0xFFEFF6FF),
                        iconTint = Color(0xFF2563EB),
                        route = "home_screen"
                    ),
                    isDarkMode = isDarkMode,
                    onClick = { navController.navigate("home_screen") }
                )
            }

            item {
                GridDashboardCard(
                    item = DashboardCardItem(
                        vectorIcon = Icons.AutoMirrored.Filled.DirectionsWalk,
                        title = "Step Counter",
                        subtitle = "LIS3DH XYZ pedometer & walking dynamics",
                        badgeText = if (stepCounterDevices.isNotEmpty()) "${stepCounterDevices.size} Active" else "Pedometer",
                        badgeBg = Color(0xFFECFDF5),
                        badgeTextTint = Color(0xFF059669),
                        iconBg = Color(0xFFECFDF5),
                        iconTint = Color(0xFF059669),
                        route = "step_counter_screen"
                    ),
                    isDarkMode = isDarkMode,
                    onClick = { navController.navigate("step_counter_screen") }
                )
            }

            item {
                GridDashboardCard(
                    item = DashboardCardItem(
                        vectorIcon = Icons.Default.DeveloperBoard,
                        title = "LiveStock Logger",
                        subtitle = "Record & export high-precision logs",
                        badgeText = if (dataLoggerDevices.isNotEmpty()) "${dataLoggerDevices.size} Active" else "Active Logs",
                        badgeBg = Color(0xFFECFEFF),
                        badgeTextTint = Color(0xFF0891B2),
                        iconBg = Color(0xFFECFEFF),
                        iconTint = Color(0xFF0891B2),
                        route = "data_logger_list"
                    ),
                    isDarkMode = isDarkMode,
                    onClick = { navController.navigate("data_logger_list") }
                )
            }
            
            item {
                GridDashboardCard(
                    item = DashboardCardItem(
                        vectorIcon = Icons.Default.Cloud,
                        title = "AWS Station",
                        subtitle = "Weather station telemetry & stats",
                        badgeText = if (awsDevices.isNotEmpty()) "${awsDevices.size} Active" else "AWS Monitor",
                        badgeBg = Color(0xFFFFF7ED),
                        badgeTextTint = Color(0xFFEA580C),
                        iconBg = Color(0xFFFFF7ED),
                        iconTint = Color(0xFFEA580C),
                        route = "aws_scanner"
                    ),
                    isDarkMode = isDarkMode,
                    onClick = { navController.navigate("aws_scanner") }
                )
            }

            item {
                GridDashboardCard(
                    item = DashboardCardItem(
                        vectorIcon = Icons.Default.DirectionsCar,
                        title = "Robot Control",
                        subtitle = "Drive & joystick steering interface",
                        badgeText = "Drive Mode",
                        badgeBg = Color(0xFFF5F3FF),
                        badgeTextTint = Color(0xFF7C3AED),
                        iconBg = Color(0xFFF5F3FF),
                        iconTint = Color(0xFF7C3AED),
                        route = "robot_screen"
                    ),
                    isDarkMode = isDarkMode,
                    onClick = { navController.navigate("robot_screen") }
                )
            }
            
            item {
                GridDashboardCard(
                    item = DashboardCardItem(
                        vectorIcon = Icons.Default.Sensors,
                        title = "Raw Scanner",
                        subtitle = "Extended BLE advertising packets",
                        badgeText = "Raw Bytes",
                        badgeBg = Color(0xFFECFDF5),
                        badgeTextTint = Color(0xFF059669),
                        iconBg = Color(0xFFECFDF5),
                        iconTint = Color(0xFF059669),
                        route = "bigadv_scanner_screen"
                    ),
                    isDarkMode = isDarkMode,
                    onClick = { navController.navigate("bigadv_scanner_screen") }
                )
            }

            item {
                GridDashboardCard(
                    item = DashboardCardItem(
                        vectorIcon = Icons.Default.SportsEsports,
                        title = "Hero Game Zone",
                        subtitle = "Marvel beacon radar hunt & scavenger quest",
                        badgeText = "Play & Hunt",
                        badgeBg = Color(0xFFFDF2F8),
                        badgeTextTint = Color(0xFFDB2777),
                        iconBg = Color(0xFFFDF2F8),
                        iconTint = Color(0xFFDB2777),
                        route = "game_start_screen"
                    ),
                    isDarkMode = isDarkMode,
                    onClick = { navController.navigate("game_start_screen") }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun DashboardTopHeader(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit
) {
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color(0xFFA1A1AA) else Color(0xFF64748B)
    val iconBtnBg = if (isDarkMode) Color(0xFF1E293B).copy(alpha = 0.5f) else Color.White
    val iconBtnBorder = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color(0xFFE2EEF9)
    val senseGradient = if (isDarkMode) listOf(Color(0xFF38BDF8), Color(0xFF818CF8)) else listOf(Color(0xFF0284C7), Color(0xFF4F46E5))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp, top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF1D4ED8)))
                    )
                    .shadow(if (isDarkMode) 0.dp else 4.dp, RoundedCornerShape(16.dp), spotColor = Color(0xFF38BDF8)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "BLE ",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textPrimary,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Sense",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Transparent,
                        letterSpacing = (-0.5).sp,
                        style = androidx.compose.ui.text.TextStyle(
                            brush = Brush.linearGradient(colors = senseGradient)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Connect", fontSize = 10.sp, color = textSecondary.copy(alpha = 0.8f))
                    Box(modifier = Modifier.size(3.dp).background(Color(0xFF38BDF8), CircleShape))
                    Text("Monitor", fontSize = 10.sp, color = textSecondary.copy(alpha = 0.8f))
                    Box(modifier = Modifier.size(3.dp).background(Color(0xFF38BDF8), CircleShape))
                    Text("Control", fontSize = 10.sp, color = textSecondary.copy(alpha = 0.8f))
                }
            }
        }

        IconButton(
            onClick = onToggleTheme,
            modifier = Modifier
                .size(42.dp)
                .background(iconBtnBg, CircleShape)
                .border(1.dp, iconBtnBorder, CircleShape)
                .shadow(if (isDarkMode) 0.dp else 2.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.1f))
        ) {
            Icon(
                imageVector = if (isDarkMode) Icons.Default.WbSunny else Icons.Default.NightsStay,
                contentDescription = "Toggle Theme",
                tint = textPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun IntegratedHeroCard(
    totalDiscovered: Int,
    activeSensors: Int,
    isDarkMode: Boolean
) {
    val bgGradient = if (isDarkMode) listOf(Color(0xFF061428), Color(0xFF040E1E)) else listOf(Color(0xFFF0F9FF), Color(0xFFE0F2FE))
    val textMain = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val textSub = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val boxBg = if (isDarkMode) Color(0xFF0F172A).copy(alpha = 0.6f) else Color.White
    val boxBorder = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color(0xFFBAE6FD)
    val glow = if (isDarkMode) Color(0xFF06B6D4) else Color(0xFF0284C7)
    val onlineBg = if (isDarkMode) Color(0xFF082F49).copy(alpha = 0.6f) else Color(0xFFBAE6FD).copy(alpha = 0.4f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = glow.copy(alpha = if (isDarkMode) 0.3f else 0.1f),
                ambientColor = glow.copy(alpha = if (isDarkMode) 0.1f else 0.05f)
            )
            .border(1.dp, glow.copy(alpha = if (isDarkMode) 0.3f else 0.2f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        elevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(bgGradient))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                // Top Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = null,
                            tint = if (isDarkMode) Color(0xFF22D3EE) else glow,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "System Status",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = textMain
                            )
                            Text(
                                text = "All systems operational",
                                fontSize = 12.sp,
                                color = textSub
                            )
                        }
                    }

                    // Online Badge
                    Row(
                        modifier = Modifier
                            .border(1.dp, glow.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .background(onlineBg, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(if (isDarkMode) Color(0xFF22D3EE) else glow, CircleShape))
                        Text(
                            text = "ONLINE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkMode) Color(0xFF22D3EE) else glow,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Discovered Box
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .background(boxBg, RoundedCornerShape(16.dp))
                            .border(1.dp, boxBorder, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Brush.linearGradient(listOf(Color(0xFF60A5FA), Color(0xFF2563EB))), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(totalDiscovered.toString(), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = textMain)
                            Text("Discovered", fontSize = 11.sp, color = textSub)
                        }
                    }

                    // Active Pods Box
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .background(boxBg, RoundedCornerShape(16.dp))
                            .border(1.dp, boxBorder, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Brush.linearGradient(listOf(Color(0xFF34D399), Color(0xFF059669))), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.WifiTethering, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(activeSensors.toString(), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = textMain)
                            Text("Active Devices", fontSize = 11.sp, color = textSub)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderTitle(
    title: String,
    subtitle: String,
    isDarkMode: Boolean
) {
    val titleColor = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val subtitleColor = if (isDarkMode) BleSenseColors.TextSecondary else Color(0xFF64748B)

    Column(modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = titleColor
        )
        Text(
            text = subtitle,
            fontSize = 11.sp,
            color = subtitleColor
        )
    }
}

@Composable
private fun PrimaryFeaturedCard(
    item: DashboardCardItem,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val bgGradient = if (isDarkMode) listOf(Color(0xFF1E1B4B), Color(0xFF0F172A)) else listOf(Color(0xFFEEF2FF), Color(0xFFE0E7FF))
    val textMain = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val textSub = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val border = if (isDarkMode) Color(0xFF6366F1).copy(alpha = 0.3f) else Color(0xFFC7D2FE)
    val shadow = if (isDarkMode) Color(0xFF6366F1) else Color(0xFF4F46E5)
    val badgeBg = if (isDarkMode) Color(0xFF312E81).copy(alpha = 0.4f) else Color.White
    val badgeBorder = if (isDarkMode) Color(0xFF6366F1).copy(alpha = 0.5f) else Color(0xFFC7D2FE)
    val badgeText = if (isDarkMode) Color(0xFFA5B4FC) else Color(0xFF4F46E5)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = shadow.copy(alpha = if (isDarkMode) 0.3f else 0.1f),
                ambientColor = shadow.copy(alpha = if (isDarkMode) 0.1f else 0.05f)
            )
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        elevation = 0.dp,
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(bgGradient))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF818CF8), Color(0xFF4F46E5)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.vectorIcon,
                        contentDescription = item.title,
                        modifier = Modifier.size(28.dp),
                        tint = Color.White
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textMain,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .border(1.dp, badgeBorder, RoundedCornerShape(16.dp))
                                .background(badgeBg, RoundedCornerShape(16.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = item.badgeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeText
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = badgeText,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.subtitle,
                        fontSize = 10.sp, // reduced to 10sp to ensure it fits on small screens
                        color = textSub,
                        lineHeight = 12.sp, // reduced line height
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun GridDashboardCard(
    item: DashboardCardItem,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val baseGlow = when(item.title) {
        "LiveStock Logger" -> Color(0xFF10B981)
        "AWS Station" -> Color(0xFFF59E0B)
        "Robot Control" -> Color(0xFF8B5CF6)
        "Raw Scanner" -> Color(0xFF06B6D4)
        "Hero Game Zone" -> Color(0xFFEC4899)
        else -> Color(0xFF6366F1)
    }

    val cardBg = if (isDarkMode) Color(0xFF0F172A) else Color.White
    val textMain = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val textSub = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val border = if (isDarkMode) Color.White.copy(alpha=0.05f) else Color(0xFFF1F5F9)
    val iconBtnBg = if (isDarkMode) baseGlow.copy(alpha=0.15f) else baseGlow.copy(alpha=0.1f)
    val arrowTint = if (isDarkMode) Color(0xFF64748B) else Color(0xFF94A3B8)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        elevation = if (isDarkMode) 0.dp else 2.dp,
        shape = RoundedCornerShape(20.dp),
        backgroundColor = cardBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading Icon
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(iconBtnBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.vectorIcon,
                    contentDescription = item.title,
                    tint = baseGlow,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.subtitle,
                    fontSize = 12.sp,
                    color = textSub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Trailing Arrow
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isDarkMode) Color(0xFF1E293B) else Color(0xFFF8FAFC)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = arrowTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
