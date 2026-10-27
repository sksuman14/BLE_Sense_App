package com.blesense.app.viewmodel.handler

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.os.Build
import android.util.Log
import android.util.SparseArray
import com.blesense.app.model.BluetoothDeviceModel
import com.blesense.app.model.HistoricalDataEntry
import com.blesense.app.model.SensorData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Isolated handler for raw test devices.
 * Used exclusively to inspect raw payloads and integer byte conversions in Raw Scanner.
 * Never displays in regular sensor screens.
 */
class RawTestDeviceHandler(
    private val coroutineScope: CoroutineScope
) : BleDeviceHandler {

    companion object {
        private const val TAG = "RawTestDevice"
        const val DEFAULT_TEST_ADDRESS = "TE:ST:00:00:00:01"
        const val DEFAULT_TEST_NAME = "test"
    }

    // Default sample BLE advertisement for instant payload inspection.
    // - Flags (3B): 02 01 06
    // - Complete Local Name "test" (6B): 05 09 74 65 73 74
    // - Manufacturer Data Type 0xFF (15B): 0E FF 59 00 12 34 56 78 9A BC DE F0 11 22 33
    private val defaultSampleBytes = byteArrayOf(
        0x02, 0x01, 0x06,
        0x05, 0x09, 0x74, 0x65, 0x73, 0x74,
        0x0E.toByte(), 0xFF.toByte(), 0x59, 0x00, 0x12, 0x34, 0x56, 0x78,
        0x9A.toByte(), 0xBC.toByte(), 0xDE.toByte(), 0xF0.toByte(), 0x11, 0x22, 0x33
    )

    private val defaultTestDevice = BluetoothDeviceModel(
        name = DEFAULT_TEST_NAME,
        rssi = "-45",
        address = DEFAULT_TEST_ADDRESS,
        deviceId = "test",
        sensorData = null,
        scanRecordBytes = defaultSampleBytes,
        isScannable = true,
        isConnectable = false,
        lastSeen = System.currentTimeMillis()
    )

    private val _devices = MutableStateFlow<List<BluetoothDeviceModel>>(listOf(defaultTestDevice))
    override val devices: StateFlow<List<BluetoothDeviceModel>> = _devices.asStateFlow()

    private val _historyUpdateTrigger = MutableStateFlow(0L)
    override val historyUpdateTrigger: StateFlow<Long> = _historyUpdateTrigger.asStateFlow()

    override val sensorDataStream: MutableSharedFlow<SensorData>? = null

    private val deviceHistoricalData = HashMap<String, ArrayDeque<HistoricalDataEntry>>().apply {
        put(DEFAULT_TEST_ADDRESS, ArrayDeque<HistoricalDataEntry>().apply {
            add(HistoricalDataEntry(System.currentTimeMillis(), null, defaultSampleBytes))
        })
    }

    private val pendingDeviceUpdates = java.util.concurrent.ConcurrentLinkedQueue<Pair<BluetoothDeviceModel, HistoricalDataEntry>>()
    private var flusherJob: Job? = null
    private var hasDiscoveredLiveDevice = false

    init {
        startFlusher()
    }

    private fun startFlusher() {
        flusherJob?.cancel()
        flusherJob = coroutineScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(200)
                flushUpdates()
            }
        }
    }

    private fun flushUpdates() {
        val now = System.currentTimeMillis()
        if (pendingDeviceUpdates.isEmpty()) return

        val updates = mutableListOf<Pair<BluetoothDeviceModel, HistoricalDataEntry>>()
        while (true) {
            updates.add(pendingDeviceUpdates.poll() ?: break)
        }
        if (updates.isEmpty()) return

        updates.forEach { (device, entry) ->
            ScanResultUtils.addHistoryEntry(deviceHistoricalData, device.address, entry)
        }

        updates.reversed().distinctBy { it.first.address }.forEach { (device, _) ->
            if (device.address != DEFAULT_TEST_ADDRESS) {
                _devices.value = _devices.value.filter { it.address != DEFAULT_TEST_ADDRESS }
            }
            ScanResultUtils.updateDeviceInList(_devices, device)
        }

        _historyUpdateTrigger.value = now
    }

    fun getHistory(address: String): List<HistoricalDataEntry> {
        val decoded = try { android.net.Uri.decode(address) } catch (_: Exception) { address }
        return synchronized(deviceHistoricalData) {
            val list = deviceHistoricalData[address]
                ?: deviceHistoricalData[decoded]
                ?: if (address.contains("TEST", ignoreCase = true) || decoded.contains("TEST", ignoreCase = true)) {
                    deviceHistoricalData[DEFAULT_TEST_ADDRESS]
                } else null
            list?.toList() ?: emptyList()
        }
    }

    override fun canHandle(
        deviceName: String?,
        deviceAddress: String,
        manufacturerData: SparseArray<ByteArray>
    ): Boolean {
        if (deviceAddress.equals(DEFAULT_TEST_ADDRESS, ignoreCase = true)) return true

        val name = deviceName ?: ""
        return name.equals("test", ignoreCase = true) ||
                (name.contains("test", ignoreCase = true) &&
                 !name.contains("Activity", ignoreCase = true) &&
                 !name.contains("Step", ignoreCase = true) &&
                 !name.contains("DataLogger", ignoreCase = true))
    }

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
            val rawBytes = scanRecordBytes ?: ByteArray(0)
            val name = deviceName ?: DEFAULT_TEST_NAME

            val entry = HistoricalDataEntry(
                timestamp = System.currentTimeMillis(),
                sensorData = null,
                rawData = rawBytes
            )

            val bluetoothDevice = BluetoothDeviceModel(
                name = name,
                address = deviceAddress,
                rssi = rssi.toString(),
                deviceId = "test",
                sensorData = null,
                scanRecordBytes = rawBytes,
                isScannable = isScannable,
                isConnectable = isConnectable
            )

            pendingDeviceUpdates.add(Pair(bluetoothDevice, entry))
        } catch (e: Exception) {
            Log.e(TAG, "Error processing test device scan result: ${e.message}")
        }
    }


    override fun onCleared() {
        flusherJob?.cancel()
        clearAllData()
    }

    fun clearAllData() {
        hasDiscoveredLiveDevice = false
        _devices.value = listOf(defaultTestDevice)
        synchronized(deviceHistoricalData) {
            deviceHistoricalData.clear()
            deviceHistoricalData[DEFAULT_TEST_ADDRESS] = ArrayDeque<HistoricalDataEntry>().apply {
                add(HistoricalDataEntry(System.currentTimeMillis(), null, defaultSampleBytes))
            }
        }
        pendingDeviceUpdates.clear()
        _historyUpdateTrigger.value = System.currentTimeMillis()
    }
}
