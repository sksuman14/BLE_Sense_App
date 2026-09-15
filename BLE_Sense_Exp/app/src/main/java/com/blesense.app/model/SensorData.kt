package com.blesense.app.model

import java.util.Locale

sealed class SensorData {
    abstract val deviceId: String

    data class SHT40Data(
        override val deviceId: String,
        val temperature: String,
        val humidity: String,
        val deviceAddress: String = ""
    ) : SensorData()

    data class STS30Data(
        override val deviceId: String,
        val temperatureC: String,
        val temperatureF: String,
        val deviceAddress: String = ""
    ) : SensorData()

    data class STTS751Data(
        override val deviceId: String,
        val temperatureC: String,
        val temperatureF: String,
        val deviceAddress: String = ""
    ) : SensorData()

    data class WeatherData(
        override val deviceId: String,
        val temperature: String,
        val humidity: String,
        val lux: String,
        val pressure: String,
        val deviceAddress: String = ""
    ) : SensorData()

    data class RainData(
        override val deviceId: String,
        val rainfall: String,
        val deviceAddress: String = ""
    ) : SensorData()

    data class WindData(
        override val deviceId: String,
        val windSpeed: String,
        val windDirection: String,
        val deviceAddress: String = ""
    ) : SensorData()

    data class LIS3DHData(
        override val deviceId: String,
        val x: String,
        val y: String,
        val z: String,
        val deviceAddress: String = ""
    ) : SensorData()

    data class SoilSensorData(
        override val deviceId: String,
        val nitrogen: String,
        val phosphorus: String,
        val potassium: String,
        val moisture: String,
        val temperature: String,
        val ec: String,
        val pH: String,
        val salinity: String,
        val deviceAddress: String = ""
    ) : SensorData()

    data class AmmoniaSensorData(
        override val deviceId: String,
        val ammonia: String,
        val rawData: String,
        val deviceAddress: String = ""
    ) : SensorData()

    data class VEML7700Data(
        override val deviceId: String,
        val lux: String,
        val deviceAddress: String = ""
    ) : SensorData()

    data class VCNL4040Data(
        override val deviceId: String,
        val lux: String,
        val deviceAddress: String = ""
    ) : SensorData()

    data class AHT20Data(
        override val deviceId: String,
        val temperature: String,
        val humidity: String,
        val deviceAddress: String = ""
    ) : SensorData()

    data class BME680Data(
        override val deviceId: String,
        val temperature: String,
        val humidity: String,
        val pressure: String,
        val deviceAddress: String = ""
    ) : SensorData()

    data class TempLoggerData(
        override val deviceId: String,
        val temperature: String,
        val humidity: String,
        val rawTemperature: Int,
        val rawHumidity: Int,
        val rawData: String,
        val deviceAddress: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : SensorData() {
        val displaySummary: String
            get() = "Temp: $temperature°C, Hum: $humidity%, Device: $deviceId"
    }

    data class AWSData(
        override val deviceId: String,
        val temperature: String,
        val humidity: String,
        val windSpeed: String,
        val windDirection: String,
        val rfCumulative: String,
        val batteryVoltage: String,
        val solarVoltage: String,
        val signalStrength: String,
        val totalErrors: String,
        val error1: String,
        val error2: String,
        val error3: String,
        val error4: String,
        val error5: String,
        val error6: String,
        val error7: String,
        val error8: String,
        val error9: String,
        val error10: String,
        val error11: String,
        val error12: String,
        val error13: String,
        val error14: String,
        val error15: String,
        val error16: String,
        val rawData: String,
        val deviceAddress: String
    ) : SensorData()

    data class Sen66Data(
        override val deviceId: String,
        val pm1: String,
        val pm25: String,
        val pm4: String,
        val pm10: String,
        val temperature: String,
        val humidity: String,
        val co2: String,
        val voc: String,
        val nox: String,
        val deviceAddress: String = "",
        val timestamp: Long = System.currentTimeMillis()
    ) : SensorData() {

        val displaySummary: String
            get() = "PM2.5: $pm25 μg/m³, CO₂: $co2 ppm, Temp: $temperature°C, RH: $humidity%"

        val airQualityIndex: String
            get() = when {
                pm25.toDoubleOrNull()?.let { it <= 12.0 } == true -> "Good"
                pm25.toDoubleOrNull()?.let { it <= 35.4 } == true -> "Moderate"
                pm25.toDoubleOrNull()?.let { it <= 55.4 } == true -> "Unhealthy for Sensitive Groups"
                pm25.toDoubleOrNull()?.let { it <= 150.4 } == true -> "Unhealthy"
                else -> "Very Unhealthy"
            }

        val airQualityColor: Int
            get() = when (airQualityIndex) {
                "Good" -> 0xFF00FF00.toInt()
                "Moderate" -> 0xFFFFFF00.toInt()
                "Unhealthy for Sensitive Groups" -> 0xFFFFA500.toInt()
                "Unhealthy" -> 0xFFFF0000.toInt()
                else -> 0xFF800080.toInt()
            }

        val co2Quality: String
            get() = when {
                co2.toIntOrNull()?.let { it < 800 } == true -> "Good"
                co2.toIntOrNull()?.let { it < 1200 } == true -> "Fair"
                co2.toIntOrNull()?.let { it < 2000 } == true -> "Poor"
                else -> "Very Poor"
            }
    }

    data class DataLoggerData(
        override val deviceId: String,
        val currentPacketId: Int, // Maps to totalPackets count
        val lastPacketId: Int,    // Maps to unique packetId
        val timestamp: Long,
        val rawData: ByteArray,
        val round: Int = 1,
        val payloadAccel: List<Triple<Int, Int, Int>> = emptyList(),
        val arrivalTime: Long = System.currentTimeMillis(),
        val nodeId: Int = 0,
        val bundleId: Int = 0,
        val deviceAddress: String = ""
    ) : SensorData() {

        val rawDataHex: String get() = rawData.joinToString(" ") { "%02X".format(it) }

        /**
         * Optimized lazy parser for acceleration data.
         * Parses Mfg3 to Mfg242 (240 bytes = 80 x,y,z points)
         */
        fun getParsedPoints(): List<Triple<Int, Int, Int>> {
            if (rawData.size < 243) return emptyList()
            val list = ArrayList<Triple<Int, Int, Int>>(80)
            var idx = 3 // mfg3 starts at byte 3
            val payloadEnd = 243
            while (idx + 2 < payloadEnd) {
                list.add(Triple(rawData[idx++].toInt(), rawData[idx++].toInt(), rawData[idx++].toInt()))
            }
            return list
        }

        val displaySummary: String
            get() = "Packet: $currentPacketId (last: $lastPacketId), Round: $round"

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DataLoggerData) return false
            if (deviceId != other.deviceId) return false
            if (currentPacketId != other.currentPacketId) return false
            if (lastPacketId != other.lastPacketId) return false
            if (timestamp != other.timestamp) return false
            if (!rawData.contentEquals(other.rawData)) return false
            if (round != other.round) return false
            return true
        }

        override fun hashCode(): Int {
            var result = deviceId.hashCode()
            result = 31 * result + currentPacketId
            result = 31 * result + lastPacketId
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + rawData.contentHashCode()
            result = 31 * result + round
            return result
        }
    }
}



