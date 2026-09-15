package com.blesense.app.viewmodel.handler

import android.bluetooth.le.ScanResult
import android.util.SparseArray
import com.blesense.app.model.BluetoothDeviceModel
import com.blesense.app.model.SensorData
import kotlinx.coroutines.flow.StateFlow

/**
 * Common interface for isolated BLE device handlers.
 *
 * Each handler owns its own parsing, state, and error boundary.
 * A bug or crash in one handler cannot affect the others because
 * the orchestrator wraps each [handle] call in a try/catch.
 */
interface BleDeviceHandler {

    /**
     * Return `true` if this handler wants to process the given scan result.
     * Called on every incoming BLE advertisement.
     *
     * @param deviceName  The advertised device name (may be null).
     * @param deviceAddress  The BLE MAC address.
     * @param manufacturerData  Manufacturer-specific data from the scan record.
     */
    fun canHandle(
        deviceName: String?,
        deviceAddress: String,
        manufacturerData: SparseArray<ByteArray>
    ): Boolean

    /**
     * Process a BLE scan result that this handler has claimed via [canHandle].
     * Called on a background dispatcher. Must be safe to call concurrently.
     */
    suspend fun handle(result: ScanResult)

    /**
     * The list of devices discovered and managed by this handler.
     * The orchestrator merges all handler device flows into one unified flow.
     */
    val devices: StateFlow<List<BluetoothDeviceModel>>

    /**
     * A flow that emits a timestamp whenever the history of a device is updated.
     * Screens use this to know when to refresh their historical data lists.
     */
    val historyUpdateTrigger: StateFlow<Long>

    /**
     * The shared sensor data stream emitter.
     * Handlers emit parsed [SensorData] into this for cross-cutting observers.
     */
    val sensorDataStream: kotlinx.coroutines.flow.MutableSharedFlow<SensorData>?
        get() = null

    /**
     * Clean up resources (cancel jobs, clear caches, etc.).
     * Called when the parent ViewModel is cleared.
     */
    fun onCleared()
}
