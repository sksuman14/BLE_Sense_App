package com.blesense.app

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
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

// Main ViewModel for Bluetooth Low Energy scanning and data management
class BluetoothScanViewModel<T>(private val context: Context) : ViewModel() {

    // Mutable state for discovered devices, exposed as read-only StateFlow
    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()

    // Mutable state for scanning status
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Mutable state for latest packet ID from DataLogger
    private val _latestPacketId = MutableStateFlow(-1)
    val latestPacketId: StateFlow<Int> = _latestPacketId.asStateFlow()

    // State flow for storing DataLogger packet history
    val dataLoggerPacketHistory = MutableStateFlow<List<SensorData.DataLoggerData>>(emptyList())

    // State flow for TempLogger packets organized by device address
    val tempLoggerPacketHistory = MutableStateFlow<Map<String, List<SensorData.TempLoggerData>>>(emptyMap())
    // State flow for latest TempLogger packet per device
    val latestTempLoggerPacket = MutableStateFlow<Map<String, SensorData.TempLoggerData?>>(emptyMap())

    // Mutable state for latest DataLogger packet
    private val _latestDataLoggerPacket = MutableStateFlow<SensorData.DataLoggerData?>(null)
    val latestDataLoggerPacket: StateFlow<SensorData.DataLoggerData?> = _latestDataLoggerPacket.asStateFlow()

    // Job to manage continuous scanning coroutine
    private var scanJob: Job? = null

    // Map to store historical data per device address
    private val deviceHistoricalData = HashMap<String, MutableList<HistoricalDataEntry>>()

    // Lazy initialization of Bluetooth scanner
    private val bluetoothScanner: BluetoothLeScanner? by lazy {
        BluetoothAdapter.getDefaultAdapter()?.bluetoothLeScanner
    }

    // Companion object for constants
    companion object {
        // Interval to restart scan (5 minutes) to prevent battery drain
        private const val SCAN_RESTART_INTERVAL = 5 * 60 * 1000L
        // Maximum historical entries to store per device to prevent memory overflow
        private const val MAX_HISTORY_ENTRIES_PER_DEVICE = 1000
    }

    // Data class representing a single historical data entry
    data class HistoricalDataEntry(
        val timestamp: Long,  // Unix timestamp when data was received
        val sensorData: SensorData?  // Parsed sensor data or null if parsing failed
    )

    // Sealed class hierarchy representing different sensor data types
    sealed class SensorData {
        abstract val deviceId: String  // Abstract property for device identifier

        // Data class for SHT40 temperature and humidity sensor
        data class SHT40Data(
            override val deviceId: String,
            val temperature: String,  // Temperature in Celsius as formatted string
            val humidity: String  // Humidity percentage as formatted string
        ) : SensorData()

        // Data class for Lux sensor (light intensity)
        data class LuxSensorData(
            override val deviceId: String,
            val lux: String,  // Light intensity in LUX
            val rawData: String  // Raw hex data for debugging
        ) : SensorData()

        // Data class for LIS2DH 3-axis accelerometer
        data class LIS2DHData(
            override val deviceId: String,
            val x: String,  // X-axis acceleration
            val y: String,  // Y-axis acceleration
            val z: String  // Z-axis acceleration
        ) : SensorData()

        // Data class for multi-parameter soil sensor
        data class SoilSensorData(
            override val deviceId: String,
            val nitrogen: String,  // Nitrogen content
            val phosphorus: String,  // Phosphorus content
            val potassium: String,  // Potassium content
            val moisture: String,  // Soil moisture percentage
            val temperature: String,  // Soil temperature
            val ec: String,  // Electric conductivity
            val pH: String,  // Soil pH level
            val salinity: String  // Soil salinity
        ) : SensorData()

        // Data class for speed and distance sensor
        data class SDTData(
            override val deviceId: String,
            val speed: String,  // Speed measurement
            val distance: String  // Distance measurement
        ) : SensorData()

        // Data class for ammonia gas sensor
        data class AmmoniaSensorData(
            override val deviceId: String,
            val ammonia: String,  // Ammonia concentration in ppm
            val rawData: String  // Raw hex data
        ) : SensorData()

        // Data class for temperature logger device
        data class TempLoggerData(
            override val deviceId: String,
            val temperature: String,  // Formatted temperature
            val humidity: String,  // Formatted humidity
            val rawTemperature: Int,  // Raw integer temperature for calculations
            val rawHumidity: Int,  // Raw integer humidity for calculations
            val rawData: String,  // Raw hex data packet
            val deviceAddress: String,  // Device MAC address
            val timestamp: Long = System.currentTimeMillis()  // When data was received
        ) : SensorData() {

            // Computed property for display summary
            val displaySummary: String
                get() = "Temp: $temperature°C, Hum: $humidity%, Device: $deviceId"
        }

        // Data class for data logger with accelerometer readings
        data class DataLoggerData(
            override val deviceId: String,
            val currentPacketId: Int,  // Total packets stored in device
            val lastPacketId: Int,  // Currently received packet ID
            val payloadAccel: List<Triple<Int, Int, Int>>,  // List of XYZ acceleration triplets
            val timestamp: Long,  // Calculated timestamp based on packet sequencing
            val rawData: String  // Raw hex data
        ) : SensorData() {

            // Computed property for display summary
            val displaySummary: String
                get() = "Packet: $currentPacketId (last: $lastPacketId), Points: ${payloadAccel.size}, Time: $timestamp"
        }
    }

    // Data class representing a discovered Bluetooth device
    data class BluetoothDevice(
        val name: String,  // Device name from advertisement
        val rssi: String,  // Signal strength indicator
        val address: String,  // MAC address
        val deviceId: String,  // Device identifier from sensor data
        val sensorData: SensorData? = null  // Parsed sensor data if available
    )

    // Starts continuous scanning with periodic restarts
    fun startContinuousScan(activity: Activity) {
        if (_isScanning.value) return  // Prevent multiple scans

        scanJob?.cancel()  // Cancel existing scan job
        scanJob = viewModelScope.launch {  // Launch new coroutine
            _isScanning.value = true  // Update scanning state
            startScan(activity)  // Start initial scan

            while (isActive) {  // Loop while coroutine is active
                delay(SCAN_RESTART_INTERVAL)  // Wait for restart interval
                restartScan(activity)  // Restart scan
            }
        }
    }

    // Restarts scanning with a small delay
    private fun restartScan(activity: Activity) {
        stopScan()  // Stop current scan
        Handler(Looper.getMainLooper()).postDelayed({  // Post delayed to main thread
            startScan(activity)  // Start scan after delay
        }, 100)  // 100ms delay
    }

    // Starts BLE scanning with proper permissions
    @SuppressLint("MissingPermission")
    fun startScan(activity: Activity?) {
        if (!hasRequiredPermissions()) return  // Check permissions first

        try {
            bluetoothScanner?.let { scanner ->  // Safe access to scanner
                val scanSettings = createScanSettings()  // Create optimal scan settings
                scanCallback = createScanCallback()  // Create scan callback
                scanner.startScan(null, scanSettings, scanCallback)  // Start scanning
            }
        } catch (_: Exception) { }  // Silent catch for edge cases
    }

    // Stops BLE scanning
    @SuppressLint("MissingPermission")
    fun stopScan() {
        try {
            bluetoothScanner?.let { scanner ->  // Safe access to scanner
                scanCallback?.let {  // If callback exists
                    scanner.stopScan(it)  // Stop scanning
                    scanCallback = null  // Clear callback reference
                }
            }
        } catch (_: Exception) { }  // Silent catch for edge cases
        _isScanning.value = false  // Update scanning state
    }

    // Stops continuous scanning completely
    fun stopContinuousScan() {
        scanJob?.cancel()  // Cancel scanning coroutine
        stopScan()  // Stop BLE scan
        _isScanning.value = false  // Update state
        println("Continuous scan stopped completely")  // Debug log
    }

    // Creates optimized scan settings for BLE
    private fun createScanSettings(): ScanSettings =
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)  // Fastest scanning
            .setLegacy(false)  // Use extended advertising
            .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)  // Support all PHY layers
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)  // Report all matches
            .setReportDelay(0)  // No batching, immediate reports
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)  // Max matches
            .build()

    // Reference to current scan callback
    private var scanCallback: ScanCallback? = null

    // Global state variables for DataLogger timestamp calculation
    private var lastPacketIdGlobal: Int? = null  // Last received packet ID
    private var lastPacketTimeGlobal: Long? = null  // Last packet receive time
    private val PACKET_INTERVAL_MS = 10_000L  // 10 seconds per packet interval

    // Converts two bytes to unsigned integer
    private fun bytesToUInt(msb: Byte, lsb: Byte): Int {
        return ((msb.toInt() and 0xFF) shl 8) or (lsb.toInt() and 0xFF)
    }

    // Calculates difference between packet IDs with wrap-around handling
    private fun packetDiff(last: Int, current: Int, max: Int = 65536): Int {
        return if (last >= current) {
            last - current  // Normal subtraction
        } else {
            last + (max - current)  // Handle wrap-around
        }
    }

    // Creates the scan callback for handling BLE scan results
    private fun createScanCallback(): ScanCallback = object : ScanCallback() {
        // Called when a single scan result is found
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try {
                if (!hasRequiredPermissions()) return  // Check permissions

                result.device?.let { device ->  // Safe access to device
                    val deviceName = device.name ?: return  // Get device name or exit
                    val deviceAddress = device.address ?: return  // Get address or exit

                    val deviceType = determineDeviceType(deviceName)  // Determine sensor type
                    val manufacturerData = result.scanRecord?.manufacturerSpecificData ?: return
                    if (manufacturerData.size() == 0) return  // Skip if no manufacturer data

                    // Parse sensor data based on device type
                    val sensorData: SensorData? = when (deviceType) {
                        "Ammonia Sensor" -> {
                            val data = manufacturerData.valueAt(0)  // Get first data block
                            parseAmmoniaSensorData(data, deviceAddress)  // Parse ammonia data
                        }
                        "Lux Sensor" -> {
                            var parsed: SensorData? = null
                            // Iterate through all manufacturer data blocks
                            for (i in 0 until manufacturerData.size()) {
                                val data = manufacturerData.valueAt(i) ?: continue
                                parsed = parseLuxSensorData(data, deviceAddress)  // Parse lux data
                                break  // Stop after first valid data
                            }
                            parsed  // Return parsed data
                        }
                        "TempLogger" -> {
                            val data = manufacturerData.valueAt(0)
                            parseTempLoggerData(data, deviceAddress, deviceName)  // Parse temp logger
                        }
                        "DataLogger" -> {
                            val data = manufacturerData.valueAt(0)
                            parseDataLoggerData(data, deviceAddress)  // Parse data logger
                        }
                        else -> {
                            parseAdvertisingData(result, deviceType)  // Parse generic sensor data
                        }
                    }

                    // Create Bluetooth device object
                    val bluetoothDevice = BluetoothDevice(
                        name = deviceName,
                        address = deviceAddress,
                        rssi = result.rssi.toString(),  // Convert RSSI to string
                        deviceId = sensorData?.deviceId ?: "Unknown",  // Use sensor ID or default
                        sensorData = sensorData  // Attach parsed data
                    )

                    // Store historical data if sensor data exists
                    sensorData?.let { data ->
                        val entry = HistoricalDataEntry(
                            timestamp = System.currentTimeMillis(),  // Current time
                            sensorData = data  // Parsed data
                        )
                        // Get or create history list for this device
                        val historyList = deviceHistoricalData.getOrPut(deviceAddress) { mutableListOf() }
                        historyList.add(entry)  // Add new entry
                        // Maintain maximum history size
                        while (historyList.size > MAX_HISTORY_ENTRIES_PER_DEVICE) {
                            historyList.removeAt(0)  // Remove oldest entry
                        }
                    }

                    updateDevice(bluetoothDevice)  // Update device list

                    // Update latest packet ID for DataLogger
                    if (deviceType == "DataLogger" && sensorData is SensorData.DataLoggerData) {
                        _latestPacketId.value = sensorData.currentPacketId
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()  // Print stack trace for debugging
                println("Error processing scan result: ${e.message}")  // Log error
            }
        }

        // Called when batch scan results are available
        override fun onBatchScanResults(results: List<ScanResult>) {
            // Process each result individually
            for (result in results) {
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
            }
        }

        // Called when scan fails
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)  // Call parent implementation
            println("BLE Scan failed with error code: $errorCode")  // Log error code
        }
    }

    // Placeholder for sending advertise command (not implemented)
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun sendAdvertiseCommandToSensor(deviceAddress: String) {
        // TODO: Implement BLE advertising command sending
    }

    // Checks if required BLE permissions are granted
    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires new permissions
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            // Older Android versions use classic permissions
            context.checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Gets historical data for a specific device address
    fun getHistoricalDataForDevice(address: String): MutableList<HistoricalDataEntry> {
        return deviceHistoricalData.getOrPut(address) { mutableListOf() }  // Get or create list
    }

    // Parses advertising data based on device type
    fun parseAdvertisingData(result: ScanResult, deviceType: String?): SensorData? {
        val manufacturerData = result.scanRecord?.manufacturerSpecificData ?: return null
        if (manufacturerData.size() == 0) return null  // No data to parse
        val data = manufacturerData.valueAt(0) ?: return null  // Get first data block
        val deviceAddress = result.device?.address ?: return null  // Get device address

        // Route to appropriate parser based on device type
        return when (deviceType) {
            "SHT40" -> parseSHT40Data(data)
            "LIS2DH" -> parseLIS2DHData(data)
            "Soil Sensor" -> parseSoilSensorData(data)
            "SPEED_DISTANCE" -> parseSDTData(data)
            "Ammonia Sensor" -> parseAmmoniaSensorData(data, deviceAddress)
            "Lux Sensor" -> parseLuxSensorData(data, deviceAddress)
            "DataLogger" -> parseDataLoggerData(data, deviceAddress)
            else -> null  // Unknown device type
        }
    }

    // ====================== Individual Sensor Parsers ======================

    // Parses Lux sensor data from byte array
    private fun parseLuxSensorData(data: ByteArray?, deviceAddress: String): SensorData? {
        if (data == null || data.size < 5) return null  // Validate data length
        val rawDataString = data.joinToString(" ") { "%02X".format(it) }  // Convert to hex string
        val deviceId = data[0].toInt() and 0xFF  // Extract device ID (byte 0)
        val highLux = data[1].toInt() and 0xFF  // High byte of lux value (byte 1)
        val lowLux = data[2].toInt() and 0xFF  // Low byte of lux value (byte 2)
        val luxValue = (highLux * 256) + lowLux  // Combine bytes for 16-bit value
        return SensorData.LuxSensorData(
            deviceId = deviceId.toString(),
            lux = luxValue.toString(),
            rawData = rawDataString
        )
    }

    // Parses TempLogger data from byte array
    private fun parseTempLoggerData(
        data: ByteArray?,
        deviceAddress: String,
        deviceName: String
    ): SensorData.TempLoggerData? {

        if (data == null || data.size < 5) return null  // Validate minimum data length

        val rawDataString = data.joinToString(" ") { "%02X".format(it) }  // Convert to hex

        // Extract individual bytes for temperature and humidity
        val deviceId = data[0].toInt() and 0xFF  // Device ID (byte 0)
        val tempInt = data[1].toInt() and 0xFF  // Temperature integer part (byte 1)
        val tempFrac = data[2].toInt() and 0xFF  // Temperature fractional part (byte 2)
        val humInt = data[3].toInt() and 0xFF  // Humidity integer part (byte 3)
        val humFrac = data[4].toInt() and 0xFF  // Humidity fractional part (byte 4)

        // Combine integer and fractional parts
        val temperature = "$tempInt.$tempFrac".toDouble()
        val humidity = "$humInt.$humFrac".toDouble()

        // Validate data ranges for realistic values
        if (temperature in 5.0..60.0 && humidity in 10.0..99.0) {
            Log.d("TempLogger", "Data Stable: $temperature C, $humidity % - Device: $deviceAddress")
            val packet = SensorData.TempLoggerData(
                deviceId = deviceId.toString(),
                temperature = String.format("%.2f", temperature),  // Format to 2 decimal places
                humidity = String.format("%.2f", humidity),
                rawTemperature = (tempInt * 100 + tempFrac),  // Store raw value for calculations
                rawHumidity = (humInt * 100 + humFrac),
                rawData = rawDataString,
                deviceAddress = deviceAddress,
                timestamp = System.currentTimeMillis()
            )

            val deviceKey = deviceAddress  // Use device address as key

            // Update latest packet map for this device
            val newLatestMap = latestTempLoggerPacket.value.toMutableMap()
            newLatestMap[deviceKey] = packet
            latestTempLoggerPacket.value = newLatestMap

            // Update packet history map
            tempLoggerPacketHistory.update { currentMap ->
                val currentList = currentMap[deviceKey] ?: emptyList()
                // Avoid duplicates by checking raw data
                val newList = if (currentList.any { it.rawData == packet.rawData }) {
                    currentList  // Keep existing if duplicate
                } else {
                    currentList + packet  // Add new packet
                }

                val newMap = currentMap.toMutableMap()
                newMap[deviceKey] = newList
                newMap
            }
            return packet  // Return parsed packet
        }

        return null  // Return null if data invalid
    }

    // State variables for DataLogger timestamp calculation
    private var baseTimestamp: Long? = null  // Base timestamp for sequence calculation
    private var totalStoredPackets: Int = 0  // Total packets count from device
    private var dumpBaseTime: Long? = null  // Base time for current data dump

    // Parses DataLogger data with timestamp calculation
    private fun parseDataLoggerData(
        data: ByteArray?,
        deviceAddress: String
    ): SensorData.DataLoggerData? {

        if (data == null || data.size < 244) {  // DataLogger packets are 244+ bytes
            return null
        }

        val rawData = data.joinToString(" ") { "%02X".format(it) }  // Convert to hex string
        val deviceId = "1"  // Fixed device ID for DataLogger

        val size = data.size  // Total data size

        // Extract current received packet ID (bytes size-5 and size-4)
        val currentReceivedId =
            (data[size - 5].toInt() and 0xFF) or
                    ((data[size - 4].toInt() and 0xFF) shl 8)

        // Extract total packets count (bytes size-3 and size-2)
        val totalPacketsCount =
            (data[size - 3].toInt() and 0xFF) or
                    ((data[size - 2].toInt() and 0xFF) shl 8)

        val now = System.currentTimeMillis()  // Current time

        if (dumpBaseTime == null) {
            dumpBaseTime = now  // Set base time for this data dump
        }

        val baseTime = dumpBaseTime!!  // Get base time
        val packetAge = (totalPacketsCount - currentReceivedId) * 60_000L  // Calculate age (1 min per packet)
        val calculatedTimestamp = baseTime - packetAge  // Back-calculate timestamp

        // Parse accelerometer data from payload
        val accelData = mutableListOf<Triple<Int, Int, Int>>()
        var index = 0
        val payloadEnd = 240  // Accelerometer data ends at byte 240

        // Read XYZ triples until payload end
        while (index + 2 < payloadEnd) {
            val x = data[index++].toByte().toInt()  // X acceleration
            val y = data[index++].toByte().toInt()  // Y acceleration
            val z = data[index++].toByte().toInt()  // Z acceleration
            accelData.add(Triple(x, y, z))  // Add to list
        }

        val loggerData = SensorData.DataLoggerData(
            deviceId = deviceId,
            currentPacketId = totalPacketsCount,
            lastPacketId = currentReceivedId,
            payloadAccel = accelData,
            timestamp = calculatedTimestamp,
            rawData = rawData
        )

        _latestDataLoggerPacket.value = loggerData  // Update latest packet

        // Update packet history, avoiding duplicates
        dataLoggerPacketHistory.update { currentList ->
            if (currentList.any { it.lastPacketId == loggerData.lastPacketId }) {
                currentList  // Keep existing if duplicate
            } else {
                currentList + loggerData  // Add new packet
            }
        }

        if (currentReceivedId == 1) {
            dumpBaseTime = null  // Reset base time when new dump starts
        }

        return loggerData  // Return parsed data
    }

    // Parses SHT40 temperature and humidity sensor data
    private fun parseSHT40Data(data: ByteArray): SensorData? {
        if (data.size < 5) return null  // Validate length
        val tempInt = data[1].toInt()  // Temperature integer part (byte 1)
        val tempFrac = data[2].toUByte().toInt()  // Temperature fractional part (byte 2)
        val humInt = data[3].toInt()  // Humidity integer part (byte 3)
        val humFrac = data[4].toUByte().toInt()  // Humidity fractional part (byte 4)
        val temperature = tempInt + tempFrac / 10000.0  // Combine parts
        val humidity = humInt + humFrac / 10000.0
        return SensorData.SHT40Data(
            deviceId = data[0].toUByte().toString(),  // Device ID (byte 0)
            temperature = String.format("%.2f", temperature),  // Format to 2 decimal places
            humidity = String.format("%.2f", humidity)
        )
    }

    // Parses LIS2DH accelerometer data
    private fun parseLIS2DHData(data: ByteArray): SensorData? {
        if (data.size < 7) return null  // Validate length
        return SensorData.LIS2DHData(
            deviceId = data[0].toUByte().toString(),  // Device ID
            x = "${data[1].toInt()}.${data[2].toUByte()}",  // X with fractional
            y = "${data[3].toInt()}.${data[4].toUByte()}",  // Y with fractional
            z = "${data[5].toInt()}.${data[6].toUByte()}"  // Z with fractional
        )
    }

    // Parses soil sensor multi-parameter data
    private fun parseSoilSensorData(data: ByteArray): SensorData? {
        if (data.size < 13) return null  // Validate length
        return SensorData.SoilSensorData(
            deviceId = data[0].toUByte().toString(),  // Device ID
            nitrogen = data[1].toUByte().toString(),  // Nitrogen (byte 1)
            phosphorus = data[2].toUByte().toString(),  // Phosphorus (byte 2)
            potassium = data[3].toUByte().toString(),  // Potassium (byte 3)
            moisture = data[4].toUByte().toString(),  // Moisture (byte 4)
            temperature = "${data[5].toUByte()}.${data[6].toUByte()}",  // Temp with fractional
            ec = "${data[7].toUByte()}.${data[8].toUByte()}",  // EC with fractional
            pH = "${data[9].toUByte()}.${data[10].toUByte()}",  // pH with fractional
            salinity = ((data[11].toUByte().toInt() shl 8) or data[12].toUByte().toInt()).toString()  // 16-bit salinity
        )
    }

    // Parses speed and distance sensor data
    private fun parseSDTData(data: ByteArray): SensorData? {
        if (data.size < 6) return null  // Validate length
        return SensorData.SDTData(
            deviceId = data[0].toUByte().toString(),  // Device ID
            speed = "${data[1].toUByte()}.${data[2].toUByte()}",  // Speed with fractional
            distance = "${data[4].toUByte()}.${data[5].toUByte()}"  // Distance with fractional
        )
    }

    // Parses ammonia sensor data
    private fun parseAmmoniaSensorData(data: ByteArray?, deviceAddress: String): SensorData? {
        if (data == null || data.size < 6) return null  // Validate length
        val rawDataString = data.joinToString(" ") { String.format("%02X", it) }  // Convert to hex
        val deviceId = data[0].toUByte().toString()  // Device ID
        val ammoniaPpm = try { data[5].toUByte().toFloat() } catch (e: Exception) { return null }  // Ammonia value (byte 5)
        val ammoniaValue = String.format(Locale.US, "%.1f", ammoniaPpm)  // Format to 1 decimal
        return SensorData.AmmoniaSensorData(
            deviceId = deviceId,
            ammonia = "$ammoniaValue ppm",  // Add unit
            rawData = rawDataString
        )
    }

    // Determines device type from advertisement name
    private fun determineDeviceType(name: String?): String = when {
        name?.contains("SHT", ignoreCase = true) == true -> "SHT40"  // Temperature sensor
        name?.contains("Lux_Data", ignoreCase = true) == true -> "Lux Sensor"  // Light sensor
        name?.contains("SOIL", ignoreCase = true) == true -> "Soil Sensor"  // Soil sensor
        name?.contains("Activity", ignoreCase = true) == true -> "LIS2DH"  // Accelerometer
        name?.contains("Speed", ignoreCase = true) == true -> "SPEED_DISTANCE"  // Speed sensor
        name?.contains("NH", ignoreCase = true) == true -> "Ammonia Sensor"  // Ammonia sensor
        name?.contains("DataLogger", ignoreCase = true) == true -> "DataLogger"  // Data logger
        name?.contains("Data Logger", ignoreCase = true) == true -> "DataLogger"  // Alternative spelling
        name?.contains("DLOG", ignoreCase = true) == true -> "DataLogger"  // Short form
        name?.contains("TempLogger", ignoreCase = true) == true -> "TempLogger"  // Temperature logger
        name?.contains("TLOG", ignoreCase = true) == true -> "TempLogger"  // Short form
        name?.contains("Temp Logger", ignoreCase = true) == true -> "TempLogger"  // Alternative spelling
        else -> "Unknown Device"  // Default for unknown devices
    }

    // Updates device list with new or updated device information
    private fun updateDevice(newDevice: BluetoothDevice, sensorData: SensorData? = null) {
        _devices.update { devices ->
            val existingIndex = devices.indexOfFirst { it.address == newDevice.address }
            if (existingIndex >= 0) {
                // Update existing device
                val updatedList = devices.toMutableList()
                val finalDevice = sensorData?.let { newDevice.copy(sensorData = it) } ?: newDevice
                updatedList[existingIndex] = finalDevice
                updatedList
            } else {
                // Add new device
                devices + (sensorData?.let { newDevice.copy(sensorData = it) } ?: newDevice)
            }
        }
    }

    // Clears all discovered devices
    fun clearDevices() {
        _devices.value = emptyList()
    }

    // Called when ViewModel is cleared (app lifecycle)
    override fun onCleared() {
        super.onCleared()
        stopContinuousScan()  // Stop scanning to save resources
    }
}