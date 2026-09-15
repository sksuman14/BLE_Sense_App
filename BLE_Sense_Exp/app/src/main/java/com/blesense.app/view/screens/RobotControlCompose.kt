@file:Suppress("DEPRECATION", "UseCompatLoadingForDrawables")
package com.blesense.app.view.screens

import com.blesense.app.R
import com.blesense.app.ui.theme.BleSenseColors
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blesense.app.viewmodel.BluetoothScanViewModel
import com.blesense.app.viewmodel.BluetoothScanViewModelFactory
import com.blesense.app.model.SensorData
import com.blesense.app.model.BluetoothDeviceModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import com.blesense.app.util.ThemeManager
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.OutputStream
import java.util.UUID
import kotlin.random.Random
import kotlin.math.pow
import kotlin.math.sin

// Enum to represent Bluetooth scanning states
enum class ScanState {
    IDLE, SCANNING
}

// ================= BLUETOOTH SCANNING VIEW MODEL =================
class ClassicBluetoothViewModel : ViewModel() {
    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()
    private val _scanState = MutableStateFlow(ScanState.IDLE)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    internal val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }
    private var receiverRegistered = false

    private val deviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let {
                        val currentDevices = _devices.value.toMutableList()
                        if (!currentDevices.contains(device)) {
                            currentDevices.add(device)
                            _devices.value = currentDevices
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _scanState.value = ScanState.IDLE
                }
            }
        }
    }

    @RequiresPermission(allOf = [
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    ])
    fun startScan(context: Context) {
        if (bluetoothAdapter == null) {
            _errorMessage.value = "Bluetooth not supported on this device"
            return
        }
        if (!bluetoothAdapter!!.isEnabled) {
            _errorMessage.value = "Bluetooth is disabled"
            return
        }
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
                    PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) ==
                    PackageManager.PERMISSION_GRANTED
        }
        if (!hasPermission) {
            _errorMessage.value = "Bluetooth permissions required"
            return
        }
        _scanState.value = ScanState.SCANNING
        _devices.value = emptyList()
        _errorMessage.value = null

        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            context.registerReceiver(deviceReceiver, filter)
            receiverRegistered = true
        }

        if (bluetoothAdapter!!.isDiscovering) {
            bluetoothAdapter!!.cancelDiscovery()
        }
        bluetoothAdapter!!.startDiscovery()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan(context: Context) {
        _scanState.value = ScanState.IDLE
        bluetoothAdapter?.cancelDiscovery()
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(deviceReceiver)
                receiverRegistered = false
            } catch (e: Exception) {
                Log.e("ClassicBT", "Error unregistering receiver: ${e.message}")
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onCleared() {
        super.onCleared()
        bluetoothAdapter?.cancelDiscovery()
    }
}

// Enable immersive mode for full-screen experience
fun Activity.enableImmersiveMode() {
    window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
}

// ================= GLOBAL BLUETOOTH CONNECTION MANAGER =================
object BluetoothConnectionManager {
    var bluetoothSocket: BluetoothSocket? = null
    fun disconnect() {
        try {
            bluetoothSocket?.close()
            bluetoothSocket = null
        } catch (e: Exception) {
            Log.e("BluetoothManager", "Error closing socket: ${e.message}")
        }
    }
    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected == true
    }
}

// Robust Bluetooth connection function with multi-channel RFCOMM fallback
@SuppressLint("MissingPermission")
fun connectToDevice(context: Context, address: String) {
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
        (context as? Activity)?.runOnUiThread {
            Toast.makeText(context, "Bluetooth is disabled on device", Toast.LENGTH_SHORT).show()
        }
        return
    }

    val device = bluetoothAdapter.getRemoteDevice(address)
    val uuid = device.uuids?.firstOrNull()?.uuid ?: UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    if (device.bondState == BluetoothDevice.BOND_NONE) {
        try {
            Log.d("BluetoothConnect", "Device not paired. Initiating auto-bond...")
            device.createBond()
        } catch (e: Exception) {
            Log.e("BluetoothConnect", "Auto-bond exception: ${e.message}")
        }
    }

    BluetoothConnectionManager.disconnect()

    Thread {
        try {
            bluetoothAdapter.cancelDiscovery()
            var socket: BluetoothSocket? = null

            try {
                Log.d("BluetoothConnect", "Attempting Method 1 (Standard SPP)...")
                socket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()
            } catch (e1: Exception) {
                Log.d("BluetoothConnect", "Method 1 failed (${e1.message}), trying Fallback Channel 1...")
                try { socket?.close() } catch (_: Exception) {}

                try {
                    socket = createFallbackSocket(device, 1)
                    socket?.connect()
                } catch (e2: Exception) {
                    Log.d("BluetoothConnect", "Method 2 failed (${e2.message}), trying Fallback Channel 2...")
                    try { socket?.close() } catch (_: Exception) {}

                    socket = createFallbackSocket(device, 2)
                    socket?.connect()
                }
            }

            if (socket?.isConnected == true) {
                BluetoothConnectionManager.bluetoothSocket = socket
                (context as? Activity)?.runOnUiThread {
                    Toast.makeText(context, "Connected to ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
                }
            } else {
                throw Exception("Unable to establish Bluetooth RFCOMM socket connection")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            (context as? Activity)?.runOnUiThread {
                Toast.makeText(context, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }.start()
}

@SuppressLint("MissingPermission", "DiscouragedPrivateApi")
private fun createFallbackSocket(device: BluetoothDevice, channel: Int = 1): BluetoothSocket? {
    try {
        val m = device.javaClass.getMethod(
            "createRfcommSocket",
            *arrayOf<Class<*>>(Int::class.javaPrimitiveType as Class<*>)
        )
        return m.invoke(device, channel) as BluetoothSocket
    } catch (e: Exception) {
        Log.e("BluetoothConnect", "Fallback socket creation failed for channel $channel", e)
    }
    return null
}

// ================= ROBOT CONTROL ACTIVITY =================
class RobotControlCompose : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF030A1A)
                ) {
                    RobotControlScreen(onBackPressed = { finish() })
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveMode()
        }
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveMode()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }
}

// ================= ROBOT CONTROL VIEW MODEL =================
class RobotControlViewModel : ViewModel() {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    private var outputStream: OutputStream? = null

    // Serialized command queue to prevent UI freeze and socket contention
    private val commandChannel = kotlinx.coroutines.channels.Channel<String>(
        capacity = kotlinx.coroutines.channels.Channel.UNLIMITED
    )

    init {
        updateConnectionStatus()
        startCommandProcessor()
    }

    private fun startCommandProcessor() {
        viewModelScope.launch(Dispatchers.IO) {
            for (command in commandChannel) {
                processSendCommand(command)
            }
        }
    }

    fun updateConnectionStatus() {
        val connected = BluetoothConnectionManager.isConnected()
        _isConnected.value = connected
        if (connected && outputStream == null) {
            try {
                outputStream = BluetoothConnectionManager.bluetoothSocket?.outputStream
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (!connected) {
            outputStream = null
        }
    }

    fun isBluetoothConnected(): Boolean {
        return BluetoothConnectionManager.isConnected()
    }

    fun sendCommand(command: String) {
        commandChannel.trySend(command)
    }

    private fun processSendCommand(command: String) {
        try {
            if (!isBluetoothConnected()) {
                _isConnected.value = false
                outputStream = null
                return
            }

            if (outputStream == null) {
                outputStream = BluetoothConnectionManager.bluetoothSocket?.outputStream
            }

            outputStream?.let { os ->
                os.write(command.toByteArray())
                os.flush()
                _isConnected.value = true
            } ?: run {
                _isConnected.value = false
            }
        } catch (e: Exception) {
            Log.e("RobotCommand", "Failed to send command $command", e)
            _isConnected.value = false
            try {
                outputStream = BluetoothConnectionManager.bluetoothSocket?.outputStream
            } catch (_: Exception) {}
        }
    }
}

// ================= DEVICE SELECTION DIALOG =================
@Composable
fun DeviceSelectionDialog(
    devices: List<BluetoothDevice>,
    isScanning: Boolean,
    onDeviceSelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val cardBackgroundColor = BleSenseColors.SurfaceDark
    val textColor = BleSenseColors.TextPrimary
    val dividerColor = BleSenseColors.BorderDark

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.width(340.dp),
            shape = RoundedCornerShape(20.dp),
            backgroundColor = cardBackgroundColor
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Robot Device",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Icon(
                        Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = Color(0xFF007AFF),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (isScanning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF007AFF)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Scanning nearby devices...",
                            color = textColor,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                if (devices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isScanning) "Searching for Robot..." else "No devices found",
                            color = textColor.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        items(devices) { device ->
                            DeviceItem(
                                device = device,
                                onClick = { onDeviceSelected(device.address) },
                                textColor = textColor
                            )
                            Divider(color = dividerColor)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Cancel", color = textColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(
    device: BluetoothDevice,
    onClick: () -> Unit,
    textColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        val deviceName = remember(device) {
            try {
                device.name ?: "Unknown Robot Module"
            } catch (e: SecurityException) {
                "Unknown Robot Module"
            }
        }
        Text(
            text = deviceName,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = textColor
        )
        Text(
            text = device.address,
            fontSize = 11.sp,
            color = textColor.copy(alpha = 0.6f)
        )
    }
}

// ================= SENSOR TELEMETRY DETAIL DIALOG =================
@Composable
fun SensorDetailDialog(
    sensorName: String,
    sensorIcon: ImageVector,
    details: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(320.dp),
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0xFF0B1936)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(sensorIcon, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(sensorName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF050E24))
                        .padding(12.dp)
                ) {
                    Column {
                        Text("LIVE TELEMETRY READINGS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(details, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = Color(0xFF007AFF), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ================= HUD SUB-COMPONENTS =================
@Composable
fun TelemetryCardHUD(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    cardBg: Color,
    borderColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(1.2.dp, borderColor.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
fun SparklineGraphHUD(color: Color) {
    Canvas(modifier = Modifier
        .width(50.dp)
        .height(18.dp)) {
        val path = Path().apply {
            moveTo(0f, size.height * 0.7f)
            lineTo(size.width * 0.25f, size.height * 0.3f)
            lineTo(size.width * 0.5f, size.height * 0.6f)
            lineTo(size.width * 0.75f, size.height * 0.2f)
            lineTo(size.width, size.height * 0.5f)
        }
        drawPath(path, color = color, style = Stroke(width = 2.5f))
    }
}

@Composable
fun Axis3DVisualizerHUD() {
    Canvas(modifier = Modifier.size(30.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        // X axis (Red)
        drawLine(Color(0xFFEF4444), Offset(cx, cy), Offset(cx + 10f, cy + 10f), strokeWidth = 2.5f)
        // Y axis (Green)
        drawLine(Color(0xFF22C55E), Offset(cx, cy), Offset(cx + 12f, cy - 8f), strokeWidth = 2.5f)
        // Z axis (Blue)
        drawLine(Color(0xFF38BDF8), Offset(cx, cy), Offset(cx, cy - 12f), strokeWidth = 2.5f)
    }
}

@Composable
fun HexControlPanelHUD(
    modifier: Modifier = Modifier,
    titleTop: String,
    titleBottom: String,
    iconTop: ImageVector,
    iconBottom: ImageVector,
    onPressTop: () -> Unit,
    onReleaseTop: () -> Unit,
    onPressBottom: () -> Unit,
    onReleaseBottom: () -> Unit,
    cardBg: Color,
    glowColor: Color
) {
    var topPressed by remember { mutableStateOf(false) }
    var bottomPressed by remember { mutableStateOf(false) }

    val currentOnPressTop by rememberUpdatedState(onPressTop)
    val currentOnReleaseTop by rememberUpdatedState(onReleaseTop)
    val currentOnPressBottom by rememberUpdatedState(onPressBottom)
    val currentOnReleaseBottom by rememberUpdatedState(onReleaseBottom)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(cardBg)
            .border(1.2.dp, glowColor.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
            .padding(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top Action Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(titleTop, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(if (topPressed) Color(0xFF0059CC) else Color(0xFF007AFF))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitFirstDown(requireUnconsumed = false)
                                    topPressed = true
                                    currentOnPressTop()
                                    waitForUpOrCancellation()
                                    topPressed = false
                                    currentOnReleaseTop()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(iconTop, contentDescription = titleTop, tint = Color.White, modifier = Modifier.size(34.dp))
                }
            }

            Text("•••", fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)

            // Bottom Action Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(if (bottomPressed) Color(0xFF0059CC) else Color(0xFF007AFF))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitFirstDown(requireUnconsumed = false)
                                    bottomPressed = true
                                    currentOnPressBottom()
                                    waitForUpOrCancellation()
                                    bottomPressed = false
                                    currentOnReleaseBottom()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(iconBottom, contentDescription = titleBottom, tint = Color.White, modifier = Modifier.size(34.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(titleBottom, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}

// ── State for frame-synced highway motion ──
private class DriveSimState {
    var roadOffset by mutableFloatStateOf(0f)
    var wheelAngle by mutableFloatStateOf(0f)
}

// ================= UNIFIED MAIN SCREEN: 3D HIGHWAY + CONTROLS =================
@Composable
fun RobotControlScreen(
    viewModel: RobotControlViewModel = viewModel(),
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val bluetoothViewModel: ClassicBluetoothViewModel = viewModel()

    val activity = context as? androidx.activity.ComponentActivity

    // LOCK ORIENTATION TO SENSOR_LANDSCAPE
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    val sensorViewModel: BluetoothScanViewModel? = activity?.let {
        viewModel(
            viewModelStoreOwner = it,
            factory = BluetoothScanViewModelFactory(it.application)
        )
    }

    val isConnected by viewModel.isConnected.collectAsState()

    var showDeviceDialog by remember { mutableStateOf(false) }
    var showSensorDialog by remember { mutableStateOf(false) }
    var showSensorMenuDialog by remember { mutableStateOf(false) }
    var selectedSensorName by remember { mutableStateOf("") }
    var selectedSensorIcon by remember { mutableStateOf(Icons.Default.Thermostat) }
    var selectedSensorDetails by remember { mutableStateOf("") }

    var speed by remember { mutableStateOf(30) }
    var buzzerOn by remember { mutableStateOf(false) }
    var driveDirection by remember { mutableStateOf("STOP") }

    val isSystemDark = isSystemInDarkTheme()
    val isAppDarkMode by ThemeManager.isDarkMode.collectAsState()
    var isDayMode by remember { mutableStateOf(!isAppDarkMode) }

    val scanState by bluetoothViewModel.scanState.collectAsState()
    val devices by bluetoothViewModel.devices.collectAsState()
    val errorMessage by bluetoothViewModel.errorMessage.collectAsState()

    val sensorDevices by sensorViewModel?.devices?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val historyTrigger by sensorViewModel?.historyUpdateTrigger?.collectAsState() ?: remember { mutableStateOf(0L) }

    val sht40Device = remember(sensorDevices, historyTrigger) {
        sensorDevices.find { it.sensorData is SensorData.SHT40Data }
    }
    val lis3dhDevice = remember(sensorDevices, historyTrigger) {
        sensorDevices.find { it.sensorData is SensorData.LIS3DHData }
    }

    val sht40Data = sht40Device?.sensorData as? SensorData.SHT40Data
    val lis3dhData = lis3dhDevice?.sensorData as? SensorData.LIS3DHData

    val tempStr = sht40Data?.temperature ?: "0"
    val humStr = sht40Data?.humidity ?: "0"

    val posXStr = lis3dhData?.x ?: "0"
    val posYStr = lis3dhData?.y ?: "0"
    val posZStr = lis3dhData?.z ?: "0"

    LaunchedEffect(Unit) {
        ThemeManager.initializeWithSystemTheme(context, isSystemDark)
        activity?.let { sensorViewModel?.startContinuousScan(it) }

        while (true) {
            viewModel.updateConnectionStatus()
            delay(250)
        }
    }

    val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            bluetoothViewModel.startScan(context)
            showDeviceDialog = true
        } else {
            Toast.makeText(context, "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
        }
    }

    val bluetoothEnableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter?.isEnabled == true) {
            val hasPermissions = bluetoothPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            if (hasPermissions) {
                bluetoothViewModel.startScan(context)
                showDeviceDialog = true
            } else {
                permissionsLauncher.launch(bluetoothPermissions)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (scanState == ScanState.SCANNING) {
                bluetoothViewModel.stopScan(context)
            }
        }
    }

    // UI Theme Constants
    val hudCardBg = Color(0xFF0A1833).copy(alpha = 0.90f)
    val glowBorder = Color(0xFF007AFF)
    val textPrimary = Color.White
    val textSecondary = Color(0xFF94A3B8)

    // ── Unified 3D Simulation & Physics States ──
    val simState = remember { DriveSimState() }

    LaunchedEffect(driveDirection, speed) {
        var lastTime = -1L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastTime < 0) lastTime = frameNanos
                val dtMs = (frameNanos - lastTime) / 1_000_000f
                lastTime = frameNanos

                if (driveDirection != "STOP") {
                    val sf = (speed / 50f).coerceAtLeast(0.4f)
                    val step = dtMs.coerceIn(1f, 33f)
                    when (driveDirection) {
                        "FORWARD" -> {
                            simState.roadOffset += 0.35f * sf * step
                            simState.wheelAngle += 0.75f * sf * step
                        }
                        "BACKWARD" -> {
                            simState.roadOffset -= 0.25f * sf * step
                            simState.wheelAngle -= 0.55f * sf * step
                        }
                        "LEFT", "RIGHT" -> {
                            simState.roadOffset += 0.28f * sf * step
                            simState.wheelAngle += 0.60f * sf * step
                        }
                    }
                }
            }
        }
    }

    val bodyTilt by animateFloatAsState(
        targetValue = when (driveDirection) {
            "LEFT" -> -8.5f
            "RIGHT" -> 8.5f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 160f),
        label = "chassisRoll"
    )

    val turnYaw by animateFloatAsState(
        targetValue = when (driveDirection) {
            "LEFT" -> -1f
            "RIGHT" -> 1f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = 0.60f, stiffness = 130f),
        label = "turnYaw"
    )

    val roadCurveFactor by animateFloatAsState(
        targetValue = when (driveDirection) {
            "LEFT" -> -1f
            "RIGHT" -> 1f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = 0.70f, stiffness = 120f),
        label = "roadCurveFactor"
    )

    val starData = remember {
        List(45) {
            Triple(Random.nextFloat(), Random.nextFloat() * 0.95f, Random.nextFloat() * 2.2f + 0.5f)
        }
    }
    data class CyberTower(
        val xFrac: Float,
        val wFrac: Float,
        val heightPx: Float,
        val spireHeight: Float,
        val beaconRed: Boolean,
        val hasWindows: Boolean
    )
    val cyberTowers = remember {
        listOf(
            CyberTower(0.01f, 0.07f, 65f, 25f, true, true),
            CyberTower(0.08f, 0.08f, 90f, 35f, false, true),
            CyberTower(0.16f, 0.07f, 52f, 15f, true, false),
            CyberTower(0.22f, 0.08f, 75f, 28f, false, true),
            CyberTower(0.29f, 0.06f, 42f, 12f, true, false),
            CyberTower(0.35f, 0.07f, 68f, 22f, false, true),
            CyberTower(0.58f, 0.07f, 58f, 20f, true, true),
            CyberTower(0.64f, 0.08f, 82f, 32f, false, true),
            CyberTower(0.72f, 0.06f, 48f, 14f, true, false),
            CyberTower(0.78f, 0.09f, 95f, 40f, false, true),
            CyberTower(0.86f, 0.07f, 62f, 24f, true, true),
            CyberTower(0.92f, 0.08f, 72f, 28f, false, true)
        )
    }

    val pathRoad = remember { Path() }
    val pathHeadlight = remember { Path() }
    val pathDroneBeam = remember { Path() }

    val carRearBitmap: ImageBitmap = remember {
        try {
            // Loading the new IIT Ropar Car Image from Drawables
            BitmapFactory.decodeResource(context.resources, R.drawable.iitroparcarimage).asImageBitmap()
        } catch (e: Exception) {
            try {
                BitmapFactory.decodeResource(context.resources, R.drawable.cyber_rover_car).asImageBitmap()
            } catch (e2: Exception) {
                android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF01040A))
    ) {
        // ── 1. CINEMATIC 3D HIGHWAY BACKGROUND WITH REALISTIC CONCEPT SUV (PB BADGE) ──
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val w = size.width
            val h = size.height
            val horizonY = h * 0.35f
            val roadBend = roadCurveFactor * w * 0.22f
            val roadScroll = simState.roadOffset
            val isMoving = driveDirection != "STOP"

            fun getRoadCenter(z: Float): Float {
                return (w / 2f) + roadBend * (1f - z).pow(1.6f)
            }

            // ── Atmospheric Sky (Day / Night Mode) ──
            if (isDayMode) {
                // Vibrant Azure Daylight Sky
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0284C7),
                            Color(0xFF38BDF8),
                            Color(0xFF7DD3FC),
                            Color(0xFFBAE6FD),
                            Color(0xFFFEF08A).copy(alpha = 0.90f)
                        ),
                        startY = 0f,
                        endY = horizonY + 25f
                    ),
                    size = Size(w, horizonY + 25f)
                )

                // Radiant Sun with Solar Corona Halo
                val sunX = w * 0.78f + roadBend * 0.08f
                val sunY = horizonY * 0.38f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFFFFF),
                            Color(0xFFFFF59D).copy(alpha = 0.85f),
                            Color(0xFFFFD54F).copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        center = Offset(sunX, sunY),
                        radius = 70f
                    ),
                    radius = 70f,
                    center = Offset(sunX, sunY)
                )

                // Fluffy Procedural Daylight Clouds
                val cloudOffset = (simState.roadOffset * 0.08f) % (w + 200f)
                drawOval(
                    color = Color.White.copy(alpha = 0.55f),
                    topLeft = Offset((w * 0.12f + cloudOffset) % (w + 200f) - 100f, horizonY * 0.26f),
                    size = Size(120f, 32f)
                )
                drawOval(
                    color = Color.White.copy(alpha = 0.65f),
                    topLeft = Offset((w * 0.16f + cloudOffset) % (w + 200f) - 100f, horizonY * 0.20f),
                    size = Size(80f, 38f)
                )
                drawOval(
                    color = Color.White.copy(alpha = 0.50f),
                    topLeft = Offset((w * 0.52f + cloudOffset * 0.7f) % (w + 200f) - 100f, horizonY * 0.34f),
                    size = Size(140f, 28f)
                )
            } else {
                // Atmospheric Night Sky
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF01040A),
                            Color(0xFF050F1E),
                            Color(0xFF0A1E38),
                            Color(0xFF13325B),
                            Color(0xFF1E4878).copy(alpha = 0.85f)
                        ),
                        startY = 0f,
                        endY = horizonY + 25f
                    ),
                    size = Size(w, horizonY + 25f)
                )

                // Twinkling Star Field
                starData.forEach { (xf, yf, sz) ->
                    val starAlpha = (0.40f + xf * 0.55f).coerceIn(0.2f, 1f)
                    drawCircle(
                        color = Color.White.copy(alpha = starAlpha),
                        radius = sz,
                        center = Offset(w * xf, horizonY * yf)
                    )
                }
            }

            // ── 1. ROBOTICS RESEARCH CITY & SMART PROVING GROUNDS SKYLINE ──
            val beaconBlink = (System.currentTimeMillis() / 450) % 2 == 0L
            val towerShift = roadBend * 0.12f

            cyberTowers.forEach { tower ->
                val tx = w * tower.xFrac + towerShift
                val tw = w * tower.wFrac
                val ty = horizonY - tower.heightPx

                // Building Silhouette Block
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = if (isDayMode) {
                            listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1), Color(0xFF94A3B8))
                        } else {
                            listOf(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF020617))
                        },
                        startY = ty,
                        endY = horizonY
                    ),
                    topLeft = Offset(tx, ty),
                    size = Size(tw, tower.heightPx)
                )

                // Building Border / Cyber Frame Line
                drawRect(
                    color = if (isDayMode) Color(0xFF64748B).copy(alpha = 0.45f) else Color(0xFF00F0FF).copy(alpha = 0.25f),
                    topLeft = Offset(tx, ty),
                    size = Size(tw, tower.heightPx),
                    style = Stroke(width = 1f)
                )

                // Cyber Glowing Windows / Data Matrix Columns
                if (tower.hasWindows) {
                    val winRows = (tower.heightPx / 12f).toInt().coerceIn(2, 6)
                    val winCols = 3
                    val colW = tw / (winCols + 1)
                    val rowH = tower.heightPx / (winRows + 1)
                    for (r in 1..winRows) {
                        for (c in 1..winCols) {
                            val wx = tx + c * colW - 1.5f
                            val wy = ty + r * rowH - 1.5f
                            val winColor = if (isDayMode) {
                                Color(0xFF0284C7).copy(alpha = 0.65f)
                            } else {
                                if ((r + c) % 2 == 0) Color(0xFF00F0FF).copy(alpha = 0.75f) else Color(0xFF818CF8).copy(alpha = 0.60f)
                            }
                            drawRect(
                                color = winColor,
                                topLeft = Offset(wx, wy),
                                size = Size(3f, 3f)
                            )
                        }
                    }
                }

                // Antenna Spire & Pulsing Aviation Beacon Light
                val spireX = tx + tw / 2f
                val spireTopY = ty - tower.spireHeight
                drawLine(
                    color = if (isDayMode) Color(0xFF475569) else Color(0xFF38BDF8),
                    start = Offset(spireX, ty),
                    end = Offset(spireX, spireTopY),
                    strokeWidth = 1.5f
                )

                val beaconColor = if (tower.beaconRed) Color(0xFFFF1744) else Color(0xFF00F0FF)
                val beaconAlpha = if (beaconBlink) 0.95f else 0.25f
                drawCircle(
                    color = beaconColor.copy(alpha = beaconAlpha),
                    radius = 2.5f,
                    center = Offset(spireX, spireTopY)
                )
                if (beaconBlink) {
                    drawCircle(
                        brush = Brush.radialGradient(listOf(beaconColor.copy(alpha = 0.50f), Color.Transparent)),
                        radius = 8f,
                        center = Offset(spireX, spireTopY)
                    )
                }
            }

            // ── 2. AUTONOMOUS SCANNING SURVEILLANCE DRONES ──
            val timeSec = System.currentTimeMillis() / 1000f
            val drones = listOf(
                Pair(0.20f, 0.38f),
                Pair(0.80f, 0.32f),
                Pair(0.48f, 0.24f)
            )

            drones.forEachIndexed { idx, (dxFrac, dyFrac) ->
                val dx = w * dxFrac + roadBend * 0.08f
                val hoverPhase = (timeSec * 2.2f + idx * 1.8f)
                val dy = horizonY * dyFrac + kotlin.math.sin(hoverPhase) * 5f

                // Downward Holographic Scanning Sensor Cone
                pathDroneBeam.reset()
                pathDroneBeam.moveTo(dx, dy + 2f)
                pathDroneBeam.lineTo(dx - 32f, horizonY + 8f)
                pathDroneBeam.lineTo(dx + 32f, horizonY + 8f)
                pathDroneBeam.close()

                val beamColor = if (isDayMode) Color(0xFF0284C7) else Color(0xFF00F0FF)
                drawPath(
                    pathDroneBeam,
                    brush = Brush.verticalGradient(
                        colors = listOf(beamColor.copy(alpha = 0.16f), Color.Transparent),
                        startY = dy,
                        endY = horizonY + 8f
                    )
                )

                // Drone Aerodynamic Fuselage & Rotors
                drawRoundRect(
                    color = if (isDayMode) Color(0xFF0F172A) else Color(0xFF1E293B),
                    topLeft = Offset(dx - 9f, dy - 2.5f),
                    size = Size(18f, 5f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
                // Glowing Optic Eye
                drawCircle(
                    color = beamColor,
                    radius = 2.0f,
                    center = Offset(dx, dy)
                )
                // Rotor Blades
                drawLine(
                    color = Color.White.copy(alpha = 0.70f),
                    start = Offset(dx - 12f, dy - 2f),
                    end = Offset(dx - 4f, dy - 2f),
                    strokeWidth = 1f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.70f),
                    start = Offset(dx + 4f, dy - 2f),
                    end = Offset(dx + 12f, dy - 2f),
                    strokeWidth = 1f
                )
            }

            // ── 3. HIGH-TECH 3D CYBER-GRID TERRAIN MATRIX (REPLACING GRASS) ──
            // Base Ground
            drawRect(
                brush = Brush.verticalGradient(
                    colors = if (isDayMode) {
                        listOf(
                            Color(0xFF0F172A),
                            Color(0xFF1E293B),
                            Color(0xFF0F172A)
                        )
                    } else {
                        listOf(
                            Color(0xFF020617),
                            Color(0xFF050B18),
                            Color(0xFF02040A)
                        )
                    },
                    startY = horizonY,
                    endY = h
                ),
                topLeft = Offset(0f, horizonY),
                size = Size(w, h - horizonY)
            )

            // 3D Perspective Radial Grid Lines spreading across terrain
            val gridRays = 18
            val gridColor = if (isDayMode) Color(0xFF38BDF8).copy(alpha = 0.22f) else Color(0xFF00F0FF).copy(alpha = 0.18f)
            val rayCenter = getRoadCenter(0f)

            for (r in 0..gridRays) {
                val rayFrac = r.toFloat() / gridRays
                val rayEndX = w * rayFrac
                drawLine(
                    color = gridColor,
                    start = Offset(rayCenter, horizonY),
                    end = Offset(rayEndX, h),
                    strokeWidth = 1.0f
                )
            }

            // Dynamic Longitudinal Scrolling 3D Grid Rings
            val gridRings = 14
            val gridScroll = (roadScroll * 0.08f) % 1.0f
            for (g in 0..gridRings) {
                val baseT = (g.toFloat() / gridRings + gridScroll) % 1.0f
                val gz = baseT * baseT
                val gy = horizonY + (h - horizonY) * gz
                val gAlpha = (gz * 0.35f + 0.04f).coerceAtMost(0.40f)

                drawLine(
                    color = if (isDayMode) Color(0xFF0284C7).copy(alpha = gAlpha) else Color(0xFF8B5CF6).copy(alpha = gAlpha),
                    start = Offset(0f, gy),
                    end = Offset(w, gy),
                    strokeWidth = (gz * 2.2f + 0.5f)
                )

                // Glowing Circuit Nodes along the sides of the road
                if (gz > 0.10f) {
                    val gcx = getRoadCenter(gz)
                    val ghw = (0.02f + 0.98f * gz) * (w * 0.46f)
                    val nodeRadius = gz * 3.5f + 1f
                    // Left and right nodes
                    drawCircle(
                        color = Color(0xFF00F0FF).copy(alpha = gz * 0.65f),
                        radius = nodeRadius,
                        center = Offset(gcx - ghw - 18f * gz, gy)
                    )
                    drawCircle(
                        color = Color(0xFF00F0FF).copy(alpha = gz * 0.65f),
                        radius = nodeRadius,
                        center = Offset(gcx + ghw + 18f * gz, gy)
                    )
                }
            }

            // 3D Receding Curved Highway Asphalt
            val roadHalfWidth = w * 0.46f
            val segments = 40

            pathRoad.reset()
            for (i in 0..segments) {
                val t = i.toFloat() / segments
                val z = t * t
                val y = horizonY + (h - horizonY) * z
                val cx = getRoadCenter(z)
                val hw = (0.02f + 0.98f * z) * roadHalfWidth
                val x = cx - hw
                if (i == 0) pathRoad.moveTo(x, y) else pathRoad.lineTo(x, y)
            }
            for (i in segments downTo 0) {
                val t = i.toFloat() / segments
                val z = t * t
                val y = horizonY + (h - horizonY) * z
                val cx = getRoadCenter(z)
                val hw = (0.02f + 0.98f * z) * roadHalfWidth
                val x = cx + hw
                pathRoad.lineTo(x, y)
            }
            pathRoad.close()

            drawPath(
                pathRoad,
                brush = Brush.verticalGradient(
                    colors = if (isDayMode) {
                        listOf(
                            Color(0xFF475569),
                            Color(0xFF334155),
                            Color(0xFF1E293B),
                            Color(0xFF334155)
                        )
                    } else {
                        listOf(
                            Color(0xFF0F172A),
                            Color(0xFF141E33),
                            Color(0xFF1A2640),
                            Color(0xFF0F182A)
                        )
                    },
                    startY = horizonY,
                    endY = h
                )
            )

            // Road Edge Lines, Curbs & Posts
            for (i in 1..segments) {
                val t = i.toFloat() / segments
                val prevT = (i - 1).toFloat() / segments
                val z = t * t
                val pz = prevT * prevT

                val y = horizonY + (h - horizonY) * z
                val py = horizonY + (h - horizonY) * pz
                val cx = getRoadCenter(z)
                val pcx = getRoadCenter(pz)
                val hw = (0.02f + 0.98f * z) * roadHalfWidth
                val phw = (0.02f + 0.98f * pz) * roadHalfWidth

                val lineThick = z * 4.2f + 0.5f
                val lineAlpha = (z * 0.90f + 0.08f).coerceAtMost(1f)

                drawLine(
                    color = Color.White.copy(alpha = lineAlpha * 0.90f),
                    start = Offset(pcx - phw, py),
                    end = Offset(cx - hw, y),
                    strokeWidth = lineThick
                )
                drawLine(
                    color = Color.White.copy(alpha = lineAlpha * 0.90f),
                    start = Offset(pcx + phw, py),
                    end = Offset(cx + hw, y),
                    strokeWidth = lineThick
                )

                if (i % 3 == 0 && z > 0.06f) {
                    val sw = z * 9f
                    val sh = z * 5.5f
                    val curbColor = if ((i / 3) % 2 == 0) Color(0xFFEF4444) else Color(0xFFF8FAFC)
                    drawRect(
                        color = curbColor.copy(alpha = z * 0.50f),
                        topLeft = Offset(cx - hw - sw, y - sh / 2),
                        size = Size(sw, sh)
                    )
                    drawRect(
                        color = curbColor.copy(alpha = z * 0.50f),
                        topLeft = Offset(cx + hw, y - sh / 2),
                        size = Size(sw, sh)
                    )
                }

                if (i % 8 == 0 && z > 0.12f) {
                    val postH = z * 24f
                    val postW = z * 3.5f
                    drawRoundRect(
                        color = Color(0xFF475569),
                        topLeft = Offset(cx - hw - postW * 3.5f, y - postH),
                        size = Size(postW, postH),
                        cornerRadius = CornerRadius(1f, 1f)
                    )
                    drawCircle(
                        color = Color(0xFFF59E0B).copy(alpha = z * 0.90f),
                        radius = postW * 0.85f,
                        center = Offset(cx - hw - postW * 3.0f, y - postH * 0.80f)
                    )
                    drawRoundRect(
                        color = Color(0xFF475569),
                        topLeft = Offset(cx + hw + postW * 2.5f, y - postH),
                        size = Size(postW, postH),
                        cornerRadius = CornerRadius(1f, 1f)
                    )
                    drawCircle(
                        color = Color(0xFFF59E0B).copy(alpha = z * 0.90f),
                        radius = postW * 0.85f,
                        center = Offset(cx + hw + postW * 3.0f, y - postH * 0.80f)
                    )
                }
            }

            // Scrolling Lane Divider Dashes
            val dashCount = 26
            val scrollNorm = ((roadScroll * 0.0036f) % 1f + 1f) % 1f
            for (i in 0 until dashCount) {
                if (i % 2 != 0) continue
                val baseT = i.toFloat() / dashCount
                val st = (baseT + scrollNorm) % 1f
                if (st < 0.012f || st > 0.97f) continue

                val z = st * st
                val y = horizonY + (h - horizonY) * z
                val cx = getRoadCenter(z)
                val hw = (0.02f + 0.98f * z) * roadHalfWidth

                val cDashW = z * 7.5f + 0.8f
                val cDashH = z * 24f + 1.8f
                val cAlpha = (z * 0.96f).coerceAtMost(1f)
                drawRoundRect(
                    color = Color(0xFFF59E0B).copy(alpha = cAlpha),
                    topLeft = Offset(cx - cDashW / 2, y - cDashH / 2),
                    size = Size(cDashW, cDashH),
                    cornerRadius = CornerRadius(cDashW / 2, cDashW / 2)
                )

                val sDashW = z * 4.2f + 0.6f
                val sDashH = z * 16f + 1.2f
                val sAlpha = (z * 0.60f).coerceAtMost(0.80f)
                drawRoundRect(
                    color = Color.White.copy(alpha = sAlpha),
                    topLeft = Offset(cx - hw * 0.50f - sDashW / 2, y - sDashH / 2),
                    size = Size(sDashW, sDashH),
                    cornerRadius = CornerRadius(sDashW / 2, sDashW / 2)
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = sAlpha),
                    topLeft = Offset(cx + hw * 0.50f - sDashW / 2, y - sDashH / 2),
                    size = Size(sDashW, sDashH),
                    cornerRadius = CornerRadius(sDashW / 2, sDashW / 2)
                )
            }

            // 100% REAL AUTHENTIC CONCEPT SUV WITH PB BADGE
            val vBaseY = h * 0.90f
            val zCar = ((vBaseY - horizonY) / (h - horizonY)).coerceIn(0f, 1f)
            val roadCenterAtCar = getRoadCenter(zCar)
            val roadHwAtCar = (0.02f + 0.98f * zCar) * roadHalfWidth
            val vTurnDrift = turnYaw * (roadHwAtCar * 0.24f)
            val vCx = roadCenterAtCar + vTurnDrift

            val carDrawW = w * 0.245f
            val carDrawH = carDrawW * (carRearBitmap.height.toFloat() / carRearBitmap.width.toFloat())

            val activeBounce = if (isMoving) (kotlin.math.sin(roadScroll * 0.40f) * 1.5f).coerceAtLeast(0f) else 0f

            // Dedicated Tire Shadows
            val tireShadowW = carDrawW * 0.24f
            val tireShadowH = 7f
            val tireLeftX = vCx - carDrawW * 0.35f
            val tireRightX = vCx + carDrawW * 0.35f

            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent),
                    center = Offset(tireLeftX, vBaseY),
                    radius = tireShadowW * 0.55f
                ),
                topLeft = Offset(tireLeftX - tireShadowW * 0.5f, vBaseY - tireShadowH * 0.5f),
                size = Size(tireShadowW, tireShadowH)
            )
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent),
                    center = Offset(tireRightX, vBaseY),
                    radius = tireShadowW * 0.55f
                ),
                topLeft = Offset(tireRightX - tireShadowW * 0.5f, vBaseY - tireShadowH * 0.5f),
                size = Size(tireShadowW, tireShadowH)
            )

            // ── Forward Headlight Road Surface Illumination ──
            val headlightApexX = getRoadCenter(0.18f)
            pathHeadlight.reset()
            pathHeadlight.moveTo(vCx - carDrawW * 0.30f, vBaseY - carDrawH * 0.15f)
            pathHeadlight.lineTo(headlightApexX - roadHalfWidth * 0.35f, horizonY + (h - horizonY) * 0.18f)
            pathHeadlight.lineTo(headlightApexX + roadHalfWidth * 0.35f, horizonY + (h - horizonY) * 0.18f)
            pathHeadlight.lineTo(vCx + carDrawW * 0.30f, vBaseY - carDrawH * 0.15f)
            pathHeadlight.close()

            drawPath(
                pathHeadlight,
                brush = Brush.verticalGradient(
                    colors = if (isDayMode) {
                        listOf(
                            Color(0xFF38BDF8).copy(alpha = 0.01f),
                            Color(0xFF38BDF8).copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    } else {
                        listOf(
                            Color(0xFF38BDF8).copy(alpha = 0.02f),
                            Color(0xFF38BDF8).copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    },
                    startY = horizonY,
                    endY = vBaseY
                )
            )

            // ── Photorealistic 3D Concept SUV Render with Dynamic Body Tilt ──
            translate(left = 0f, top = -activeBounce) {
                rotate(degrees = bodyTilt, pivot = Offset(vCx, vBaseY)) {
                    val isRev = driveDirection == "BACKWARD"
                    val leftX = (vCx - carDrawW / 2f).toInt()
                    val topY = (vBaseY - carDrawH).toInt()

                    // Main Rear Concept Vehicle Image (100% Authentic & Crisp)
                    drawImage(
                        image = carRearBitmap,
                        dstOffset = IntOffset(leftX, topY),
                        dstSize = IntSize(carDrawW.toInt(), carDrawH.toInt())
                    )

                    // ── Dynamic Tail Lamp Running Lights (Subtle & Integrated) ──
                    val leftTailX = vCx - carDrawW * 0.30f
                    val rightTailX = vCx + carDrawW * 0.30f
                    val tailY = vBaseY - carDrawH * 0.66f
                    val tailAlpha = if (driveDirection == "STOP") 0.85f else 0.40f

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFF1744).copy(alpha = tailAlpha), Color.Transparent),
                            center = Offset(leftTailX, tailY),
                            radius = carDrawW * 0.10f
                        ),
                        radius = carDrawW * 0.10f,
                        center = Offset(leftTailX, tailY)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFF1744).copy(alpha = tailAlpha), Color.Transparent),
                            center = Offset(rightTailX, tailY),
                            radius = carDrawW * 0.10f
                        ),
                        radius = carDrawW * 0.10f,
                        center = Offset(rightTailX, tailY)
                    )

                    // ── Dynamic Animated Amber LED Turn Indicators (Clean & Precise) ──
                    val isIndicatorBlink = (driveDirection == "LEFT" || driveDirection == "RIGHT") && ((System.currentTimeMillis() / 260) % 2 == 0L)

                    if (driveDirection == "LEFT" && isIndicatorBlink) {
                        // Left Tail Amber Radial Glow
                        drawCircle(
                            brush = Brush.radialGradient(listOf(Color(0xFFFFB300).copy(alpha = 0.85f), Color.Transparent)),
                            radius = carDrawW * 0.09f,
                            center = Offset(leftTailX, tailY)
                        )

                        // Ground Amber Flare Pulse
                        drawOval(
                            brush = Brush.radialGradient(listOf(Color(0xFFFFA000).copy(alpha = 0.35f), Color.Transparent)),
                            topLeft = Offset(tireLeftX - carDrawW * 0.20f, vBaseY - 4f),
                            size = Size(carDrawW * 0.40f, carDrawH * 0.14f)
                        )
                    }

                    if (driveDirection == "RIGHT" && isIndicatorBlink) {
                        // Right Tail Amber Radial Glow
                        drawCircle(
                            brush = Brush.radialGradient(listOf(Color(0xFFFFB300).copy(alpha = 0.85f), Color.Transparent)),
                            radius = carDrawW * 0.09f,
                            center = Offset(rightTailX, tailY)
                        )

                        // Ground Amber Flare Pulse
                        drawOval(
                            brush = Brush.radialGradient(listOf(Color(0xFFFFA000).copy(alpha = 0.35f), Color.Transparent)),
                            topLeft = Offset(tireRightX - carDrawW * 0.20f, vBaseY - 4f),
                            size = Size(carDrawW * 0.40f, carDrawH * 0.14f)
                        )
                    }

                    // Dust Trails
                    if (driveDirection == "FORWARD") {
                        val phase = (roadScroll * 0.35f) % 45f
                        for (p in 0..4) {
                            val off = (phase + p * 9f) % 45f
                            val pAlpha = (1f - off / 45f) * 0.28f
                            val pRad = 2.5f + off * 0.20f
                            drawCircle(
                                color = Color(0xFF94A3B8).copy(alpha = pAlpha),
                                radius = pRad,
                                center = Offset(tireLeftX + p * 2f, vBaseY + 4f + off)
                            )
                            drawCircle(
                                color = Color(0xFF94A3B8).copy(alpha = pAlpha),
                                radius = pRad * 0.95f,
                                center = Offset(tireRightX - p * 2f, vBaseY + 4f + off)
                            )
                        }
                    }

                    // Reversing Ground Light Pools
                    if (isRev) {
                        drawOval(
                            brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent)),
                            topLeft = Offset(vCx - carDrawW * 0.50f, vBaseY + 4f),
                            size = Size(carDrawW * 1.00f, carDrawH * 0.14f)
                        )
                    }
                }
            }
        }

        // ── 2. EXACT MAIN SCREEN CONTROLS & HUD OVERLAY (TRANSLUCENT & SHARP) ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. TOP HEADER BAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Back Icon & CONTROL PANEL
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F2347).copy(alpha = 0.90f))
                                .border(1.2.dp, Color(0xFF1E3E7A), RoundedCornerShape(12.dp))
                                .clickable { onBackPressed() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                "CONTROL PANEL",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = textPrimary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                "Drive • Monitor • Learn",
                                fontSize = 10.sp,
                                color = textSecondary
                            )
                        }
                    }

                    // Center: Capsule Pill Status Bar (DISCONNECT ONLY)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF071630).copy(alpha = 0.90f))
                            .border(1.2.dp, Color(0xFF007AFF).copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                            .clickable {
                                if (isConnected) {
                                    BluetoothConnectionManager.disconnect()
                                    viewModel.updateConnectionStatus()
                                    Toast.makeText(context, "Disconnected from Robot", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "ROBOT CAR 01",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = textPrimary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isConnected) Color(0xFF22C55E) else Color(0xFFEF4444))
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    if (isConnected) "CONNECTED" else "DISCONNECTED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isConnected) Color(0xFF22C55E) else Color(0xFFEF4444)
                                )
                            }
                            Text("🔋 78%", fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Bold)
                            Text("📶 -45 dBm", fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(15.dp))
                        }
                    }

                    // Right: BLE Button | Settings | Overflow Menu
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF007AFF))
                                .clickable {
                                    if (BluetoothConnectionManager.isConnected()) {
                                        BluetoothConnectionManager.disconnect()
                                        Toast.makeText(context, "Disconnected from Robot", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                                        if (bluetoothAdapter?.isEnabled == true) {
                                            bluetoothViewModel.startScan(context)
                                            showDeviceDialog = true
                                        } else {
                                            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                            bluetoothEnableLauncher.launch(enableBtIntent)
                                        }
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("BLE", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White)
                            }
                        }

                        // Day / Night Mode Toggle Button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDayMode) Color(0xFFE0F2FE).copy(alpha = 0.90f) else Color(0xFF0F2347).copy(alpha = 0.90f))
                                .border(1.2.dp, if (isDayMode) Color(0xFF38BDF8) else Color(0xFF1E3E7A), RoundedCornerShape(12.dp))
                                .clickable {
                                    isDayMode = !isDayMode
                                    Toast.makeText(context, if (isDayMode) "Day Mode Active ☀️" else "Night Mode Active 🌙", Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isDayMode) Icons.Default.WbSunny else Icons.Default.DarkMode,
                                contentDescription = "Toggle Day/Night Mode",
                                tint = if (isDayMode) Color(0xFFF59E0B) else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F2347).copy(alpha = 0.90f))
                                .border(1.2.dp, Color(0xFF1E3E7A), RoundedCornerShape(12.dp))
                                .clickable {
                                    Toast.makeText(context, "Robot Channel Settings: Baud 9600 • SPP RFCOMM", Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(18.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F2347).copy(alpha = 0.90f))
                                .border(1.2.dp, Color(0xFF1E3E7A), RoundedCornerShape(12.dp))
                                .clickable {
                                    showSensorMenuDialog = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Sensor Telemetry Menu", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // 2. MAIN INTERACTIVE HUD STAGE ROW
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Polygonal Drive Panel (FORWARD / BACKWARD)
                    HexControlPanelHUD(
                        modifier = Modifier.width(170.dp),
                        titleTop = "FORWARD",
                        titleBottom = "BACKWARD",
                        iconTop = Icons.Default.KeyboardArrowUp,
                        iconBottom = Icons.Default.KeyboardArrowDown,
                        onPressTop = {
                            driveDirection = "FORWARD"
                            viewModel.sendCommand("F")
                        },
                        onReleaseTop = {
                            driveDirection = "STOP"
                            viewModel.sendCommand("S")
                        },
                        onPressBottom = {
                            driveDirection = "BACKWARD"
                            viewModel.sendCommand("B")
                        },
                        onReleaseBottom = {
                            driveDirection = "STOP"
                            viewModel.sendCommand("S")
                        },
                        cardBg = hudCardBg,
                        glowColor = glowBorder
                    )

                    // Center Column: Speed Selector Pill (Clickable)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF071630).copy(alpha = 0.85f))
                                .border(1.2.dp, Color(0xFF007AFF).copy(alpha = 0.50f), RoundedCornerShape(14.dp))
                                .clickable {
                                    speed = when (speed) {
                                        10 -> 30
                                        30 -> 50
                                        50 -> 70
                                        70 -> 100
                                        else -> 10
                                    }
                                    viewModel.sendCommand("SPEED:$speed")
                                    Toast.makeText(context, "Motor Speed set to $speed%", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "$speed%",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = textPrimary
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    "SPEED",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textSecondary,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }

                    // Right Column: Turning Panel (TURN LEFT / TURN RIGHT) + Buzzer Module
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Buzzer Module (Now on the Left of the Right Column)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(hudCardBg)
                                .border(1.2.dp, glowBorder.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("BUZZER", fontSize = 9.sp, fontWeight = FontWeight.Black, color = textSecondary, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (buzzerOn) Color(0xFFEF4444) else Color(0xFF0F2347))
                                        .border(1.dp, Color(0xFF1E3E7A), CircleShape)
                                        .pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    awaitFirstDown(requireUnconsumed = false)
                                                    buzzerOn = true
                                                    viewModel.sendCommand("H")
                                                    waitForUpOrCancellation()
                                                    buzzerOn = false
                                                    viewModel.sendCommand("C")
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (buzzerOn) "ON" else "OFF",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (buzzerOn) Color(0xFFEF4444) else textSecondary
                                )
                            }
                        }

                        HexControlPanelHUD(
                            modifier = Modifier.width(170.dp),
                            titleTop = "TURN LEFT",
                            titleBottom = "TURN RIGHT",
                            iconTop = Icons.Default.KeyboardArrowLeft,
                            iconBottom = Icons.Default.KeyboardArrowRight,
                            onPressTop = {
                                driveDirection = "LEFT"
                                viewModel.sendCommand("L")
                            },
                            onReleaseTop = {
                                driveDirection = "STOP"
                                viewModel.sendCommand("S")
                            },
                            onPressBottom = {
                                driveDirection = "RIGHT"
                                viewModel.sendCommand("R")
                            },
                            onReleaseBottom = {
                                driveDirection = "STOP"
                                viewModel.sendCommand("S")
                            },
                            cardBg = hudCardBg,
                            glowColor = glowBorder
                        )
                    }
                }
            }
        }
    }

    if (showDeviceDialog) {
        DeviceSelectionDialog(
            devices = devices,
            isScanning = scanState == ScanState.SCANNING,
            onDeviceSelected = { address ->
                connectToDevice(context, address)
                showDeviceDialog = false
                coroutineScope.launch {
                    delay(2000)
                    viewModel.updateConnectionStatus()
                }
            },
            onDismissRequest = {
                showDeviceDialog = false
                bluetoothViewModel.stopScan(context)
            }
        )
    }

    if (showSensorDialog) {
        SensorDetailDialog(
            sensorName = selectedSensorName,
            sensorIcon = selectedSensorIcon,
            details = selectedSensorDetails,
            onDismiss = { showSensorDialog = false }
        )
    }

    if (showSensorMenuDialog) {
        AlertDialog(
            onDismissRequest = { showSensorMenuDialog = false },
            backgroundColor = Color(0xFF07132B),
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sensors, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Active Sensor Telemetry Pods", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    IconButton(onClick = { showSensorMenuDialog = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. SHT40 (Temp & Humidity) Card
                    TelemetryCardHUD(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedSensorName = "SHT40 (Temp & Humidity)"
                                selectedSensorIcon = Icons.Default.Thermostat
                                selectedSensorDetails = "Temperature: $tempStr °C\nHumidity: $humStr %\nStatus: ${if (sht40Data != null) "Normal" else "Offline"}"
                                showSensorDialog = true
                            },
                        title = "SHT40 (Temp & Humidity)",
                        icon = Icons.Default.Thermostat,
                        cardBg = hudCardBg,
                        borderColor = glowBorder
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Temperature", fontSize = 11.sp, color = textSecondary)
                                Text("$tempStr °C", fontSize = 18.sp, fontWeight = FontWeight.Black, color = textPrimary)
                                Spacer(modifier = Modifier.height(4.dp))
                                SparklineGraphHUD(color = Color(0xFF38BDF8))
                            }
                            Column {
                                Text("Humidity", fontSize = 11.sp, color = textSecondary)
                                Text("$humStr %", fontSize = 18.sp, fontWeight = FontWeight.Black, color = textPrimary)
                                Spacer(modifier = Modifier.height(4.dp))
                                SparklineGraphHUD(color = Color(0xFF22C55E))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            if (sht40Data != null) "● Live Updates Active" else "○ Sensor Disconnected",
                            fontSize = 10.sp,
                            color = if (sht40Data != null) Color(0xFF22C55E) else Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 2. LIS3DH (Accelerometer) Card
                    TelemetryCardHUD(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedSensorName = "LIS3DH (Accelerometer)"
                                selectedSensorIcon = Icons.Default.Sensors
                                selectedSensorDetails = "X-axis: $posXStr g\nY-axis: $posYStr g\nZ-axis: $posZStr g\nStatus: ${if (lis3dhData != null) "Active" else "Offline"}"
                                showSensorDialog = true
                            },
                        title = "LIS3DH (Accelerometer)",
                        icon = Icons.Default.Sensors,
                        cardBg = hudCardBg,
                        borderColor = glowBorder
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("X-axis: $posXStr g", fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Medium)
                                Text("Y-axis: $posYStr g", fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Medium)
                                Text("Z-axis: $posZStr g", fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    if (lis3dhData != null) "Activity: Detected" else "Activity: None",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (lis3dhData != null) Color(0xFF22C55E) else Color(0xFF64748B)
                                )
                            }
                            Axis3DVisualizerHUD()
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            if (lis3dhData != null) "● Live Updates Active" else "○ Sensor Disconnected",
                            fontSize = 10.sp,
                            color = if (lis3dhData != null) Color(0xFF22C55E) else Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSensorMenuDialog = false }) {
                    Text("Close", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            bluetoothViewModel.clearError()
        }
    }
}

//// ── SELF-CONTAINED EMBEDDED VEHICLE ASSET (PORTABLE ACROSS ALL PROJECTS) ──
//private const val EMBEDDED_CAR_PART1 = "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"
//private const val EMBEDDED_CAR_PART2 = "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"
