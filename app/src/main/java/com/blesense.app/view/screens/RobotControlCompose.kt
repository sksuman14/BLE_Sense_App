@file:Suppress("DEPRECATION", "UseCompatLoadingForDrawables")
package com.blesense.app.view.screens

import com.blesense.app.R
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.blesense.app.util.ThemeManager
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.OutputStream
import java.util.UUID
import kotlin.math.roundToInt

// Model to track discovered devices with their transport type (Classic vs BLE)
data class DiscoveredRobotDevice(
    val device: BluetoothDevice,
    val isBle: Boolean,
    val rssi: Int = 0,
    val name: String = ""
)

// Enum to represent Bluetooth scanning states
enum class ScanState {
    IDLE, SCANNING
}

// ================= ROBOT DEVICE FILTER UTILITY =================
// Strictly filters out consumer audio/video, TVs, smartphones, wearables,
// and anonymous/unnamed beacons, allowing only verified Robot modules (Classic & BLE).
fun isRobotDevice(
    device: BluetoothDevice,
    advertisedName: String? = null,
    serviceUuids: List<ParcelUuid>? = null
): Boolean {
    // 1. Check for known Robot BLE GATT Services (Nordic UART Service NUS or HM-10 Serial) FIRST
    val nusUuid = ParcelUuid.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    val hm10Uuid = ParcelUuid.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
    if (serviceUuids?.any { it == nusUuid || it == hm10Uuid } == true) {
        return true
    }

    val name = advertisedName?.takeIf { it.isNotBlank() }
        ?: (try { device.name } catch (_: SecurityException) { null })
        ?: (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { device.alias } catch (_: SecurityException) { null }
        } else null)

    // Never show unnamed, null, or empty devices
    if (name.isNullOrBlank() || name.equals("Unknown", ignoreCase = true) || name.contains("Unknown Robot", ignoreCase = true)) {
        return false
    }

    val upperName = name.trim().uppercase()

    // 2. Explicit blacklist for non-robot consumer electronics (TVs, earbuds, phones, laptops, etc.)
    val nonRobotKeywords = listOf(
        "TV", "WEBOS", "BRAVIA", "SAMSUNG", "LG", "OPPO", "VIVO", "REALME", "XIAOMI", "REDMI",
        "ONEPLUS", "IPHONE", "IPAD", "MACBOOK", "BUDS", "EARBUDS", "AIRPOD", "HEADPHONE",
        "HEADSET", "SPEAKER", "SOUNDBAR", "WATCH", "BAND", "FITNESS", "PRINTER", "KEYBOARD", "MOUSE"
    )
    if (nonRobotKeywords.any { upperName.contains(it) } && !upperName.contains("ROBOT")) {
        return false
    }

    // 3. Exclude devices identified as Audio/Video, Phone, Wearable, Computer via BluetoothClass
    try {
        val btClass = device.bluetoothClass
        if (btClass != null) {
            val major = btClass.majorDeviceClass
            if (major == BluetoothClass.Device.Major.AUDIO_VIDEO ||
                major == BluetoothClass.Device.Major.PHONE ||
                major == BluetoothClass.Device.Major.WEARABLE ||
                major == BluetoothClass.Device.Major.COMPUTER
            ) {
                // If categorized as consumer audio/phone/wearable, require explicit ROBOT/HC module naming
                val explicitRobot = upperName.contains("ROBOT") ||
                        upperName.contains("HC-05") ||
                        upperName.contains("HC-06") ||
                        upperName.contains("CPS")
                if (!explicitRobot) return false
            }
        }
    } catch (_: SecurityException) {}

    // 4. Match robot, microcontroller, and serial module prefixes/keywords
    val robotKeywords = listOf(
        "ROBOT", "CPS", "HC-05", "HC-06", "HC-08", "HC05", "HC06", "HC08",
        "HM-10", "HM-11", "HM-19", "HM10", "CC2541",
        "JDY", "BT05", "BT-05", "BT06", "BT-06", "AT-09", "SPP-CA",
        "CAR", "BOT", "ROBOCAR", "SMART_CAR", "RC_CAR", "BLE_CAR",
        "NRF52", "NRF51", "NORDIC", "ARDUINO", "ESP32", "STM32", "L293D", "L298N", "MOTOR",
        "BLESENSE"
    )

    return robotKeywords.any { upperName.contains(it) }
}

// ================= DUAL-MODE BLUETOOTH SCANNING VIEW MODEL =================
// Discovers both Bluetooth Classic (HC-05/06) and BLE devices (nRF52832) concurrently
class DualBluetoothViewModel : ViewModel() {
    private val _devices = MutableStateFlow<List<DiscoveredRobotDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredRobotDevice>> = _devices.asStateFlow()
    private val _scanState = MutableStateFlow(ScanState.IDLE)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    internal val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }
    private var receiverRegistered = false
    private var isBleScanning = false

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val devName = result.scanRecord?.deviceName
                ?: (try { result.device.name } catch (_: SecurityException) { null })
                ?: (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try { result.device.alias } catch (_: SecurityException) { null }
                } else null)

            if (isRobotDevice(result.device, devName, result.scanRecord?.serviceUuids)) {
                val displayName = if (!devName.isNullOrBlank()) devName else "BLE Robot (${result.device.address.takeLast(5)})"
                addOrUpdateDevice(result.device, isBle = true, rssi = result.rssi, name = displayName)
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { result ->
                val devName = result.scanRecord?.deviceName
                    ?: (try { result.device.name } catch (_: SecurityException) { null })
                if (isRobotDevice(result.device, devName, result.scanRecord?.serviceUuids)) {
                    val displayName = if (!devName.isNullOrBlank()) devName else "BLE Robot (${result.device.address.takeLast(5)})"
                    addOrUpdateDevice(result.device, isBle = true, rssi = result.rssi, name = displayName)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w("DualScan", "BLE scan failed with error code $errorCode")
        }
    }

    private val deviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                // ACTION_FOUND only fires when a physical device actually responds over the air to RF inquiry!
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val extraName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    device?.let { dev ->
                        val devName = extraName?.takeIf { it.isNotBlank() }
                            ?: (try { dev.name } catch (_: SecurityException) { null })
                            ?: (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try { dev.alias } catch (_: SecurityException) { null }
                            } else null)
                        if (isRobotDevice(dev, devName)) {
                            val isBle = (dev.type == BluetoothDevice.DEVICE_TYPE_LE)
                            addOrUpdateDevice(dev, isBle = isBle, rssi = if (rssi != Short.MIN_VALUE.toInt()) rssi else 0, name = devName)
                        }
                    }
                }
                // When remote name request completes for an over-the-air discovered device, ONLY update if already found!
                BluetoothDevice.ACTION_NAME_CHANGED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val extraName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                    device?.let { dev ->
                        val devName = extraName?.takeIf { it.isNotBlank() }
                            ?: (try { dev.name } catch (_: SecurityException) { null })
                        if (!devName.isNullOrBlank()) {
                            val current = _devices.value.toMutableList()
                            val index = current.indexOfFirst { it.device.address == dev.address }
                            if (index >= 0) {
                                current[index] = current[index].copy(name = devName)
                                _devices.value = current
                            }
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (!isBleScanning) {
                        _scanState.value = ScanState.IDLE
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
                        _scanState.value = ScanState.IDLE
                        isBleScanning = false
                    }
                }
            }
        }
    }

    private fun addOrUpdateDevice(device: BluetoothDevice, isBle: Boolean, rssi: Int = 0, name: String? = null) {
        val resolvedName = (name?.takeIf { it.isNotBlank() }
            ?: (try { device.name } catch (_: SecurityException) { null })
            ?: (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try { device.alias } catch (_: SecurityException) { null }
            } else null)) ?: ""
        if (resolvedName.isBlank()) return // Strictly do not add nameless/unknown devices

        val current = _devices.value.toMutableList()
        val index = current.indexOfFirst { it.device.address == device.address }
        if (index >= 0) {
            val existing = current[index]
            val updatedIsBle = existing.isBle || isBle
            val updatedName = if (existing.name.isBlank()) resolvedName else existing.name
            if (updatedIsBle != existing.isBle || (rssi != 0 && rssi != existing.rssi) || updatedName != existing.name) {
                current[index] = existing.copy(
                    isBle = updatedIsBle,
                    rssi = if (rssi != 0) rssi else existing.rssi,
                    name = updatedName
                )
                current.sortByDescending { it.rssi }
                _devices.value = current
            }
        } else {
            current.add(DiscoveredRobotDevice(device, isBle, rssi, resolvedName))
            current.sortByDescending { it.rssi }
            _devices.value = current
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

        // STRICT REQUIREMENT: Only show currently present, nearby devices!
        // Start completely clean. Never show offline paired devices!
        _scanState.value = ScanState.SCANNING
        _devices.value = emptyList()
        _errorMessage.value = null

        // 1. Register Receiver for Live Classic Discovery (ACTION_FOUND only fires for devices physically responding)
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothDevice.ACTION_NAME_CHANGED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            context.registerReceiver(deviceReceiver, filter)
            receiverRegistered = true
        }

        if (bluetoothAdapter!!.isDiscovering) {
            bluetoothAdapter!!.cancelDiscovery()
        }
        bluetoothAdapter!!.startDiscovery()

        // 2. Start BLE scanner concurrently (Low latency, zero report delay for instant detection)
        try {
            val leScanner = bluetoothAdapter?.bluetoothLeScanner
            if (leScanner != null && bluetoothAdapter!!.isEnabled) {
                val builder = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setReportDelay(0)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    builder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    builder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    builder.setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder.setLegacy(false)
                }

                leScanner.startScan(null, builder.build(), bleScanCallback)
                isBleScanning = true
            }
        } catch (e: Exception) {
            Log.w("DualScan", "Could not start BLE scanner: ${e.message}")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan(context: Context) {
        _scanState.value = ScanState.IDLE
        try {
            if (bluetoothAdapter?.isEnabled == true) {
                bluetoothAdapter?.cancelDiscovery()
            }
        } catch (_: Exception) {}

        if (receiverRegistered) {
            try {
                context.unregisterReceiver(deviceReceiver)
                receiverRegistered = false
            } catch (e: Exception) {
                Log.e("DualScan", "Error unregistering receiver: ${e.message}")
            }
        }

        if (isBleScanning) {
            try {
                if (bluetoothAdapter?.isEnabled == true) {
                    bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleScanCallback)
                }
                isBleScanning = false
            } catch (e: Exception) {
                Log.e("DualScan", "Error stopping BLE scanner: ${e.message}")
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onCleared() {
        super.onCleared()
        try {
            if (bluetoothAdapter?.isEnabled == true) {
                bluetoothAdapter?.cancelDiscovery()
                if (isBleScanning) {
                    bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleScanCallback)
                }
            }
        } catch (_: Exception) {}
    }
}

// Typealias for backward compatibility
typealias ClassicBluetoothViewModel = DualBluetoothViewModel

// ================= DUAL-MODE BLUETOOTH CONNECTION MANAGER =================
enum class RobotConnectionType {
    NONE, CLASSIC_SPP, BLE_NUS
}

object BluetoothConnectionManager {
    var connectionType: RobotConnectionType = RobotConnectionType.NONE
    var bluetoothSocket: BluetoothSocket? = null
    var bluetoothGatt: BluetoothGatt? = null
    var bleRxCharacteristic: BluetoothGattCharacteristic? = null
    var bleTxCharacteristic: BluetoothGattCharacteristic? = null
    var connectedDeviceName: String? = null
    var connectedDeviceAddress: String? = null

    // Synchronization lock to strictly prevent concurrent socket write races
    private val writeLock = Any()

    fun isConnected(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            if (connectionType != RobotConnectionType.NONE) {
                disconnect()
            }
            return false
        }

        return when (connectionType) {
            RobotConnectionType.CLASSIC_SPP -> {
                val socket = bluetoothSocket
                if (socket != null && socket.isConnected) {
                    true
                } else {
                    if (connectionType == RobotConnectionType.CLASSIC_SPP) {
                        disconnect()
                    }
                    false
                }
            }
            RobotConnectionType.BLE_NUS -> {
                val ok = (bluetoothGatt != null && bleRxCharacteristic != null)
                if (!ok && connectionType == RobotConnectionType.BLE_NUS) {
                    disconnect()
                }
                ok
            }
            RobotConnectionType.NONE -> false
        }
    }

    fun sendStopCommandImmediate(): Boolean {
        synchronized(writeLock) {
            return try {
                when (connectionType) {
                    RobotConnectionType.CLASSIC_SPP -> {
                        val socket = bluetoothSocket
                        if (socket != null && socket.isConnected) {
                            val stream = socket.outputStream
                            // Send Stop command with redundancy: 'S\n' and 'S' to handle all Arduino firmware variants
                            stream.write("S\n".toByteArray(Charsets.UTF_8))
                            stream.flush()
                            stream.write("S".toByteArray(Charsets.UTF_8))
                            stream.flush()
                            true
                        } else false
                    }
                    RobotConnectionType.BLE_NUS -> {
                        val gatt = bluetoothGatt
                        val rxChar = bleRxCharacteristic
                        if (gatt != null && rxChar != null) {
                            writeBleCharacteristic(gatt, rxChar, "S\n".toByteArray(Charsets.UTF_8))
                            writeBleCharacteristic(gatt, rxChar, "S".toByteArray(Charsets.UTF_8))
                            true
                        } else false
                    }
                    RobotConnectionType.NONE -> false
                }
            } catch (e: Exception) {
                Log.e("BluetoothManager", "sendStopCommandImmediate error: ${e.message}")
                false
            }
        }
    }

    fun disconnect() {
        // ALWAYS send emergency stop to the robot before severing the Bluetooth connection!
        try {
            sendStopCommandImmediate()
        } catch (_: Exception) {}

        synchronized(writeLock) {
            try {
                bluetoothSocket?.close()
            } catch (e: Exception) {
                Log.e("BluetoothManager", "Error closing classic socket: ${e.message}")
            }
            bluetoothSocket = null

            try {
                bluetoothGatt?.disconnect()
                bluetoothGatt?.close()
            } catch (e: Exception) {
                Log.e("BluetoothManager", "Error closing BLE gatt: ${e.message}")
            }
            bluetoothGatt = null
            bleRxCharacteristic = null
            bleTxCharacteristic = null
            connectionType = RobotConnectionType.NONE
            connectedDeviceName = null
            connectedDeviceAddress = null
        }
    }

    fun sendCommand(command: String): Boolean {
        if (!isConnected()) {
            disconnect()
            return false
        }

        if (command == "S") {
            return sendStopCommandImmediate()
        }

        synchronized(writeLock) {
            return try {
                when (connectionType) {
                    RobotConnectionType.CLASSIC_SPP -> {
                        val socket = bluetoothSocket
                        if (socket != null && socket.isConnected) {
                            socket.outputStream.write(command.toByteArray(Charsets.UTF_8))
                            socket.outputStream.flush()
                            true
                        } else {
                            disconnect()
                            false
                        }
                    }
                    RobotConnectionType.BLE_NUS -> {
                        val gatt = bluetoothGatt
                        val rxChar = bleRxCharacteristic
                        if (gatt != null && rxChar != null) {
                            val ok = writeBleCharacteristic(gatt, rxChar, command.toByteArray(Charsets.UTF_8))
                            if (!ok) {
                                Log.w("BluetoothManager", "BLE characteristic write failed")
                            }
                            ok
                        } else {
                            disconnect()
                            false
                        }
                    }
                    RobotConnectionType.NONE -> false
                }
            } catch (e: Exception) {
                Log.e("BluetoothManager", "Failed to send command $command: ${e.message}")
                disconnect()
                false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun writeBleCharacteristic(
        gatt: BluetoothGatt,
        char: BluetoothGattCharacteristic,
        bytes: ByteArray
    ): Boolean {
        return try {
            val writeType = if ((char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val status = gatt.writeCharacteristic(char, bytes, writeType)
                status == BluetoothStatusCodes.SUCCESS
            } else {
                char.writeType = writeType
                char.value = bytes
                gatt.writeCharacteristic(char)
            }
        } catch (e: Exception) {
            Log.e("BluetoothManager", "writeBleCharacteristic error: ${e.message}")
            false
        }
    }
}

// Connect to Classic Bluetooth SPP with multi-channel fallback
@SuppressLint("MissingPermission")
fun connectClassicDevice(context: Context, address: String) {
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
                BluetoothConnectionManager.connectionType = RobotConnectionType.CLASSIC_SPP
                BluetoothConnectionManager.connectedDeviceName = device.name ?: device.address
                BluetoothConnectionManager.connectedDeviceAddress = device.address
                (context as? Activity)?.runOnUiThread {
                    Toast.makeText(context, "Connected (Classic): ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
                }
            } else {
                throw Exception("Unable to establish Bluetooth RFCOMM socket connection")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            BluetoothConnectionManager.disconnect()
            (context as? Activity)?.runOnUiThread {
                Toast.makeText(context, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }.start()
}

// Connect to BLE peripheral (nRF52832) using Nordic UART Service (NUS)
@SuppressLint("MissingPermission")
fun connectBleDevice(context: Context, device: BluetoothDevice) {
    BluetoothConnectionManager.disconnect()

    (context as? Activity)?.runOnUiThread {
        Toast.makeText(context, "Connecting (BLE): ${device.name ?: device.address}...", Toast.LENGTH_SHORT).show()
    }

    val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BleConnect", "Connected to GATT server for ${device.address}, discovering services...")
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BleConnect", "Disconnected from GATT server ${device.address} (status: $status)")
                BluetoothConnectionManager.disconnect()
                (context as? Activity)?.runOnUiThread {
                    Toast.makeText(context, "Disconnected: ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Nordic UART Service (NUS) UUIDs
                val nusServiceUuid = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
                val nusRxUuid = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
                val nusTxUuid = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

                // Fallback to HM-10 / CC2541 Generic Serial UUIDs
                val hm10ServiceUuid = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB")
                val hm10CharUuid = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB")

                val service = gatt.getService(nusServiceUuid) ?: gatt.getService(hm10ServiceUuid)
                val rxChar = service?.getCharacteristic(nusRxUuid) ?: service?.getCharacteristic(hm10CharUuid)
                val txChar = service?.getCharacteristic(nusTxUuid) ?: rxChar

                if (rxChar != null) {
                    BluetoothConnectionManager.bluetoothGatt = gatt
                    BluetoothConnectionManager.bleRxCharacteristic = rxChar
                    BluetoothConnectionManager.bleTxCharacteristic = txChar
                    BluetoothConnectionManager.connectionType = RobotConnectionType.BLE_NUS
                    BluetoothConnectionManager.connectedDeviceName = device.name ?: device.address
                    BluetoothConnectionManager.connectedDeviceAddress = device.address

                    // Enable notification on TX if available
                    if (txChar != null && (txChar.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        try {
                            gatt.setCharacteristicNotification(txChar, true)
                            val cccd = txChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"))
                            if (cccd != null) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                                } else {
                                    @Suppress("DEPRECATION")
                                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    @Suppress("DEPRECATION")
                                    gatt.writeDescriptor(cccd)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("BleConnect", "Failed to subscribe to notifications: ${e.message}")
                        }
                    }

                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Connected (BLE NUS): ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("BleConnect", "No supported UART service found on ${device.address}")
                    (context as? Activity)?.runOnUiThread {
                        Toast.makeText(context, "Device missing Nordic UART Service", Toast.LENGTH_LONG).show()
                    }
                    BluetoothConnectionManager.disconnect()
                }
            } else {
                Log.e("BleConnect", "Service discovery failed with status $status")
                BluetoothConnectionManager.disconnect()
            }
        }
    }

    try {
        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
        BluetoothConnectionManager.bluetoothGatt = gatt
    } catch (e: Exception) {
        Log.e("BleConnect", "connectGatt error: ${e.message}")
        BluetoothConnectionManager.disconnect()
    }
}

// Unified connection dispatcher supporting both Classic RFCOMM and BLE NUS
@SuppressLint("MissingPermission")
fun connectToDevice(context: Context, device: BluetoothDevice, isBle: Boolean) {
    if (isBle || device.type == BluetoothDevice.DEVICE_TYPE_LE) {
        connectBleDevice(context, device)
    } else {
        connectClassicDevice(context, device.address)
    }
}

// Overload for backward compatibility
@SuppressLint("MissingPermission")
fun connectToDevice(context: Context, address: String) {
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return
    val device = bluetoothAdapter.getRemoteDevice(address)
    val isBle = (device.type == BluetoothDevice.DEVICE_TYPE_LE)
    connectToDevice(context, device, isBle)
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

// Enable immersive mode for full-screen experience
fun Activity.enableImmersiveMode() {
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    insetsController.hide(WindowInsetsCompat.Type.systemBars())
    @Suppress("DEPRECATION")
    window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
}

// Restore system bars and navigation bar upon exiting full-screen mode
fun Activity.disableImmersiveMode() {
    @Suppress("DEPRECATION")
    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    insetsController.show(WindowInsetsCompat.Type.systemBars())
    WindowCompat.setDecorFitsSystemWindows(window, false)
    (this as? ComponentActivity)?.enableEdgeToEdge()
    insetsController.isAppearanceLightStatusBars = !ThemeManager.isDarkMode.value
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

    @Volatile
    private var lastSentCommand: String? = null

    // Drive states
    @Volatile
    private var isForward = false
    @Volatile
    private var isBackward = false
    @Volatile
    private var isLeft = false
    @Volatile
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

    fun stopRobotImmediate() {
        isForward = false
        isBackward = false
        isLeft = false
        isRight = false
        _driveDirection.value = "STOP"
        lastSentCommand = "S"
        // Drain / purge any queued commands so nothing old executes
        while (commandChannel.tryReceive().isSuccess) {}

        // Send STOP via dedicated background thread so it is never cancelled if viewModelScope dies
        Thread {
            BluetoothConnectionManager.sendStopCommandImmediate()
        }.start()

        val current = _commandLogs.value.toMutableList()
        current.add(0, "> STOP (S)")
        if (current.size > 5) current.removeAt(5)
        _commandLogs.value = current
    }

    /**
     * Atomically updates vertical movement axis:
     * +1: Forward
     * -1: Backward
     *  0: Stop / Neutral (clears both forward and backward simultaneously without intermediate race)
     */
    fun setMovementAxis(axis: Int) {
        when (axis) {
            1 -> {
                isForward = true
                isBackward = false
            }
            -1 -> {
                isForward = false
                isBackward = true
            }
            else -> {
                isForward = false
                isBackward = false
            }
        }
        recalculateAndDispatchDrive()
    }

    /**
     * Atomically updates horizontal steering axis:
     * +1: Right
     * -1: Left
     *  0: Center / Neutral (clears both left and right simultaneously without intermediate race)
     */
    fun setSteeringAxis(axis: Int) {
        when (axis) {
            1 -> {
                isRight = true
                isLeft = false
            }
            -1 -> {
                isRight = false
                isLeft = true
            }
            else -> {
                isRight = false
                isLeft = false
            }
        }
        recalculateAndDispatchDrive()
    }

    private fun recalculateAndDispatchDrive() {
        val activeCommands = mutableListOf<String>()
        if (isForward && !isBackward) activeCommands.add("F")
        else if (isBackward && !isForward) activeCommands.add("B")
        
        if (isLeft && !isRight) activeCommands.add("L")
        else if (isRight && !isLeft) activeCommands.add("R")

        if (activeCommands.isEmpty()) {
            // User released all controls -> INSTANT STOP
            stopRobotImmediate()
        } else {
            val cmd = activeCommands.joinToString("")
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
    }

    fun updateDriveState(f: Boolean? = null, b: Boolean? = null, l: Boolean? = null, r: Boolean? = null) {
        if (f != null) isForward = f
        if (b != null) isBackward = b
        if (l != null) isLeft = l
        if (r != null) isRight = r
        recalculateAndDispatchDrive()
    }

    fun updateConnectionStatus() {
        val connected = BluetoothConnectionManager.isConnected()
        _isConnected.value = connected
        if (!connected) {
            lastSentCommand = null
        }
    }

    fun disconnectRobot() {
        stopRobotImmediate()
        viewModelScope.launch(Dispatchers.IO) {
            delay(50) // Brief time for "S" to hit the wire
            BluetoothConnectionManager.disconnect()
            updateConnectionStatus()
        }
    }

    fun isBluetoothConnected(): Boolean {
        return BluetoothConnectionManager.isConnected()
    }

    fun sendCommand(command: String) {
        if (command == "S") {
            stopRobotImmediate()
            return
        }
        // Purge old command and enqueue new
        while (commandChannel.tryReceive().isSuccess) {}
        commandChannel.trySend(command)
    }

    private fun processSendCommand(command: String) {
        try {
            if (!isBluetoothConnected()) {
                _isConnected.value = false
                lastSentCommand = null
                return
            }

            if (command == lastSentCommand) return

            val success = BluetoothConnectionManager.sendCommand(command)
            if (success) {
                val current = _commandLogs.value.toMutableList()
                current.add(0, "> $command")
                if (current.size > 5) current.removeAt(5)
                _commandLogs.value = current

                lastSentCommand = command
                _isConnected.value = true
            } else {
                _isConnected.value = false
                lastSentCommand = null
            }
        } catch (e: Exception) {
            Log.e("RobotCommand", "Failed to send command $command", e)
            BluetoothConnectionManager.disconnect()
            _isConnected.value = false
            lastSentCommand = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopRobotImmediate()
        // Ensure stop is physically dispatched to socket even when viewModel is cleared on popBackStack
        Thread {
            BluetoothConnectionManager.sendStopCommandImmediate()
        }.start()
    }
}

// ================= DEVICE SELECTION DIALOG =================
@Composable
fun DeviceSelectionDialog(
    devices: List<DiscoveredRobotDevice>,
    isScanning: Boolean,
    onDeviceSelected: (DiscoveredRobotDevice) -> Unit,
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
                            text = "Scanning for Robots (Classic + BLE)...",
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
                            text = if (isScanning) "Searching for Robot..." else "No robot devices found",
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
                        items(devices) { item ->
                            DeviceItem(
                                item = item,
                                onClick = { onDeviceSelected(item) },
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
    item: DiscoveredRobotDevice,
    onClick: () -> Unit,
    textColor: Color
) {
    val device = item.device
    val isBle = item.isBle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val deviceName = remember(item) {
                if (item.name.isNotBlank()) {
                    item.name
                } else {
                    try {
                        device.name ?: device.address
                    } catch (e: SecurityException) {
                        device.address
                    }
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
        Spacer(modifier = Modifier.width(8.dp))
        // Subtle badge distinguishing BLE from Classic Bluetooth
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isBle) Color(0x2238BDF8) else Color(0x22F59E0B))
                .border(
                    0.8.dp,
                    if (isBle) Color(0xFF38BDF8) else Color(0xFFF59E0B),
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (isBle) "BLE" else "CLASSIC",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isBle) Color(0xFF38BDF8) else Color(0xFFF59E0B)
            )
        }
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

// ================= FUTURISTIC ROBOT CONTROL UI (ROBOT CAR COMPOSE) =================
private val Cyan = Color(0xFF42D8FF)
private val Panel = Color(0xCC071A2B)
private val Muted = Color(0xFF9AB0C6)

@Composable
fun Cyber3DControlButton(
    modifier: Modifier = Modifier,
    isMoved: Boolean = false,
) {
    Box(
        modifier = modifier.clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Glowing cyan bloom under knob when dragged
        if (isMoved) {
            Box(
                modifier = Modifier
                    .fillMaxSize(1.18f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xAA00F5FF),
                                Color(0x3300E5FF),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        Image(
            painter = painterResource(R.drawable.control_knob_3d),
            contentDescription = "3D Control Knob",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun InteractiveControlDial(
    modifier: Modifier,
    isVertical: Boolean,
    onPositivePress: () -> Unit = {},
    onPositiveRelease: () -> Unit = {},
    onNegativePress: () -> Unit = {},
    onNegativeRelease: () -> Unit = {},
    onAxisChanged: ((Int) -> Unit)? = null
) {
    val animKnobX = remember { Animatable(0f) }
    val animKnobY = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val currentPosPress by rememberUpdatedState(onPositivePress)
    val currentPosRelease by rememberUpdatedState(onPositiveRelease)
    val currentNegPress by rememberUpdatedState(onNegativePress)
    val currentNegRelease by rememberUpdatedState(onNegativeRelease)
    val currentAxisChanged by rememberUpdatedState(onAxisChanged)

    var currentActiveAxis by remember { mutableStateOf(0) }
    var isPosActive by remember { mutableStateOf(false) }
    var isNegActive by remember { mutableStateOf(false) }

    fun resetState() {
        if (currentAxisChanged != null) {
            if (currentActiveAxis != 0) {
                currentActiveAxis = 0
                currentAxisChanged?.invoke(0)
            }
        } else {
            // Only fire release if that specific direction was actually active!
            if (isPosActive) {
                isPosActive = false
                currentPosRelease()
            }
            if (isNegActive) {
                isNegActive = false
                currentNegRelease()
            }
        }
        coroutineScope.launch {
            animKnobX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
        coroutineScope.launch {
            animKnobY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(CircleShape)
            .pointerInput(isVertical) {
                try {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val radius = size.width / 2f
                            if (radius <= 0f) continue
                            val maxTravelPx = radius * 0.55f

                            fun updateFromPosition(pos: Offset) {
                                val deadZone = 0.20f
                                if (isVertical) {
                                    // Movement Dial: Strictly Vertical along Y-axis (Forward ^ / Backward v)
                                    // X-axis is permanently locked to 0f
                                    val relY = (pos.y - radius) / maxTravelPx
                                    val clampedY = relY.coerceIn(-1f, 1f)

                                    coroutineScope.launch {
                                        animKnobX.snapTo(0f)
                                        animKnobY.snapTo(clampedY)
                                    }

                                    if (currentAxisChanged != null) {
                                        val newAxis = when {
                                            clampedY < -deadZone -> 1  // Pushed Up -> Forward (+1)
                                            clampedY > deadZone -> -1  // Pushed Down -> Backward (-1)
                                            else -> 0                 // Deadzone -> Neutral / Stop (0)
                                        }
                                        if (newAxis != currentActiveAxis) {
                                            currentActiveAxis = newAxis
                                            currentAxisChanged?.invoke(newAxis)
                                        }
                                    } else {
                                        if (clampedY < -deadZone) {
                                            // Pushed Up -> Forward
                                            if (!isPosActive) {
                                                isPosActive = true
                                                currentPosPress()
                                            }
                                            if (isNegActive) {
                                                isNegActive = false
                                                currentNegRelease()
                                            }
                                        } else if (clampedY > deadZone) {
                                            // Pushed Down -> Backward
                                            if (!isNegActive) {
                                                isNegActive = true
                                                currentNegPress()
                                            }
                                            if (isPosActive) {
                                                isPosActive = false
                                                currentPosRelease()
                                            }
                                        } else {
                                            if (isPosActive) {
                                                isPosActive = false
                                                currentPosRelease()
                                            }
                                            if (isNegActive) {
                                                isNegActive = false
                                                currentNegRelease()
                                            }
                                        }
                                    }
                                } else {
                                    // Steering Dial: Strictly Horizontal along X-axis (Left < / Right >)
                                    // Y-axis is permanently locked to 0f
                                    val relX = (pos.x - radius) / maxTravelPx
                                    val clampedX = relX.coerceIn(-1f, 1f)

                                    coroutineScope.launch {
                                        animKnobX.snapTo(clampedX)
                                        animKnobY.snapTo(0f)
                                    }

                                    if (currentAxisChanged != null) {
                                        val newAxis = when {
                                            clampedX > deadZone -> 1   // Pushed Right -> Right (+1)
                                            clampedX < -deadZone -> -1 // Pushed Left -> Left (-1)
                                            else -> 0                 // Deadzone -> Center / Neutral (0)
                                        }
                                        if (newAxis != currentActiveAxis) {
                                            currentActiveAxis = newAxis
                                            currentAxisChanged?.invoke(newAxis)
                                        }
                                    } else {
                                        if (clampedX > deadZone) {
                                            // Pushed Right -> Right
                                            if (!isPosActive) {
                                                isPosActive = true
                                                currentPosPress()
                                            }
                                            if (isNegActive) {
                                                isNegActive = false
                                                currentNegRelease()
                                            }
                                        } else if (clampedX < -deadZone) {
                                            // Pushed Left -> Left
                                            if (!isNegActive) {
                                                isNegActive = true
                                                currentNegPress()
                                            }
                                            if (isPosActive) {
                                                isPosActive = false
                                                currentPosRelease()
                                            }
                                        } else {
                                            if (isPosActive) {
                                                isPosActive = false
                                                currentPosRelease()
                                            }
                                            if (isNegActive) {
                                                isNegActive = false
                                                currentNegRelease()
                                            }
                                        }
                                    }
                                }
                            }

                            updateFromPosition(down.position)

                            val pointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.find { it.id == pointerId }
                                if (change == null || !change.pressed) {
                                    resetState()
                                    break
                                }
                                updateFromPosition(change.position)
                            }
                        }
                    }
                } finally {
                    resetState()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val maxTravel = maxWidth * 0.28f
        val knobDiameter = maxWidth * 0.32f
        val isMoved = animKnobX.value != 0f || animKnobY.value != 0f

        // Draw the 3D interactive knob
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (animKnobX.value * maxTravel.toPx()).roundToInt(),
                        (animKnobY.value * maxTravel.toPx()).roundToInt()
                    )
                }
                .size(knobDiameter)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Cyber3DControlButton(
                modifier = Modifier.fillMaxSize(),
                isMoved = isMoved
            )
        }
    }
}

// Standalone composable matching the provided code signature
@Composable
fun RobotControlPanelScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSettings: () -> Unit = {},
    onMenu: () -> Unit = {},
    onBluetooth: () -> Unit = {},
    onSteeringChanged: (x: Float) -> Unit = {},
    onMovementChanged: (y: Float) -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color(0xFF020B14))) {
        val w = maxWidth
        val h = maxHeight
        var hornPressed by remember { mutableStateOf(false) }

        // Pixel-accurate background artwork
        Image(
            painter = painterResource(R.drawable.robot_car_control_panel),
            contentDescription = "Futuristic robotic car control panel",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )

        // Back
        Box(
            modifier = Modifier
                .offset(x = w * 0.015f, y = h * 0.035f)
                .size(width = w * 0.065f, height = h * 0.125f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onBack() }
        )

        // BLE
        Box(
            modifier = Modifier
                .offset(x = w * 0.670f, y = h * 0.033f)
                .size(width = w * 0.105f, height = h * 0.114f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onBluetooth() }
        )

        // Settings
        Box(
            modifier = Modifier
                .offset(x = w * 0.855f, y = h * 0.033f)
                .size(width = w * 0.055f, height = h * 0.105f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onSettings() }
        )

        // Menu
        Box(
            modifier = Modifier
                .offset(x = w * 0.920f, y = h * 0.033f)
                .size(width = w * 0.055f, height = h * 0.105f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onMenu() }
        )

        // Left Joystick (Steering)
        val dialSize = h * 0.345f
        InteractiveControlDial(
            modifier = Modifier
                .offset(x = (w * 0.1483f) - (dialSize / 2f), y = (h * 0.546f) - (dialSize / 2f))
                .size(dialSize),
            isVertical = false,
            onPositivePress = { onSteeringChanged(1f) },
            onPositiveRelease = { onSteeringChanged(0f) },
            onNegativePress = { onSteeringChanged(-1f) },
            onNegativeRelease = { onSteeringChanged(0f) }
        )

        // Right Joystick (Movement)
        InteractiveControlDial(
            modifier = Modifier
                .offset(x = (w * 0.8520f) - (dialSize / 2f), y = (h * 0.547f) - (dialSize / 2f))
                .size(dialSize),
            isVertical = true,
            onPositivePress = { onMovementChanged(1f) },
            onPositiveRelease = { onMovementChanged(0f) },
            onNegativePress = { onMovementChanged(-1f) },
            onNegativeRelease = { onMovementChanged(0f) }
        )

        // HORN
        Box(
            modifier = Modifier
                .offset(x = w * 0.7326f, y = h * 0.8526f)
                .size(width = w * 0.1091f, height = h * 0.0840f)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitFirstDown(requireUnconsumed = false)
                            hornPressed = true
                            waitForUpOrCancellation()
                            hornPressed = false
                        }
                    }
                }
        ) {
            if (hornPressed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Cyan.copy(alpha = 0.30f))
                        .border(1.5.dp, Cyan, CircleShape)
                )
            }
        }
    }
}

// ================= ROBOT HARDWARE & CONTROL SETTINGS DIALOG =================
@Composable
fun RobotSettingsDialog(
    isConnected: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(360.dp),
            shape = RoundedCornerShape(20.dp),
            backgroundColor = Color(0xFF0B1936)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = Cyan, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Control Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Muted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Hardware Protocol Info
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF050E24))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("HARDWARE PROTOCOL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                        Text("Protocols: Bluetooth Classic (RFCOMM) & BLE (NUS)", fontSize = 12.sp, color = Color.White)
                        Text("Baud / Speed: 9600 bps / 1 Mbps BLE PHY", fontSize = 12.sp, color = Color.White)
                        Text("Channels: Auto RFCOMM / Nordic UART (6E400001-...)", fontSize = 11.sp, color = Muted)
                        val connStatus = when (BluetoothConnectionManager.connectionType) {
                            RobotConnectionType.CLASSIC_SPP -> "Connected (Classic RFCOMM)"
                            RobotConnectionType.BLE_NUS -> "Connected (BLE NUS)"
                            RobotConnectionType.NONE -> "Disconnected"
                        }
                        Text("Status: $connStatus", fontSize = 12.sp, color = if (isConnected) Color(0xFF22C55E) else Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Control Configuration Info
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF050E24))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("CONTROL AXIS MAPPING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                        Text("Left Dial: Steering (Horizontal: Left < / Right >)", fontSize = 12.sp, color = Color.White)
                        Text("Right Dial: Movement (Vertical: Forward ^ / Backward v)", fontSize = 12.sp, color = Color.White)
                        Text("Horn Button: Horn (H) / Release (C)", fontSize = 12.sp, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Done", color = Cyan, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ================= MAIN SCREEN WITH BLUETOOTH & TELEMETRY INTEGRATION =================
@Composable
fun RobotControlScreen(
    viewModel: RobotControlViewModel = viewModel(),
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val bluetoothViewModel: ClassicBluetoothViewModel = viewModel()

    val activity = context as? ComponentActivity

    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.enableImmersiveMode()
        onDispose {
            activity?.requestedOrientation = originalOrientation
            activity?.disableImmersiveMode()
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
    var showSettingsDialog by remember { mutableStateOf(false) }
    var selectedSensorName by remember { mutableStateOf("") }
    var selectedSensorIcon by remember { mutableStateOf(Icons.Default.Thermostat) }
    var selectedSensorDetails by remember { mutableStateOf("") }
    var hornPressed by remember { mutableStateOf(false) }

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
        // Guarantee robot starts in verified STOP state immediately on entering screen
        viewModel.stopRobotImmediate()
        activity?.let { sensorViewModel?.startContinuousScan(it) }
        while (true) {
            viewModel.updateConnectionStatus()
            delay(500)
        }
    }

    // Actively monitor phone's Bluetooth ON/OFF state and device connection drops
    DisposableEffect(context) {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        val btStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
                            viewModel.stopRobotImmediate()
                            BluetoothConnectionManager.disconnect()
                            viewModel.updateConnectionStatus()
                            bluetoothViewModel.stopScan(context)
                        }
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val disconnectedDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        if (disconnectedDevice?.address == BluetoothConnectionManager.connectedDeviceAddress ||
                            BluetoothConnectionManager.connectionType != RobotConnectionType.NONE
                        ) {
                            viewModel.stopRobotImmediate()
                            BluetoothConnectionManager.disconnect()
                            viewModel.updateConnectionStatus()
                        }
                    }
                }
            }
        }
        context.registerReceiver(btStateReceiver, filter)
        onDispose {
            try {
                context.unregisterReceiver(btStateReceiver)
            } catch (_: Exception) {}
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

    val openBluetoothPicker: () -> Unit = {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter?.isEnabled == true) {
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
            bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (scanState == ScanState.SCANNING) {
                bluetoothViewModel.stopScan(context)
            }
            // Safety Stop: Stop the robot if the screen is exited via dedicated thread
            viewModel.stopRobotImmediate()
            Thread {
                BluetoothConnectionManager.sendStopCommandImmediate()
            }.start()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020B14))
    ) {
        val w = maxWidth
        val h = maxHeight

        // 1. Pixel-Accurate Background Artwork (Displays car, hangar, and panel graphics)
        Image(
            painter = painterResource(R.drawable.robot_car_control_panel),
            contentDescription = "Futuristic robotic car control panel",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )

        // 2. Interactive Back Button Touch Zone (Top-Left)
        Box(
            modifier = Modifier
                .offset(x = w * 0.015f, y = h * 0.035f)
                .size(width = w * 0.065f, height = h * 0.125f)
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    viewModel.stopRobotImmediate()
                    Thread {
                        BluetoothConnectionManager.sendStopCommandImmediate()
                    }.start()
                    onBackPressed()
                }
        )

        // 3. Status Pill (Top-Center)
        // If disconnected, overlay a clean indicator directly covering the baked-in "CONNECTED" badge.
        // It is strictly an indicator and does NOT open the device picker.
        if (!isConnected) {
            Box(
                modifier = Modifier
                    .offset(x = w * 0.366f, y = h * 0.034f)
                    .size(width = w * 0.292f, height = h * 0.113f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF040E1B))
                    .border(1.2.dp, Cyan.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        tint = Cyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "ROBOTIC CAR",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(14.dp)
                            .background(Color(0xFF1E3851))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "DISCONNECTED",
                        color = Color(0xFFEF4444),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        } else {
            // When connected, clicking on the center button disconnects the device
            Box(
                modifier = Modifier
                    .offset(x = w * 0.366f, y = h * 0.034f)
                    .size(width = w * 0.292f, height = h * 0.113f)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable {
                        viewModel.disconnectRobot()
                        Toast.makeText(context, "Disconnecting Robot...", Toast.LENGTH_SHORT).show()
                    }
            )
        }

        // 4. BLE Button Touch Zone (Top-Right)
        Box(
            modifier = Modifier
                .offset(x = w * 0.670f, y = h * 0.033f)
                .size(width = w * 0.105f, height = h * 0.114f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { openBluetoothPicker() }
        )

        // 5. Settings Button Touch Zone (Top-Right)
        Box(
            modifier = Modifier
                .offset(x = w * 0.855f, y = h * 0.033f)
                .size(width = w * 0.055f, height = h * 0.105f)
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    showSettingsDialog = true
                }
        )

        // 6. More (Menu) Button Touch Zone (Top-Right)
        Box(
            modifier = Modifier
                .offset(x = w * 0.920f, y = h * 0.033f)
                .size(width = w * 0.055f, height = h * 0.105f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { showSensorMenuDialog = true }
        )

        // 7. Interactive Left Dial (Steering: Strictly Left < / Right > on horizontal X-axis)
        val dialSize = h * 0.345f
        InteractiveControlDial(
            modifier = Modifier
                .offset(x = (w * 0.1483f) - (dialSize / 2f), y = (h * 0.546f) - (dialSize / 2f))
                .size(dialSize),
            isVertical = false,
            onAxisChanged = { axis -> viewModel.setSteeringAxis(axis) }
        )

        // 8. Interactive Right Dial (Movement: Strictly Forward ^ / Backward v on vertical Y-axis)
        InteractiveControlDial(
            modifier = Modifier
                .offset(x = (w * 0.8520f) - (dialSize / 2f), y = (h * 0.547f) - (dialSize / 2f))
                .size(dialSize),
            isVertical = true,
            onAxisChanged = { axis -> viewModel.setMovementAxis(axis) }
        )

        // 9. Interactive HORN Button Touch Zone (Bottom-Right)
        // Pill coordinates matching the artwork exactly: x = w * 0.7326, y = h * 0.8526, w = w * 0.1091, h = h * 0.0840
        Box(
            modifier = Modifier
                .offset(x = w * 0.7326f, y = h * 0.8526f)
                .size(width = w * 0.1091f, height = h * 0.0840f)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    try {
                        awaitPointerEventScope {
                            while (true) {
                                awaitFirstDown(requireUnconsumed = false)
                                hornPressed = true
                                viewModel.sendCommand("H")
                                waitForUpOrCancellation()
                                hornPressed = false
                                viewModel.sendCommand("C")
                            }
                        }
                    } finally {
                        hornPressed = false
                        viewModel.sendCommand("C")
                    }
                }
        ) {
            // When pressed, highlight the horn button with a subtle cyan glow overlay
            if (hornPressed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Cyan.copy(alpha = 0.30f))
                        .border(1.5.dp, Cyan, CircleShape)
                )
            }
        }
    }

    if (showDeviceDialog) {
        DeviceSelectionDialog(
            devices = devices, isScanning = scanState == ScanState.SCANNING,
            onDeviceSelected = { selected ->
                connectToDevice(context, selected.device, selected.isBle)
                showDeviceDialog = false
                coroutineScope.launch { delay(2000); viewModel.updateConnectionStatus() }
            },
            onDismissRequest = { showDeviceDialog = false; bluetoothViewModel.stopScan(context) }
        )
    }

    if (showSettingsDialog) {
        RobotSettingsDialog(
            isConnected = isConnected,
            onDismiss = { showSettingsDialog = false }
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
                        Icon(Icons.Default.Sensors, contentDescription = null, tint = Cyan, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Active Telemetry Pods", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    IconButton(onClick = { showSensorMenuDialog = false }) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Muted) }
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
                        title = "SHT40 (Temp & Humidity)", icon = Icons.Default.Thermostat, cardBg = Panel, borderColor = Cyan
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Temperature", fontSize = 11.sp, color = Muted)
                                Text("$tempStr °C", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                                SparklineGraphHUD(color = Color(0xFF38BDF8))
                            }
                            Column {
                                Text("Humidity", fontSize = 11.sp, color = Muted)
                                Text("$humStr %", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
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
                        title = "LIS3DH (Accelerometer)", icon = Icons.Default.Sensors, cardBg = Panel, borderColor = Cyan
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("X: $posXStr g", fontSize = 11.sp, color = Color.White)
                                Text("Y: $posYStr g", fontSize = 11.sp, color = Color.White)
                                Text("Z: $posZStr g", fontSize = 11.sp, color = Color.White)
                            }
                            Axis3DVisualizerHUD()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSensorMenuDialog = false }) { Text("Close", color = Cyan) } }
        )
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); bluetoothViewModel.clearError() }
    }
}
