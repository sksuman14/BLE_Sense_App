package com.blesense.app.model

data class BluetoothDeviceModel(
    val name: String,
    val rssi: String,
    val address: String,
    val deviceId: String,
    val sensorData: SensorData? = null,
    val scanRecordBytes: ByteArray? = null,
    val isScannable: Boolean = false,
    val isConnectable: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)

data class HistoricalDataEntry(
    val timestamp: Long,
    val sensorData: SensorData?,
    val rawData: ByteArray? = null
)
