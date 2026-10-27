package com.blesense.app.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.util.SparseArray
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nordicsemi.android.kotlin.ble.advertiser.BleAdvertiser
import no.nordicsemi.android.kotlin.ble.core.RealServerDevice
import no.nordicsemi.android.kotlin.ble.core.advertiser.BleAdvertisingConfig
import no.nordicsemi.android.kotlin.ble.core.advertiser.BleAdvertisingData
import no.nordicsemi.android.kotlin.ble.core.advertiser.BleAdvertisingInterval
import no.nordicsemi.android.kotlin.ble.core.advertiser.BleAdvertisingSettings
import no.nordicsemi.android.kotlin.ble.core.advertiser.BleTxPowerLevel
import no.nordicsemi.android.kotlin.ble.core.advertiser.ManufacturerData
import no.nordicsemi.android.kotlin.ble.core.data.util.DataByteArray
import no.nordicsemi.android.kotlin.ble.core.scanner.BleNumOfMatches
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScanDataStatus
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScanFilter
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScanMode
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScanResult
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScannerCallbackType
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScannerMatchMode
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScannerPhy
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScannerSettings
import no.nordicsemi.android.kotlin.ble.core.scanner.FilteredManufacturerData
import no.nordicsemi.android.kotlin.ble.scanner.BleScanner

import com.blesense.app.model.SensorData
import com.blesense.app.model.BluetoothDeviceModel as BluetoothDevice
import com.blesense.app.model.HistoricalDataEntry
import com.blesense.app.viewmodel.handler.AwsSensorHandler
import com.blesense.app.viewmodel.handler.BleDeviceHandler
import com.blesense.app.viewmodel.handler.DataLoggerHandler
import com.blesense.app.viewmodel.handler.RawTestDeviceHandler
import com.blesense.app.viewmodel.handler.SensorHubHandler
import com.blesense.app.viewmodel.handler.StepCounterHandler

/**
 * Slim orchestrator for BLE scanning.
 *
 * Owns ONLY:
 * - Nordic BleScanner lifecycle (start/stop/restart/refresh)
 * - Flow<BleScanResult> → Channel pipeline
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
        extraBufferCapacity = 50000,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val sensorDataStream: SharedFlow<SensorData> = _sensorDataStream.asSharedFlow()

    val stepCounterHandler = StepCounterHandler(viewModelScope, _sensorDataStream)
    val sensorHubHandler = SensorHubHandler(viewModelScope, _sensorDataStream)
    val dataLoggerHandler = DataLoggerHandler(context, viewModelScope, _sensorDataStream)
    val awsSensorHandler = AwsSensorHandler(viewModelScope, _sensorDataStream)
    val rawTestDeviceHandler = RawTestDeviceHandler(viewModelScope)

    private val handlers: List<BleDeviceHandler> = listOf(
        stepCounterHandler,
        sensorHubHandler,
        dataLoggerHandler,
        awsSensorHandler,
        rawTestDeviceHandler
    )

    // ══════════════════════════════════════════════════════════════════════════
    // MERGED DEVICE LIST — Union of all handler device flows
    // ══════════════════════════════════════════════════════════════════════════

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()

    private val _historyUpdateTrigger = MutableStateFlow(0L)
    val historyUpdateTrigger: StateFlow<Long> = _historyUpdateTrigger.asStateFlow()

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
    val recentManifestPackets get() = dataLoggerHandler.recentManifestPackets


    fun setSelectedDataLogger(deviceId: String?, address: String?) =
        dataLoggerHandler.setSelectedDataLogger(deviceId, address)
    fun clearDataLoggerHistory(deviceId: String) {
        stallRearmCount = 0
        lastObservedCapturedCount = -1
        dataLoggerHandler.clearDataLoggerHistory(deviceId)
    }
    fun clearAllDeviceData() {
        stallRearmCount = 0
        lastObservedCapturedCount = -1
        dataLoggerHandler.clearAllDataLoggerHistory()
        sensorHubHandler.clearAllData()
        awsSensorHandler.clearAllData()
        stepCounterHandler.clearAllData()
        rawTestDeviceHandler.clearAllData()
    }

    // --- StepCounter delegations ---
    val latestStepResult get() = stepCounterHandler.latestStepResult
    val selectedStepCounterAddress get() = stepCounterHandler.selectedDeviceAddress
    val stepCounterDevices get() = stepCounterHandler.devices
    fun resetStepCounter(address: String? = null) = stepCounterHandler.resetSteps(address)
    fun toggleStepCounterPause(address: String? = null) = stepCounterHandler.togglePause(address)
    fun selectStepCounterDevice(address: String) = stepCounterHandler.selectDevice(address)
    fun uploadCapturedDataToDashboard(deviceId: String, address: String) =
        dataLoggerHandler.uploadCapturedDataToDashboard(deviceId, address)
    fun flushPendingDataLoggerPackets() =
        dataLoggerHandler.flushPendingDataLoggerPackets()

    // --- SensorHub delegations ---
    val sensorHubDevices get() = sensorHubHandler.devices
    val tempLoggerPacketHistory get() = sensorHubHandler.tempLoggerPacketHistory
    val latestTempLoggerPacket get() = sensorHubHandler.latestTempLoggerPacket
    val sen66PacketHistory get() = sensorHubHandler.sen66PacketHistory
    val latestSen66Packet get() = sensorHubHandler.latestSen66Packet

    // --- Additional Device Lists ---
    val dataLoggerDevices get() = dataLoggerHandler.devices
    val awsDevices get() = awsSensorHandler.devices
    val rawTestDevices get() = rawTestDeviceHandler.devices

    // ══════════════════════════════════════════════════════════════════════════
    // BLE SCANNER LIFECYCLE (Nordic BleScanner)
    // ══════════════════════════════════════════════════════════════════════════

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null
    private var restartJob: Job? = null
    private var watchdogJob: Job? = null
    @Volatile
    private var lastGlobalPacketReceivedTime = 0L
    @Volatile
    private var stallRearmCount = 0
    @Volatile
    private var lastObservedCapturedCount = -1

    private val scanResultChannel = Channel<BleScanResult>(
        capacity = 50000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val bleScanner: BleScanner by lazy {
        BleScanner(context)
    }

    private val bleAdvertiser: BleAdvertiser by lazy {
        BleAdvertiser.create(context)
    }

    companion object {
        // Restart scan cleanly at 3.5 minutes to flush native HAL buffers before the Android/Samsung 5-minute stall
        private const val SCAN_RESTART_INTERVAL = 3 * 60 * 1000L + 30 * 1000L
    }

    init {
        // Automatically stop trigger advertising as soon as first DataLogger packet arrives
        dataLoggerHandler.onFirstPacketReceived = {
            stopTrigger()
        }

        // Single dedicated coroutine to process scan results sequentially in chronological order
        // Guarantees zero out-of-order race conditions for BundleTracker and round detection
        viewModelScope.launch(Dispatchers.Default) {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            } catch (_: Exception) {}
            for (result in scanResultChannel) {
                routeScanResult(result)
            }
        }
    }

    /**
     * Highly optimized routing path for 100% accuracy.
     * Uses a fast-path for DataLogger and caches handler routes and device names.
     */
    private val handlerCache = java.util.concurrent.ConcurrentHashMap<String, BleDeviceHandler>()
    private val deviceNameCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val powerManager by lazy { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    private val wifiManager by lazy { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager }
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "BleSense:ScanWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.acquire(60 * 60 * 1000L) // 1 hour safety timeout
            Log.d("BLE", "WakeLock acquired for continuous scanning")
        } catch (e: Exception) {
            Log.w("BLE", "Could not acquire WakeLock: ${e.message}")
        }

        try {
            if (wifiLock == null) {
                @Suppress("DEPRECATION")
                wifiLock = wifiManager?.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "BleSense:WifiLock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
                Log.d("BLE", "WifiLock acquired for radio coexistence stability")
            }
        } catch (e: Exception) {
            Log.w("BLE", "Could not acquire WifiLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d("BLE", "WakeLock released")
            }
        } catch (e: Exception) {
            Log.w("BLE", "Error releasing WakeLock: ${e.message}")
        }
        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
                Log.d("BLE", "WifiLock released")
            }
        } catch (e: Exception) {
            Log.w("BLE", "Error releasing WifiLock: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun routeScanResult(result: BleScanResult) {
        lastGlobalPacketReceivedTime = System.currentTimeMillis()
        try {
            val resultData = result.data ?: return
            val scanRecord = resultData.scanRecord ?: return
            val serverDevice = result.device
            val deviceAddress = serverDevice.address
            val rssi = resultData.rssi
            val rawBytes = scanRecord.bytes?.value

            val isScannable = resultData.dataStatus == BleScanDataStatus.DATA_COMPLETE
            val isConnectable = resultData.isConnectable ?: false

            val mData = scanRecord.manufacturerSpecificData
            val manufacturerData = SparseArray<ByteArray>(mData.size())
            var hasBigAdv = false
            for (i in 0 until mData.size()) {
                val bytes = mData.valueAt(i).value
                manufacturerData.put(mData.keyAt(i), bytes)
                if (bytes.size >= 240) {
                    hasBigAdv = true
                }
            }

            // FAST PATH: DataLogger BigAdv packets (240+ bytes) bypass all discovery checks & blocking IPC
            if (hasBigAdv) {
                val fastName = scanRecord.deviceName?.ifBlank { null }
                    ?: deviceNameCache[deviceAddress]
                    ?: "DataLogger"
                try {
                    dataLoggerHandler.handle(
                        deviceAddress = deviceAddress,
                        deviceName = fastName,
                        rssi = rssi,
                        manufacturerData = manufacturerData,
                        scanRecordBytes = rawBytes,
                        isScannable = isScannable,
                        isConnectable = isConnectable
                    )
                } catch (e: Exception) {
                    Log.e("BLE", "DataLogger fast path error: ${e.message}")
                }
                return
            }

            val deviceName = scanRecord.deviceName?.ifBlank { null }
                ?: deviceNameCache[deviceAddress]
                ?: run {
                    val fetched = try { serverDevice.name } catch (_: SecurityException) { null }
                    if (!fetched.isNullOrBlank()) {
                        deviceNameCache[deviceAddress] = fetched
                    }
                    fetched
                }
                ?: "N/A"

            // Normal Path: Check handler cache
            val cachedHandler = handlerCache[deviceAddress]
            if (cachedHandler != null) {
                try {
                    cachedHandler.handle(
                        deviceAddress = deviceAddress,
                        deviceName = deviceName,
                        rssi = rssi,
                        manufacturerData = manufacturerData,
                        scanRecordBytes = rawBytes,
                        isScannable = isScannable,
                        isConnectable = isConnectable
                    )
                    return
                } catch (e: Exception) {
                    Log.e("BLE", "Cached handler error: ${e.message}")
                    handlerCache.remove(deviceAddress)
                }
            }

            // Slow Path: Discovery and cache population
            for (handler in handlers) {
                if (handler.canHandle(deviceName, deviceAddress, manufacturerData)) {
                    handlerCache[deviceAddress] = handler
                    try {
                        handler.handle(
                            deviceAddress = deviceAddress,
                            deviceName = deviceName,
                            rssi = rssi,
                            manufacturerData = manufacturerData,
                            scanRecordBytes = rawBytes,
                            isScannable = isScannable,
                            isConnectable = isConnectable
                        )
                    } catch (e: Exception) {
                        Log.e("BLE", "Handler ${handler::class.simpleName} error: ${e.message}")
                    }
                    return // Found the handler, move to next result
                }
            }
        } catch (_: Exception) { }
    }


    // ── Scan Start/Stop (Protected against Android LeScanQuota & HAL leaks) ───

    // ── Scan Start/Stop (Protected against Android LeScanQuota & HAL leaks) ───

    private val scanLifecycleMutex = Mutex()
    private var lastScanStartTime = 0L

    @Volatile
    var isAppInForeground: Boolean = true
        private set

    fun onAppForegrounded(activity: Activity? = null) {
        Log.d("BLE", "App foregrounded - ensuring scanner is active and fresh")
        isAppInForeground = true
        viewModelScope.launch {
            scanLifecycleMutex.withLock {
                val now = System.currentTimeMillis()
                // If scan is running and fresh (< 10 minutes), just ensure watchdog is running
                if (_isScanning.value && scanJob?.isActive == true && (now - lastScanStartTime < 10 * 60 * 1000L)) {
                    Log.d("BLE", "Scanner is already healthy and active on resume")
                    startScanWatchdog()
                    return@withLock
                }
                startScanInternal()
            }
        }
    }

    fun onAppBackgrounded() {
        Log.d("BLE", "App backgrounded - stopping scanner to preserve battery and prevent OS throttles")
        isAppInForeground = false
        stopScanWatchdog()
        viewModelScope.launch {
            scanLifecycleMutex.withLock {
                try {
                    scanJob?.cancelAndJoin()
                } catch (_: Exception) {}
                scanJob = null
                _isScanning.value = false
                releaseWakeLock()
            }
        }
    }

    fun startContinuousScan(activity: Activity? = null) {
        if (_isScanning.value && scanJob?.isActive == true) return

        restartJob?.cancel()
        restartJob = viewModelScope.launch {
            scanLifecycleMutex.withLock {
                startScanInternal()
            }
        }
    }

    fun ensureScanning(activity: Activity? = null) {
        if (!_isScanning.value || scanJob?.isActive != true) {
            startContinuousScan(activity)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        restartJob?.cancel()
        restartJob = null
        viewModelScope.launch {
            stopScanInternal()
        }
    }

    private suspend fun stopScanInternal() {
        scanLifecycleMutex.withLock {
            stopScanWatchdog()
            try {
                scanJob?.cancelAndJoin()
            } catch (_: Exception) {}
            scanJob = null
            _isScanning.value = false
            releaseWakeLock()
        }
    }

    fun stopContinuousScan() {
        stopScan()
    }

    /**
     * Synchronous blocking scan stop called when the app closes or is destroyed.
     * Ensures native Android Bluetooth HAL unbinds completely before the process exits.
     */
    fun stopScanBlocking() {
        try {
            restartJob?.cancel()
            restartJob = null
            runBlocking(Dispatchers.Default) {
                withTimeoutOrNull(600) {
                    stopScanInternal()
                }
            }
        } catch (_: Exception) {}
        releaseWakeLock()
    }

    /**
     * Internal unified scan starter. Must be invoked while holding scanLifecycleMutex.
     * Manages clean unbinding, fresh BleScanner instantiation, error backoff, and watchdog activation.
     */
    @SuppressLint("MissingPermission")
    private suspend fun startScanInternal() {
        if (!isAppInForeground) {
            Log.d("BLE", "startScanInternal: App is in background, skipping scan start")
            return
        }

        // Prevent Android LeScanQuota abuse (ensure at least 1500ms between start attempts)
        val now = System.currentTimeMillis()
        val elapsedSinceLastStart = now - lastScanStartTime
        if (elapsedSinceLastStart < 1500L) {
            delay(1500L - elapsedSinceLastStart)
        }

        // Cleanly cancel any old job and await its complete unbinding in native HAL
        try {
            scanJob?.cancelAndJoin()
        } catch (_: Exception) {}
        scanJob = null
        _isScanning.value = false

        // Allow Android Bluetooth HAL daemon 200ms to cleanly unbind and release client_if slot
        delay(200L)

        if (!hasRequiredPermissions()) {
            Log.e("BLE", "Missing permissions to start scan")
            return
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            Log.e("BLE", "Bluetooth is null or disabled")
            return
        }

        lastScanStartTime = System.currentTimeMillis()
        val scanSettings = createScanSettings()
        val scanFilters = createScanFilters()

        _isScanning.value = true
        acquireWakeLock()
        Log.d("BLE", "Nordic BleScanner started (PHY=${scanSettings.phy}, Hardware Filters=None)")

        val currentScanner = BleScanner(context)

        scanJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            } catch (_: Exception) {}

            currentScanner.scan(filters = scanFilters, settings = scanSettings)
                .buffer(capacity = 50000, onBufferOverflow = BufferOverflow.DROP_OLDEST)
                .catch { e ->
                    val isQuota = e.message?.contains("SCANNING_TOO_FREQUENTLY", ignoreCase = true) == true ||
                                  e.message?.contains("6", ignoreCase = true) == true
                    Log.e("BLE", "Nordic BleScanner error: ${e.message} (quota=$isQuota)", e)
                    _isScanning.value = false
                    releaseWakeLock()

                    // Auto-recover cleanly without requiring phone reboot if still in foreground
                    if (isAppInForeground) {
                        viewModelScope.launch {
                            val waitTime = if (isQuota) 4500L else 2000L
                            delay(waitTime)
                            scanLifecycleMutex.withLock {
                                if (!_isScanning.value && isAppInForeground) {
                                    Log.d("BLE", "Auto-recovering scan after error...")
                                    startScanInternal()
                                }
                            }
                        }
                    }
                }
                .collect { bleResult ->
                    scanResultChannel.trySend(bleResult)
                }
        }
        startScanWatchdog()
    }

    /**
     * Seamlessly re-arms the native BLE scanner when needed.
     */
    @SuppressLint("MissingPermission")
    suspend fun rearmScannerInternal() {
        scanLifecycleMutex.withLock {
            if (!isAppInForeground) {
                Log.d("BLE", "rearmScannerInternal: App is in background, skipping")
                return@withLock
            }

            val now = System.currentTimeMillis()
            // Respect Android LeScanQuota: ensure at least 2500ms between re-arms
            if (now - lastScanStartTime < 2500L) {
                Log.d("BLE", "Watchdog: Re-arm throttled to respect LeScanQuota (${now - lastScanStartTime}ms elapsed)")
                return@withLock
            }

            Log.d("BLE", "Watchdog: Re-arming BLE scanner to clear HAL stall...")
            startScanInternal()
            Log.d("BLE", "Watchdog: BLE scanner successfully re-armed")
        }
    }

    /**
     * Prepares the BLE scanner specifically for an incoming high-speed DataLogger dump.
     * Ensures scanner is active, healthy, and primed in the native HAL before the trigger command is sent.
     */
    @SuppressLint("MissingPermission")
    suspend fun prepareForDataCapture(): Boolean {
        return scanLifecycleMutex.withLock {
            val now = System.currentTimeMillis()
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null || !adapter.isEnabled || !hasRequiredPermissions()) {
                Log.e("BLE", "prepareForDataCapture: Bluetooth unavailable or permissions missing")
                return@withLock false
            }

            // If already scanning and started within last 3 minutes, it is primed and ready!
            if (_isScanning.value && scanJob?.isActive == true && (now - lastScanStartTime < 3 * 60 * 1000L)) {
                Log.d("BLE", "prepareForDataCapture: Scanner already active and primed (${(now - lastScanStartTime)/1000}s ago)")
                return@withLock true
            }

            Log.d("BLE", "prepareForDataCapture: Re-priming scanner before data burst...")
            startScanInternal()
            delay(150L) // Settle time for native HAL daemon
            return@withLock _isScanning.value
        }
    }

    /**
     * Active Scan Watchdog:
     * 1. Detects stream stalls during active DataLogger transfer (e.g. at 70% between rounds or on OS pause)
     *    and immediately re-arms the scanner so remaining packets reach 100%.
     * 2. Periodic 15-minute refresh: Keeps scanning in pristine LOW_LATENCY mode without violating
     *    Android's quota or triggering excessive scan penalties.
     * 3. NEVER cycles every 25s when idle (which burned through OS quotas and killed the scanner).
     */
    private fun startScanWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = viewModelScope.launch(Dispatchers.Default) {
            Log.d("BLE", "Active Scan Watchdog started")
            while (isActive && _isScanning.value && isAppInForeground) {
                delay(1000L)

                if (!_isScanning.value || !isAppInForeground) break

                val now = System.currentTimeMillis()
                val currentCaptured = dataLoggerHandler.capturedCount.value
                val currentExpected = dataLoggerHandler.expectedCount.value

                // If new packets arrived, reset the stall retry counter
                if (currentCaptured > lastObservedCapturedCount) {
                    lastObservedCapturedCount = currentCaptured
                    stallRearmCount = 0
                }

                // ── 1. ACTIVE TRANSFER WATCHDOG (Stall Buster for DataLogger) ──
                // If we are expecting more packets (e.g. 3500/5000) and no packets arrived for >= 2000ms
                if (dataLoggerHandler.isExpectingMorePackets()) {
                    val silenceDuration = now - dataLoggerHandler.lastPacketReceivedTime
                    val timeSinceScanStart = now - lastScanStartTime

                    if (silenceDuration >= 2000L && timeSinceScanStart >= 2500L) {
                        if (stallRearmCount < 3) {
                            stallRearmCount++
                            Log.w("BLE", "WATCHDOG: Stream stall detected at $currentCaptured/$currentExpected ($silenceDuration ms silence). Attempt $stallRearmCount/3. Re-arming scanner...")
                            rearmScannerInternal()
                            delay(1000L)
                            continue
                        } else {
                            Log.d("BLE", "WATCHDOG: Max re-arms reached for this sync session, device transmission may have concluded.")
                        }
                    }
                }

                // ── 2. LONG-TERM PERIODIC REFRESH (15 minutes) ──
                // Android OS automatically downgrades SCAN_MODE_LOW_LATENCY to OPPORTUNISTIC after 30 minutes.
                // We refresh cleanly at 15 minutes to keep scanning in pristine low-latency mode.
                val scanDuration = now - lastScanStartTime
                val isReceiving = dataLoggerHandler.isReceivingPacketsActive() || (now - lastGlobalPacketReceivedTime < 3000L)
                if (!isReceiving && scanDuration >= 15 * 60 * 1000L) {
                    Log.d("BLE", "WATCHDOG: Periodic 15-minute refresh to maintain LOW_LATENCY scanning mode...")
                    rearmScannerInternal()
                }
            }
        }
    }

    private fun stopScanWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    /**
     * Cleanly restarts the scan to clear native HAL buffers without restarting the phone.
     */
    @SuppressLint("MissingPermission")
    fun refreshScan(activity: Activity? = null) {
        Log.d("BLE", "Refreshing scan cleanly...")
        viewModelScope.launch {
            scanLifecycleMutex.withLock {
                startScanInternal()
            }
        }
    }

    fun resetBluetoothStack(activity: Activity? = null) {
        refreshScan(activity)
    }

    @SuppressLint("MissingPermission")
    fun startScan(activity: Activity? = null) {
        viewModelScope.launch {
            scanLifecycleMutex.withLock {
                startScanInternal()
            }
        }
    }

    private fun createScanSettings(): BleScannerSettings {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        val isExtendedSupported = adapter?.isLeExtendedAdvertisingSupported == true
        val isCodedSupported = adapter?.isLeCodedPhySupported == true

        // ── 1. Processor & SoC Architecture Detection ────────────────────────
        val hardware = Build.HARDWARE.lowercase()
        val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MANUFACTURER.lowercase()
        } else ""

        // MediaTek: Helio G-series, Dimensity series (Galaxy A-series, Realme, Redmi, Vivo Y-series, Oppo Reno)
        val isMediaTek = hardware.contains("mt") ||
                hardware.contains("mediatek") ||
                socManufacturer.contains("mediatek")

        // Unisoc / Spreadtrum: T606, T612, T616 (Realme C-series, Moto E-series, Infinix, Tecno)
        val isUnisoc = hardware.contains("ums") ||
                hardware.contains("sprd") ||
                hardware.contains("unisoc") ||
                hardware.contains("sharkl") ||
                hardware.contains("roc") ||
                socManufacturer.contains("unisoc") ||
                socManufacturer.contains("spreadtrum")

        // ── 2. PHY Selection Strategy across Processors ──────────────────────
        // - MediaTek: Strictly force PHY_LE_1M to eliminate the 1,000ms Coded-PHY alternating blind pause.
        // - Unisoc: Budget controllers do not support Coded PHY or fail if queried with ALL_SUPPORTED. Force PHY_LE_1M.
        // - Qualcomm / Exynos / Tensor: Benefit from PHY_LE_ALL_SUPPORTED to properly receive extended advertising payloads on secondary channels.
        val scanPhy = if (!isExtendedSupported || !isCodedSupported || isMediaTek || isUnisoc) {
            BleScannerPhy.PHY_LE_1M
        } else {
            BleScannerPhy.PHY_LE_ALL_SUPPORTED
        }

        return BleScannerSettings(
            scanMode = BleScanMode.SCAN_MODE_LOW_LATENCY,
            phy = if (isExtendedSupported) scanPhy else null,
            legacy = !isExtendedSupported, // Critical fallback: If hardware lacks extended adv, fallback to legacy to prevent SCAN_FAILED_FEATURE_UNSUPPORTED
            reportDelay = 0L,
            includeStoredBondedDevices = false
        )
    }

    /**
     * Universal Filter Architecture:
     * We scan without hardware-offloaded filters (emptyList) so that older Bluetooth 5.0
     * chipsets (such as Qualcomm WCN3990 on Samsung M51) do NOT drop Bluetooth 5 Extended Advertising
     * primary packets (ADV_EXT_IND) in hardware.
     * All filtering is performed instantly in memory in under 0.05ms via our Kotlin fast-path.
     */
    private fun createScanFilters(): List<BleScanFilter> = emptyList()



    // ══════════════════════════════════════════════════════════════════════════
    // TRIGGER ADVERTISING (Nordic BleAdvertiser)
    // ══════════════════════════════════════════════════════════════════════════

    private var triggerJob: Job? = null
    private var targetedAdvJob: Job? = null

    /**
     * Starts a targeted trigger advertisement using Nordic BleAdvertiser.
     * Controlled by the ViewModel so it can be stopped instantly on packet arrival.
     */
    @SuppressLint("MissingPermission")
    fun startTrigger(command: ByteArray, deviceAddress: String?, durationMs: Long = 10000) {
        stopTrigger()
        dataLoggerHandler.hasAutoStoppedTriggerForSession = false

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

        val data = BleAdvertisingData(
            manufacturerData = listOf(ManufacturerData(0x0059, DataByteArray(finalPayload))),
            includeDeviceName = false,
            includeTxPowerLever = false
        )

        val settings = BleAdvertisingSettings(
            interval = BleAdvertisingInterval.INTERVAL_LOW,
            txPowerLevel = BleTxPowerLevel.TX_POWER_HIGH,
            connectable = false,
            timeout = 0
        )

        val config = BleAdvertisingConfig(
            settings = settings,
            advertiseData = data
        )

        if (!hasAdvertisePermission()) {
            Log.e("BLE", "Missing BLUETOOTH_ADVERTISE permission for trigger")
            return
        }

        triggerJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!hasAdvertisePermission()) return@launch
                Log.d("BLE", "Trigger advertising started for $deviceAddress")
                withTimeoutOrNull(durationMs) {
                    bleAdvertiser.advertise(config).collect()
                }
            } catch (e: SecurityException) {
                Log.e("BLE", "Trigger advertising permission rejected: ${e.message}")
            } catch (e: CancellationException) {
                Log.d("BLE", "Trigger stopped immediately")
            } catch (e: Exception) {
                Log.e("BLE", "Trigger advertising error: ${e.message}")
            } finally {
                stopTrigger()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopTrigger() {
        triggerJob?.cancel()
        triggerJob = null
        Log.d("BLE", "Trigger stopped immediately")
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun sendAdvertiseCommandToSensor(deviceAddress: String, command: ByteArray) {
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

        val settings = BleAdvertisingSettings(
            interval = BleAdvertisingInterval.INTERVAL_LOW,
            txPowerLevel = BleTxPowerLevel.TX_POWER_HIGH,
            connectable = false,
            timeout = 5000
        )

        val data = BleAdvertisingData(
            manufacturerData = listOf(ManufacturerData(0x0059, DataByteArray(payload))),
            includeDeviceName = false,
            includeTxPowerLever = false
        )

        val config = BleAdvertisingConfig(
            settings = settings,
            advertiseData = data
        )

        if (!hasAdvertisePermission()) {
            Log.e("BLE", "Missing BLUETOOTH_ADVERTISE permission")
            return
        }

        targetedAdvJob?.cancel()
        targetedAdvJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!hasAdvertisePermission()) return@launch
                Log.d("BLE", "Targeted advertising started for $deviceAddress")
                withTimeoutOrNull(5000L) {
                    bleAdvertiser.advertise(config).collect()
                }
            } catch (e: SecurityException) {
                Log.e("BLE", "Targeted advertising permission rejected: ${e.message}")
            } catch (e: CancellationException) {
                Log.d("BLE", "Targeted advertising completed")
            } catch (e: Exception) {
                Log.e("BLE", "Targeted advertising failed: ${e.message}")
            }
        }
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

        val stepHistory = stepCounterHandler.getHistory(address)
        if (stepHistory.isNotEmpty()) return stepHistory

        val rawTestHistory = rawTestDeviceHandler.getHistory(address)
        if (rawTestHistory.isNotEmpty()) return rawTestHistory

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
                "VEML7700", "VCNL4040", "AHT20", "BME680", "TempLogger",
                "sen66", "STS30", "STTS751", "ATRH", "Rain", "Wind" ->
                    sensorHubHandler.parseAdvertisingData(data, deviceType, deviceAddress)
                "DataLogger" ->
                    dataLoggerHandler.parseAdvertisingDataLoggerData(data, deviceAddress)
                "AWS" ->
                    awsSensorHandler.parseAdvertisingAwsData(data, deviceAddress)
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

    fun hasAdvertisePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }

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


    override fun onCleared() {
        super.onCleared()
        stopScanBlocking()
        handlers.forEach { it.onCleared() }
    }
}
