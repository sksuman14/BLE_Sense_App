package com.blesense.app.viewmodel.handler

import android.bluetooth.le.ScanResult
import android.os.Build
import android.util.Log
import android.util.SparseArray
import com.blesense.app.model.BluetoothDeviceModel
import com.blesense.app.model.HistoricalDataEntry
import com.blesense.app.model.SensorData
import com.blesense.app.model.pedometer.StepCounter
import com.blesense.app.model.pedometer.StepResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Isolated handler for Step Counter (LIS3DH Pedometer) devices.
 *
 * Dedicated strictly to devices named "Step Counter" / "StepCounter" / "Step_Counter".
 * Step Counter devices are never handled or shown in Sensor Hub, DataLogger, or AWS sections.
 */
class StepCounterHandler(
    private val coroutineScope: CoroutineScope,
    private val sharedSensorDataStream: MutableSharedFlow<SensorData>
) : BleDeviceHandler {

    companion object {
        private const val TAG = "StepCounterHandler"
    }

    // ── Device List State ────────────────────────────────────────────────────
    private val _devices = MutableStateFlow<List<BluetoothDeviceModel>>(emptyList())
    override val devices: StateFlow<List<BluetoothDeviceModel>> = _devices.asStateFlow()

    private val _historyUpdateTrigger = MutableStateFlow(0L)
    override val historyUpdateTrigger: StateFlow<Long> = _historyUpdateTrigger.asStateFlow()

    override val sensorDataStream: MutableSharedFlow<SensorData> = sharedSensorDataStream

    // ── Dedicated Step Counter Engines ───────────────────────────────────────
    private val stepCounters = ConcurrentHashMap<String, StepCounter>()

    private val _selectedDeviceAddress = MutableStateFlow<String?>(null)
    val selectedDeviceAddress: StateFlow<String?> = _selectedDeviceAddress.asStateFlow()

    private val _latestStepResult = MutableStateFlow<StepResult?>(null)
    val latestStepResult: StateFlow<StepResult?> = _latestStepResult.asStateFlow()

    // ── History & Batching ───────────────────────────────────────────────────
    private val deviceHistoricalData = HashMap<String, ArrayDeque<HistoricalDataEntry>>()
    private val pendingDeviceUpdates = ConcurrentLinkedQueue<Pair<BluetoothDeviceModel, HistoricalDataEntry>>()
    private var flusherJob: kotlinx.coroutines.Job? = null
    private var lastListUpdateTime = 0L

    init {
        startFlusher()
    }

    private fun startFlusher() {
        flusherJob?.cancel()
        flusherJob = coroutineScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(50)
                flushUpdates()
            }
        }
    }

    private fun flushUpdates() {
        val now = System.currentTimeMillis()
        if (now - lastListUpdateTime < 100 && pendingDeviceUpdates.isNotEmpty()) return

        val updates = mutableListOf<Pair<BluetoothDeviceModel, HistoricalDataEntry>>()
        while (true) {
            updates.add(pendingDeviceUpdates.poll() ?: break)
        }
        if (updates.isEmpty()) return
        lastListUpdateTime = now

        updates.forEach { (device, entry) ->
            ScanResultUtils.addHistoryEntry(deviceHistoricalData, device.address, entry)
        }
        updates.reversed().distinctBy { it.first.address }.forEach { (device, _) ->
            ScanResultUtils.updateDeviceInList(_devices, device)
        }

        _historyUpdateTrigger.value = now
    }

    // ── Device Recognition: STRICT FILTER ─────────────────────────────────────
    override fun canHandle(
        deviceName: String?,
        deviceAddress: String,
        manufacturerData: SparseArray<ByteArray>
    ): Boolean {
        if (deviceName.isNullOrBlank()) return false
        val clean = deviceName.trim().lowercase(Locale.ROOT)
        return clean.contains("step counter") ||
                clean.contains("stepcounter") ||
                clean.contains("step_counter") ||
                clean.contains("step-counter") ||
                clean.startsWith("step")
    }

    // ── BLE Scan Result Processing ───────────────────────────────────────────
    override suspend fun handle(
        deviceAddress: String,
        deviceName: String?,
        rssi: Int,
        manufacturerData: SparseArray<ByteArray>,
        scanRecordBytes: ByteArray?,
        isScannable: Boolean,
        isConnectable: Boolean
    ) {
        try {
            val upperAddress = deviceAddress.uppercase(Locale.ROOT)
            val devName = deviceName ?: "Step Counter"

            var nodeId = "0"
            var xVal = 0f
            var yVal = 0f
            var zVal = 0f
            var foundPayload = false

            // Parse LIS3DH raw bytes from manufacturerSpecificData
            if (manufacturerData.size() > 0) {
                for (i in 0 until manufacturerData.size()) {
                    val data = manufacturerData.valueAt(i) ?: continue
                    val parsed = parseLis3dhPayload(data)
                    if (parsed != null) {
                        nodeId = parsed.nodeId
                        xVal = parsed.x
                        yVal = parsed.y
                        zVal = parsed.z
                        foundPayload = true
                        break
                    }
                }
            }

            // Fallback: check raw scanRecord bytes if needed
            if (!foundPayload && scanRecordBytes != null) {
                val parsed = extractLis3dhFromRawBytes(scanRecordBytes)
                if (parsed != null) {
                    nodeId = parsed.nodeId
                    xVal = parsed.x
                    yVal = parsed.y
                    zVal = parsed.z
                    foundPayload = true
                }
            }

            if (!foundPayload) return

            // Get or create dedicated StepCounter engine for this device
            val engine = stepCounters.getOrPut(upperAddress) {
                StepCounter()
            }

            val timestamp = System.currentTimeMillis()
            val stepResult = engine.addSample(timestamp, xVal, yVal, zVal)

            // Auto-select first device if none selected
            val selected = _selectedDeviceAddress.value
            if (selected == null || selected.equals(upperAddress, ignoreCase = true)) {
                _selectedDeviceAddress.value = upperAddress
                _latestStepResult.value = stepResult
            }

            val stepData = SensorData.StepCounterData(
                deviceId = nodeId,
                totalSteps = stepResult.totalSteps,
                isWalking = stepResult.isWalking,
                cadenceSpm = stepResult.cadenceSpm,
                distanceMeters = stepResult.distanceMeters,
                caloriesKcal = stepResult.caloriesKcal,
                x = xVal,
                y = yVal,
                z = zVal,
                magnitude = stepResult.currentMagnitude,
                motion = stepResult.currentMotion,
                dynamicThreshold = stepResult.dynamicThreshold,
                deviceAddress = upperAddress,
                timestamp = timestamp
            )

            sharedSensorDataStream.tryEmit(stepData)

            val entry = HistoricalDataEntry(
                timestamp = timestamp,
                sensorData = stepData,
                rawData = scanRecordBytes
            )

            val bluetoothDevice = BluetoothDeviceModel(
                name = devName,
                address = upperAddress,
                rssi = rssi.toString(),
                deviceId = nodeId,
                sensorData = stepData,
                scanRecordBytes = scanRecordBytes,
                isScannable = isScannable,
                isConnectable = isConnectable
            )

            pendingDeviceUpdates.add(Pair(bluetoothDevice, entry))

        } catch (e: Exception) {
            Log.e(TAG, "Error handling Step Counter scan result: ${e.message}", e)
        }
    }


    // ── Public Controls ──────────────────────────────────────────────────────

    fun selectDevice(address: String) {
        _selectedDeviceAddress.value = address
        _latestStepResult.value = stepCounters[address]?.latestResult
    }

    fun resetSteps(address: String? = null) {
        val targetAddr = address ?: _selectedDeviceAddress.value ?: stepCounters.keys().toList().firstOrNull()
        if (targetAddr != null) {
            val engine = stepCounters.computeIfAbsent(targetAddr) { StepCounter() }
            engine.reset()
            _latestStepResult.value = engine.latestResult
        } else {
            stepCounters.values.forEach { it.reset() }
            _latestStepResult.value = StepResult(
                totalSteps = 0,
                isWalking = false,
                cadenceSpm = 0,
                distanceMeters = 0f,
                caloriesKcal = 0f,
                currentMagnitude = 0f,
                currentMotion = 0f,
                dynamicThreshold = 0.45f,
                lastStepTimeMs = 0L,
                sampleTimestampMs = System.currentTimeMillis()
            )
        }
        val currentDevices = _devices.value
        if (currentDevices.isNotEmpty()) {
            _devices.value = currentDevices.map { dev ->
                val sData = dev.sensorData
                if (sData is SensorData.StepCounterData) {
                    dev.copy(sensorData = sData.copy(totalSteps = 0, cadenceSpm = 0, isWalking = false, distanceMeters = 0f, caloriesKcal = 0f))
                } else {
                    dev
                }
            }
        }
    }

    fun togglePause(address: String? = null): Boolean {
        val targetAddr = address ?: _selectedDeviceAddress.value ?: stepCounters.keys().toList().firstOrNull()
        val engine = if (targetAddr != null) {
            stepCounters.computeIfAbsent(targetAddr) { StepCounter() }
        } else {
            stepCounters.values.firstOrNull() ?: StepCounter().also { stepCounters["default"] = it }
        }
        engine.isPaused = !engine.isPaused
        return engine.isPaused
    }

    fun getHistory(address: String): List<HistoricalDataEntry> {
        return synchronized(deviceHistoricalData) {
            deviceHistoricalData[address]?.toList() ?: emptyList()
        }
    }

    override fun onCleared() {
        flusherJob?.cancel()
        clearAllData()
    }

    fun clearAllData() {
        _devices.value = emptyList()
        _latestStepResult.value = null
        _selectedDeviceAddress.value = null
        stepCounters.clear()
        synchronized(deviceHistoricalData) {
            deviceHistoricalData.clear()
        }
        pendingDeviceUpdates.clear()
    }

    // ── LIS3DH Payload Parsers ────────────────────────────────────────────────

    private data class ParsedLis3dh(val nodeId: String, val x: Float, val y: Float, val z: Float)

    /**
     * Parses standard BLE Sense LIS3DH 7-byte format:
     * [0] = Node ID
     * [1] = X integer part, [2] = X fractional part
     * [3] = Y integer part, [4] = Y fractional part
     * [5] = Z integer part, [6] = Z fractional part
     */
    private fun parseLis3dhPayload(data: ByteArray): ParsedLis3dh? {
        if (data.size < 7) return null
        try {
            val nodeId = data[0].toUByte().toString()
            val x = parseAxis(data[1], data[2])
            val y = parseAxis(data[3], data[4])
            val z = parseAxis(data[5], data[6])
            return ParsedLis3dh(nodeId, x, y, z)
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Converts signed integer byte and unsigned fractional byte to float.
     * Accurately reproduces the real accelerometer values recorded in sensor_data CSV:
     * E.g. int=-7, frac=24 -> -7.24 m/s²
     * E.g. int=3, frac=40 -> 3.40 m/s²
     */
    private fun parseAxis(intByte: Byte, fracByte: Byte): Float {
        val i = intByte.toInt()
        val f = fracByte.toUByte().toInt()
        val str = "$i.$f"
        val parsed = str.toFloatOrNull()
        return if (parsed != null) {
            if (i < 0 && parsed > 0) -parsed else parsed
        } else {
            val fracVal = f / 100.0f
            if (i < 0) i.toFloat() - fracVal else i.toFloat() + fracVal
        }
    }

    /**
     * Scans raw advertising bytes for LIS3DH manufacturer block if not directly
     * exposed via standard API.
     */
    private fun extractLis3dhFromRawBytes(bytes: ByteArray): ParsedLis3dh? {
        var idx = 0
        while (idx < bytes.size - 1) {
            val len = bytes[idx].toInt() and 0xFF
            if (len == 0 || idx + len >= bytes.size) break
            val type = bytes[idx + 1].toInt() and 0xFF
            // Type 0xFF = Manufacturer Specific Data
            if (type == 0xFF && len >= 9) {
                // bytes[idx + 2], bytes[idx + 3] are Company ID (e.g. 0x0059)
                // bytes[idx + 4] = NodeId
                val payload = bytes.copyOfRange(idx + 4, idx + 1 + len)
                return parseLis3dhPayload(payload)
            }
            idx += (len + 1)
        }
        return null
    }
}
