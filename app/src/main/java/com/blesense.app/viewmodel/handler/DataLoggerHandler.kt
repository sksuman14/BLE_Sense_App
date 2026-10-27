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

    // ── Per-Device Stats Isolation ───────────────────────────────────────────
    data class DeviceSyncStats(
        var capturedCount: Int = 0,
        var expectedCount: Int = 0,
        var r1Count: Int = 0,
        var r2Count: Int = 0,
        var r3Count: Int = 0
    )

    private val deviceStatsMap = java.util.concurrent.ConcurrentHashMap<String, DeviceSyncStats>()

    fun setSelectedDataLogger(deviceId: String?, address: String?) {
        _selectedDataLoggerDeviceId.value = if (deviceId.isNullOrBlank()) null else deviceId
        val normAddr = address?.trim()?.uppercase()
        _selectedDataLoggerAddress.value = if (normAddr.isNullOrBlank()) null else normAddr
        cachedSelectedIdDigits = deviceId?.filter { it.isDigit() }?.toIntOrNull()

        // Immediately bind UI StateFlow counters to the stats of the selected device
        if (normAddr != null) {
            val stats = deviceStatsMap[normAddr]
            if (stats != null) {
                _capturedCount.value = stats.capturedCount
                _expectedCount.value = stats.expectedCount
                _r1Count.value = stats.r1Count
                _r2Count.value = stats.r2Count
                _r3Count.value = stats.r3Count
            } else {
                _capturedCount.value = 0
                _expectedCount.value = 0
                _r1Count.value = 0
                _r2Count.value = 0
                _r3Count.value = 0
            }
        } else {
            _capturedCount.value = 0
            _expectedCount.value = 0
            _r1Count.value = 0
            _r2Count.value = 0
            _r3Count.value = 0
        }
        Log.d(TAG, "Selected DataLogger: ID=$deviceId, Address=$normAddr")
    }

    // ── Packet Storage & History ─────────────────────────────────────────────
    // Zero-allocation thread-safe internal packet storage
    private val devicePacketStorage = java.util.concurrent.ConcurrentHashMap<String, MutableList<SensorData.DataLoggerData>>()

    // Lightweight UI Manifest StateFlow — contains ONLY the latest 15 packets for the selected device
    private val _recentManifestPackets = MutableStateFlow<List<SensorData.DataLoggerData>>(emptyList())
    val recentManifestPackets: StateFlow<List<SensorData.DataLoggerData>> = _recentManifestPackets.asStateFlow()

    val dataLoggerPacketHistory = MutableStateFlow<Map<String, List<SensorData.DataLoggerData>>>(emptyMap())

    private val _latestDataLoggerPacket = MutableSharedFlow<SensorData.DataLoggerData?>(
        extraBufferCapacity = 50000,
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
        val receivedPacketIds = BitSet(65536)
        val r1PacketIds = BitSet(65536)
        val r2PacketIds = BitSet(65536)
        val r3PacketIds = BitSet(65536)
    }


    // ── Deduplication State ──────────────────────────────────────────────────
    private val deviceDumpBaseTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val deviceUniqueIdBitSets = java.util.concurrent.ConcurrentHashMap<String, BitSet>()

    @Volatile
    var hasAutoStoppedTriggerForSession = false
    var onFirstPacketReceived: (() -> Unit)? = null

    // Tracks activity to prevent mid-stream radio restart during high-volume blasts
    @Volatile
    var lastPacketReceivedTime: Long = 0L
        private set

    fun isReceivingPacketsActive(): Boolean {
        val elapsed = System.currentTimeMillis() - lastPacketReceivedTime
        return elapsed in 0..3000L
    }

    fun isExpectingMorePackets(): Boolean {
        val selectedAddr = _selectedDataLoggerAddress.value
        if (selectedAddr != null) {
            val stats = deviceStatsMap[selectedAddr]
            if (stats != null) {
                return stats.expectedCount > 0 && stats.capturedCount > 0 && stats.capturedCount < stats.expectedCount
            }
        }
        val exp = _expectedCount.value
        val cap = _capturedCount.value
        return exp > 0 && cap > 0 && cap < exp
    }

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

        // Update stats incrementally in batch (every 100ms) without locking up the packet receiver
        val selectedAddr = _selectedDataLoggerAddress.value
        val distinctAddresses = drained.map { it.deviceAddress.trim().uppercase() }.distinct()
        for (dAddr in distinctAddresses) {
            val tracker = deviceBundleTracker[dAddr]
            val devStats = deviceStatsMap[dAddr]
            if (tracker != null && devStats != null) {
                synchronized(tracker) {
                    devStats.capturedCount = tracker.receivedPacketIds.cardinality()
                    devStats.r1Count = tracker.r1PacketIds.cardinality()
                    devStats.r2Count = tracker.r2PacketIds.cardinality()
                    devStats.r3Count = tracker.r3PacketIds.cardinality()
                    
                    val lastPacket = drained.lastOrNull { it.deviceAddress.equals(dAddr, ignoreCase = true) }
                    val totalId = lastPacket?.currentPacketId ?: devStats.expectedCount
                    if (totalId > 0) {
                        val firstId = tracker.firstBundleFirstPacketId
                        val calculatedExpected = if (firstId > 0 && totalId >= firstId) {
                            totalId - firstId + 1
                        } else {
                            totalId
                        }
                        devStats.expectedCount = maxOf(calculatedExpected, devStats.capturedCount)
                    }

                    if (selectedAddr == null || dAddr.equals(selectedAddr, ignoreCase = true)) {
                        _capturedCount.value = devStats.capturedCount
                        _r1Count.value = devStats.r1Count
                        _r2Count.value = devStats.r2Count
                        _r3Count.value = devStats.r3Count
                        _expectedCount.value = devStats.expectedCount
                    }
                }
            }
        }

        // 1. In-place append to devicePacketStorage — ZERO array copies, ZERO GC allocations!
        drained.groupBy { it.deviceId }.forEach { (id, packets) ->
            val list = devicePacketStorage.getOrPut(id) { java.util.Collections.synchronizedList(ArrayList<SensorData.DataLoggerData>(15000)) }
            synchronized(list) {
                list.addAll(packets)
                if (list.size > 50000) {
                    val excess = list.size - 50000
                    list.subList(0, excess).clear()
                }
            }
        }

        // 2. Update recentManifestPackets with up to 150 items so all packets can be viewed
        val selectedId = _selectedDataLoggerDeviceId.value
        val targetList = if (selectedId != null) devicePacketStorage[selectedId] else devicePacketStorage.values.firstOrNull()
        if (targetList != null) {
            val manifestPackets = synchronized(targetList) {
                targetList.takeLast(150).toList()
            }
            _recentManifestPackets.value = manifestPackets
        }

        // 3. Throttle full dataLoggerPacketHistory updates so Compose doesn't re-render 5,000 items 10 times a second
        // Emit full map once every 1.5 seconds, or when drained queue is empty (end of burst)
        if (now - lastHistoryUpdateTime >= 1500 || pendingDataLoggerPackets.isEmpty()) {
            lastHistoryUpdateTime = now
            val snapshot = HashMap<String, List<SensorData.DataLoggerData>>(devicePacketStorage.size)
            devicePacketStorage.forEach { (id, list) ->
                snapshot[id] = synchronized(list) { ArrayList(list) }
            }
            dataLoggerPacketHistory.value = snapshot
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
        // Never handle Step Counter
        if (deviceName?.contains("Step", ignoreCase = true) == true) return false

        // 1. Explicitly selected address always allowed
        if (deviceAddress.equals(_selectedDataLoggerAddress.value, ignoreCase = true)) return true

        // 2. Known DataLogger name patterns
        val isDataLoggerName = deviceName?.contains("DataLogger", ignoreCase = true) == true ||
                               deviceName?.contains("DLOG", ignoreCase = true) == true
        if (isDataLoggerName) return true

        // 3. For BigAdv packets (240+ bytes):
        // Allow if no specific address selected, or if byte 2 matches selected node ID, or if device name matches
        for (i in 0 until manufacturerData.size()) {
            val data = manufacturerData.valueAt(i) ?: continue
            if (data.size >= 240) {
                val selDigits = cachedSelectedIdDigits
                if (selDigits != null) {
                    val nodeId = data.getOrNull(2)?.toInt()?.and(0xFF)
                    if (nodeId == selDigits) return true
                }
                return true
            }
        }
        return false
    }

    // ── handle ───────────────────────────────────────────────────────────────

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

            if (sensorData != null) {
                sharedSensorDataStream.tryEmit(sensorData)
            }

            // High-throughput burst protection:
            // Avoid creating 1,200 heavy HistoricalDataEntry and BluetoothDeviceModel objects in 2 seconds,
            // which triggers ART Garbage Collection (GC) pauses that drop incoming HCI UART frames.
            val now = System.currentTimeMillis()
            if (sensorData == null || now - lastDeviceListUpdateTime > 500) {
                lastDeviceListUpdateTime = now
                val entry = HistoricalDataEntry(
                    timestamp = now,
                    sensorData = sensorData,
                    rawData = scanRecordBytes
                )

                val bluetoothDevice = BluetoothDeviceModel(
                    name = deviceName ?: "N/A",
                    address = deviceAddress,
                    rssi = rssi.toString(),
                    deviceId = sensorData?.deviceId ?: "Unknown",
                    sensorData = sensorData,
                    scanRecordBytes = scanRecordBytes,
                    isScannable = isScannable,
                    isConnectable = isConnectable
                )

                pendingDeviceUpdates.add(Pair(bluetoothDevice, entry))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing DataLogger scan: ${e.message}")
        }
    }

    // ── DataLogger Parsing ───────────────────────────────────────────────────

    private fun parseDataLoggerData(data: ByteArray?, deviceAddress: String): SensorData.DataLoggerData? {
        if (data == null || data.size < 246) return null

        val normDeviceAddr = deviceAddress.trim().uppercase()
        val selectedAddr = _selectedDataLoggerAddress.value

        // STRICT DEVICE ISOLATION:
        // When a user is inside a specific DataLogger screen (selectedAddr != null),
        // completely ignore packets from all other devices so they cannot cross-contaminate counters or UI.
        if (!selectedAddr.isNullOrBlank() && !normDeviceAddr.equals(selectedAddr, ignoreCase = true)) {
            return null
        }

        val now = System.currentTimeMillis()
        lastPacketReceivedTime = now
        val packetSize = 246
        var lastParsedPacket: SensorData.DataLoggerData? = null

        for (offset in 0 until data.size step packetSize) {
            if (offset + packetSize > data.size) break

            // Node ID is at byte 2
            val nodeId = data[offset + 2].toInt() and 0xFF

            // Auto-stop continuous trigger as soon as first packet arrives to free up radio
            if (!hasAutoStoppedTriggerForSession) {
                hasAutoStoppedTriggerForSession = true
                onFirstPacketReceived?.invoke()
            }
            
            // Decode packet ID (Mfg0-mfg1)
            val packetId = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
            val totalPacketsCount = (data[offset + 243].toInt() and 0xFF) or ((data[offset + 244].toInt() and 0xFF) shl 8)
            val hwRound = data[offset + 245].toInt() and 0xFF
            val bundleId = if (packetId > 0) packetId - ((packetId - 1) % 6) else 0

            val tracker = deviceBundleTracker.getOrPut(normDeviceAddr) { BundleTracker() }
            val devStats = deviceStatsMap.getOrPut(normDeviceAddr) { DeviceSyncStats() }

            var isNewPacket = false
            val currentRound = synchronized(tracker) {
                val round = if (hwRound in 1..5) {
                    hwRound
                } else {
                    if (tracker.currentBundleId != bundleId) {
                        tracker.currentBundleId = bundleId
                        tracker.currentRound = 1
                    } else {
                        if (now - tracker.lastBundleIdReceiveTime > 150) {
                            tracker.currentRound = (tracker.currentRound + 1).coerceAtMost(3)
                        }
                    }
                    tracker.currentRound
                }
                tracker.lastBundleIdReceiveTime = now
                if (tracker.firstBundleFirstPacketId == -1 || (packetId > 0 && packetId < tracker.firstBundleFirstPacketId)) {
                    tracker.firstBundleFirstPacketId = packetId
                }
                when (round) {
                    1 -> tracker.r1PacketIds.set(packetId)
                    2 -> if (!tracker.r1PacketIds.get(packetId)) tracker.r2PacketIds.set(packetId)
                    3 -> if (!tracker.r1PacketIds.get(packetId) && !tracker.r2PacketIds.get(packetId)) tracker.r3PacketIds.set(packetId)
                    else -> tracker.r3PacketIds.set(packetId)
                }
                if (!tracker.receivedPacketIds.get(packetId)) {
                    tracker.receivedPacketIds.set(packetId)
                    devStats.capturedCount++
                    isNewPacket = true
                }
                if (totalPacketsCount > 0) {
                    val firstId = tracker.firstBundleFirstPacketId
                    val calculatedExpected = if (firstId > 0 && totalPacketsCount >= firstId) {
                        totalPacketsCount - firstId + 1
                    } else {
                        totalPacketsCount
                    }
                    devStats.expectedCount = maxOf(calculatedExpected, devStats.capturedCount)
                }
                round
            }

            val dId = if (!selectedAddr.isNullOrBlank() && normDeviceAddr.equals(selectedAddr, ignoreCase = true) && !_selectedDataLoggerDeviceId.value.isNullOrBlank()) {
                _selectedDataLoggerDeviceId.value!!
            } else {
                _devices.value.find { it.address.equals(normDeviceAddr, ignoreCase = true) }?.deviceId ?: nodeId.toString()
            }

            if (isNewPacket) {
                val baseTime = deviceDumpBaseTimes.getOrPut(normDeviceAddr) { now }
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
                    deviceAddress = normDeviceAddr
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
        devicePacketStorage.remove(deviceId)
        _recentManifestPackets.value = emptyList()
        dataLoggerPacketHistory.update { it.toMutableMap().apply { remove(deviceId) } }

        val matchedAddr = _devices.value.find { it.deviceId == deviceId }?.address?.trim()?.uppercase()
            ?: _selectedDataLoggerAddress.value

        if (matchedAddr != null) {
            deviceStatsMap.remove(matchedAddr)
            deviceBundleTracker.remove(matchedAddr)
            deviceDumpBaseTimes.remove(matchedAddr)
            deviceBitSets.remove(matchedAddr)
            deviceUniqueIdBitSets.remove(matchedAddr)
        }

        if (_selectedDataLoggerDeviceId.value == deviceId || _selectedDataLoggerAddress.value == matchedAddr) {
            _capturedCount.value = 0
            _expectedCount.value = 0
            _r1Count.value = 0
            _r2Count.value = 0
            _r3Count.value = 0
        }
        lastPacketReceivedTime = 0L
        hasAutoStoppedTriggerForSession = false
    }

    fun clearAllDataLoggerHistory() {
        _devices.value = emptyList()
        devicePacketStorage.clear()
        _recentManifestPackets.value = emptyList()
        dataLoggerPacketHistory.value = emptyMap()
        _capturedCount.value = 0
        _expectedCount.value = 0
        _r1Count.value = 0
        _r2Count.value = 0
        _r3Count.value = 0
        lastPacketReceivedTime = 0L
        hasAutoStoppedTriggerForSession = false
        deviceStatsMap.clear()
        deviceBundleTracker.clear()
        deviceBitSets.clear()
        deviceUniqueIdBitSets.clear()
        deviceDumpBaseTimes.clear()
        synchronized(deviceHistoricalData) {
            deviceHistoricalData.clear()
        }
    }

    fun uploadCapturedDataToDashboard(deviceId: String, address: String) {
        val history = devicePacketStorage[deviceId]?.let { synchronized(it) { ArrayList(it) } }
            ?: dataLoggerPacketHistory.value[deviceId]
            ?: return
        if (history.isEmpty()) return

        coroutineScope.launch(Dispatchers.IO) {
            try {
                _isUploadingToDashboard.value = true

                history.asSequence().chunked(50).forEach { chunk ->
                    val batch = chunk.map { sensorData ->
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
