package com.blesense.app.viewmodel.handler

import com.blesense.app.model.BluetoothDeviceModel
import com.blesense.app.model.HistoricalDataEntry
import com.blesense.app.model.SensorData
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Shared utility functions used by all device handlers.
 * Extracted to avoid code duplication across SensorHub, DataLogger, and AWS handlers.
 */
object ScanResultUtils {

    /**
     * Thread-safe update of a device list StateFlow.
     * If a device with the same address already exists, it is updated in-place;
     * otherwise the new device is appended.
     */
    fun updateDeviceInList(
        devicesFlow: MutableStateFlow<List<BluetoothDeviceModel>>,
        newDevice: BluetoothDeviceModel
    ) {
        devicesFlow.value = devicesFlow.value.let { devices ->
            val idx = devices.indexOfFirst {
                it.address.equals(newDevice.address, ignoreCase = true)
            }
            if (idx >= 0) {
                val existing = devices[idx]
                devices.toMutableList().apply {
                    set(idx, newDevice.copy(
                        name = if (newDevice.name == "N/A" && existing.name != "N/A") existing.name else newDevice.name,
                        sensorData = newDevice.sensorData ?: existing.sensorData,
                        scanRecordBytes = newDevice.scanRecordBytes ?: existing.scanRecordBytes,
                        lastSeen = System.currentTimeMillis()
                    ))
                }
            } else {
                devices + newDevice.copy(lastSeen = System.currentTimeMillis())
            }
        }
    }

    /**
     * Add a historical data entry for a device, respecting a max-entries cap
     * per device address. Uses ArrayDeque internally for O(1) removals.
     */
    fun addHistoryEntry(
        historyMap: HashMap<String, ArrayDeque<HistoricalDataEntry>>,
        logicalAddress: String,
        entry: HistoricalDataEntry,
        maxEntries: Int = 20000
    ) {
        synchronized(historyMap) {
            val deque = historyMap.getOrPut(logicalAddress) { ArrayDeque() }
            deque.addLast(entry)
            if (deque.size > maxEntries) {
                deque.removeFirst()
            }
        }
    }

    /**
     * Determine the logical address for a device.
     * Devices that share a MAC (like multiple AWS pods on the same MAC or
     * multiple SHT40 devices) get a composite "MAC_ID" address.
     */
    fun buildLogicalAddress(
        deviceAddress: String,
        awsMac: String,
        deviceType: String,
        scanRecordDeviceName: String?,
        sensorData: SensorData?
    ): String {
        return if (deviceAddress.equals(awsMac, ignoreCase = true)) {
            if (scanRecordDeviceName != null) {
                "${deviceAddress}_${scanRecordDeviceName}"
            } else {
                val dId = sensorData?.deviceId ?: "Unknown"
                "${deviceAddress}_$dId"
            }
        } else {
            deviceAddress
        }
    }
}
