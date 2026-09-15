package com.blesense.app.util

import com.blesense.app.model.BluetoothDeviceModel
import com.blesense.app.model.SensorData

object MockSensorUtils {
    fun getMockDeviceForTag(tag: String): BluetoothDeviceModel? {
        return when (tag) {
            "SHT40" -> BluetoothDeviceModel("SHT_Preview", "-50", "AA:BB:CC:00:00:01", "1", SensorData.SHT40Data("1", "24.50", "48.20"))
            "LIS3DH" -> BluetoothDeviceModel("Activity_Preview", "-55", "AA:BB:CC:00:00:02", "2", SensorData.LIS3DHData("2", "0.01", "0.02", "1.00"))
            "Soil Sensor" -> BluetoothDeviceModel("Soil_Preview", "-60", "AA:BB:CC:00:00:03", "3", SensorData.SoilSensorData("3", "120", "250", "310", "22", "26.5", "510", "6.8", "110"))
            "Ammonia Sensor" -> BluetoothDeviceModel("NH3_Preview", "-65", "AA:BB:CC:00:00:04", "4", SensorData.AmmoniaSensorData("4", "12.5 ppm", "01 02 03 04 05 06"))
            "sen66" -> BluetoothDeviceModel("SEN66_Preview", "-70", "AA:BB:CC:00:00:05", "5", SensorData.Sen66Data("5", "2.1", "5.4", "8.2", "12.5", "23.4", "45.1", "410", "150", "12"))
            "VEML7700" -> BluetoothDeviceModel("VEML_Preview", "-75", "AA:BB:CC:00:00:06", "6", SensorData.VEML7700Data("6", "850"))
            "VCNL4040" -> BluetoothDeviceModel("VCNL_Preview", "-80", "AA:BB:CC:00:00:07", "7", SensorData.VCNL4040Data("7", "1200"))
            "AHT20" -> BluetoothDeviceModel("AHT_Preview", "-85", "AA:BB:CC:00:00:08", "8", SensorData.AHT20Data("8", "22.10", "55.40"))
            "BME680" -> BluetoothDeviceModel("BME_Preview", "-90", "AA:BB:CC:00:00:09", "9", SensorData.BME680Data("9", "28.30", "42.10", "1013"))
            "TempLogger" -> BluetoothDeviceModel("TemLogger_Preview", "-95", "AA:BB:CC:00:00:10", "10", SensorData.TempLoggerData("10", "21.50", "60.00", 2150, 6000, "AA BB CC", "AA:BB:CC:00:00:10"))
            "STS30" -> BluetoothDeviceModel("STS30_Preview", "-50", "AA:BB:CC:00:00:11", "11", SensorData.STS30Data("11", "25.00", "77.00"))
            "STTS751" -> BluetoothDeviceModel("STTS_Preview", "-52", "AA:BB:CC:00:00:12", "12", SensorData.STTS751Data("12", "26.00", "78.80"))
            "Weather" -> BluetoothDeviceModel("Weather_Preview", "-45", "AA:BB:CC:00:00:13", "13", SensorData.WeatherData("13", "24.00", "50.00", "1500", "1015"))
            "Rain" -> BluetoothDeviceModel("Rain_Preview", "-48", "AA:BB:CC:00:00:14", "14", SensorData.RainData("14", "12.50"))
            "Wind" -> BluetoothDeviceModel("Wind_Preview", "-42", "AA:BB:CC:00:00:15", "15", SensorData.WindData("15", "5.40", "180"))
            else -> null
        }
    }
    
    fun getMockDataForAddress(address: String): SensorData? {
        if (!address.startsWith("AA:BB:CC:00")) return null
        return when (address) {
            "AA:BB:CC:00:00:01" -> SensorData.SHT40Data("1", "24.50", "48.20", address)
            "AA:BB:CC:00:00:02" -> SensorData.LIS3DHData("2", "0.01", "0.02", "1.00", address)
            "AA:BB:CC:00:00:03" -> SensorData.SoilSensorData("3", "120", "250", "310", "22", "26.5", "510", "6.8", "110", address)
            "AA:BB:CC:00:00:04" -> SensorData.AmmoniaSensorData("4", "12.5 ppm", "01 02 03 04 05 06", address)
            "AA:BB:CC:00:00:05" -> SensorData.Sen66Data("5", "2.1", "5.4", "8.2", "12.5", "23.4", "45.1", "410", "150", "12", address)
            "AA:BB:CC:00:00:06" -> SensorData.VEML7700Data("6", "850", address)
            "AA:BB:CC:00:00:07" -> SensorData.VCNL4040Data("7", "1200", address)
            "AA:BB:CC:00:00:08" -> SensorData.AHT20Data("8", "22.10", "55.40", address)
            "AA:BB:CC:00:00:09" -> SensorData.BME680Data("9", "28.30", "42.10", "1013", address)
            "AA:BB:CC:00:00:10" -> SensorData.TempLoggerData("10", "21.50", "60.00", 2150, 6000, "AA BB CC", address)
            "AA:BB:CC:00:00:11" -> SensorData.STS30Data("11", "25.00", "77.00", address)
            "AA:BB:CC:00:00:12" -> SensorData.STTS751Data("12", "26.00", "78.80", address)
            "AA:BB:CC:00:00:13" -> SensorData.WeatherData("13", "24.00", "50.00", "1500", "1015", address)
            "AA:BB:CC:00:00:14" -> SensorData.RainData("14", "12.50", address)
            "AA:BB:CC:00:00:15" -> SensorData.WindData("15", "5.40", "180", address)
            else -> null
        }
    }
}
