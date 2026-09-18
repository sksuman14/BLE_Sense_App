@file:Suppress("DEPRECATION", "UseCompatLoadingForDrawables")
package com.blesense.app.view.screens

import com.blesense.app.R
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
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
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.channels.Channel

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
    
    private val _driveDirection = MutableStateFlow("STOP")
    val driveDirection: StateFlow<String> = _driveDirection.asStateFlow()

    private var outputStream: OutputStream? = null
    private var lastSentCommand: String? = null

    // Drive states
    private var isForward = false
    private var isBackward = false
    private var isLeft = false
    private var isRight = false

    private val _commandLogs = MutableStateFlow<List<String>>(emptyList())
    val commandLogs: StateFlow<List<String>> = _commandLogs.asStateFlow()

    // Using CONFLATED capacity to ensure only the LATEST command is kept in the queue.
    // This prevents a command backlog if Bluetooth transmission is slower than user input.
    private val commandChannel = Channel<String>(capacity = Channel.CONFLATED)

    init {
        updateConnectionStatus()
        startCommandProcessor()
    }

    private fun startCommandProcessor() {
        viewModelScope.launch(Dispatchers.IO) {
            for (command in commandChannel) {
                processSendCommand(command)
                // Small yielding delay to prevent motor/serial jitter
                delay(5)
            }
        }
    }

    fun updateDriveState(f: Boolean? = null, b: Boolean? = null, l: Boolean? = null, r: Boolean? = null) {
        if (f != null) isForward = f
        if (b != null) isBackward = b
        if (l != null) isLeft = l
        if (r != null) isRight = r

        val activeCommands = mutableListOf<String>()
        if (isForward && !isBackward) activeCommands.add("F")
        else if (isBackward && !isForward) activeCommands.add("B")
        
        if (isLeft && !isRight) activeCommands.add("L")
        else if (isRight && !isLeft) activeCommands.add("R")

        val cmd = if (activeCommands.isEmpty()) "S" else activeCommands.joinToString("")
        sendCommand(cmd)

        _driveDirection.value = when {
            isForward && isLeft -> "FORWARD_LEFT"
            isForward && isRight -> "FORWARD_RIGHT"
            isBackward && isLeft -> "BACKWARD_LEFT"
            isBackward && isRight -> "BACKWARD_RIGHT"
            isForward -> "FORWARD"
            isBackward -> "BACKWARD"
            isLeft -> "LEFT"
            isRight -> "RIGHT"
            else -> "STOP"
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
            lastSentCommand = null
        }
    }

    fun disconnectRobot() {
        viewModelScope.launch(Dispatchers.IO) {
            // Priority safety stop
            processSendCommand("S")
            delay(100) // Brief time for "S" to hit the wire
            BluetoothConnectionManager.disconnect()
            updateConnectionStatus()
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
                lastSentCommand = null
                return
            }

            if (command == lastSentCommand) return

            if (outputStream == null) {
                outputStream = BluetoothConnectionManager.bluetoothSocket?.outputStream
            }

            outputStream?.let { os ->
                os.write(command.toByteArray())
                os.flush()
                
                val current = _commandLogs.value.toMutableList()
                current.add(0, "> $command")
                if (current.size > 5) current.removeAt(5)
                _commandLogs.value = current

                lastSentCommand = command
                _isConnected.value = true
            } ?: run {
                _isConnected.value = false
            }
        } catch (e: Exception) {
            Log.e("RobotCommand", "Failed to send command $command", e)
            _isConnected.value = false
            lastSentCommand = null
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
    val cardBackgroundColor = Color(0xFF0F172A)
    val textColor = Color.White
    val dividerColor = Color(0xFF1E293B)

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
            .clip(RoundedCornerShape(24.dp))
            .background(cardBg)
            .border(
                width = 1.5.dp,
                brush = if (topPressed || bottomPressed) 
                    Brush.radialGradient(listOf(glowColor, Color.Transparent)) 
                    else Brush.linearGradient(listOf(glowColor.copy(0.4f), glowColor.copy(0.1f))),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(14.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top Action Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(titleTop, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(72.dp)
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
                    Icon(iconTop, contentDescription = titleTop, tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }

            Text("•••", fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)

            // Bottom Action Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
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
                    Icon(iconBottom, contentDescription = titleBottom, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(titleBottom, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}

@Composable
fun ActionButtonHUD(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (isActive) Color(0xFF007AFF) else Color(0xFF0F2347))
                .border(1.dp, Color(0xFF1E3E7A), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(label, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}

// 3D Cyber-Rover Vehicle Showcase — animated 3D movement & uncropped 3D presentation
@Composable
fun CyberRoverGraphicHUD(
    modifier: Modifier = Modifier,
    lightsOn: Boolean,
    direction: String = "STOP"
) {
    val infiniteTransition = rememberInfiniteTransition(label = "motion")
    
    // Radial motion pulse offset
    val pulseOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseOffset"
    )

    val isMoving = direction != "STOP"

    val springSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    val targetRotX = when {
        direction.contains("FORWARD") -> -14f
        direction.contains("BACKWARD") -> 14f
        else -> 0f
    }
    val targetRotY = when {
        direction.contains("LEFT") -> 22f
        direction.contains("RIGHT") -> -22f
        else -> 0f
    }
    val targetRotZ = when {
        direction.contains("LEFT") -> 4f
        direction.contains("RIGHT") -> -4f
        else -> 0f
    }
    val targetTransY = when {
        direction.contains("FORWARD") -> -6f
        direction.contains("BACKWARD") -> 6f
        else -> 0f
    }
    val targetScale = when {
        direction != "STOP" -> 1.06f
        else -> 1f
    }

    val animRotX by animateFloatAsState(targetValue = targetRotX, animationSpec = springSpec, label = "rotX")
    val animRotY by animateFloatAsState(targetValue = targetRotY, animationSpec = springSpec, label = "rotY")
    val animRotZ by animateFloatAsState(targetValue = targetRotZ, animationSpec = springSpec, label = "rotZ")
    val animTransY by animateFloatAsState(targetValue = targetTransY, animationSpec = springSpec, label = "transY")
    val animScale by animateFloatAsState(targetValue = targetScale, animationSpec = springSpec, label = "scale")

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.65f
            val rx = size.width * 0.42f
            val ry = size.height * 0.26f

            // Dynamic Motion Pulse Rings
            if (isMoving) {
                val ringColor = Color(0xFF00E5FF).copy(alpha = 0.4f * (1f - pulseOffset))
                val ringRx = rx * (0.3f + pulseOffset * 0.7f)
                val ringRy = ry * (0.3f + pulseOffset * 0.7f)
                
                drawOval(
                    color = ringColor,
                    topLeft = Offset(cx - ringRx, cy - ringRy),
                    size = Size(ringRx * 2f, ringRy * 2f),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00E5FF).copy(alpha = if (lightsOn) 0.50f else 0.30f),
                        Color(0xFF007AFF).copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = rx * 1.30f
                ),
                topLeft = Offset(cx - rx * 1.30f, cy - ry * 1.30f),
                size = Size(rx * 2.60f, ry * 2.60f)
            )

            val gridColor = Color(0xFF00E5FF).copy(alpha = 0.25f)
            val gridStroke = 1.dp.toPx()
            for (angle in listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)) {
                val rad = Math.toRadians(angle.toDouble())
                val cosA = Math.cos(rad).toFloat()
                val sinA = Math.sin(rad).toFloat()
                drawLine(
                    color = gridColor,
                    start = Offset(cx + rx * 0.3f * cosA, cy + ry * 0.3f * sinA),
                    end = Offset(cx + rx * 1.05f * cosA, cy + ry * 1.05f * sinA),
                    strokeWidth = gridStroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
            }

            drawOval(
                color = Color(0xFF00E5FF).copy(alpha = 0.90f),
                topLeft = Offset(cx - rx, cy - ry),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f))
                )
            )

            drawOval(
                color = Color(0xFF007AFF).copy(alpha = 0.70f),
                topLeft = Offset(cx - rx * 0.82f, cy - ry * 0.82f),
                size = Size(rx * 1.64f, ry * 1.64f),
                style = Stroke(width = 2.dp.toPx())
            )

            val tickLen = 16.dp.toPx()
            val tickColor = Color(0xFF00E5FF)
            drawLine(tickColor, Offset(cx - rx - tickLen, cy), Offset(cx - rx + 4.dp.toPx(), cy), strokeWidth = 2.dp.toPx())
            drawLine(tickColor, Offset(cx + rx - 4.dp.toPx(), cy), Offset(cx + rx + tickLen, cy), strokeWidth = 2.dp.toPx())
            drawLine(tickColor, Offset(cx, cy - ry - tickLen), Offset(cx, cy - ry + 4.dp.toPx()), strokeWidth = 2.dp.toPx())
            drawLine(tickColor, Offset(cx, cy + ry - 4.dp.toPx()), Offset(cx, cy + ry + tickLen), strokeWidth = 2.dp.toPx())
        }

        Image(
            painter = painterResource(id = R.drawable.cyber_rover_car),
            contentDescription = "3D Cyber Rover",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationX = animRotX
                    rotationY = animRotY
                    rotationZ = animRotZ
                    translationY = animTransY - 14.dp.toPx()
                    scaleX = animScale * 1.22f
                    scaleY = animScale * 1.22f
                    cameraDistance = 12f * density
                }
        )
    }
}

// ================= UNIFIED MAIN SCREEN: CONTROLS & HUD =================
@Composable
fun RobotControlScreen(
    viewModel: RobotControlViewModel = viewModel(),
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val bluetoothViewModel: ClassicBluetoothViewModel = viewModel()
    val haptic = LocalHapticFeedback.current

    val activity = context as? ComponentActivity

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
    val driveDirection by viewModel.driveDirection.collectAsState()

    var showDeviceDialog by remember { mutableStateOf(false) }
    var showSensorDialog by remember { mutableStateOf(false) }
    var showSensorMenuDialog by remember { mutableStateOf(false) }
    var selectedSensorName by remember { mutableStateOf("") }
    var selectedSensorIcon by remember { mutableStateOf(Icons.Default.Thermostat) }
    var selectedSensorDetails by remember { mutableStateOf("") }

    var speed by remember { mutableStateOf(30) }
    var buzzerOn by remember { mutableStateOf(false) }

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
        activity?.let { sensorViewModel?.startContinuousScan(it) }
        while (true) {
            viewModel.updateConnectionStatus()
            delay(500)
        }
    }

    val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            bluetoothViewModel.startScan(context)
            showDeviceDialog = true
        } else {
            Toast.makeText(context, "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
        }
    }

    val bluetoothEnableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (BluetoothAdapter.getDefaultAdapter()?.isEnabled == true) {
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
            // Safety Stop: Stop the robot if the screen is exited
            viewModel.sendCommand("S")
        }
    }

    val bgGradient = Brush.verticalGradient(listOf(Color(0xFF030919), Color(0xFF07132B)))
    val hudCardBg = Color(0xFF0A1833).copy(alpha = 0.95f)
    val glowBorder = Color(0xFF007AFF)
    val textPrimary = Color.White
    val textSecondary = Color(0xFF94A3B8)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            .systemBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. TOP HEADER BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0F2347))
                            .border(1.2.dp, Color(0xFF1E3E7A), RoundedCornerShape(12.dp))
                            .clickable { onBackPressed() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text("CONTROL PANEL", fontWeight = FontWeight.Black, fontSize = 15.sp, color = textPrimary, letterSpacing = 1.sp)
                        Text("Drive • Monitor • Learn", fontSize = 10.sp, color = textSecondary)
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF071630))
                        .border(1.2.dp, Color(0xFF007AFF).copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                        .clickable {
                            if (isConnected) {
                                viewModel.disconnectRobot()
                                Toast.makeText(context, "Disconnecting Robot...", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("ROBOT CAR 01", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = textPrimary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(if (isConnected) Color(0xFF22C55E) else Color(0xFFEF4444)))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(if (isConnected) "CONNECTED" else "DISCONNECTED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isConnected) Color(0xFF22C55E) else Color(0xFFEF4444) )
                        }
                        Text("🔋 78%", fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Bold)
                        Text("📶 -45 dBm", fontSize = 11.sp, color = textPrimary, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(15.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF007AFF))
                            .clickable {
                                val adapter = BluetoothAdapter.getDefaultAdapter()
                                if (adapter?.isEnabled == true) {
                                    bluetoothViewModel.startScan(context)
                                    showDeviceDialog = true
                                } else {
                                    bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
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
                    Box(
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0F2347)).border(1.2.dp, Color(0xFF1E3E7A), RoundedCornerShape(12.dp)).clickable {
                            Toast.makeText(context, "Robot Channel Settings: Baud 9600 • SPP RFCOMM", Toast.LENGTH_SHORT).show()
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Box(
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0F2347)).border(1.2.dp, Color(0xFF1E3E7A), RoundedCornerShape(12.dp)).clickable { showSensorMenuDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // 2. MAIN INTERACTIVE HUD STAGE ROW
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HexControlPanelHUD(
                    modifier = Modifier.width(180.dp),
                    titleTop = "FORWARD", titleBottom = "BACKWARD",
                    iconTop = Icons.Default.KeyboardArrowUp, iconBottom = Icons.Default.KeyboardArrowDown,
                    onPressTop = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.updateDriveState(f = true) 
                    },
                    onReleaseTop = { viewModel.updateDriveState(f = false) },
                    onPressBottom = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.updateDriveState(b = true) 
                    },
                    onReleaseBottom = { viewModel.updateDriveState(b = false) },
                    cardBg = hudCardBg, glowColor = glowBorder
                )

                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                        speed = when (speed) { 10 -> 30; 30 -> 50; 50 -> 70; 70 -> 100; else -> 10 }
                        viewModel.sendCommand("SPEED:$speed")
                        Toast.makeText(context, "Speed: $speed%", Toast.LENGTH_SHORT).show()
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("$speed%", fontSize = 22.sp, fontWeight = FontWeight.Black, color = textPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SPEED", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = textSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    CyberRoverGraphicHUD(modifier = Modifier.fillMaxWidth().height(210.dp), lightsOn = false, direction = driveDirection)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Buzzer Module (Shifted to the left of the right panel)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(hudCardBg)
                            .border(1.2.dp, glowBorder.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                            .padding(10.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("BUZZER", fontSize = 9.sp, fontWeight = FontWeight.Black, color = textSecondary)
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
                                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(if (buzzerOn) "ON" else "OFF", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (buzzerOn) Color(0xFFEF4444) else textSecondary)
                        }
                    }

                    HexControlPanelHUD(
                        modifier = Modifier.width(180.dp),
                        titleTop = "TURN LEFT", titleBottom = "TURN RIGHT",
                        iconTop = Icons.Default.KeyboardArrowLeft, iconBottom = Icons.Default.KeyboardArrowRight,
                        onPressTop = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.updateDriveState(l = true) 
                        },
                        onReleaseTop = { viewModel.updateDriveState(l = false) },
                        onPressBottom = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.updateDriveState(r = true) 
                        },
                        onReleaseBottom = { viewModel.updateDriveState(r = false) },
                        cardBg = hudCardBg, glowColor = glowBorder
                    )
                }
            }
        }
    }

    if (showDeviceDialog) {
        DeviceSelectionDialog(
            devices = devices, isScanning = scanState == ScanState.SCANNING,
            onDeviceSelected = { address ->
                connectToDevice(context, address)
                showDeviceDialog = false
                coroutineScope.launch { delay(2000); viewModel.updateConnectionStatus() }
            },
            onDismissRequest = { showDeviceDialog = false; bluetoothViewModel.stopScan(context) }
        )
    }

    if (showSensorDialog) {
        SensorDetailDialog(sensorName = selectedSensorName, sensorIcon = selectedSensorIcon, details = selectedSensorDetails, onDismiss = { showSensorDialog = false })
    }

    if (showSensorMenuDialog) {
        AlertDialog(
            onDismissRequest = { showSensorMenuDialog = false },
            backgroundColor = Color(0xFF07132B),
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sensors, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Active Telemetry Pods", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    IconButton(onClick = { showSensorMenuDialog = false }) { Icon(Icons.Default.Close, contentDescription = "Close", tint = textSecondary) }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TelemetryCardHUD(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedSensorName = "SHT40 (Temp & Humidity)"
                            selectedSensorIcon = Icons.Default.Thermostat
                            selectedSensorDetails = "Temperature: $tempStr °C\nHumidity: $humStr %\nStatus: ${if (sht40Data != null) "Live" else "Offline"}"
                            showSensorDialog = true
                        },
                        title = "SHT40 (Temp & Humidity)", icon = Icons.Default.Thermostat, cardBg = hudCardBg, borderColor = glowBorder
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Temperature", fontSize = 11.sp, color = textSecondary)
                                Text("$tempStr °C", fontSize = 18.sp, fontWeight = FontWeight.Black, color = textPrimary)
                                SparklineGraphHUD(color = Color(0xFF38BDF8))
                            }
                            Column {
                                Text("Humidity", fontSize = 11.sp, color = textSecondary)
                                Text("$humStr %", fontSize = 18.sp, fontWeight = FontWeight.Black, color = textPrimary)
                                SparklineGraphHUD(color = Color(0xFF22C55E))
                            }
                        }
                    }

                    TelemetryCardHUD(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedSensorName = "LIS3DH (Accelerometer)"
                            selectedSensorIcon = Icons.Default.Sensors
                            selectedSensorDetails = "X: $posXStr g\nY: $posYStr g\nZ: $posZStr g\nStatus: ${if (lis3dhData != null) "Active" else "Offline"}"
                            showSensorDialog = true
                        },
                        title = "LIS3DH (Accelerometer)", icon = Icons.Default.Sensors, cardBg = hudCardBg, borderColor = glowBorder
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("X: $posXStr g", fontSize = 11.sp, color = textPrimary)
                                Text("Y: $posYStr g", fontSize = 11.sp, color = textPrimary)
                                Text("Z: $posZStr g", fontSize = 11.sp, color = textPrimary)
                            }
                            Axis3DVisualizerHUD()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSensorMenuDialog = false }) { Text("Close", color = Color(0xFF00E5FF)) } }
        )
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); bluetoothViewModel.clearError() }
    }
}
