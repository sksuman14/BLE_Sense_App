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
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * Isolated handler for Sensor Hub devices.
 *
 * Owns all parsing and state for:
 * SHT40, LIS3DH, SoilSensor, AmmoniaSensor, Sen66,
 * VEML7700, VCNL4040, AHT20, BME680, TempLogger.
 *
 * Changes to this file cannot affect DataLogger or AWS scanning.
 */
class SensorHubHandler(
    private val coroutineScope: kotlinx.coroutines.CoroutineScope,
    private val sharedSensorDataStream: MutableSharedFlow<SensorData>
) : BleDeviceHandler {

    companion object {
        private const val TAG = "SensorHub"
        private const val AWS_MAC = "DE:AD:BE:AF:BA:11"
    }

    // ── Device List ──────────────────────────────────────────────────────────
    private val _devices = MutableStateFlow<List<BluetoothDeviceModel>>(emptyList())
    override val devices: StateFlow<List<BluetoothDeviceModel>> = _devices.asStateFlow()

    private val _historyUpdateTrigger = MutableStateFlow(0L)
    override val historyUpdateTrigger: StateFlow<Long> = _historyUpdateTrigger.asStateFlow()

    override val sensorDataStream: MutableSharedFlow<SensorData> = sharedSensorDataStream

    // ── TempLogger State ─────────────────────────────────────────────────────
    val tempLoggerPacketHistory = MutableStateFlow<Map<String, List<SensorData.TempLoggerData>>>(emptyMap())
    val latestTempLoggerPacket = MutableStateFlow<Map<String, SensorData.TempLoggerData?>>(emptyMap())

    // ── Sen66 State ──────────────────────────────────────────────────────────
    val sen66PacketHistory = MutableStateFlow<Map<String, List<SensorData.Sen66Data>>>(emptyMap())
    val latestSen66Packet = MutableStateFlow<Map<String, SensorData.Sen66Data?>>(emptyMap())

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
                delay(50)
                flushUpdates()
            }
        }
    }

    private fun flushUpdates() {
        val now = System.currentTimeMillis()
        if (now - lastListUpdateTime < 200 && pendingDeviceUpdates.isNotEmpty()) return

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

    // ── Sensor Hub device name patterns ──────────────────────────────────────
    private val sensorHubPatterns = listOf(
        "SHT", "SOIL", "Activity", "LIS3DH", "NH", "Ammonia",
        "sen66", "VEML", "VCNL", "AHT", "BME", "TempLogger", "TLOG", "STS30", "STTS751", "ATRH", "Rain", "Wind"
    )

    override fun canHandle(
        deviceName: String?,
        deviceAddress: String,
        manufacturerData: SparseArray<ByteArray>
    ): Boolean {
        // Never handle AWS MAC
        if (deviceAddress.equals(AWS_MAC, ignoreCase = true)) return false
        // Never handle DataLogger/DLOG
        if (deviceName?.contains("DataLogger", ignoreCase = true) == true) return false
        if (deviceName?.contains("DLOG", ignoreCase = true) == true) return false

        // Handle if name matches any sensor hub pattern
        if (deviceName != null) {
            for (pattern in sensorHubPatterns) {
                if (deviceName.contains(pattern, ignoreCase = true)) return true
            }
        }

        return false
    }

    override suspend fun handle(result: ScanResult) {
        try {
            val device = result.device ?: return
            val scanRecord = result.scanRecord ?: return
            val deviceAddress = device.address ?: return
            val manufacturerData = scanRecord.manufacturerSpecificData ?: return
            val deviceName = scanRecord.deviceName ?: "N/A"

            var sensorData: SensorData? = null
            val deviceType = determineDeviceType(deviceName, deviceAddress)

            if (manufacturerData.size() > 0) {
                for (i in 0 until manufacturerData.size()) {
                    val data = manufacturerData.valueAt(i) ?: continue

                    // TempLogger Large Packet path (224 byte packets)
                    if (data.size >= 224) {
                        val parsedTempLogger = parseTempLoggerData(data, deviceAddress, deviceName)
                        if (parsedTempLogger != null) {
                            sensorData = parsedTempLogger
                            break
                        }
                    }

                    val parsed = when (deviceType) {
                        "SHT40" -> parseSHT40Data(data, deviceAddress)
                        "LIS3DH" -> parseLIS3DHData(data, deviceAddress)
                        "Soil Sensor" -> parseSoilSensorData(data, deviceAddress)
                        "Ammonia Sensor" -> parseAmmoniaSensorData(data, deviceAddress)
                        "VEML7700" -> parseVEML7700Data(data, deviceAddress)
                        "VCNL4040" -> parseVCNL4040Data(data, deviceAddress)
                        "AHT20" -> parseAHT20Data(data, deviceAddress)
                        "BME680" -> parseBME680Data(data, deviceAddress)
                        "TempLogger" -> parseTempLoggerData(data, deviceAddress, deviceName)
                        "sen66" -> parseSen66Data(manufacturerData, deviceAddress)
                        "STS30" -> parseSTS30Data(data, deviceAddress)
                        "STTS751" -> parseSTTS751Data(data, deviceAddress)
                        "ATRH" -> parseATRHData(data, deviceAddress)
                        "Rain" -> parseRainData(data, deviceAddress)
                        "Wind" -> parseWindData(data, deviceAddress)
                        else -> null
                    }

                    if (parsed != null) {
                        sensorData = parsed
                        break
                    }
                }
            }

            // Strict filter: only process known sensor types
            if (deviceType == "Unknown Device" && sensorData == null) return

            val logicalAddress = ScanResultUtils.buildLogicalAddress(
                deviceAddress, AWS_MAC, deviceType, scanRecord.deviceName, sensorData
            )

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
            Log.e(TAG, "Error processing scan result: ${e.message}")
        }
    }

    override fun onCleared() {
        flusherJob?.cancel()
        clearAllData()
    }

    fun clearAllData() {
        _devices.value = emptyList()
        tempLoggerPacketHistory.value = emptyMap()
        latestTempLoggerPacket.value = emptyMap()
        sen66PacketHistory.value = emptyMap()
        latestSen66Packet.value = emptyMap()
        synchronized(deviceHistoricalData) {
            deviceHistoricalData.clear()
        }
        pendingDeviceUpdates.clear()
    }

    // ── Device Type Detection ────────────────────────────────────────────────

    private fun determineDeviceType(name: String?, address: String): String = when {
        name?.contains("SHT", ignoreCase = true) == true -> "SHT40"
        name?.contains("SOIL", ignoreCase = true) == true -> "Soil Sensor"
        name?.contains("Activity", ignoreCase = true) == true ||
                name?.contains("LIS3DH", ignoreCase = true) == true -> "LIS3DH"
        name?.contains("NH", ignoreCase = true) == true ||
                name?.contains("Ammonia", ignoreCase = true) == true -> "Ammonia Sensor"
        name?.contains("sen66", ignoreCase = true) == true -> "sen66"
        name?.contains("VEML", ignoreCase = true) == true -> "VEML7700"
        name?.contains("VCNL", ignoreCase = true) == true -> "VCNL4040"
        name?.contains("AHT", ignoreCase = true) == true -> "AHT20"
        name?.contains("BME", ignoreCase = true) == true -> "BME680"
        name?.contains("TempLogger", ignoreCase = true) == true ||
                name?.contains("TLOG", ignoreCase = true) == true -> "TempLogger"
        name?.contains("STS", ignoreCase = true) == true -> "STS30"
        name?.contains("STTS", ignoreCase = true) == true -> "STTS751"
        name?.contains("ATRH", ignoreCase = true) == true -> "ATRH"
        name?.contains("Rain", ignoreCase = true) == true -> "Rain"
        name?.contains("Wind", ignoreCase = true) == true -> "Wind"
        else -> "Unknown Device"
    }

    // ── Parsers ──────────────────────────────────────────────────────────────

    private fun parseSHT40Data(data: ByteArray, deviceAddress: String): SensorData? {
        if (data.size < 5) return null
        val temp = data[1].toInt() + data[2].toUByte().toInt() / 100.0
        val hum = data[3].toInt() + data[4].toUByte().toInt() / 100.0
        return SensorData.SHT40Data(
            data[0].toUByte().toString(),
            String.format("%.2f", temp),
            String.format("%.2f", hum),
            deviceAddress
        )
    }

    private fun parseSTS30Data(data: ByteArray, deviceAddress: String): SensorData? {
        if (data.size < 5) return null
        val tempC = data[1].toInt() + data[2].toUByte().toInt() / 100.0
        val tempF = data[3].toInt() + data[4].toUByte().toInt() / 100.0
        return SensorData.STS30Data(
            data[0].toUByte().toString(),
            String.format("%.2f", tempC),
            String.format("%.2f", tempF),
            deviceAddress
        )
    }

    private fun parseSTTS751Data(data: ByteArray, deviceAddress: String): SensorData? {
        if (data.size < 5) return null
        val tempC = data[1].toInt() + data[2].toUByte().toInt() / 100.0
        val tempF = data[3].toInt() + data[4].toUByte().toInt() / 100.0
        return SensorData.STTS751Data(
            data[0].toUByte().toString(),
            String.format("%.2f", tempC),
            String.format("%.2f", tempF),
            deviceAddress
        )
    }

    private fun parseATRHData(data: ByteArray, deviceAddress: String): SensorData? {
        if (data.size < 9) return null
        val nodeId = data[0].toUByte().toString()
        val temp = data[1].toInt() + data[2].toUByte().toInt() / 100.0
        val hum = data[3].toInt() + data[4].toUByte().toInt() / 100.0
        val lux = ((data[5].toInt() and 0xFF) shl 8) or (data[6].toInt() and 0xFF)
        val press = ((data[7].toInt() and 0xFF) shl 8) or (data[8].toInt() and 0xFF)

        return SensorData.ATRHData(
            deviceId = nodeId,
            temperature = String.format("%.2f", temp),
            humidity = String.format("%.2f", hum),
            lux = lux.toString(),
            pressure = press.toString(),
            deviceAddress = deviceAddress
        )
    }

    private fun parseLIS3DHData(data: ByteArray, deviceAddress: String): SensorData? {
        if (data.size < 7) return null
        return SensorData.LIS3DHData(
            data[0].toUByte().toString(),
            "${data[1].toInt()}.${data[2].toUByte()}",
            "${data[3].toInt()}.${data[4].toUByte()}",
            "${data[5].toInt()}.${data[6].toUByte()}",
            deviceAddress
        )
    }

    private fun parseSoilSensorData(data: ByteArray, deviceAddress: String): SensorData? {
        if (data.size < 16) return null
        fun u(i: Int) = data[i].toUByte().toInt()
        return SensorData.SoilSensorData(
            u(0).toString(),
            ((u(2) shl 8) or u(1)).toString(),
            ((u(4) shl 8) or u(3)).toString(),
            ((u(6) shl 8) or u(5)).toString(),
            u(7).toString(),
            "${u(8)}.${u(9)}",
            ((u(11) shl 8) or u(10)).toString(),
            "${u(12)}.${u(13)}",
            ((u(15) shl 8) or u(14)).toString(),
            deviceAddress
        )
    }

    private fun parseAmmoniaSensorData(data: ByteArray?, address: String): SensorData? {
        if (data == null || data.size < 6) return null
        return SensorData.AmmoniaSensorData(
            data[0].toUByte().toString(),
            String.format(Locale.US, "%.1f ppm", data[5].toUByte().toFloat()),
            data.joinToString(" ") { "%02X".format(it) },
            address
        )
    }

    private fun parseVEML7700Data(data: ByteArray, address: String): SensorData? {
        if (data.size < 3) return null
        return SensorData.VEML7700Data(
            data[0].toUByte().toString(),
            (((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)).toString(),
            address
        )
    }

    private fun parseVCNL4040Data(data: ByteArray, address: String): SensorData? {
        if (data.size < 3) return null
        return SensorData.VCNL4040Data(
            data[0].toUByte().toString(),
            (((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)).toString(),
            address
        )
    }

    private fun parseAHT20Data(data: ByteArray, address: String): SensorData? {
        if (data.size < 5) return null
        return SensorData.AHT20Data(
            data[0].toUByte().toString(),
            String.format("%.2f", data[1].toInt() + data[2].toUByte().toInt() / 100.0),
            String.format("%.2f", data[3].toInt() + data[4].toUByte().toInt() / 100.0),
            address
        )
    }

    private fun parseBME680Data(data: ByteArray, address: String): SensorData? {
        if (data.size < 7) return null
        return SensorData.BME680Data(
            data[0].toUByte().toString(),
            String.format("%.2f", data[1].toInt() + data[2].toUByte().toInt() / 100.0),
            String.format("%.2f", data[3].toInt() + data[4].toUByte().toInt() / 100.0),
            (((data[5].toInt() and 0xFF) shl 8) or (data[6].toInt() and 0xFF)).toString(),
            address
        )
    }

    private fun parseTempLoggerData(data: ByteArray?, deviceAddress: String, deviceName: String): SensorData.TempLoggerData? {
        if (data == null || data.size < 5) return null
        val deviceId = data[0].toInt() and 0xFF
        val tempInt = data[1].toInt() and 0xFF
        val tempFrac = data[2].toInt() and 0xFF
        val humInt = data[3].toInt() and 0xFF
        val humFrac = data[4].toInt() and 0xFF

        val temperature = "$tempInt.$tempFrac".toDoubleOrNull() ?: 0.0
        val humidity = "$humInt.$humFrac".toDoubleOrNull() ?: 0.0

        if (temperature in 5.0..60.0 && humidity in 10.0..99.0) {
            val packet = SensorData.TempLoggerData(
                deviceId = deviceId.toString(),
                temperature = String.format("%.2f", temperature),
                humidity = String.format("%.2f", humidity),
                rawTemperature = (tempInt * 100 + tempFrac),
                rawHumidity = (humInt * 100 + humFrac),
                rawData = data.joinToString(" ") { "%02X".format(it) },
                deviceAddress = deviceAddress,
                timestamp = System.currentTimeMillis()
            )

            val newLatestMap = latestTempLoggerPacket.value.toMutableMap()
            newLatestMap[deviceAddress] = packet
            latestTempLoggerPacket.value = newLatestMap

            tempLoggerPacketHistory.update { currentMap ->
                val currentList = currentMap[deviceAddress] ?: emptyList()
                val newList = if (currentList.any { it.rawData == packet.rawData }) currentList else currentList + packet
                currentMap.toMutableMap().apply { put(deviceAddress, newList) }
            }
            return packet
        }
        return null
    }

    private fun parseSen66Data(manufacturerData: SparseArray<ByteArray>, deviceAddress: String): SensorData.Sen66Data? {
        var data: ByteArray? = null
        var startOffset = 0
        for (i in 0 until manufacturerData.size()) {
            val candidate = manufacturerData.valueAt(i) ?: continue
            when (candidate.size) {
                19 -> { data = candidate; startOffset = 2; break }
                21 -> { data = candidate; startOffset = 0; break }
            }
        }
        if (data == null) return null

        val finalData = data
        fun readUInt16(idx: Int): Int {
            val lIdx = idx - startOffset
            if (lIdx < 0 || lIdx + 1 >= finalData.size) return 0
            return (finalData[lIdx].toUByte().toInt() shl 8) or finalData[lIdx + 1].toUByte().toInt()
        }
        fun readInt16(idx: Int): Int {
            return readUInt16(idx).toShort().toInt()
        }

        val deviceId = (if (2 - startOffset >= 0) finalData[2 - startOffset].toUByte().toInt() else 0).toString()
        val sen66Data = SensorData.Sen66Data(
            deviceId = deviceId,
            pm1  = String.format("%.1f", readUInt16(3) / 10.0),
            pm25 = String.format("%.1f", readUInt16(5) / 10.0),
            pm4  = String.format("%.1f", readUInt16(7) / 10.0),
            pm10 = String.format("%.1f", readUInt16(9) / 10.0),
            temperature = String.format("%.2f", readInt16(11) / 200.0),
            humidity    = String.format("%.2f", readInt16(13) / 100.0),
            co2 = readUInt16(15).toString(),
            voc = readInt16(17).toString(),
            nox = readInt16(19).toString(),
            deviceAddress = deviceAddress
        )

        val newLatestMap = latestSen66Packet.value.toMutableMap()
        newLatestMap[deviceAddress] = sen66Data
        latestSen66Packet.value = newLatestMap

        sen66PacketHistory.update { currentMap ->
            val currentList = currentMap[deviceAddress] ?: emptyList()
            val newList = if (currentList.any { it.pm25 == sen66Data.pm25 && it.temperature == sen66Data.temperature }) currentList else currentList + sen66Data
            currentMap.toMutableMap().apply { put(deviceAddress, newList) }
        }
        return sen66Data
    }

    /**
     * Public parsing entry point for external callers (backward compatibility).
     */
    fun parseAdvertisingData(data: ByteArray, deviceType: String, deviceAddress: String): SensorData? {
        return when (deviceType) {
            "SHT40" -> parseSHT40Data(data, deviceAddress)
            "LIS3DH" -> parseLIS3DHData(data, deviceAddress)
            "Soil Sensor" -> parseSoilSensorData(data, deviceAddress)
            "Ammonia Sensor" -> parseAmmoniaSensorData(data, deviceAddress)
            "VEML7700" -> parseVEML7700Data(data, deviceAddress)
            "VCNL4040" -> parseVCNL4040Data(data, deviceAddress)
            "AHT20" -> parseAHT20Data(data, deviceAddress)
            "BME680" -> parseBME680Data(data, deviceAddress)
            "TempLogger" -> parseTempLoggerData(data, deviceAddress, "")
            "STS30" -> parseSTS30Data(data, deviceAddress)
            "STTS751" -> parseSTTS751Data(data, deviceAddress)
            "ATRH" -> parseATRHData(data, deviceAddress)
            "Rain" -> parseRainData(data, deviceAddress)
            "Wind" -> parseWindData(data, deviceAddress)
            else -> null
        }
    }

    private fun parseRainData(data: ByteArray, deviceAddress: String): SensorData? {
        if (data.size < 3) return null
        // data[0] = NodeID (mfg[2]), data[1] = r_int (mfg[3]), data[2] = r_frac (mfg[4])
        val rainfall = data[1].toInt() + data[2].toUByte().toInt() / 100.0
        return SensorData.RainData(
            deviceId = data[0].toUByte().toString(),
            rainfall = String.format("%.2f", rainfall),
            deviceAddress = deviceAddress
        )
    }

    private fun parseWindData(data: ByteArray, deviceAddress: String): SensorData? {
        if (data.size < 5) return null
        // data[0] = NodeID (mfg[2]), data[1] = w_int (mfg[3]), data[2] = w_frac (mfg[4])
        // data[3] = direction MSB (mfg[5]), data[4] = direction LSB (mfg[6])
        val speed = data[1].toInt() + data[2].toUByte().toInt() / 100.0
        val direction = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
        return SensorData.WindData(
            deviceId = data[0].toUByte().toString(),
            windSpeed = String.format("%.2f", speed),
            windDirection = direction.toString(),
            deviceAddress = deviceAddress
        )
    }
}
