package com.blesense.app.view.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily.Companion.Monospace
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.blesense.app.R
import com.blesense.app.ui.theme.BleSenseColors
import com.blesense.app.util.ThemeManager
import com.blesense.app.util.DeviceIdentifier
import com.blesense.app.view.components.ReferenceBottomNavBar

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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontFamily = Monospace,
                        style = MaterialTheme.typography.h5.copy(
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                },
                backgroundColor = cardBackground,
                elevation = 0.dp
            )
        },
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Info Card
            AppInfoCard(
                cardBackground = cardBackground,
                textColor = textColor,
                secondaryTextColor = secondaryTextColor,
                iconTint = iconTint,
                deviceId = DeviceIdentifier.getOrGenerateId(context)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Settings options list
            SettingsOptionsList(
                cardBackground = cardBackground,
                textColor = textColor,
                secondaryTextColor = secondaryTextColor,
                dividerColor = dividerColor,
                iconTint = iconTint,
                isDarkMode = isDarkMode,
                navController = navController
            )

            // Privacy policy button
            PrivacyPolicyButton()

            // ── Background Watermark filling the entire blank space ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                SettingsWatermarkLogos(isDarkMode = isDarkMode)
            }
        }
    }
}

@Composable
fun AppInfoCard(
    cardBackground: Color,
    textColor: Color,
    secondaryTextColor: Color,
    iconTint: Color,
    deviceId: String
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        backgroundColor = cardBackground,
        elevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "App Info",
                    tint = iconTint,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "BLE Sense",
                    fontFamily = Monospace,
                    style = MaterialTheme.typography.subtitle1.copy(
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                )
                Text(
                    text = "Version 1.0.0",
                    fontFamily = Monospace,
                    style = MaterialTheme.typography.body2.copy(
                        color = secondaryTextColor
                    )
                )
                Text(
                    text = "ID: ${deviceId.take(8)}...",
                    fontFamily = Monospace,
                    style = MaterialTheme.typography.caption.copy(
                        color = secondaryTextColor
                    )
                )
            }
        }
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
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text("Privacy Policy", fontFamily = Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsOptionsList(
    cardBackground: Color,
    textColor: Color,
    secondaryTextColor: Color,
    dividerColor: Color,
    iconTint: Color,
    isDarkMode: Boolean,
    navController: NavHostController
) {
    val settingsOptions = listOf(
        SettingsItem(Icons.Outlined.Palette, "Dark Mode", SettingsItemType.SWITCH),
        SettingsItem(Icons.AutoMirrored.Outlined.Help, "Help", SettingsItemType.DETAIL),
        SettingsItem(Icons.Outlined.Info, "About BLE", SettingsItemType.DETAIL),
    )

    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(12.dp),
        backgroundColor = cardBackground,
        elevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
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
                        thickness = 1.dp,
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
    val context = LocalContext.current
    
    val isAboutItem = item.icon == Icons.Outlined.Info
    val isHelpItem = item.icon == Icons.AutoMirrored.Outlined.Help

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.type == SettingsItemType.DETAIL) {
                when {
                    isAboutItem -> showAboutDialog = true
                    isHelpItem -> showHelpDialog = true
                }
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.title,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = item.title,
            fontFamily = Monospace,
            style = MaterialTheme.typography.body1.copy(
                fontWeight = FontWeight.Medium,
                color = textColor
            ),
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
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = "Navigate",
                    tint = secondaryTextColor
                )
            }
        }
    }

    if (showAboutDialog) {
        Dialog(onDismissRequest = { showAboutDialog = false }) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .heightIn(max = 600.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (initialSwitchState) Color(0xFF1E1E1E) else Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "BLE Info",
                                tint = iconTint,
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(top = 16.dp, bottom = 16.dp)
                            )

                            Text(
                                text = "Bluetooth Low Energy",
                                fontFamily = Monospace,
                                style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Center,
                                color = if (initialSwitchState) Color.White else Color.Black,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            )

                            Text(
                                text = "Bluetooth Low Energy (BLE) is a wireless personal area network technology designed for low power consumption.",
                                fontFamily = Monospace,
                                style = MaterialTheme.typography.body2,
                                textAlign = TextAlign.Justify,
                                color = if (initialSwitchState) Color.White else Color.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                            )
                        }
                    }

                    TextButton(
                        onClick = { showAboutDialog = false },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)
                    ) {
                        Text("Close", color = iconTint, fontFamily = Monospace)
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
                shape = RoundedCornerShape(16.dp),
                color = if (initialSwitchState) Color(0xFF1E1E1E) else Color.White
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
                        modifier = Modifier.size(48.dp).padding(bottom = 16.dp)
                    )

                    Text(
                        text = "For any help or to report bugs:",
                        fontFamily = Monospace,
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Center,
                        color = if (initialSwitchState) Color.White else Color.Black,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:awadhropar@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "Help/Support Request")
                            }
                            try { context.startActivity(Intent.createChooser(intent, "Send Email")) } catch (e: Exception) {}
                            showHelpDialog = false
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Text("Contact Developer", color = iconTint, fontFamily = Monospace)
                    }

                    TextButton(
                        onClick = { showHelpDialog = false },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Text("Close", color = iconTint, fontFamily = Monospace)
                    }
                }
            }
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

@Composable
fun SettingsWatermarkLogos(isDarkMode: Boolean) {
    val watermarkAlpha = if (isDarkMode) 0.35f else 0.22f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        // ── IIT Ropar Logo (Big, Full Uncropped Crest & Banner) ──
        Image(
            painter = painterResource(id = R.drawable.logo_iit_ropar),
            contentDescription = "IIT Ropar Watermark",
            modifier = Modifier
                .size(width = 190.dp, height = 210.dp)
                .graphicsLayer(alpha = watermarkAlpha),
            contentScale = ContentScale.Fit,
            colorFilter = if (isDarkMode) ColorFilter.tint(Color(0xFFCBD5E1)) else null
        )
    }
}
