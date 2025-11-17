@file:Suppress("DEPRECATION", "UseCompatLoadingForDrawables")
package com.blesense.app

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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.OutputStream
import java.util.UUID
import kotlin.random.Random
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

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

// ================= ROBOT CONTROL ACTIVITY =================
class RobotControlCompose : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
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
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
}

// ================= ROBOT CONTROL VIEW MODEL =================
open class RobotControlViewModel : ViewModel() {
    private var outputStream: OutputStream? = null
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    init {
        initBluetooth()
    }

    private fun initBluetooth() {
        try {
            outputStream = BluetoothConnectionManager.bluetoothSocket?.outputStream
            _isConnected.value = isBluetoothConnected()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    open fun isBluetoothConnected(): Boolean {
        return BluetoothConnectionManager.isConnected()
    }

    open fun sendCommand(command: String) {
        try {
            Log.d("RobotCommand", "Sending command: $command")
            if (!isBluetoothConnected()) {
                Log.e("RobotCommand", "Bluetooth not connected!")
                _isConnected.value = false
                return
            }
            if (outputStream == null) {
                outputStream = BluetoothConnectionManager.bluetoothSocket?.outputStream
                if (outputStream == null) {
                    Log.e("RobotCommand", "Failed to get output stream!")
                    return
                }
            }
            outputStream?.write(command.toByteArray())
            outputStream?.flush()
            Log.d("RobotCommand", "Command sent successfully")
        } catch (e: Exception) {
            Log.e("RobotCommand", "Failed to send command", e)
            _isConnected.value = false
            try {
                outputStream = BluetoothConnectionManager.bluetoothSocket?.outputStream
            } catch (innerEx: Exception) {
                Log.e("RobotCommand", "Failed to refresh output stream", innerEx)
            }
        }
    }

    open fun handleSensorClick(sensorName: String, onDataReceived: (String, String) -> Unit) {
        if (isBluetoothConnected()) {
            when (sensorName) {
                "Temperature Sensor" -> {
                    val rawData = generateRandomRawData()
                    val temperature = rawData[0].toInt()
                    val humidity = rawData[1].toInt()
                    val rawDisplay = "Raw Data: ${rawData.contentToString()}"
                    val allData = "Temperature: $temperature°C\nHumidity: $humidity%"
                    onDataReceived(rawDisplay, allData)
                }
                else -> onDataReceived("Raw Data: N/A", "Default Data")
            }
        }
    }

    private fun generateRandomRawData(): ByteArray {
        val temperature = Random.nextInt(20, 40).toByte()
        val humidity = Random.nextInt(40, 80).toByte()
        return byteArrayOf(temperature, humidity)
    }
}

// Fake ViewModel for preview purposes
class FakeRobotControlViewModel : RobotControlViewModel() {
    override fun isBluetoothConnected(): Boolean = true
    override fun sendCommand(command: String) {}
    override fun handleSensorClick(sensorName: String, onDataReceived: (String, String) -> Unit) {
        onDataReceived("Raw Data: [20, 40]", "Temp: 20°C, Humidity: 40%")
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
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val cardBackgroundColor = if (isDarkMode) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black
    val dividerColor = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFFE0E0E0)

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.width(300.dp),
            backgroundColor = cardBackgroundColor
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Select a Device",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (isScanning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = if (isDarkMode) Color(0xFF64B5F6) else Color(0xFF007AFF)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Scanning for devices...",
                            color = textColor
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (devices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isScanning) "Searching..." else "No devices found",
                            color = if (isDarkMode) Color(0xFFB0B0B0) else Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
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
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(
                            text = "Cancel",
                            color = textColor
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onDismissRequest() }
                    ) {
                        Text(
                            text = "Close",
                            color = if (isDarkMode) Color.White else Color.Black
                        )
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
            .padding(16.dp)
    ) {
        val deviceName = remember(device) {
            try {
                device.name ?: "Unknown Device"
            } catch (e: SecurityException) {
                "Unknown Device"
            }
        }
        Text(
            text = deviceName,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = textColor
        )
        Text(
            text = device.address,
            fontSize = 14.sp,
            color = if (textColor == Color.White) Color(0xFFB0B0B0) else Color.Gray
        )
    }
}

// ================= ROBOT CONTROL SCREEN =================
@Composable
fun RobotControlScreen(
    viewModel: RobotControlViewModel = viewModel(),
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val bluetoothViewModel: ClassicBluetoothViewModel = viewModel()
    val configuration = LocalConfiguration.current
    var isConnected by remember { mutableStateOf(BluetoothConnectionManager.isConnected()) }
    val isDarkMode by ThemeManager.isDarkMode.collectAsState()
    val backgroundColor = if (isDarkMode) Color(0xFF121212) else Color(0xFFF2F2F7)
    val textColor = if (isDarkMode) Color.White else Color.Black
    val secondaryTextColor = if (isDarkMode) Color(0xFFB0B0B0) else Color.Gray
    val iconTint = if (isDarkMode) Color(0xFF64B5F6) else Color(0xFF007AFF)

    if (configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
        LaunchedEffect(Unit) {
            // Optional: Handle rotation
        }
    }
    var selectedSensor by remember { mutableStateOf(SensorItem(0, "Select Sensor")) }
    var showDialog by remember { mutableStateOf(false) }
    var dialogContent by remember { mutableStateOf("") }
    var showDeviceDialog by remember { mutableStateOf(false) }
    val scanState by bluetoothViewModel.scanState.collectAsState()
    val devices by bluetoothViewModel.devices.collectAsState()
    val errorMessage by bluetoothViewModel.errorMessage.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            isConnected = BluetoothConnectionManager.isConnected()
            delay(1000)
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
    ) { result ->
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val bluetoothEnabled = bluetoothAdapter?.isEnabled == true
        if (bluetoothEnabled) {
            val hasPermissions = bluetoothPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            if (hasPermissions) {
                bluetoothViewModel.startScan(context)
                showDeviceDialog = true
            } else {
                permissionsLauncher.launch(bluetoothPermissions)
            }
        } else {
            Toast.makeText(context, "Bluetooth must be enabled to scan for devices", Toast.LENGTH_SHORT).show()
        }
    }

    val sensorData = listOf(
        SensorItem(0, "Select Sensor"),
        SensorItem(R.drawable.ic_thermometer, "Temperature Sensor"),
        SensorItem(R.drawable.ic_accelerometer, "Accelerometer Sensor"),
        SensorItem(R.drawable.ic_pressure_sensor, "Pressure Sensor"),
        SensorItem(R.drawable.ic_turbo, "Turbo Sensor"),
        SensorItem(R.drawable.ic_motor, "Motor Sensor"),
        SensorItem(R.drawable.ic_switch, "Switch Sensor")
    )

    val backgroundPainter = painterResource(id = R.drawable.racing_bg7)

    DisposableEffect(Unit) {
        onDispose {
            if (scanState == ScanState.SCANNING) {
                bluetoothViewModel.stopScan(context)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Image(
            painter = backgroundPainter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(
                modifier = Modifier.size(60.dp),
                onClick = onBackPressed,
                isDarkMode = isDarkMode
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(
                        if (isConnected) Color.Green.copy(alpha = 0.7f)
                        else Color.Red.copy(alpha = 0.7f),
                        CircleShape
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (isConnected) "Connected" else "Disconnected",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        BluetoothButton(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp),
            onClick = {
                if (BluetoothConnectionManager.isConnected()) {
                    BluetoothConnectionManager.disconnect()
                    Toast.makeText(context, "Disconnected from device", Toast.LENGTH_SHORT).show()
                    return@BluetoothButton
                }
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                if (bluetoothAdapter == null) {
                    Toast.makeText(context, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
                    return@BluetoothButton
                }
                if (!bluetoothAdapter.isEnabled) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    bluetoothEnableLauncher.launch(enableBtIntent)
                } else {
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
            },
            isDarkMode = isDarkMode
        )

        SensorSpinner(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(top = 60.dp),
            sensorData = sensorData,
            selectedSensor = selectedSensor,
            onSensorSelected = { sensor ->
                selectedSensor = sensor
                if (sensor.name != "Select Sensor") {
                    viewModel.handleSensorClick(sensor.name) { rawDisplay, allData ->
                        dialogContent = "$rawDisplay\n$allData"
                        showDialog = true
                    }
                }
            },
            isDarkMode = isDarkMode
        )

        HornButton(
            modifier = Modifier.align(Alignment.BottomEnd),
            isBluetoothConnected = isConnected,
            onHornActive = { isActive ->
                if (isActive) {
                    viewModel.sendCommand("H")
                } else {
                    viewModel.sendCommand("C")
                }
            },
            isDarkMode = isDarkMode
        )

        VerticalJoystick(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 32.dp),
            onDirectionChange = { command ->
                viewModel.sendCommand(command)
            },
            isDarkMode = isDarkMode
        )

        HorizontalJoystick(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 32.dp),
            onDirectionChange = { direction ->
                when (direction) {
                    "L" -> viewModel.sendCommand("L")
                    "R" -> viewModel.sendCommand("R")
                    else -> viewModel.sendCommand("C")
                }
            },
            isDarkMode = isDarkMode
        )
    }

    if (showDialog) {
        SensorDataDialog(
            sensorName = selectedSensor.name,
            content = dialogContent,
            onDismiss = { showDialog = false },
            isDarkMode = isDarkMode
        )
    }
    if (showDeviceDialog) {
        DeviceSelectionDialog(
            devices = devices,
            isScanning = scanState == ScanState.SCANNING,
            onDeviceSelected = { address ->
                connectToDevice(context, address)
                showDeviceDialog = false
            },
            onDismissRequest = {
                showDeviceDialog = false
                bluetoothViewModel.stopScan(context)
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

@Composable
fun BackButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isDarkMode: Boolean
) {
    val scale by animateFloatAsState(targetValue = 1f, animationSpec = tween(300), label = "")
    val buttonBackgroundColor = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFF007AFF).copy(alpha = 0.3f)
    Box(
        modifier = modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(buttonBackgroundColor, CircleShape)
            .clickable { onClick() }
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_back_arrow),
            contentDescription = "Back Button",
            tint = if (isDarkMode) Color.White else Color.White,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
fun BluetoothButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isDarkMode: Boolean
) {
    val scale by animateFloatAsState(targetValue = 1f, animationSpec = tween(300), label = "")
    val buttonBackgroundColor = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFF007AFF).copy(alpha = 0.3f)
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(buttonBackgroundColor, CircleShape)
            .clickable { onClick() }
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_bluetooth),
            contentDescription = "Bluetooth Button",
            modifier = Modifier.size(50.dp),
            colorFilter = if (isDarkMode) ColorFilter.tint(Color.White) else ColorFilter.tint(Color.White)
        )
    }
}

@Composable
fun HornButton(
    modifier: Modifier = Modifier,
    isBluetoothConnected: Boolean,
    onHornActive: (Boolean) -> Unit,
    isDarkMode: Boolean
) {
    val context = LocalContext.current
    var isHornPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isHornPressed) 1.2f else 1f,
        animationSpec = tween(300),
        label = ""
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isHornPressed) Color.Red.copy(alpha = 0.3f)
        else if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFF007AFF).copy(alpha = 0.3f),
        animationSpec = tween(300),
        label = "background color animation"
    )
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(backgroundColor, CircleShape)
            .pointerInput(isBluetoothConnected) {
                if (!isBluetoothConnected) {
                    detectTapGestures {
                        Toast.makeText(context, "Connect Bluetooth first", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    detectTapGestures(
                        onPress = {
                            isHornPressed = true
                            onHornActive(true)
                            try {
                                awaitRelease()
                                isHornPressed = false
                                onHornActive(false)
                            } catch (e: Exception) {
                                isHornPressed = false
                                onHornActive(false)
                            }
                        }
                    )
                }
            }
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_horn),
            contentDescription = "Horn Button",
            modifier = Modifier.size(50.dp),
            colorFilter = if (isHornPressed) ColorFilter.tint(Color.Red) else if (isDarkMode) ColorFilter.tint(Color.White) else ColorFilter.tint(Color.White)
        )
    }
}

@Composable
fun VerticalJoystick(
    modifier: Modifier = Modifier,
    onDirectionChange: (String) -> Unit,
    isDarkMode: Boolean
) {
    val density = LocalDensity.current
    var offset by remember { mutableStateOf(Offset.Zero) }
    val maxDistance = with(density) { 60.dp.toPx() }
    val deadZone = with(density) { 5.dp.toPx() }
    var currentCommand by remember { mutableStateOf("C") }
    val joystickBackgroundColor = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFF007AFF).copy(alpha = 0.3f)
    val joystickHandleColor = if (isDarkMode) Color(0xFF4A4A4A) else Color.White.copy(alpha = 0.8f)
    Box(
        modifier = modifier
            .size(150.dp)
            .background(joystickBackgroundColor, CircleShape)
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newY = (offset.y + dragAmount.y).coerceIn(-maxDistance, maxDistance)
                        val newOffset = Offset(0f, newY)
                        offset = newOffset
                        val newCommand = when {
                            newY < -deadZone -> "U"
                            newY > deadZone -> "D"
                            else -> "C"
                        }
                        if (newCommand != currentCommand) {
                            currentCommand = newCommand
                            onDirectionChange(newCommand)
                        }
                    },
                    onDragEnd = {
                        offset = Offset.Zero
                        if (currentCommand != "C") {
                            currentCommand = "C"
                            onDirectionChange("C")
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.x.toInt(), offset.y.toInt()) }
                .size(90.dp)
                .shadow(6.dp, CircleShape)
                .background(joystickHandleColor, CircleShape)
        )
    }
}

@Composable
fun HorizontalJoystick(
    modifier: Modifier = Modifier,
    onDirectionChange: (String) -> Unit,
    isDarkMode: Boolean
) {
    val density = LocalDensity.current
    var offset by remember { mutableStateOf(Offset.Zero) }
    val maxDistance = with(density) { 60.dp.toPx() }
    val deadZone = with(density) { 5.dp.toPx() }
    var currentCommand by remember { mutableStateOf("C") }
    val joystickBackgroundColor = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFF007AFF).copy(alpha = 0.3f)
    val joystickHandleColor = if (isDarkMode) Color(0xFF4A4A4A) else Color.White.copy(alpha = 0.8f)
    Box(
        modifier = modifier
            .size(150.dp)
            .background(joystickBackgroundColor, CircleShape)
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newX = (offset.x + dragAmount.x).coerceIn(-maxDistance, maxDistance)
                        val newOffset = Offset(newX, 0f)
                        offset = newOffset
                        val newCommand = when {
                            newX < -deadZone -> "L"
                            newX > deadZone -> "R"
                            else -> "C"
                        }
                        if (newCommand != currentCommand) {
                            currentCommand = newCommand
                            onDirectionChange(newCommand)
                        }
                    },
                    onDragEnd = {
                        offset = Offset.Zero
                        if (currentCommand != "C") {
                            currentCommand = "C"
                            onDirectionChange("C")
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.x.toInt(), offset.y.toInt()) }
                .size(90.dp)
                .shadow(6.dp, CircleShape)
                .background(joystickHandleColor, CircleShape)
        )
    }
}

@SuppressLint("UseCompatLoadingForDrawables")
@Composable
fun SensorSpinner(
    modifier: Modifier = Modifier,
    sensorData: List<SensorItem>,
    selectedSensor: SensorItem,
    onSensorSelected: (SensorItem) -> Unit,
    isDarkMode: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val backgroundColor = if (isDarkMode) Color(0xFF2A2A2A) else Color(0xFF007AFF).copy(alpha = 0.3f)
    val textColor = if (isDarkMode) Color.White else Color.White
    val dropdownBackgroundColor = if (isDarkMode) Color(0xFF1E1E1E) else Color.White
    val dropdownTextColor = if (isDarkMode) Color.White else Color.Black
    val iconColor = if (isDarkMode) Color.White else Color.Black
    val context = LocalContext.current
    Box(
        modifier = modifier.width(150.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(backgroundColor, CircleShape)
                .padding(12.dp)
                .clickable { expanded = true }
        ) {
            if (selectedSensor.iconResId != 0) {
                val drawable = context.resources.getDrawable(selectedSensor.iconResId, null)
                Image(
                    bitmap = drawable.toBitmap().asImageBitmap(),
                    contentDescription = selectedSensor.name,
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(iconColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = selectedSensor.name,
                color = textColor,
                fontSize = 18.sp
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(180.dp)
                .background(dropdownBackgroundColor)
        ) {
            sensorData.forEach { sensor ->
                DropdownMenuItem(
                    onClick = {
                        onSensorSelected(sensor)
                        expanded = false
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (sensor.iconResId != 0) {
                            val drawable = context.resources.getDrawable(sensor.iconResId, null)
                            Image(
                                bitmap = drawable.toBitmap().asImageBitmap(),
                                contentDescription = sensor.name,
                                modifier = Modifier.size(24.dp),
                                colorFilter = ColorFilter.tint(iconColor)
                            )
                        } else {
                            Spacer(modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = sensor.name,
                            color = dropdownTextColor
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("AutoboxingStateCreation")
@Composable
fun FloatingJoystickView(
    modifier: Modifier = Modifier,
    onDirectionChange: (String) -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            }
            .zIndex(1f)
    )
}

@Composable
fun SensorDataDialog(
    sensorName: String,
    content: String,
    onDismiss: () -> Unit,
    isDarkMode: Boolean
) {
    val cardBackgroundColor = if (isDarkMode) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkMode) Color.White else Color.Black
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(300.dp)
                .padding(16.dp),
            backgroundColor = cardBackgroundColor
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = sensorName,
                    fontSize = 20.sp,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = content,
                    fontSize = 16.sp,
                    color = textColor
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
fun connectToDevice(context: Context, address: String) {
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    val device = bluetoothAdapter.getRemoteDevice(address)
    val uuid = device.uuids?.firstOrNull()?.uuid ?: UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    BluetoothConnectionManager.disconnect()
    Thread {
        try {
            val socket: BluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            bluetoothAdapter.cancelDiscovery()
            try {
                socket.connect()
            } catch (connectException: Exception) {
                try {
                    socket.close()
                } catch (closeException: Exception) { }
                try {
                    Log.d("BluetoothConnect", "Trying fallback connection...")
                    val fallbackSocket = createFallbackSocket(device)
                    fallbackSocket?.connect()
                    if (fallbackSocket?.isConnected == true) {
                        BluetoothConnectionManager.bluetoothSocket = fallbackSocket
                        (context as? Activity)?.runOnUiThread {
                            Toast.makeText(context, "Connected to ${device.name} (fallback)", Toast.LENGTH_SHORT).show()
                        }
                        return@Thread
                    }
                } catch (fallbackException: Exception) {
                    Log.e("BluetoothConnect", "Fallback connection failed", fallbackException)
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Connection failed: ${fallbackException.message}", Toast.LENGTH_LONG).show()
                    }
                    return@Thread
                }
                throw connectException
            }
            BluetoothConnectionManager.bluetoothSocket = socket
            (context as? Activity)?.runOnUiThread {
                Toast.makeText(context, "Connected to ${device.name}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            (context as? Activity)?.runOnUiThread {
                Toast.makeText(context, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }.start()
}

data class SensorItem(val iconResId: Int, val name: String)

@SuppressLint("MissingPermission", "DiscouragedPrivateApi")
private fun createFallbackSocket(device: BluetoothDevice): BluetoothSocket? {
    try {
        val m = device.javaClass.getMethod(
            "createRfcommSocket",
            *arrayOf<Class<*>>(Int::class.javaPrimitiveType as Class<*>)
        )
        return m.invoke(device, 1) as BluetoothSocket
    } catch (e: Exception) {
        Log.e("BluetoothConnect", "Fallback socket creation failed", e)
    }
    return null
}

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

@Preview(showBackground = true)
@Composable
fun PreviewRobotControlScreen() {
    RobotControlScreen(viewModel = FakeRobotControlViewModel(), onBackPressed = {})
}