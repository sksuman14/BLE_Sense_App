package com.blesense.app.view.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.blesense.app.model.DashboardCardItem
import com.blesense.app.ui.theme.BleSenseColors
import com.blesense.app.util.ThemeManager
import com.blesense.app.view.components.ReferenceBottomNavBar
import com.blesense.app.model.*
import com.blesense.app.viewmodel.BluetoothScanViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun IntermediateScreen(
    navController: NavHostController,
    bluetoothViewModel: BluetoothScanViewModel? = null,
    isDarkMode: Boolean
) {
    val context = LocalContext.current
    val bluetoothDevicesRaw = bluetoothViewModel?.devices?.collectAsState()?.value ?: emptyList()
    val bluetoothDevices = remember(bluetoothDevicesRaw) {
        bluetoothDevicesRaw.filter { device ->
            device.sensorData != null
        }
    }

    val totalDiscovered = bluetoothDevices.size
    val activeSensors = bluetoothDevices.count { it.sensorData != null }

    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dot"
    )

    val bgColor = if (isDarkMode) BleSenseColors.BackgroundDark else BleSenseColors.LightBackground

    Scaffold(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
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
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp)
        ) {
            item {
                DashboardTopHeader(
                    isDarkMode = isDarkMode,
                    onToggleTheme = { ThemeManager.toggleDarkMode(context, !isDarkMode) }
                )
            }

            item {
                DashboardHeroCard(
                    dotAlpha = dotAlpha,
                    totalDiscovered = totalDiscovered,
                    activeSensors = activeSensors,
                    isDarkMode = isDarkMode
                )
            }

            item {
                SectionHeaderTitle(title = "Primary Sensor Hub", subtitle = "Core Bluetooth scanning & data recording", isDarkMode = isDarkMode)
            }


            item {
                WideHeroDashboardCard(
                    item = DashboardCardItem(
                        vectorIcon = Icons.Default.Bluetooth,
                        title = "Sensor Hub",
                        subtitle = "Discover, connect & monitor nearby BLE sensor pods",
                        badgeText = if (totalDiscovered > 0) "$totalDiscovered Pods" else "Continuous Scan",
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
                WideHeroDashboardCard(
                    item = DashboardCardItem(
                        vectorIcon = Icons.Default.DeveloperBoard,
                        title = "LiveStock logger",
                        subtitle = "Record, store & export high-precision telemetry logs",
                        badgeText = "Loggers Active",
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
                SectionHeaderTitle(title = "AWS", subtitle = "AWS Environmental Weather Station", isDarkMode = isDarkMode)
            }

            item {
                WideHeroDashboardCard(
                    item = DashboardCardItem(
                        vectorIcon = Icons.Default.Cloud,
                        title = "AWS Weather Station",
                        subtitle = "Monitor AWS environmental telemetry & diagnostics",
                        badgeText = "AWS Monitor",
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
                SectionHeaderTitle(title = "Control & Diagnostics", subtitle = "Drive controls & steering", isDarkMode = isDarkMode)
            }

            item {
                WideHeroDashboardCard(
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
                WideHeroDashboardCard(
                    item = DashboardCardItem(
                        vectorIcon = Icons.Default.Sensors,
                        title = "BigAdv Raw Data Scanner",
                        subtitle = "Extended BLE advertising packets & raw data byte inspector",
                        badgeText = "Raw Packets",
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
                SectionHeaderTitle(title = "App Configuration", subtitle = "Manage system themes, account & credentials", isDarkMode = isDarkMode)
            }

            item {
                WideHeroDashboardCard(
                    item = DashboardCardItem(
                        vectorIcon = Icons.Default.Settings,
                        title = "System Settings & Preferences",
                        subtitle = "Theme controls, account security, permissions & calibration",
                        badgeText = "System Config",
                        badgeBg = Color(0xFFF8FAFC),
                        badgeTextTint = Color(0xFF475569),
                        iconBg = Color(0xFFF8FAFC),
                        iconTint = Color(0xFF475569),
                        route = "settings_screen"
                    ),
                    isDarkMode = isDarkMode,
                    onClick = { navController.navigate("settings_screen") }
                )
            }

            item {
                QuickStatsCard(
                    totalDiscovered = totalDiscovered,
                    activeSensors = activeSensors,
                    isDarkMode = isDarkMode
                )
            }

            item { Spacer(modifier = Modifier.height(10.dp)) }
        }
    }
}

@Composable
private fun DashboardTopHeader(
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit
) {
    val textPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val textSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary
    val headerCardBg = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White
    val borderColor = if (isDarkMode) BleSenseColors.BorderDark else Color(0xFFE2EEF9)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(headerCardBg)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF2563EB), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text("BLE Sense", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                    Text("SenseLink Pro", fontSize = 10.sp, color = textSecondary)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .size(34.dp)
                        .background(if (isDarkMode) BleSenseColors.SurfaceLight else Color(0xFFF1F5F9), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Default.WbSunny else Icons.Default.NightsStay,
                        contentDescription = "Toggle Theme",
                        tint = textPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardHeroCard(
    dotAlpha: Float,
    totalDiscovered: Int,
    activeSensors: Int,
    isDarkMode: Boolean
) {
    val gradientColors = if (isDarkMode) {
        listOf(Color(0xFF181820), Color(0xFF22222C))
    } else {
        listOf(Color(0xFF0284C7), Color(0xFF38BDF8))
    }

    val isConnected = totalDiscovered > 0
    val statusBadgeText = if (isConnected) "CONNECTED & MONITORING" else "STANDBY · DISCONNECTED"
    val statusBadgeColor = if (isConnected) Color(0xFFBBF7D0) else Color(0xFFFEF08A)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .shadow(4.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(gradientColors)))
        Box(modifier = Modifier.size(180.dp).align(Alignment.CenterEnd).offset(x = 50.dp, y = 20.dp).background(Color.White.copy(alpha = 0.15f), CircleShape))

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(7.dp).background(statusBadgeColor.copy(alpha = dotAlpha), CircleShape))
                Text(statusBadgeText, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = statusBadgeColor, letterSpacing = 1.0.sp)
            }
            Column {
                Text("Sensor Dashboard Hub", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    if (isConnected) "$totalDiscovered device(s) active · $activeSensors live telemetry stream(s)"
                    else "0 active devices · 0 telemetry points",
                    fontSize = 11.sp,
                    color = Color(0xFFE0F2FE)
                )
            }
        }
    }
}

@Composable
private fun SectionHeaderTitle(title: String, subtitle: String, isDarkMode: Boolean) {
    val titleColor = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val subtitleColor = if (isDarkMode) BleSenseColors.TextSecondary else Color(0xFF64748B)

    Column(modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = titleColor)
        Text(subtitle, fontSize = 11.sp, color = subtitleColor)
    }
}

@Composable
fun WideHeroDashboardCard(
    item: DashboardCardItem,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val cardBg = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White
    val borderColor = if (isDarkMode) BleSenseColors.BorderDark else Color(0xFFE2EEF9)
    val textPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val textSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(22.dp))
            .clickable { onClick() },
        backgroundColor = cardBg,
        elevation = 0.dp,
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(22.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(item.iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.vectorIcon,
                        contentDescription = item.title,
                        modifier = Modifier.size(24.dp),
                        tint = item.iconTint
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(item.badgeBg)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = item.badgeText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = item.badgeTextTint,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = item.subtitle,
                        fontSize = 11.sp,
                        color = textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun MediumDashboardCard(
    item: DashboardCardItem,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val cardBg = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White
    val borderColor = if (isDarkMode) BleSenseColors.BorderDark else Color(0xFFE2EEF9)
    val textPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val textSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    Card(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(22.dp))
            .clickable { onClick() },
        backgroundColor = cardBg,
        elevation = 0.dp,
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(item.iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.vectorIcon,
                        contentDescription = item.title,
                        modifier = Modifier.size(24.dp),
                        tint = item.iconTint
                    )
                }

                Box(
                    modifier = Modifier
                        .background(item.badgeBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(item.badgeText, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = item.badgeTextTint)
                }
            }

            Column {
                Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(2.dp))
                Text(item.subtitle, fontSize = 11.sp, color = textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun QuickStatsCard(
    totalDiscovered: Int,
    activeSensors: Int,
    isDarkMode: Boolean
) {
    val cardBg = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White
    val borderColor = if (isDarkMode) BleSenseColors.BorderDark else Color(0xFFE2EEF9)
    val textSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(
            "SYSTEM METRICS OVERVIEW",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = textSecondary,
            letterSpacing = 1.0.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$totalDiscovered", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF059669))
                Text("Discovered", fontSize = 10.sp, color = textSecondary)
            }
            Box(modifier = Modifier.width(0.5.dp).height(36.dp).background(borderColor))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$activeSensors", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2563EB))
                Text("Active Pods", fontSize = 10.sp, color = textSecondary)
            }
            Box(modifier = Modifier.width(0.5.dp).height(36.dp).background(borderColor))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (totalDiscovered > 0) "ONLINE" else "STANDBY", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (totalDiscovered > 0) Color(0xFF0891B2) else Color(0xFFD97706))
                Text("System State", fontSize = 10.sp, color = textSecondary)
            }
        }
    }
}


