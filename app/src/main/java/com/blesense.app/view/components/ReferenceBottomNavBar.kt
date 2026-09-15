package com.blesense.app.view.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.blesense.app.ui.theme.BleSenseColors

@Composable
fun ReferenceBottomNavBar(
    navController: NavHostController,
    currentRoute: String,
    isDarkMode: Boolean
) {
    val navBgColor = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White
    val borderColor = if (isDarkMode) BleSenseColors.BorderDark else Color(0xFFE2EEF9)
    val activeColor = if (isDarkMode) Color(0xFF38BDF8) else Color(0xFF1976D2)
    val inactiveColor = if (isDarkMode) BleSenseColors.TextTertiary else Color(0xFF94A3B8)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.08f))
                .clip(RoundedCornerShape(20.dp))
                .background(navBgColor)
                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 1. Home Tab
            BottomNavItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = (currentRoute == "intermediate_screen"),
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                onClick = {
                    if (currentRoute != "intermediate_screen") {
                        navController.navigate("intermediate_screen") {
                            popUpTo("intermediate_screen") { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }
            )

            // 2. Devices Tab
            BottomNavItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Bluetooth,
                label = "Devices",
                isSelected = (currentRoute == "home_screen" || currentRoute == "aws_scanner"),
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                onClick = {
                    if (currentRoute != "home_screen") {
                        navController.navigate("home_screen") {
                            launchSingleTop = true
                        }
                    }
                }
            )

            // 3. History Tab
            BottomNavItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.History,
                label = "History",
                isSelected = (currentRoute == "scan_history" || currentRoute == "bigadv_scanner_screen" || currentRoute == "data_logger_list"),
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                onClick = {
                    if (currentRoute != "scan_history") {
                        navController.navigate("scan_history") {
                            launchSingleTop = true
                        }
                    }
                }
            )

            // 4. Settings Tab
            BottomNavItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Settings,
                label = "Settings",
                isSelected = (currentRoute == "settings_screen"),
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                onClick = {
                    if (currentRoute != "settings_screen") {
                        navController.navigate("settings_screen") {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit
) {
    val tintColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else inactiveColor,
        label = "nav_item_tint"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true, color = activeColor.copy(alpha = 0.2f)),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tintColor,
                modifier = Modifier.size(width = 30.dp, height = 22.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = tintColor,
                maxLines = 1
            )
        }
    }
}
