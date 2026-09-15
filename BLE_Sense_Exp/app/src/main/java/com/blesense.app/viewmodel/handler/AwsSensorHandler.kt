package com.blesense.app.viewmodel.handler

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
import java.util.Locale

/**
 * Isolated handler for AWS (Autonomous Weather Station) devices.
 *
 * Owns all AWS-specific logic:
 * - AWS data parsing with error code decoding
 * - AWS protection logic (skip packets without device name)
 * - MAC-based identification (DE:AD:BE:AF:BA:11)
 *
 * Changes to this file CANNOT affect SensorHub or DataLogger scanning.
 */
class AwsSensorHandler(
    private val coroutineScope: kotlinx.coroutines.CoroutineScope,
    private val sharedSensorDataStream: MutableSharedFlow<SensorData>
) : BleDeviceHandler {

    companion object {
        private const val TAG = "AwsSensor"
        const val AWS_MAC = "DE:AD:BE:AF:BA:11"
    }

    // ── Device List ──────────────────────────────────────────────────────────
    private val _devices = MutableStateFlow<List<BluetoothDeviceModel>>(emptyList())
    override val devices: StateFlow<List<BluetoothDeviceModel>> = _devices.asStateFlow()

    private val _historyUpdateTrigger = MutableStateFlow(0L)
    override val historyUpdateTrigger: StateFlow<Long> = _historyUpdateTrigger.asStateFlow()

    override val sensorDataStream: MutableSharedFlow<SensorData> = sharedSensorDataStream

    // ── History & Throttling ─────────────────────────────────────────────────
    private val deviceHistoricalData = HashMap<String, ArrayDeque<HistoricalDataEntry>>()
    private val pendingDeviceUpdates = java.util.concurrent.ConcurrentLinkedQueue<Pair<BluetoothDeviceModel, HistoricalDataEntry>>()
    private var flusherJob: kotlinx.coroutines.Job? = null
    private var lastListUpdateTime = 0L

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
        while(true) {
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
        return deviceAddress.equals(AWS_MAC, ignoreCase = true)
    }

    // ── handle ───────────────────────────────────────────────────────────────

    override suspend fun handle(result: ScanResult) {
        try {
            val device = result.device ?: return
            val scanRecord = result.scanRecord ?: return
            val deviceAddress = device.address ?: return
            val manufacturerData = scanRecord.manufacturerSpecificData ?: return
            val deviceName = scanRecord.deviceName ?: "N/A"

            var sensorData: SensorData? = null

            if (manufacturerData.size() > 0) {
                for (i in 0 until manufacturerData.size()) {
                    val data = manufacturerData.valueAt(i) ?: continue

                    // AWS PROTECTION LOGIC:
                    // If this is an AWS device, we ONLY update if the packet explicitly contained
                    // the name. This prevents data from multiple same-MAC units colliding.
                    if (scanRecord.deviceName == null) continue

                    val parsed = parseAWSData(data, deviceAddress)
                    if (parsed != null) {
                        sensorData = parsed
                        break
                    }
                }
            }

            // Only process if we got valid AWS data
            if (sensorData == null) return

            // Use logical address: MAC_Name for AWS (shared MAC)
            val logicalAddress = if (scanRecord.deviceName != null) {
                "${deviceAddress}_${scanRecord.deviceName}"
            } else {
                val dId = sensorData.deviceId
                "${deviceAddress}_$dId"
            }

            val entry = HistoricalDataEntry(
                timestamp = System.currentTimeMillis(),
                sensorData = sensorData,
                rawData = scanRecord.bytes
            )

            // Update device list
            val bluetoothDevice = BluetoothDeviceModel(
                name = deviceName,
                address = logicalAddress,
                rssi = result.rssi.toString(),
                deviceId = sensorData.deviceId,
                sensorData = sensorData,
                scanRecordBytes = scanRecord.bytes,
                isScannable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    (result.dataStatus and ScanResult.DATA_COMPLETE) == ScanResult.DATA_COMPLETE else false,
                isConnectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    result.isConnectable else false
            )

            sharedSensorDataStream.tryEmit(sensorData)
            pendingDeviceUpdates.add(Pair(bluetoothDevice, entry))
        } catch (e: Exception) {
            Log.e(TAG, "Error processing AWS scan: ${e.message}")
        }
    }

    // ── AWS Parsing ──────────────────────────────────────────────────────────

    private fun parseAWSData(data: ByteArray, deviceAddress: String): SensorData.AWSData? {
        if (data.size < 12) return null

        val rawDataString = data.joinToString(" ") { "%02X".format(it) }
        fun getInt16(index: Int): Int? {
            if (index + 1 >= data.size) return null
            return ((data[index].toInt() and 0xFF) shl 8) or (data[index + 1].toInt() and 0xFF)
        }
        val temp = getInt16(0)?.toShort()?.toDouble()?.div(100.0) ?: 0.0
        val hum = getInt16(2)?.toDouble()?.div(100.0) ?: 0.0
        val wSpeed = getInt16(4)?.toDouble()?.div(100.0) ?: 0.0
        val wDir = getInt16(6)?.toDouble() ?: 0.0
        val rfCum = if (data.size > 8) data[8].toUByte().toLong() else 0L
        val payloadDeviceId = if (data.size > 11) data[11].toUByte().toInt().toString() else "0"
        val battVolt = getInt16(12)?.toDouble()?.div(10.0) ?: 0.0
        val solarVolt = getInt16(14)?.toDouble()?.div(10.0) ?: 0.0
        val rssiVal = if (data.size > 16) data[16].toUByte().toInt() - 128 else -100

        val totalErrorsVal = if (data.size > 30) data[30].toUByte().toInt() else 0
        val err0Val = if (data.size > 31) data[31].toUByte().toInt() else 0
        val err1Val = if (data.size > 32) data[32].toUByte().toInt() else 0
        val err2Val = if (data.size > 33) data[33].toUByte().toInt() else 0
        val err3Val = if (data.size > 34) data[34].toUByte().toInt() else 0
        val err4Val = if (data.size > 35) data[35].toUByte().toInt() else 0
        val err5Val = if (data.size > 36) data[36].toUByte().toInt() else 0
        val err6Val = if (data.size > 37) data[37].toUByte().toInt() else 0
        val err7Val = if (data.size > 38) data[38].toUByte().toInt() else 0
        val err8Val = if (data.size > 39) data[39].toUByte().toInt() else 0
        val err9Val = if (data.size > 40) data[40].toUByte().toInt() else 0
        val err10Val = if (data.size > 41) data[41].toUByte().toInt() else 0
        val err11Val = if (data.size > 42) data[42].toUByte().toInt() else 0
        val err12Val = if (data.size > 43) data[43].toUByte().toInt() else 0
        val err13Val = if (data.size > 44) data[44].toUByte().toInt() else 0
        val err14Val = if (data.size > 45) data[45].toUByte().toInt() else 0
        val err15Val = if (data.size > 46) data[46].toUByte().toInt() else 0
        val err16Val = if (data.size > 47) data[47].toUByte().toInt() else 0

        return SensorData.AWSData(
            deviceId = payloadDeviceId,
            temperature = String.format(Locale.US, "%.2f", temp),
            humidity = String.format(Locale.US, "%.2f", hum),
            windSpeed = String.format(Locale.US, "%.2f", wSpeed),
            windDirection = String.format(Locale.US, "%.2f", wDir),
            rfCumulative = rfCum.toString(),
            batteryVoltage = String.format(Locale.US, "%.2f", battVolt),
            solarVoltage = String.format(Locale.US, "%.2f", solarVolt),
            signalStrength = rssiVal.toString(),
            totalErrors = totalErrorsVal.toString(),
            error1 = getBleErrorDescription(err0Val),
            error2 = getBleErrorDescription(err1Val),
            error3 = getBleErrorDescription(err2Val),
            error4 = getBleErrorDescription(err3Val),
            error5 = getBleErrorDescription(err4Val),
            error6 = getBleErrorDescription(err5Val),
            error7 = getBleErrorDescription(err6Val),
            error8 = getBleErrorDescription(err7Val),
            error9 = getBleErrorDescription(err8Val),
            error10 = getBleErrorDescription(err9Val),
            error11 = getBleErrorDescription(err10Val),
            error12 = getBleErrorDescription(err11Val),
            error13 = getBleErrorDescription(err12Val),
            error14 = getBleErrorDescription(err13Val),
            error15 = getBleErrorDescription(err14Val),
            error16 = getBleErrorDescription(err16Val),
            rawData = rawDataString,
            deviceAddress = deviceAddress
        )
    }

    private fun getBleErrorDescription(code: Int): String {
        return when (code) {
            0 -> "None"
            1 -> "ATRH Sensor Error"
            2 -> "Wind Sensor Error"
            3 -> "Tilt Sensor Error"
            4 -> "Flash Error"
            5 -> "Network Error"
            6 -> "Low Battery"
            7 -> "Battery Error"
            8 -> "Solar Error"
            9 -> "SDCard Error"
            10 -> "Rain Gauge Error"
            11 -> "Sim_Not_Inserted"
            21 -> "MQTT Connect Error"
            22 -> "MQTT Upload Error"
            23 -> "HTTP1 Connect Error"
            24 -> "HTTP1 Upload Error"
            25 -> "HTTP2 Connect Error"
            26 -> "HTTP2 Upload Error"
            else -> "Unknown ($code)"
        }
    }

    /**
     * Public parsing entry point for external callers (backward compatibility).
     */
    fun parseAdvertisingAwsData(data: ByteArray, deviceAddress: String): SensorData.AWSData? {
        return parseAWSData(data, deviceAddress)
    }

    override fun onCleared() {
        flusherJob?.cancel()
        clearAllData()
    }

    fun clearAllData() {
        _devices.value = emptyList()
        synchronized(deviceHistoricalData) {
            deviceHistoricalData.clear()
        }
        pendingDeviceUpdates.clear()
    }
}
