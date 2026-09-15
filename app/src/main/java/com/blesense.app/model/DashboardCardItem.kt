package com.blesense.app.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class DashboardCardItem(
    val vectorIcon: ImageVector,
    val title: String,
    val subtitle: String,
    val badgeText: String,
    val badgeBg: Color,
    val badgeTextTint: Color,
    val iconBg: Color,
    val iconTint: Color,
    val route: String? = null,
    val intentClass: Class<*>? = null
)
