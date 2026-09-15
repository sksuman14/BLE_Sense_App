package com.blesense.app.model

data class DataLoggerConfig(
    val id: String,
    val name: String,
    val advertiserAddress: String, // The physical BLE MAC address
    val deviceId: String,          // The device ID expected in the BLE packet (byte 0)
    val getDataCommand: ByteArray,
    val resetCommand: ByteArray,
    val description: String = "High-precision accelerometer data logger"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DataLoggerConfig
        if (id != other.id) return false
        return true
    }

    override fun hashCode(): Int = id.hashCode()
}
