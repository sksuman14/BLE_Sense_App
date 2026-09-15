package com.blesense.app.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.SparseArray
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import com.blesense.app.model.SensorData
import com.blesense.app.model.BluetoothDeviceModel as BluetoothDevice
import com.blesense.app.model.HistoricalDataEntry
import com.blesense.app.viewmodel.handler.AwsSensorHandler
import com.blesense.app.viewmodel.handler.BleDeviceHandler
import com.blesense.app.viewmodel.handler.DataLoggerHandler
import com.blesense.app.viewmodel.handler.SensorHubHandler

/**
 * Slim orchestrator for BLE scanning.
 *
 * Owns ONLY:
 * - BLE scanner lifecycle (start/stop/restart/refresh)
 * - Single ScanCallback → Channel pipeline
 * - Routing of scan results to isolated device handlers
 * - Trigger advertising (startTrigger/stopTrigger)
 * - Merging of device flows from all handlers
 *
 * All parsing, state management, and device-specific logic is delegated
 * to the three isolated handlers. A crash in one handler cannot affect
 * the others because each handle() call is wrapped in try/catch.
 */
class BluetoothScanViewModel(private val context: Context) : ViewModel() {

    // ══════════════════════════════════════════════════════════════════════════
    // HANDLERS — Each one is completely independent
    // ══════════════════════════════════════════════════════════════════════════

    private val _sensorDataStream = MutableSharedFlow<SensorData>(
        extraBufferCapacity = 10000,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val sensorDataStream: SharedFlow<SensorData> = _sensorDataStream.asSharedFlow()

    val sensorHubHandler = SensorHubHandler(viewModelScope, _sensorDataStream)
    val dataLoggerHandler = DataLoggerHandler(context, viewModelScope, _sensorDataStream)
    val awsSensorHandler = AwsSensorHandler(viewModelScope, _sensorDataStream)

    private val handlers: List<BleDeviceHandler> = listOf(
        sensorHubHandler,
        dataLoggerHandler,
        awsSensorHandler
    )

    // ══════════════════════════════════════════════════════════════════════════
    // MERGED DEVICE LIST — Union of all handler device flows
    // ══════════════════════════════════════════════════════════════════════════

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()

    init {
        // Merge all handler device flows into one unified list
        viewModelScope.launch {
            combine(handlers.map { it.devices }) { arrays ->
                arrays.flatMap { it.toList() }
            }.collect { merged ->
                _devices.value = merged
            }
        }

        // Centralized history update trigger
        handlers.forEach { handler ->
            viewModelScope.launch {
                handler.historyUpdateTrigger.collect { timestamp ->
                    if (timestamp > 0) {
                        _historyUpdateTrigger.value = timestamp
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DELEGATED STATE — Backward-compatible accessors
    // ══════════════════════════════════════════════════════════════════════════

    // --- DataLogger delegations ---
    private val _historyUpdateTrigger = MutableStateFlow(0L)
    val historyUpdateTrigger: StateFlow<Long> = _historyUpdateTrigger.asStateFlow()

    val dataLoggerPacketHistory get() = dataLoggerHandler.dataLoggerPacketHistory
    val latestDataLoggerPacket get() = dataLoggerHandler.latestDataLoggerPacket
    val selectedDataLoggerDeviceId get() = dataLoggerHandler.selectedDataLoggerDeviceId
    val selectedDataLoggerAddress get() = dataLoggerHandler.selectedDataLoggerAddress
    val isUploadingToDashboard get() = dataLoggerHandler.isUploadingToDashboard
    val capturedCount get() = dataLoggerHandler.capturedCount
    val expectedCount get() = dataLoggerHandler.expectedCount
    val r1Count get() = dataLoggerHandler.r1Count
    val r2Count get() = dataLoggerHandler.r2Count
    val r3Count get() = dataLoggerHandler.r3Count


    fun setSelectedDataLogger(deviceId: String?, address: String?) =
        dataLoggerHandler.setSelectedDataLogger(deviceId, address)
    fun clearDataLoggerHistory(deviceId: String) =
        dataLoggerHandler.clearDataLoggerHistory(deviceId)
    fun clearAllDeviceData() {
        dataLoggerHandler.clearAllDataLoggerHistory()
        sensorHubHandler.clearAllData()
        awsSensorHandler.clearAllData()
    }
    fun uploadCapturedDataToDashboard(deviceId: String, address: String) =
        dataLoggerHandler.uploadCapturedDataToDashboard(deviceId, address)
    fun flushPendingDataLoggerPackets() =
        dataLoggerHandler.flushPendingDataLoggerPackets()

    // --- SensorHub delegations ---
    val tempLoggerPacketHistory get() = sensorHubHandler.tempLoggerPacketHistory
    val latestTempLoggerPacket get() = sensorHubHandler.latestTempLoggerPacket
    val sen66PacketHistory get() = sensorHubHandler.sen66PacketHistory
    val latestSen66Packet get() = sensorHubHandler.latestSen66Packet

    // ══════════════════════════════════════════════════════════════════════════
    // BLE SCANNER LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null
    private val scanResultChannel = Channel<ScanResult>(capacity = 50000)

    private val bluetoothScanner: BluetoothLeScanner? by lazy {
        BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner
    }

    // Dedicated background thread for Bluetooth callbacks to prevent UI interference
    private val bleHandlerThread = android.os.HandlerThread("BleScanThread").apply { start() }
    private val bleHandler = Handler(bleHandlerThread.looper)

    private var scanCallback: ScanCallback? = null

    companion object {
        private const val SCAN_RESTART_INTERVAL = 10 * 60 * 1000L
        private const val MAX_HISTORY_ENTRIES_PER_DEVICE = 20000
    }

    init {
        // High-priority parallel processing of scan results to maximize throughput
        repeat(Runtime.getRuntime().availableProcessors().coerceAtLeast(2)) {
            viewModelScope.launch(Dispatchers.Default) {
                for (result in scanResultChannel) {
                    routeScanResult(result)
                }
            }
        }
    }

    /**
     * Highly optimized routing path for 100% accuracy.
     * Uses a fast-path for DataLogger and caches handler routes.
     */
    private val handlerCache = java.util.concurrent.ConcurrentHashMap<String, BleDeviceHandler>()

    private suspend fun routeScanResult(result: ScanResult) {
        try {
            val device = result.device ?: return
            val scanRecord = result.scanRecord ?: return
            val deviceAddress = device.address ?: return
            val manufacturerData = scanRecord.manufacturerSpecificData ?: return

            // Fast Path: Direct routing for known devices
            val cachedHandler = handlerCache[deviceAddress]
            if (cachedHandler != null) {
                try {
                    cachedHandler.handle(result)
                    return
                } catch (e: Exception) {
                    Log.e("BLE", "Cached handler error: ${e.message}")
                    handlerCache.remove(deviceAddress)
                }
            }

            val deviceName = scanRecord.deviceName

            // Slow Path: Discovery and cache population
            for (handler in handlers) {
                if (handler.canHandle(deviceName, deviceAddress, manufacturerData)) {
                    handlerCache[deviceAddress] = handler
                    try {
                        handler.handle(result)
                    } catch (e: Exception) {
                        Log.e("BLE", "Handler ${handler::class.simpleName} error: ${e.message}")
                    }
                    return // Found the handler, move to next result
                }
            }
        } catch (_: Exception) { }
    }

    // ── Scan Start/Stop ──────────────────────────────────────────────────────

    fun startContinuousScan(activity: Activity) {
        if (_isScanning.value && scanJob?.isActive == true) return

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _isScanning.value = true
            startScan(activity)

            while (isActive) {
                delay(SCAN_RESTART_INTERVAL)
                restartScan(activity)
            }
        }
    }

    private fun restartScan(activity: Activity) {
        stopScan()
        Handler(Looper.getMainLooper()).postDelayed({
            startScan(activity)
        }, 100)
    }

    @SuppressLint("MissingPermission")
    fun startScan(activity: Activity?) {
        if (_isScanning.value && scanCallback != null) {
            Log.d("BLE", "Scan already active, skipping start.")
            return
        }
        if (!hasRequiredPermissions()) {
            Log.e("BLE", "Missing permissions to start scan")
            return
        }

        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null || !adapter.isEnabled) {
                Log.e("BLE", "Bluetooth is null or disabled")
                return
            }

            bluetoothScanner?.let { scanner ->
                val scanSettings = createScanSettings()
                scanCallback = createScanCallback()

                // No hardware filters — filtering is handled by handlers in software
                scanner.startScan(null, scanSettings, scanCallback)

                Log.d("BLE", "Scan started for all devices (routed to modular handlers)")
                _isScanning.value = true
            } ?: Log.e("BLE", "BluetoothLeScanner is null")
        } catch (e: Exception) {
            Log.e("BLE", "Error starting scan: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            bluetoothScanner?.let { scanner ->
                scanCallback?.let {
                    scanner.stopScan(it)
                    scanCallback = null
                }
            }
        } catch (_: Exception) { }
        _isScanning.value = false
    }

    fun stopContinuousScan() {
        scanJob?.cancel()
        stopScan()
        _isScanning.value = false
    }

    /**
     * Forcefully restarts the scan to clear OS throttling or stale states.
     */
    @SuppressLint("MissingPermission")
    fun refreshScan(activity: Activity?) {
        Log.d("BLE", "Refreshing scan...")
        try {
            bluetoothScanner?.let { scanner ->
                scanCallback?.let {
                    scanner.stopScan(it)
                    scanCallback = null
                }
            }
        } catch (_: Exception) { }
        _isScanning.value = false

        Handler(Looper.getMainLooper()).postDelayed({
            startScan(activity)
        }, 100)
    }

    @SuppressLint("MissingPermission")
    private fun forceScannerReset() {
        try {
            bluetoothScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}

        Handler(Looper.getMainLooper()).postDelayed({
            val scanSettings = createScanSettings()
            scanCallback = createScanCallback()
            try {
                bluetoothScanner?.startScan(null, scanSettings, scanCallback)
                Log.d("BLE", "Scanner forcefully reset to clear OS buffer")
            } catch (_: Exception) {}
        }, 200)
    }

    private fun createScanSettings(): ScanSettings =
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setLegacy(false)
            .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0) // No delay for real-time responsiveness
            .build()

    private fun createScanCallback(): ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val sent = scanResultChannel.trySend(result).isSuccess
            if (!sent) {
                viewModelScope.launch(Dispatchers.Default) {
                    scanResultChannel.send(result)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onBatchScanResults(results: List<ScanResult>) {
            for (result in results) {
                val sent = scanResultChannel.trySend(result).isSuccess
                if (!sent) {
                    viewModelScope.launch(Dispatchers.Default) {
                        scanResultChannel.send(result)
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("BLE", "Scan failed with error code: $errorCode")
            _isScanning.value = false
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // TRIGGER ADVERTISING
    // ══════════════════════════════════════════════════════════════════════════

    private var currentTriggerCallback: AdvertiseCallback? = null
    private var triggerJob: Job? = null

    /**
     * Starts a targeted trigger advertisement.
     * Controlled by the ViewModel so it can be stopped instantly on packet arrival.
     */
    @SuppressLint("MissingPermission")
    fun startTrigger(command: ByteArray, deviceAddress: String?, durationMs: Long = 10000) {
        stopTrigger()
        dataLoggerHandler.hasAutoStoppedTriggerForSession = false
        val advertiser = (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager)
            .adapter?.bluetoothLeAdvertiser ?: return

        val finalPayload = if (!deviceAddress.isNullOrBlank()) {
            try {
                val realAddress = if (deviceAddress.contains("_")) {
                    deviceAddress.substringBefore("_")
                } else {
                    deviceAddress
                }
                val macBytes = realAddress.split(":").map { it.toInt(16).toByte() }.toByteArray()
                command + macBytes
            } catch (_: Exception) { command }
        } else command

        val data = AdvertiseData.Builder()
            .addManufacturerData(0x0059, finalPayload)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        currentTriggerCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d("BLE", "Trigger started for $deviceAddress")
            }
        }

        try {
            advertiser.startAdvertising(settings, data, currentTriggerCallback)
            triggerJob = viewModelScope.launch {
                delay(durationMs)
                stopTrigger()
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    fun stopTrigger() {
        triggerJob?.cancel()
        triggerJob = null
        val callback = currentTriggerCallback ?: return
        currentTriggerCallback = null

        val advertiser = (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager)
            .adapter?.bluetoothLeAdvertiser ?: return
        try {
            advertiser.stopAdvertising(callback)
            Log.d("BLE", "Trigger stopped immediately")
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun sendAdvertiseCommandToSensor(deviceAddress: String, command: ByteArray) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val advertiser = bluetoothManager.adapter?.bluetoothLeAdvertiser ?: return

        val realAddress = if (deviceAddress.contains("_")) {
            deviceAddress.substringBefore("_")
        } else {
            deviceAddress
        }

        val macBytes = try {
            realAddress.split(":").map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) {
            return
        }

        val payload = command + macBytes

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(5000)
            .build()

        val data = AdvertiseData.Builder()
            .addManufacturerData(0x0059, payload)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d("BLE", "Targeted advertising started for $deviceAddress")
            }
            override fun onStartFailure(errorCode: Int) {
                Log.e("BLE", "Targeted advertising failed: $errorCode")
            }
        }
        advertiser.startAdvertising(settings, data, callback)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HISTORY & BACKWARD COMPATIBILITY
    // ══════════════════════════════════════════════════════════════════════════

    fun getHistory(address: String): List<HistoricalDataEntry> {
        // Check all handlers for history of this address
        val sensorHubHistory = sensorHubHandler.getHistory(address)
        if (sensorHubHistory.isNotEmpty()) return sensorHubHistory

        val dataLoggerHistory = dataLoggerHandler.getHistory(address)
        if (dataLoggerHistory.isNotEmpty()) return dataLoggerHistory

        val awsHistory = awsSensorHandler.getHistory(address)
        if (awsHistory.isNotEmpty()) return awsHistory

        return emptyList()
    }

    /**
     * Backward-compatible public parsing entry point.
     * Routes to the correct handler based on device type.
     */
    fun parseAdvertisingData(result: ScanResult, deviceType: String?): SensorData? {
        val manufacturerData = result.scanRecord?.manufacturerSpecificData ?: return null
        if (manufacturerData.size() == 0) return null
        val deviceAddress = result.device?.address ?: return null

        for (i in 0 until manufacturerData.size()) {
            val data = manufacturerData.valueAt(i) ?: continue
            val parsed = when (deviceType) {
                "SHT40", "LIS3DH", "Soil Sensor", "Ammonia Sensor",
                "VEML7700", "VCNL4040", "AHT20", "BME680", "TempLogger" ->
                    sensorHubHandler.parseAdvertisingData(data, deviceType, deviceAddress)
                "DataLogger" ->
                    dataLoggerHandler.parseAdvertisingDataLoggerData(data, deviceAddress)
                "AWS" ->
                    awsSensorHandler.parseAdvertisingAwsData(data, deviceAddress)
                "sen66" ->
                    sensorHubHandler.parseAdvertisingData(data, deviceType, deviceAddress)
                else -> {
                    if (data.size >= 240)
                        dataLoggerHandler.parseAdvertisingDataLoggerData(data, deviceAddress)
                    else null
                }
            }
            if (parsed != null) return parsed
        }
        return null
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PERMISSIONS & UTILITIES
    // ══════════════════════════════════════════════════════════════════════════

    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun clearDevices() {
        // Note: This only clears the merged view. Individual handler states persist.
        // If needed, clear individual handlers here.
    }

    override fun onCleared() {
        super.onCleared()
        stopContinuousScan()
        handlers.forEach { it.onCleared() }
    }
}
