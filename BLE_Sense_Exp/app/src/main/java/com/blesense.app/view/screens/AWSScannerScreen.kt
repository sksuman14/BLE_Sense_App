package com.blesense.app.view.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.blesense.app.ui.theme.BleSenseColors
import com.blesense.app.model.*
import com.blesense.app.model.BluetoothDeviceModel as BluetoothDevice
import com.blesense.app.viewmodel.*
import com.blesense.app.repository.*
import com.blesense.app.util.*
import com.blesense.app.view.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AWSScannerScreen(
    navController: NavHostController,
    bluetoothViewModel: BluetoothScanViewModel
) {
    val bluetoothDevicesRaw by bluetoothViewModel.devices.collectAsState()
    val awsDevices = remember(bluetoothDevicesRaw) {
        bluetoothDevicesRaw.filter { it.sensorData is SensorData.AWSData }
    }
    
    val isScanning by bluetoothViewModel.isScanning.collectAsState()
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val bluetoothAdapter = remember { BluetoothAdapter.getDefaultAdapter() }

    val bgColor = if (isDarkMode) BleSenseColors.BackgroundDark else BleSenseColors.LightBackground

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        backgroundColor = bgColor,
        bottomBar = {
            ReferenceBottomNavBar(
                navController = navController,
                currentRoute = "aws_scanner",
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
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            // Header
            item {
                ScannerHeaderBar(
                    title = "AWS Scanner",
                    subtitle = "AWS Weather Station Hub",
                    isDarkMode = isDarkMode,
                    isScanning = isScanning,
                    onBackClick = { navController.popBackStack() }
                )
            }

            // Hero Card
            item {
                AWSHeroCard(
                    isDarkMode = isDarkMode,
                    isScanning = isScanning,
                    deviceCount = awsDevices.size,
                    onScanToggle = {
                        if (isScanning) {
                            bluetoothViewModel.stopScan()
                        } else {
                            if (checkBluetoothPermissions(context)) {
                                if (bluetoothAdapter?.isEnabled == true) {
                                    bluetoothViewModel.startContinuousScan(activity)
                                } else {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                            context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                        } else {
                                            Toast.makeText(context, "Bluetooth Connect permission required", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }

            // Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Discovered AWS Sensors (${awsDevices.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
                    )

                    IconButton(onClick = {
                        if (checkBluetoothPermissions(context)) {
                            if (bluetoothAdapter?.isEnabled == true) {
                                bluetoothViewModel.startContinuousScan(activity)
                            } else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                                        context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                    } else {
                                        Toast.makeText(context, "Bluetooth Connect permission required", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF2563EB), modifier = Modifier.size(20.dp))
                    }
                }
            }

            // List
            if (awsDevices.isEmpty()) {
                item {
                    EmptyAWSCard(isDarkMode = isDarkMode, isScanning = isScanning)
                }
            } else {
                items(awsDevices) { device ->
                    AWSDeviceCard(
                        device = device,
                        navController = navController,
                        isDarkMode = isDarkMode
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannerHeaderBar(
    title: String,
    subtitle: String,
    isDarkMode: Boolean,
    isScanning: Boolean,
    onBackClick: () -> Unit
) {
    val cardBg = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White
    val borderColor = if (isDarkMode) BleSenseColors.BorderDark else Color(0xFFE2EEF9)
    val textPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val textSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    val infiniteTransition = rememberInfiniteTransition(label = "scan_dot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dot_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(34.dp)
                .background(if (isDarkMode) BleSenseColors.SurfaceLight else Color(0xFFF1F5F9), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textPrimary, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                if (isScanning) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(Color(0xFF10B981).copy(alpha = dotAlpha), CircleShape)
                    )
                }
            }
            Text(subtitle, fontSize = 10.sp, color = textSecondary)
        }
    }
}

@Composable
private fun AWSHeroCard(
    isDarkMode: Boolean,
    isScanning: Boolean,
    deviceCount: Int,
    onScanToggle: () -> Unit
) {
    val cardBg = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White
    val borderColor = if (isDarkMode) BleSenseColors.BorderDark else Color(0xFFE2EEF9)
    val textPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val textSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("AWS Network Scanner", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (isScanning) "Searching for AWS weather pods..." else "Scanner paused",
                fontSize = 11.sp,
                color = if (isScanning) Color(0xFF059669) else textSecondary
            )
            Text("$deviceCount AWS unit(s) detected", fontSize = 10.sp, color = textSecondary)
        }

        Button(
            onClick = onScanToggle,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (isScanning) Color(0xFFEF4444) else Color(0xFF2563EB),
                contentColor = Color.White
            ),
            shape = CircleShape
        ) {
            Icon(
                if (isScanning) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(if (isScanning) "Stop" else "Start", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AWSDeviceCard(
    device: BluetoothDevice,
    navController: NavHostController,
    isDarkMode: Boolean
) {
    val cardBg = if (isDarkMode) BleSenseColors.SurfaceDark else Color.White
    val borderColor = if (isDarkMode) BleSenseColors.BorderDark else Color(0xFFE2EEF9)
    val textPrimary = if (isDarkMode) BleSenseColors.TextPrimary else BleSenseColors.LightTextPrimary
    val textSecondary = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .clickable {
                val encName = Uri.encode(device.name ?: "AWS")
                val encAddr = Uri.encode(device.address)
                navController.navigate("aws_advertising/$encName/$encAddr")
            },
        backgroundColor = cardBg,
        elevation = 0.dp,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFECFDF5)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name ?: "AWS Weather Pod", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textPrimary)
                Text(device.address, fontSize = 11.sp, color = textSecondary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EmptyAWSCard(isDarkMode: Boolean, isScanning: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(if (isDarkMode) BleSenseColors.SurfaceDark else Color.White)
            .border(1.dp, if (isDarkMode) BleSenseColors.BorderDark else Color(0xFFE2EEF9), RoundedCornerShape(20.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.CloudQueue, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(28.dp))
            Text(
                if (isScanning) "Searching for AWS sensors..." else "No AWS sensors found",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDarkMode) BleSenseColors.TextSecondary else BleSenseColors.LightTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun checkBluetoothPermissions(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}


