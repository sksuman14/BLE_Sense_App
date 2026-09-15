package com.blesense.app.viewmodel.handler

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.util.Log
import android.util.SparseArray
import com.blesense.app.api.RetrofitClient
import com.blesense.app.api.SensorPacket
import com.blesense.app.model.BluetoothDeviceModel
import com.blesense.app.model.HistoricalDataEntry
import com.blesense.app.model.SensorData
import com.blesense.app.util.DeviceIdentifier
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.util.BitSet

/**
 * Isolated handler for DataLogger devices.
 */
class DataLoggerHandler(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val sharedSensorDataStream: MutableSharedFlow<SensorData>
) : BleDeviceHandler {

    companion object {
        private const val TAG = "DataLogger"
    }

    // ── Device List ──────────────────────────────────────────────────────────
    private val _devices = MutableStateFlow<List<BluetoothDeviceModel>>(emptyList())
    override val devices: StateFlow<List<BluetoothDeviceModel>> = _devices.asStateFlow()

    override val sensorDataStream: MutableSharedFlow<SensorData> = sharedSensorDataStream

    // ── Selected DataLogger ──────────────────────────────────────────────────
    private val _selectedDataLoggerDeviceId = MutableStateFlow<String?>(null)
    val selectedDataLoggerDeviceId: StateFlow<String?> = _selectedDataLoggerDeviceId.asStateFlow()

    private val _selectedDataLoggerAddress = MutableStateFlow<String?>(null)
    val selectedDataLoggerAddress: StateFlow<String?> = _selectedDataLoggerAddress.asStateFlow()

    private var cachedSelectedIdDigits: Int? = null

    fun setSelectedDataLogger(deviceId: String?, address: String?) {
        _selectedDataLoggerDeviceId.value = if (deviceId.isNullOrBlank()) null else deviceId
        _selectedDataLoggerAddress.value = if (address.isNullOrBlank()) null else address
        cachedSelectedIdDigits = deviceId?.filter { it.isDigit() }?.toIntOrNull()
        Log.d(TAG, "Selected DataLogger: ID=$deviceId, Address=$address")
    }

    // ── Packet History & Streams ─────────────────────────────────────────────
    val dataLoggerPacketHistory = MutableStateFlow<Map<String, List<SensorData.DataLoggerData>>>(emptyMap())

    private val _latestDataLoggerPacket = MutableSharedFlow<SensorData.DataLoggerData?>(
        extraBufferCapacity = 20000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val latestDataLoggerPacket: SharedFlow<SensorData.DataLoggerData?> = _latestDataLoggerPacket.asSharedFlow()

    private val _latestPacketId = MutableStateFlow(-1)
    val latestPacketId: StateFlow<Int> = _latestPacketId.asStateFlow()

    // ── History Update Trigger ───────────────────────────────────────────────
    private val _historyUpdateTrigger = MutableStateFlow(0L)
    override val historyUpdateTrigger: StateFlow<Long> = _historyUpdateTrigger.asStateFlow()

    // ── Upload State ─────────────────────────────────────────────────────────
    private val _isUploadingToDashboard = MutableStateFlow(false)
    val isUploadingToDashboard: StateFlow<Boolean> = _isUploadingToDashboard.asStateFlow()

    // ── Batch Flusher & Throttling ──────────────────────────────────────────
    private val pendingDataLoggerPackets = java.util.concurrent.ConcurrentLinkedQueue<SensorData.DataLoggerData>()
    private val pendingDeviceUpdates = java.util.concurrent.ConcurrentLinkedQueue<Pair<BluetoothDeviceModel, HistoricalDataEntry>>()
    private var batchFlusherJob: Job? = null
    private var lastHistoryUpdateTime = 0L
    private var lastDeviceListUpdateTime = 0L

    // ── Bundle Tracking State ───────────────────────────────────────────────
    private val deviceBundleTracker = java.util.concurrent.ConcurrentHashMap<String, BundleTracker>()

    private val _capturedCount = MutableStateFlow(0)
    val capturedCount: StateFlow<Int> = _capturedCount.asStateFlow()

    private val _expectedCount = MutableStateFlow(0)
    val expectedCount: StateFlow<Int> = _expectedCount.asStateFlow()

    private val _r1Count = MutableStateFlow(0)
    val r1Count: StateFlow<Int> = _r1Count.asStateFlow()

    private val _r2Count = MutableStateFlow(0)
    val r2Count: StateFlow<Int> = _r2Count.asStateFlow()

    private val _r3Count = MutableStateFlow(0)
    val r3Count: StateFlow<Int> = _r3Count.asStateFlow()

    private val deviceBitSets = java.util.concurrent.ConcurrentHashMap<String, BitSet>()

    private class BundleTracker {
        var currentBundleId: Int = -1
        var currentRound: Int = 0
        var lastBundleIdReceiveTime: Long = 0L
        var firstBundleFirstPacketId: Int = -1
        val receivedPacketIds = BitSet(20000)
        val r1PacketIds = BitSet(20000)
        val r2PacketIds = BitSet(20000)
        val r3PacketIds = BitSet(20000)
    }


    // ── Deduplication State ──────────────────────────────────────────────────
    private val deviceDumpBaseTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val deviceUniqueIdBitSets = java.util.concurrent.ConcurrentHashMap<String, BitSet>()

    // ── Trigger State ────────────────────────────────────────────────────────
    var hasAutoStoppedTriggerForSession = false

    // ── History ──────────────────────────────────────────────────────────────
    private val deviceHistoricalData = HashMap<String, ArrayDeque<HistoricalDataEntry>>()

    // ── App ID ───────────────────────────────────────────────────────────────
    private val appId: String by lazy { DeviceIdentifier.getOrGenerateId(context) }

    init {
        startBatchFlusher()
    }

    private fun startBatchFlusher() {
        batchFlusherJob?.cancel()
        batchFlusherJob = coroutineScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(100)
                flushPendingDataLoggerPackets()
                flushPendingDeviceUpdates()
            }
        }
    }

    private fun flushPendingDeviceUpdates() {
        val now = System.currentTimeMillis()
        if (now - lastDeviceListUpdateTime < 200 && pendingDeviceUpdates.isNotEmpty()) return
        
        val updates = mutableListOf<Pair<BluetoothDeviceModel, HistoricalDataEntry>>()
        while(true) {
            updates.add(pendingDeviceUpdates.poll() ?: break)
        }
        if (updates.isEmpty()) return

        lastDeviceListUpdateTime = now
        
        // Apply historical data updates
        updates.forEach { (device, entry) ->
            ScanResultUtils.addHistoryEntry(deviceHistoricalData, device.address, entry)
        }

        // Apply device list update once for the latest of each device
        updates.reversed().distinctBy { it.first.address }.forEach { (device, _) ->
            ScanResultUtils.updateDeviceInList(_devices, device)
        }
        
        // Trigger UI update AFTER history has been populated
        _historyUpdateTrigger.value = now
    }

    fun flushPendingDataLoggerPackets() {
        if (pendingDataLoggerPackets.isEmpty()) return
        val drained = ArrayList<SensorData.DataLoggerData>()
        while (true) {
            val item = pendingDataLoggerPackets.poll() ?: break
            drained.add(item)
        }
        if (drained.isEmpty()) return

        val now = System.currentTimeMillis()

        // Update stats incrementally
        val sample = drained.lastOrNull()
        if (sample != null) {
            val dAddr = sample.deviceAddress
            val tracker = deviceBundleTracker[dAddr]
            if (tracker != null) {
                synchronized(tracker) {
                    _capturedCount.value = tracker.receivedPacketIds.cardinality()
                    _r1Count.value = tracker.r1PacketIds.cardinality()
                    _r2Count.value = tracker.r2PacketIds.cardinality()
                    _r3Count.value = tracker.r3PacketIds.cardinality()
                    
                    val totalId = sample.currentPacketId
                    val firstPacketId = tracker.firstBundleFirstPacketId
                    val firstBundleId = if (firstPacketId != -1) firstPacketId - ((firstPacketId - 1) % 6) else -1
                    
                    if (totalId > 0 && firstBundleId > 0) {
                        _expectedCount.value = (totalId - firstBundleId + 1).coerceAtLeast(0)
                    }
                }
            }
        }

        // Update History Map
        dataLoggerPacketHistory.update { currentMap ->
            val mutableMap = currentMap.toMutableMap()
            drained.groupBy { it.deviceId }.forEach { (id, packets) ->
                val existing = mutableMap[id] ?: emptyList()
                val newList = ArrayList<SensorData.DataLoggerData>(existing.size + packets.size)
                newList.addAll(existing)
                newList.addAll(packets)
                mutableMap[id] = newList
            }
            mutableMap
        }

        val lastItem = drained.lastOrNull()
        if (lastItem != null) {
            _latestDataLoggerPacket.tryEmit(lastItem)
        }
    }


    fun getHistory(address: String): List<HistoricalDataEntry> {
        return synchronized(deviceHistoricalData) {
            deviceHistoricalData[address]?.toList() ?: emptyList()
        }
    }

    // ── canHandle ────────────────────────────────────────────────────────────

    override fun canHandle(
        deviceName: String?,
        deviceAddress: String,
        manufacturerData: SparseArray<ByteArray>
    ): Boolean {
        // 1. Explicitly selected address always allowed
        if (deviceAddress.equals(_selectedDataLoggerAddress.value, ignoreCase = true)) return true

        // 2. Known DataLogger name patterns
        val isDataLoggerName = deviceName?.contains("DataLogger", ignoreCase = true) == true ||
                               deviceName?.contains("DLOG", ignoreCase = true) == true
        if (isDataLoggerName) return true

        // 3. For raw packet scanning based on size, we require the device to have a known name
        // or name pattern. Nameless devices ("N/A") often represent random BLE noise from
        // other systems and should be ignored unless explicitly selected.
        val hasValidName = !deviceName.isNullOrBlank() && deviceName != "N/A"

        for (i in 0 until manufacturerData.size()) {
            val data = manufacturerData.valueAt(i) ?: continue
            // BigAdv packets are typically 246+ bytes.
            if (data.size >= 240) {
                return hasValidName
            }
        }
        return false
    }

    // ── handle ───────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    override suspend fun handle(result: ScanResult) {
        try {
            val deviceAddress = result.device.address ?: return
            val scanRecord = result.scanRecord ?: return
            val manufacturerData = scanRecord.manufacturerSpecificData ?: return
            val deviceName = scanRecord.deviceName ?: "N/A"

            var sensorData: SensorData? = null

            if (manufacturerData.size() > 0) {
                for (i in 0 until manufacturerData.size()) {
                    val data = manufacturerData.valueAt(i) ?: continue
                    if (data.size >= 240) {
                        val parsed = parseDataLoggerData(data, deviceAddress)
                        if (parsed != null) sensorData = parsed
                    }
                }
            }

            val entry = HistoricalDataEntry(
                timestamp = System.currentTimeMillis(),
                sensorData = sensorData,
                rawData = scanRecord.bytes
            )

            val bluetoothDevice = BluetoothDeviceModel(
                name = deviceName,
                address = deviceAddress,
                rssi = result.rssi.toString(),
                deviceId = sensorData?.deviceId ?: "Unknown",
                sensorData = sensorData,
                scanRecordBytes = scanRecord.bytes,
                isScannable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    (result.dataStatus and ScanResult.DATA_COMPLETE) == ScanResult.DATA_COMPLETE else false,
                isConnectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    result.isConnectable else false
            )

            if (sensorData != null) {
                sharedSensorDataStream.tryEmit(sensorData)
            }

            pendingDeviceUpdates.add(Pair(bluetoothDevice, entry))
        } catch (e: Exception) {
            Log.e(TAG, "Error processing DataLogger scan: ${e.message}")
        }
    }

    // ── DataLogger Parsing ───────────────────────────────────────────────────

    private fun parseDataLoggerData(data: ByteArray?, deviceAddress: String): SensorData.DataLoggerData? {
        if (data == null || data.size < 246) return null

        val now = System.currentTimeMillis()
        val packetSize = 246
        var lastParsedPacket: SensorData.DataLoggerData? = null

        for (offset in 0 until data.size step packetSize) {
            if (offset + packetSize > data.size) break

            // Node ID is at byte 2
            val nodeId = data[offset + 2].toInt() and 0xFF
            val selDigits = cachedSelectedIdDigits
            val selectedAddr = _selectedDataLoggerAddress.value

            val isMacMatch = !selectedAddr.isNullOrBlank() && deviceAddress.equals(selectedAddr, ignoreCase = true)
            val isIdMatch = selDigits == null || nodeId == selDigits

            if (!isMacMatch && !isIdMatch) continue
            
            // Decode packet ID (Mfg0-mfg1)
            val packetId = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
            val totalPacketsCount = (data[offset + 243].toInt() and 0xFF) or ((data[offset + 244].toInt() and 0xFF) shl 8)
            val bundleId = if (packetId > 0) packetId - ((packetId - 1) % 6) else 0

            val tracker = deviceBundleTracker.getOrPut(deviceAddress) { BundleTracker() }

            val currentRound = synchronized(tracker) {
                if (tracker.currentBundleId != bundleId) {
                    tracker.currentBundleId = bundleId
                    tracker.currentRound = 1
                } else {
                    if (now - tracker.lastBundleIdReceiveTime > 150) {
                        tracker.currentRound = (tracker.currentRound + 1).coerceAtMost(3)
                    }
                }
                tracker.lastBundleIdReceiveTime = now
                if (tracker.firstBundleFirstPacketId == -1 && packetId > 0) {
                    tracker.firstBundleFirstPacketId = packetId
                }
                when (tracker.currentRound) {
                    1 -> tracker.r1PacketIds.set(packetId)
                    2 -> if (!tracker.r1PacketIds.get(packetId)) tracker.r2PacketIds.set(packetId)
                    3 -> if (!tracker.r1PacketIds.get(packetId) && !tracker.r2PacketIds.get(packetId)) tracker.r3PacketIds.set(packetId)
                }
                tracker.currentRound
            }

            val dId = if (isMacMatch && !_selectedDataLoggerDeviceId.value.isNullOrBlank()) _selectedDataLoggerDeviceId.value!! else nodeId.toString()

            val isNewPacket = synchronized(tracker) {
                if (!tracker.receivedPacketIds.get(packetId)) {
                    tracker.receivedPacketIds.set(packetId)
                    true
                } else false
            }

            if (isNewPacket) {
                val baseTime = deviceDumpBaseTimes.getOrPut(deviceAddress) { now }
                val packetTimestamp = baseTime - (totalPacketsCount - packetId) * 8000L

                val singlePacket = SensorData.DataLoggerData(
                    deviceId = dId,
                    currentPacketId = totalPacketsCount,
                    lastPacketId = packetId,
                    timestamp = packetTimestamp,
                    rawData = data.copyOfRange(offset, offset + packetSize),
                    round = currentRound,
                    nodeId = nodeId,
                    bundleId = bundleId,
                    deviceAddress = deviceAddress
                )

                pendingDataLoggerPackets.add(singlePacket)
                _latestDataLoggerPacket.tryEmit(singlePacket)
                lastParsedPacket = singlePacket
            }
        }
        return lastParsedPacket
    }


    // ── Public API ───────────────────────────────────────────────────────────

    fun clearDataLoggerHistory(deviceId: String) {
        dataLoggerPacketHistory.update { it.toMutableMap().apply { remove(deviceId) } }
        _capturedCount.value = 0
        _expectedCount.value = 0
        _r1Count.value = 0
        _r2Count.value = 0
        _r3Count.value = 0
        // Clean up by deviceId and also clear global base times to avoid session collision
        deviceDumpBaseTimes.clear()
        deviceBitSets.clear()
        deviceUniqueIdBitSets.clear()
        synchronized(deviceBundleTracker) {
            deviceBundleTracker.clear()
        }
    }

    fun clearAllDataLoggerHistory() {
        _devices.value = emptyList()
        dataLoggerPacketHistory.value = emptyMap()
        _capturedCount.value = 0
        _expectedCount.value = 0
        _r1Count.value = 0
        _r2Count.value = 0
        _r3Count.value = 0
        deviceBundleTracker.clear()
        deviceBitSets.clear()
        deviceUniqueIdBitSets.clear()
        deviceDumpBaseTimes.clear()
        synchronized(deviceHistoricalData) {
            deviceHistoricalData.clear()
        }
    }

    fun uploadCapturedDataToDashboard(deviceId: String, address: String) {
        val history = dataLoggerPacketHistory.value[deviceId] ?: return
        if (history.isEmpty()) return

        coroutineScope.launch(Dispatchers.IO) {
            try {
                _isUploadingToDashboard.value = true

                val allPackets = history.map { sensorData ->
                    val points = sensorData.getParsedPoints().map {
                        mapOf("x" to it.first, "y" to it.second, "z" to it.third)
                    }
                    val dataMap = mapOf(
                        "type" to "DataLogger",
                        "appId" to appId,
                        "packetId" to sensorData.lastPacketId,
                        "totalPackets" to sensorData.currentPacketId,
                        "points" to points,
                        "deviceId" to sensorData.deviceId,
                        "rawData" to sensorData.rawDataHex
                    )
                    SensorPacket(sensorData.timestamp, dataMap)
                }

                allPackets.chunked(50).forEach { batch ->
                    try {
                        val renderRes = RetrofitClient.renderInstance.sendSensorDataBatch(batch)
                        val cfRes = RetrofitClient.cloudflareInstance.sendSensorDataBatch(batch)
                        if (!renderRes.isSuccessful) {
                            Log.e(TAG, "Render upload failed HTTP ${renderRes.code()}: ${renderRes.errorBody()?.string()}")
                        }
                        if (!cfRes.isSuccessful) {
                            Log.e(TAG, "Cloudflare upload failed HTTP ${cfRes.code()}: ${cfRes.errorBody()?.string()}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Batch upload failed: ${e.message}", e)
                    }
                }
            } finally {
                _isUploadingToDashboard.value = false
            }
        }
    }

    fun parseAdvertisingDataLoggerData(data: ByteArray, deviceAddress: String): SensorData.DataLoggerData? {
        return parseDataLoggerData(data, deviceAddress)
    }

    override fun onCleared() {
        batchFlusherJob?.cancel()
        _devices.value = emptyList()
        synchronized(deviceHistoricalData) {
            deviceHistoricalData.clear()
        }
        deviceBundleTracker.clear()
        deviceBitSets.clear()
        deviceUniqueIdBitSets.clear()
        deviceDumpBaseTimes.clear()
    }
}
