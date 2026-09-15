package com.blesense.app.view.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.blesense.app.ui.theme.BleSenseColors
import com.blesense.app.model.*
import com.blesense.app.viewmodel.*
import com.blesense.app.repository.*
import com.blesense.app.util.*
import com.blesense.app.view.components.*
import com.blesense.app.repository.DataLoggerRepository
import com.blesense.app.model.DataLoggerConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataLoggerListScreen(
    navController: NavController,
    bluetoothViewModel: BluetoothScanViewModel? = null
) {
    val loggers = DataLoggerRepository.loggers
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()

    val bgColor = if (isDarkMode) BleSenseColors.BackgroundDark else BleSenseColors.LightBackground
    val cardBg = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White
    val textPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val textSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                title = { 
                    Text("Data Logger Repository", 
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        fontSize = 18.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cardBg
                )
            )
        },
        bottomBar = {
            if (navController is NavHostController) {
                ReferenceBottomNavBar(
                    navController = navController,
                    currentRoute = "data_logger_list",
                    isDarkMode = isDarkMode
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        "Data Logger Hardware Modules (${loggers.size} Active)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                }

                items(loggers) { logger ->
                    DataLoggerItem(
                        logger = logger,
                        isDarkMode = isDarkMode,
                        onClick = {
                            navController.navigate("data_logger_control/${logger.id}")
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DataLoggerItem(logger: DataLoggerConfig, isDarkMode: Boolean, onClick: () -> Unit) {
    val cardBg = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White
    val borderColor = if (isDarkMode) BleSenseColors.BorderDark else Color(0xFFE2EEF9)
    val textPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val textSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFECFEFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.DeveloperBoard,
                        contentDescription = null,
                        tint = Color(0xFF0891B2),
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Column {
                    Text(
                        text = logger.name,
                        color = textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ID: ${logger.deviceId} • MAC: ${logger.advertiserAddress}",
                        color = textSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = logger.description,
                        color = textSecondary.copy(alpha = 0.8f),
                        fontSize = 10.sp
                    )
                }
            }
            
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}


