package com.blesense.app.repository.game

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.blesense.app.model.game.HeroBeacon
import com.blesense.app.model.game.HeroRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import no.nordicsemi.android.kotlin.ble.core.scanner.BleNumOfMatches
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScanMode
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScannerCallbackType
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScannerMatchMode
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScannerPhy
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScannerSettings
import no.nordicsemi.android.kotlin.ble.scanner.BleScanner
import java.util.concurrent.ConcurrentHashMap

class BleGameRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ble_game_found_prefs", Context.MODE_PRIVATE)

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    private val bleScanner: BleScanner by lazy {
        BleScanner(context)
    }

    private val repoScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var scanJob: Job? = null

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val discoveredBeacons = ConcurrentHashMap<String, HeroBeacon>()
    private val _beaconsFlow = MutableStateFlow<List<HeroBeacon>>(emptyList())
    val beaconsFlow: StateFlow<List<HeroBeacon>> = _beaconsFlow.asStateFlow()

    private val _foundDeviceAddresses = MutableStateFlow<Set<String>>(emptySet())
    val foundDeviceAddresses: StateFlow<Set<String>> = _foundDeviceAddresses.asStateFlow()

    init {
        val saved = prefs.getStringSet("found_devices_keys", emptySet()) ?: emptySet()
        _foundDeviceAddresses.value = saved
    }

    fun markDeviceFound(address: String, name: String) {
        val current = _foundDeviceAddresses.value.toMutableSet()
        current.add(address)
        current.add(name)
        _foundDeviceAddresses.value = current
        prefs.edit().putStringSet("found_devices_keys", current).apply()

        val existing = discoveredBeacons[address]
        if (existing != null) {
            discoveredBeacons[address] = existing.copy(isFound = true)
            publishBeacons()
        }
    }

    fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value && scanJob?.isActive == true) return
        if (!hasPermissions() || bluetoothAdapter?.isEnabled != true) {
            Log.w("BleGameRepo", "Cannot start scan: permissions missing or Bluetooth disabled")
            return
        }

        val scanSettings = BleScannerSettings(
            scanMode = BleScanMode.SCAN_MODE_LOW_LATENCY,
            phy = BleScannerPhy.PHY_LE_ALL_SUPPORTED,
            callbackType = BleScannerCallbackType.CALLBACK_TYPE_ALL_MATCHES,
            matchMode = BleScannerMatchMode.MATCH_MODE_AGGRESSIVE,
            numOfMatches = BleNumOfMatches.MATCH_NUM_MAX_ADVERTISEMENT
        )

        scanJob?.cancel()
        scanJob = repoScope.launch {
            try {
                if (!hasPermissions()) {
                    _isScanning.value = false
                    return@launch
                }
                _isScanning.value = true
                bleScanner.scan(settings = scanSettings)
                    .catch { e ->
                        if (e is SecurityException) {
                            Log.e("BleGameRepo", "Bluetooth scan permission rejected: ${e.message}")
                        } else {
                            Log.e("BleGameRepo", "Error starting scan: ${e.message}")
                        }
                        _isScanning.value = false
                    }
                    .collect { result ->
                        val address = result.device.address
                        val resultData = result.data ?: return@collect
                        val name = resultData.scanRecord?.deviceName ?: try {
                            result.device.name
                        } catch (_: SecurityException) {
                            null
                        }

                        // STRICT FILTERING: Only target game devices are allowed!
                        if (name.isNullOrEmpty() || name !in HeroRegistry.TARGET_DEVICE_NAMES) {
                            return@collect
                        }

                        val rssi = resultData.rssi
                        val isFound = _foundDeviceAddresses.value.contains(address) ||
                                _foundDeviceAddresses.value.contains(name) ||
                                (discoveredBeacons[address]?.isFound == true)

                        val beacon = HeroBeacon(
                            name = name,
                            address = address,
                            rssi = rssi,
                            isFound = isFound,
                            lastSeen = System.currentTimeMillis()
                        )

                        discoveredBeacons[address] = beacon
                        publishBeacons()
                    }
            } catch (e: SecurityException) {
                Log.e("BleGameRepo", "Bluetooth scan permission rejected: ${e.message}")
                _isScanning.value = false
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
    }

    fun refreshScan(clearAll: Boolean = false) {
        stopScan()
        if (clearAll) {
            discoveredBeacons.clear()
        } else {
            // Keep found devices, clear non-found
            discoveredBeacons.values.filter { !it.isFound }.forEach {
                discoveredBeacons.remove(it.address)
            }
        }
        publishBeacons()

        repoScope.launch {
            delay(150)
            startScan()
        }
    }

    private fun publishBeacons() {
        val list = discoveredBeacons.values.toList().sortedWith(
            compareByDescending<HeroBeacon> { it.isFound }
                .thenByDescending { it.rssi }
        )
        _beaconsFlow.value = list
    }

    fun onCleared() {
        stopScan()
        repoScope.cancel()
    }
}
