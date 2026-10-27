package com.blesense.app.view.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.blesense.app.R
import com.blesense.app.ui.theme.BleSenseColors
import com.blesense.app.util.DeviceIdentifier
import com.blesense.app.util.ThemeManager
import com.blesense.app.view.components.ReferenceBottomNavBar
import com.blesense.app.view.components.StandardScreenHeaderBar

@Composable
fun ModernSettingsScreen(
    navController: NavHostController
) {
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val context = LocalContext.current

    val backgroundColor = if (isDarkMode) BleSenseColors.BackgroundDark else BleSenseColors.LightBackground
    val cardBackground = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White
    val textColor = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val secondaryTextColor = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary
    val dividerColor = if (isDarkMode) BleSenseColors.BorderDark else Color(0xFFE2EEF9)
    val iconTint = Color(0xFF2563EB)

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        backgroundColor = backgroundColor,
        bottomBar = {
            ReferenceBottomNavBar(
                navController = navController,
                currentRoute = "settings_screen",
                isDarkMode = isDarkMode
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // 1. Header Bar
            StandardScreenHeaderBar(
                title = "Settings",
                subtitle = "App preferences, theme & system info",
                isDarkMode = isDarkMode,
                onBackClick = { navController.popBackStack() }
            )

            // 2. App Info Card
            AppInfoCard(
                cardBackground = cardBackground,
                borderColor = dividerColor,
                textColor = textColor,
                secondaryTextColor = secondaryTextColor,
                iconTint = iconTint,
                deviceId = DeviceIdentifier.getOrGenerateId(context)
            )

            // 3. Settings options list
            SettingsOptionsList(
                cardBackground = cardBackground,
                borderColor = dividerColor,
                textColor = textColor,
                secondaryTextColor = secondaryTextColor,
                dividerColor = dividerColor,
                iconTint = iconTint,
                isDarkMode = isDarkMode,
                navController = navController
            )

            // 4. Privacy policy button
            PrivacyPolicyButton()
        }
    }
}

@Composable
fun AppInfoCard(
    cardBackground: Color,
    borderColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    iconTint: Color,
    deviceId: String
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        backgroundColor = cardBackground,
        elevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "App Info",
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "BLE Sense",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = "Version 1.0.0",
                    fontSize = 11.sp,
                    color = secondaryTextColor
                )
                Text(
                    text = "Device ID: ${deviceId.take(12)}...",
                    fontSize = 10.sp,
                    color = secondaryTextColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun SettingsOptionsList(
    cardBackground: Color,
    borderColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    dividerColor: Color,
    iconTint: Color,
    isDarkMode: Boolean,
    navController: NavHostController
) {
    val settingsOptions = listOf(
        SettingsItem(Icons.Outlined.Palette, "Dark Mode", SettingsItemType.SWITCH),
        SettingsItem(Icons.AutoMirrored.Outlined.MenuBook, "User Manual (All Devices)", SettingsItemType.DETAIL),
        SettingsItem(Icons.Outlined.Smartphone, "Recommended Phone Settings", SettingsItemType.DETAIL),
        SettingsItem(Icons.AutoMirrored.Outlined.Help, "Help & Support", SettingsItemType.DETAIL),
        SettingsItem(Icons.Outlined.Info, "About BLE Sense", SettingsItemType.DETAIL),
    )

    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(20.dp),
        backgroundColor = cardBackground,
        elevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
    ) {
        Column {
            settingsOptions.forEachIndexed { index, item ->
                SettingsItemRow(
                    item = item,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    iconTint = iconTint,
                    initialSwitchState = isDarkMode,
                    onSwitchChange = { if (item.title == "Dark Mode") ThemeManager.toggleDarkMode(context, it) },
                    navController = navController
                )

                if (index < settingsOptions.size - 1) {
                    Divider(
                        color = dividerColor,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsItemRow(
    item: SettingsItem,
    textColor: Color,
    secondaryTextColor: Color,
    iconTint: Color,
    initialSwitchState: Boolean = false,
    onSwitchChange: ((Boolean) -> Unit)? = null,
    navController: NavHostController,
) {
    var switchState by remember { mutableStateOf(initialSwitchState) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showUserManualDialog by remember { mutableStateOf(false) }
    var showPhoneSettingsDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val isAboutItem = item.title.contains("About", ignoreCase = true)
    val isHelpItem = item.title.contains("Help", ignoreCase = true)
    val isManualItem = item.title.contains("Manual", ignoreCase = true)
    val isPhoneSettingsItem = item.title.contains("Phone Settings", ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.type == SettingsItemType.DETAIL) {
                when {
                    isManualItem -> showUserManualDialog = true
                    isPhoneSettingsItem -> showPhoneSettingsDialog = true
                    isAboutItem -> showAboutDialog = true
                    isHelpItem -> showHelpDialog = true
                }
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = item.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.weight(1f)
        )

        when (item.type) {
            SettingsItemType.SWITCH -> {
                Switch(
                    checked = switchState,
                    onCheckedChange = { newValue ->
                        switchState = newValue
                        onSwitchChange?.invoke(newValue)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = iconTint,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = secondaryTextColor.copy(alpha = 0.3f)
                    )
                )
            }
            SettingsItemType.DETAIL -> {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = "Navigate",
                    tint = secondaryTextColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showAboutDialog) {
        Dialog(onDismissRequest = { showAboutDialog = false }) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                color = if (initialSwitchState) BleSenseColors.SurfaceDark else Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "BLE Info",
                        tint = iconTint,
                        modifier = Modifier
                            .size(42.dp)
                            .padding(bottom = 12.dp)
                    )

                    Text(
                        text = "Bluetooth Low Energy Hub",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = if (initialSwitchState) Color.White else Color.Black,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                    Text(
                        text = "BLE Sense provides high-precision telemetry monitoring, live GATT & Advertising packet parsing, AWS weather station analytics, and data logging capabilities.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = if (initialSwitchState) Color(0xFFA1A1AA) else Color(0xFF64748B),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )

                    TextButton(
                        onClick = { showAboutDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Text("Close", color = iconTint, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showHelpDialog) {
        Dialog(onDismissRequest = { showHelpDialog = false }) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                color = if (initialSwitchState) BleSenseColors.SurfaceDark else Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Help,
                        contentDescription = "Help",
                        tint = iconTint,
                        modifier = Modifier
                            .size(42.dp)
                            .padding(bottom = 12.dp)
                    )

                    Text(
                        text = "Support & Bug Reports",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = if (initialSwitchState) Color.White else Color.Black,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Text(
                        text = "Need assistance or discovered an issue? Contact our developer support team directly.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = if (initialSwitchState) Color(0xFFA1A1AA) else Color(0xFF64748B),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:awadhropar@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "BLE Sense Support Request")
                            }
                            try {
                                context.startActivity(Intent.createChooser(intent, "Send Email"))
                            } catch (e: Exception) {
                            }
                            showHelpDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF2563EB),
                            contentColor = Color.White
                        ),
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                    ) {
                        Text("Contact Developer", fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { showHelpDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close", color = iconTint, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showUserManualDialog) {
        UserManualDialog(
            isDarkMode = initialSwitchState,
            iconTint = iconTint,
            onDismiss = { showUserManualDialog = false }
        )
    }

    if (showPhoneSettingsDialog) {
        PhoneSettingsDialog(
            isDarkMode = initialSwitchState,
            iconTint = iconTint,
            onDismiss = { showPhoneSettingsDialog = false }
        )
    }
}

@Composable
fun PrivacyPolicyButton() {
    val context = LocalContext.current

    Button(
        onClick = {
            val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sumankumar891.github.io/privacy_policy_blesense/"))
            context.startActivity(urlIntent)
        },
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = Color(0xFF2563EB),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Security,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text("Privacy Policy", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

data class SettingsItem(
    val icon: ImageVector,
    val title: String,
    val type: SettingsItemType
)

enum class SettingsItemType {
    SWITCH,
    DETAIL
}

// ================= RECOMMENDED PHONE SETTINGS DIALOG =================
@Composable
fun PhoneSettingsDialog(
    isDarkMode: Boolean,
    iconTint: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val surfaceBg = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White
    val cardBg = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFF8FAFC)
    val borderColor = if (isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = surfaceBg,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(iconTint.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Smartphone,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Phone Settings",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Text(
                                text = "Optimize Android RF & Background",
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = borderColor, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Instructions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Section 1: Location Wi-Fi & BT Scanning
                    PhoneSettingInstructionCard(
                        cardBg = cardBg,
                        borderColor = borderColor,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = Color(0xFF0284C7),
                        badgeText = "Antenna RF Pauses",
                        title = "1. Turn OFF Location Scanning",
                        steps = listOf(
                            "Open phone Settings → Location → Location services.",
                            "Turn OFF 'Wi-Fi scanning'.",
                            "Turn OFF 'Bluetooth scanning'."
                        ),
                        note = "Stops Google Location Services from interrupting the physical 2.4 GHz Bluetooth antenna every second."
                    )

                    // Section 2: Wi-Fi Coexistence
                    PhoneSettingInstructionCard(
                        cardBg = cardBg,
                        borderColor = borderColor,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = Color(0xFF8B5CF6),
                        badgeText = "RF Coexistence",
                        title = "2. Use 5 GHz Wi-Fi or Turn Wi-Fi OFF",
                        steps = listOf(
                            "Connect your phone to a 5 GHz Wi-Fi network band.",
                            "Alternatively, turn Wi-Fi OFF completely during testing."
                        ),
                        note = "Eliminates 2.4 GHz antenna time-sharing between Wi-Fi and Bluetooth, giving 100% uninterrupted radio bandwidth."
                    )

                    // Section 3: Battery Unrestricted & Samsung Background Limits
                    PhoneSettingInstructionCard(
                        cardBg = cardBg,
                        borderColor = borderColor,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = Color(0xFF10B981),
                        badgeText = "Samsung & Android",
                        title = "3. Set Battery to Unrestricted",
                        steps = listOf(
                            "Go to Settings → Apps → BLE Sense → Battery.",
                            "Select 'Unrestricted' (do NOT leave on 'Optimized').",
                            "On Samsung (S24, M51, A15): Settings → Device Care → Battery → Background usage limits → Ensure BLE Sense is NOT in Sleeping or Deep sleeping apps."
                        ),
                        note = "Prevents Android OS from freezing the Bluetooth stack when the screen is turned off."
                    )

                    // Section 4: Daily Restart Quick Fix
                    PhoneSettingInstructionCard(
                        cardBg = cardBg,
                        borderColor = borderColor,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = Color(0xFFF59E0B),
                        badgeText = "5-Sec Quick Fix",
                        title = "4. Avoid Daily Phone Restarts",
                        steps = listOf(
                            "If the Bluetooth stack stalls after 24h continuous use:",
                            "Turn Airplane Mode ON for 5 seconds → Turn OFF.",
                            "OR: Settings → Bluetooth → Turn OFF for 5 seconds → Turn ON."
                        ),
                        note = "Forces Android OS Bluetooth daemon to reset its internal client tables without rebooting your phone."
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, iconTint),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "App Settings",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = iconTint
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = iconTint),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Got It",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PhoneSettingInstructionCard(
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    badgeText: String,
    title: String,
    steps: List<String>,
    note: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            steps.forEach { step ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "• ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Text(
                        text = step,
                        fontSize = 11.sp,
                        color = textPrimary.copy(alpha = 0.9f),
                        lineHeight = 15.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.08f))
                    .padding(8.dp)
            ) {
                Text(
                    text = "💡 Tip: $note",
                    fontSize = 10.sp,
                    color = textSecondary,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

// ================= DEVICE USER MANUAL DIALOG =================
@Composable
fun UserManualDialog(
    isDarkMode: Boolean,
    iconTint: Color,
    onDismiss: () -> Unit
) {
    val surfaceBg = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White
    val cardBg = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFF8FAFC)
    val borderColor = if (isDarkMode) Color(0xFF334155) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)

    var selectedDeviceIndex by remember { mutableStateOf(0) }
    val deviceTabs = listOf("🌦️ AWS Weather", "🌡️ Sensor Hub", "💾 Data Logger", "👟 Step Counter", "🏎️ Robot Car", "📡 Raw Scanner")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = surfaceBg,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(iconTint.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "App User Manual",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Text(
                                text = "How to navigate & view device data",
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = borderColor, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Category Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedDeviceIndex,
                    backgroundColor = Color.Transparent,
                    contentColor = iconTint,
                    edgePadding = 0.dp,
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedDeviceIndex]),
                            color = iconTint,
                            height = 2.5.dp
                        )
                    }
                ) {
                    deviceTabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedDeviceIndex == index,
                            onClick = { selectedDeviceIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedDeviceIndex == index) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedDeviceIndex == index) iconTint else textSecondary
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Content for Selected Device
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (selectedDeviceIndex) {
                        0 -> AWSStationAppManual(cardBg, borderColor, textPrimary, textSecondary, iconTint)
                        1 -> SensorHubAppManual(cardBg, borderColor, textPrimary, textSecondary, iconTint)
                        2 -> DataLoggerAppManual(cardBg, borderColor, textPrimary, textSecondary, iconTint)
                        3 -> StepCounterAppManual(cardBg, borderColor, textPrimary, textSecondary, iconTint)
                        4 -> RobotCarAppManual(cardBg, borderColor, textPrimary, textSecondary, iconTint)
                        5 -> RawScannerAppManual(cardBg, borderColor, textPrimary, textSecondary, iconTint)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = iconTint),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close Manual", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ================= COMPONENT HELPERS FOR USER MANUAL =================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualFlowCard(
    steps: List<String>,
    accentColor: Color,
    textPrimary: Color,
    cardBg: Color,
    borderColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accentColor.copy(alpha = 0.05f))
            .border(1.dp, accentColor.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = "APP NAVIGATION PATHWAY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        letterSpacing = 0.6.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${steps.size} STEPS",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor
                    )
                }
            }

            // FlowRow of stylish connected step badges (prevents vertical letter wrapping)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                steps.forEachIndexed { idx, step ->
                    val isDestination = idx == steps.size - 1

                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (isDestination) accentColor else cardBg,
                        border = BorderStroke(
                            1.dp,
                            if (isDestination) accentColor else accentColor.copy(alpha = 0.25f)
                        ),
                        elevation = if (isDestination) 2.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isDestination) Color.White.copy(alpha = 0.3f)
                                        else accentColor.copy(alpha = 0.18f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isDestination) "★" else "${idx + 1}",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isDestination) Color.White else accentColor
                                )
                            }

                            Text(
                                text = step,
                                fontSize = 11.sp,
                                fontWeight = if (isDestination) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isDestination) Color.White else textPrimary,
                                maxLines = 1
                            )

                            if (!isDestination) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                    contentDescription = null,
                                    tint = accentColor.copy(alpha = 0.5f),
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ManualSectionCard(
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    accentColor: Color,
    badgeText: String,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun ManualStepRow(
    stepNum: Int,
    title: String,
    desc: String,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$stepNum",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 11.sp,
                color = textSecondary,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun ManualFeatureHighlight(
    iconText: String,
    title: String,
    description: String,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(accentColor.copy(alpha = 0.05f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(iconText, fontSize = 13.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textPrimary)
            Text(description, fontSize = 10.5.sp, color = textSecondary, lineHeight = 14.sp)
        }
    }
}

@Composable
fun ManualTroubleshootItem(
    issue: String,
    solution: String,
    textPrimary: Color,
    textSecondary: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text("⚠️ ", fontSize = 11.sp)
        Column {
            Text(issue, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textPrimary)
            Text(solution, fontSize = 10.5.sp, color = textSecondary, lineHeight = 14.sp)
        }
    }
}

// ================= 1. AWS WEATHER STATION APP MANUAL =================

@Composable
fun AWSStationAppManual(
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    iconTint: Color
) {
    val accent = Color(0xFFEA580C)

    ManualFlowCard(
        steps = listOf("Dashboard", "AWS Station", "Start Scan", "Select Card", "Live Visualizer"),
        accentColor = accent,
        textPrimary = textPrimary,
        cardBg = cardBg,
        borderColor = borderColor
    )

    ManualSectionCard(cardBg, borderColor, textPrimary, accent, "Step-by-Step", "📡 1. Scanner & Discovery") {
        ManualStepRow(1, "Open AWS Scanner", "From the main dashboard, tap the 'AWS Station' card.", textPrimary, textSecondary, accent)
        ManualStepRow(2, "Start Discovery", "Tap the 'Start' button. The app filters specifically for beacons broadcasting the AWSData packet signature.", textPrimary, textSecondary, accent)
        ManualStepRow(3, "Identify Pod", "Found units display their Node ID (MAC Address) and live signal strength (RSSI). Tap the card to open the Advertising Dashboard.", textPrimary, textSecondary, accent)
    }

    ManualSectionCard(cardBg, borderColor, textPrimary, accent, "Diagnostics", "🚦 2. System Status (Priority View)") {
        ManualFeatureHighlight("🟢", "Ready (Green)", "Station is fully operational and continuously transmitting meteorological telemetry.", textPrimary, textSecondary, accent)
        ManualFeatureHighlight("🟠", "Not Ready (Orange)", "Status Byte 99: Indicates sensor boot & warm-up sequence. Normal for the first 60 seconds after power-on.", textPrimary, textSecondary, accent)
        ManualFeatureHighlight("🔴", "System Error Slots", "Displays active error count across 16 subsystems: ATRH Sensor (1), Wind (2), Tilt (3), Flash (4), Network (5), Low Battery (6), Rain Gauge (10), MQTT/HTTP (21-26).", textPrimary, textSecondary, accent)
    }

    ManualSectionCard(cardBg, borderColor, textPrimary, accent, "Live Hub", "📊 3. Interactive Visualizer Dashboard") {
        Text(
            text = "Tap the Analytics / Eye Icon at the top right of the Advertising Dashboard to launch the visual gauges:",
            fontSize = 11.sp,
            color = textSecondary
        )
        ManualFeatureHighlight("💨", "Speedometer Gauge", "Measures wind speed up to 40 km/h (converted in real-time from m/s).", textPrimary, textSecondary, accent)
        ManualFeatureHighlight("🧭", "360° Compass Needle", "Rotating compass needle indicating current wind direction (N, NE, E, SE, S, SW, W, NW).", textPrimary, textSecondary, accent)
        ManualFeatureHighlight("🌡️", "Dynamic Thermometer", "Vertical mercury tube with auto-color shifting: Blue (<15°C), Green (15-28°C), Orange (28-38°C), Red (>38°C).", textPrimary, textSecondary, accent)
        ManualFeatureHighlight("🌊", "Animated Water Gauge", "Dynamic oscillating wave interface illustrating relative humidity saturation (0-100% RH).", textPrimary, textSecondary, accent)
        ManualFeatureHighlight("⚡", "Solar & Battery Power", "Real-time battery storage voltage and photovoltaic solar panel input (0.1V precision).", textPrimary, textSecondary, accent)
        ManualFeatureHighlight("🔬", "Raw Payload Toggle", "Flip the 'Raw Payload View' switch on the dashboard to inspect individual decoded hexadecimal bytes.", textPrimary, textSecondary, accent)
    }

    ManualSectionCard(cardBg, borderColor, textPrimary, accent, "Troubleshoot", "🔧 4. Operational In-App Troubleshooting") {
        ManualTroubleshootItem("'Device is not ready': ", "Wait 60 seconds for internal sensor warm-up. If it persists, inspect solar/battery levels.", textPrimary, textSecondary)
        ManualTroubleshootItem("No devices found: ", "Ensure Bluetooth and Location are enabled on your phone and you are within 20m of the pod.", textPrimary, textSecondary)
        ManualTroubleshootItem("High error count: ", "Toggle 'Raw Payload View' in the app to identify which specific error slot byte is triggered.", textPrimary, textSecondary)
    }
}

// ================= 2. SENSOR HUB & LIVE CHARTS APP MANUAL =================

@Composable
fun SensorHubAppManual(
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    iconTint: Color
) {
    val accent = Color(0xFF2563EB)

    ManualFlowCard(
        steps = listOf("Dashboard", "Sensor Hub", "Select Sensor", "Live Tiles", "Interactive Charts"),
        accentColor = accent,
        textPrimary = textPrimary,
        cardBg = cardBg,
        borderColor = borderColor
    )

    ManualSectionCard(cardBg, borderColor, textPrimary, accent, "Step-by-Step", "🔍 1. Discovery & Pod Cards") {
        ManualStepRow(1, "Open Sensor Hub", "Tap 'Sensor Hub' on the main dashboard to view all nearby environmental pods.", textPrimary, textSecondary, accent)
        ManualStepRow(2, "Supported Pods", "The app continuously parses SHT40, LIS3DH, TempLogger, Soil, Ammonia NH3, ATRH, and BME280 broadcast packets.", textPrimary, textSecondary, accent)
        ManualStepRow(3, "Card Summary", "Each pod card shows Sensor Name, MAC address, signal bar (RSSI), and an instant telemetry preview.", textPrimary, textSecondary, accent)
    }

    ManualSectionCard(cardBg, borderColor, textPrimary, accent, "Live Telemetry", "📱 2. Advertising Data Dashboard") {
        Text("Tap any sensor card to open its dedicated telemetry screen:", fontSize = 11.sp, color = textSecondary)
        ManualFeatureHighlight("🌡️", "Temperature & Humidity", "High-contrast telemetry cards displaying current ambient readings with min and max peaks.", textPrimary, textSecondary, accent)
        ManualFeatureHighlight("🌱", "Specialized Sensor Tiles", "Displays Soil moisture saturation, Ammonia gas concentration (ppm), or 3-Axis XYZ acceleration.", textPrimary, textSecondary, accent)
        ManualFeatureHighlight("🔋", "Battery Health", "Live coin-cell / LiPo battery voltage indicator to alert before sensor shutdown.", textPrimary, textSecondary, accent)
        ManualFeatureHighlight("🔄", "Unit Conversion", "Easily switch between Celsius (°C) and Fahrenheit (°F) with one tap.", textPrimary, textSecondary, accent)
    }

    ManualSectionCard(cardBg, borderColor, textPrimary, accent, "Visual Analytics", "📈 3. Live Waveform Charts") {
        ManualStepRow(1, "Launch Graph", "Tap the Chart Icon next to any telemetry tile to open full-screen interactive waveform graphs.", textPrimary, textSecondary, accent)
        ManualStepRow(2, "Auto-Scaling Sparklines", "Charts dynamically scale on both axes to reveal micro-fluctuations in sensor readings.", textPrimary, textSecondary, accent)
        ManualStepRow(3, "Threshold Warnings", "Border glow shifts to Orange or Red if values breach safe operating ranges.", textPrimary, textSecondary, accent)
    }

    ManualSectionCard(cardBg, borderColor, textPrimary, accent, "Troubleshoot", "🔧 4. In-App Troubleshooting") {
        ManualTroubleshootItem("Sensor reading frozen: ", "Check the battery indicator on the card; if below 2.7V, replace the pod battery.", textPrimary, textSecondary)
        ManualTroubleshootItem("Pod not discovered: ", "Pull down on the screen to refresh scanning, or check Recommended Phone Settings.", textPrimary, textSecondary)
    }
}

// ================= 3. LIVESTOCK / DATA LOGGER APP MANUAL =================

@Composable
fun DataLoggerAppManual(
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    iconTint: Color
) {
    val accent = Color(0xFF0891B2)

    ManualFlowCard(
        steps = listOf("Dashboard", "LiveStock Logger", "Select Logger", "Memory Screen", "Stream Dump", "Export CSV"),
        accentColor = accent,
        textPrimary = textPrimary,
        cardBg = cardBg,
        borderColor = borderColor
    )

    ManualSectionCard(cardBg, borderColor, textPrimary, accent, "Step-by-Step", "💾 1. Connecting to Loggers") {
        ManualStepRow(1, "Open Logger List", "From Dashboard, tap 'LiveStock Logger'. The app discovers offline Data Logger devices (DataLogger, DLOG).", textPrimary, textSecondary, accent)
        ManualStepRow(2, "Select Logger", "Tap a logger card to enter the Data Logger Control Screen.", textPrimary, textSecondary, accent)
        ManualStepRow(3, "Inspect Flash Status", "View total historical records saved in flash memory, percent storage utilized, and pod battery reserve.", textPrimary, textSecondary, accent)
    }

    ManualSectionCard(cardBg, borderColor, textPrimary, accent, "Burst Stream", "⚡ 2. High-Speed Memory Download") {
        ManualStepRow(1, "Trigger Download", "Tap 'Start Download' / 'Trigger Stream'. The app commands the logger over Nordic UART / BLE burst mode.", textPrimary, textSecondary, accent)
        ManualStepRow(2, "Watch Progress", "A live animated progress bar shows records downloaded, transfer rate (packets/sec), and buffer verification.", textPrimary, textSecondary, accent)
        ManualStepRow(3, "Integrity Check", "The built-in watchdog ensures zero dropped records before finalizing the transfer.", textPrimary, textSecondary, accent)
    }

    ManualSectionCard(cardBg, borderColor, textPrimary, accent, "Export & Share", "📄 3. Table Inspection & CSV Export") {
        ManualFeatureHighlight("📋", "Chronological Log Table", "Scroll through timestamped temperature, humidity, and event logs saved on the phone.", textPrimary, textSecondary, accent)
        ManualFeatureHighlight("📤", "One-Tap CSV Export", "Tap 'Export CSV' to save the complete tabular dataset to your phone or share via email and cloud drives.", textPrimary, textSecondary, accent)
    }

    ManualSectionCard(cardBg, borderColor, textPrimary, accent, "Troubleshoot", "🔧 4. In-App Troubleshooting") {
        ManualTroubleshootItem("Download pauses: ", "Keep the phone within 3 meters of the logger during high-speed memory streaming.", textPrimary, textSecondary)
    }
}

// ================= 4. STEP COUNTER & PEDOMETER APP MANUAL =================

@Composable
fun StepCounterAppManual(
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    iconTint: Color
) {
    val accent = Color(0xFF059669)

    ManualFlowCard(
        steps = listOf("Dashboard", "Step Counter", "Auto Tag Link", "Goal Ring", "Walking Dynamics", "Reset / Target"),
        accentColor = accent,
        textPrimary = textPrimary,
        cardBg = cardBg,
        borderColor = borderColor
    )

    ManualSectionCard(cardBg, borderColor, textPrimary, accent, "Step-by-Step", "👟 1. Launch & Goal Ring") {
        ManualStepRow(1, "Open Step Counter", "Tap 'Step Counter' on the main dashboard.", textPrimary, textSecondary, accent)
        ManualStepRow(2, "Automatic Link", "The app automatically discovers and pairs with active pedometer tags and LIS3DH nodes.", textPrimary, textSecondary, accent)
        ManualStepRow(3, "Center Progress Ring", "The circular dial displays real-time steps accumulated today against your daily goal (default: 10,000 steps).", textPrimary, textSecondary, accent)
    }

    ManualSectionCard(cardBg, borderColor, textPrimary, accent, "Dynamics", "🏃 2. Walking Dynamics & Metrics") {
        ManualFeatureHighlight("⚡", "Cadence (SPM)", "Steps per minute live rate to help maintain walking and running pace.", textPrimary, textSecondary, accent)
        ManualFeatureHighlight("📏", "Estimated Distance", "Calculates total distance traveled in kilometers based on stride cadence.", textPrimary, textSecondary, accent)
        ManualFeatureHighlight("⏱️", "Active Motion Time", "Tracks duration of sustained walking activity during the day.", textPrimary, textSecondary, accent)
        ManualFeatureHighlight("🔥", "Burned Calories", "Estimated energy expenditure (kcal) based on step count and cadence.", textPrimary, textSecondary, accent)
    }

    ManualSectionCard(cardBg, borderColor, textPrimary, accent, "Controls", "⚙️ 3. Reset & Target Adjustments") {
        ManualStepRow(1, "Zero the Counter", "Tap the Reset icon to clear today's step count for a new walking session or workout.", textPrimary, textSecondary, accent)
        ManualStepRow(2, "Customize Goal", "Tap the goal selector to change your daily target (e.g. 5,000, 8,000, 10,000, 15,000 steps).", textPrimary, textSecondary, accent)
    }
}

// ================= 5. ROBOT CAR CONTROLLER APP MANUAL =================

@Composable
fun RobotCarAppManual(
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    iconTint: Color
) {
    val accent = Color(0xFF7C3AED)

    ManualFlowCard(
        steps = listOf("Dashboard", "Robot Control", "Select Protocol (Classic / BLE)", "Connect", "Dials & Horn"),
        accentColor = accent,
        textPrimary = textPrimary,
        cardBg = cardBg,
        borderColor = borderColor
    )

    ManualSectionCard(cardBg, borderColor, textPrimary, accent, "Dual-Mode", "🚗 1. Connecting in Dual Mode") {
        ManualStepRow(1, "Open Robot Screen", "Tap 'Robot Control' from the main dashboard.", textPrimary, textSecondary, accent)
        ManualStepRow(2, "Select Protocol", "Choose between Classic Bluetooth (HC-05/06 SPP) or BLE (nRF52832 Nordic UART Service).", textPrimary, textSecondary, accent)
        ManualStepRow(3, "Establish Link", "Tap 'Connect'. The top status banner turns green once serial communication is live.", textPrimary, textSecondary, accent)
    }

    ManualSectionCard(cardBg, borderColor, textPrimary, accent, "Touch Dials", "🎮 2. Steering & Drive Controls") {
        ManualFeatureHighlight("🕹️", "Left Dial (Steering)", "Rotate or slide left/right to steer. Features a 20% deadzone and auto-center spring return. Sends 'L' or 'R'.", textPrimary, textSecondary, accent)
        ManualFeatureHighlight("⚡", "Right Dial (Throttle)", "Slide up for Forward ('F') and down for Backward ('B').", textPrimary, textSecondary, accent)
        ManualFeatureHighlight("🔄", "Composite Diagonals", "Move both dials simultaneously to execute smooth turns: Forward-Left ('FL'), Forward-Right ('FR'), Backward-Left ('BL'), Backward-Right ('BR').", textPrimary, textSecondary, accent)
        ManualFeatureHighlight("📢", "Interactive Horn", "Touch and hold the center Horn button to sound horn ('H'). Releasing instantly silences it ('C').", textPrimary, textSecondary, accent)
    }

    ManualSectionCard(cardBg, borderColor, textPrimary, accent, "Safety", "🛡️ 3. Safety Failsafe Auto-Stop") {
        ManualFeatureHighlight("🛑", "Instant Brake on Release", "Lifting fingers from the dials immediately transmits 'S' (Stop).", textPrimary, textSecondary, accent)
        ManualFeatureHighlight("🔒", "Background Failsafe", "Minimizing the app, locking the screen, or Bluetooth disconnection immediately shuts down motors.", textPrimary, textSecondary, accent)
    }
}

// ================= 6. RAW BLE SCANNER & HISTORY APP MANUAL =================

@Composable
fun RawScannerAppManual(
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    iconTint: Color
) {
    val accent = Color(0xFF059669)

    ManualFlowCard(
        steps = listOf("Dashboard", "Raw Scanner", "Start", "Inspect Hex Bytes", "Bottom Nav History"),
        accentColor = accent,
        textPrimary = textPrimary,
        cardBg = cardBg,
        borderColor = borderColor
    )

    ManualSectionCard(cardBg, borderColor, textPrimary, accent, "Raw Sniffer", "📡 1. Extended BLE Advertisement Sniffing") {
        ManualStepRow(1, "Open Raw Scanner", "From the main dashboard, tap 'Raw Scanner'. Tap 'Start' to begin packet capture.", textPrimary, textSecondary, accent)
        ManualStepRow(2, "Unfiltered Capture", "Captures Bluetooth 5.0 Extended Advertising (BigAdv) and standard 31-byte legacy packets.", textPrimary, textSecondary, accent)
        ManualStepRow(3, "Packet Inspection", "Tap any card to view Company Identifier, complete raw hexadecimal bytes, Tx power, and RSSI graph.", textPrimary, textSecondary, accent)
    }

    ManualSectionCard(cardBg, borderColor, textPrimary, accent, "History Tab", "📜 2. Scan History & Device Search") {
        ManualStepRow(1, "Open History", "Tap the 'History' tab on the bottom navigation bar anytime.", textPrimary, textSecondary, accent)
        ManualStepRow(2, "Filter & Search", "Search through previously discovered devices by MAC address, name, or first/last seen timestamp.", textPrimary, textSecondary, accent)
    }
}
