package com.blesense.app.viewmodel.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.blesense.app.model.game.BombDefusalStatus
import com.blesense.app.model.game.HeroBeacon
import com.blesense.app.repository.game.BleGameRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class BleGameUiState(
    val isScanning: Boolean = false,
    val deviceList: List<HeroBeacon> = emptyList(),
    val selectedDevice: HeroBeacon? = null,
    val liveRssi: Int = -99,
    val proximityState: String = "Far",
    val isDeviceFoundChecked: Boolean = false,
    val showFoundPopup: Boolean = false,
    val foundDeviceName: String? = null,
    val foundDeviceAddress: String? = null,
    // 3-Minute Superhero Bomb Defusal Mode
    val bombStatus: BombDefusalStatus = BombDefusalStatus.IDLE,
    val bombTimeRemainingSeconds: Int = 180, // 3 minutes = 180s
    val bombTargetDeviceName: String? = null,
    val bombTargetDeviceAddress: String? = null,
    val bombLiveRssi: Int = -99
)

class BleGameViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BleGameRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(BleGameUiState())
    val uiState: StateFlow<BleGameUiState> = _uiState.asStateFlow()

    private var lastState: String? = null
    private var bombTimerJob: Job? = null

    init {
        viewModelScope.launch {
            repository.isScanning.collect { scanning ->
                _uiState.update { it.copy(isScanning = scanning) }
            }
        }

        viewModelScope.launch {
            repository.beaconsFlow.collect { beacons ->
                _uiState.update { state ->
                    val selected = state.selectedDevice
                    val updatedSelected = if (selected != null) {
                        beacons.firstOrNull { it.address == selected.address } ?: selected
                    } else null

                    // Check Bomb Defusal mode if active
                    var updatedBombStatus = state.bombStatus
                    var updatedBombRssi = state.bombLiveRssi

                    if (state.bombStatus == BombDefusalStatus.DEFUSING && state.bombTargetDeviceAddress != null) {
                        val bombBeacon = beacons.firstOrNull {
                            it.address.equals(state.bombTargetDeviceAddress, ignoreCase = true) ||
                                    (!state.bombTargetDeviceName.isNullOrEmpty() && it.name.equals(state.bombTargetDeviceName, ignoreCase = true))
                        }
                        if (bombBeacon != null) {
                            updatedBombRssi = bombBeacon.rssi
                            // WIN CONDITION: RSSI strictly greater than -40 dBm within 3 minutes
                            if (bombBeacon.rssi > -40 && bombBeacon.rssi <= 0 && bombBeacon.rssi != -99) {
                                updatedBombStatus = BombDefusalStatus.DEFUSED
                                bombTimerJob?.cancel()
                                repository.markDeviceFound(bombBeacon.address, bombBeacon.name)
                            }
                        }
                    }

                    // If currently on gameplay screen with selected device, update live state
                    if (updatedSelected != null) {
                        val rssi = updatedSelected.rssi
                        val stateStr = when {
                            rssi in -75..-41 -> "Near"
                            rssi in -40..0 -> {
                                if (lastState != "Found") {
                                    triggerDeviceFound(updatedSelected.name, updatedSelected.address)
                                }
                                "Found"
                            }
                            rssi < -75 -> "Far"
                            else -> state.proximityState
                        }
                        lastState = stateStr

                        state.copy(
                            deviceList = beacons,
                            selectedDevice = updatedSelected,
                            liveRssi = rssi,
                            proximityState = stateStr,
                            isDeviceFoundChecked = (stateStr == "Found"),
                            bombStatus = updatedBombStatus,
                            bombLiveRssi = updatedBombRssi
                        )
                    } else {
                        state.copy(
                            deviceList = beacons,
                            bombStatus = updatedBombStatus,
                            bombLiveRssi = updatedBombRssi
                        )
                    }
                }
            }
        }
    }

    fun startScan() {
        repository.startScan()
    }

    fun stopScan() {
        repository.stopScan()
    }

    fun refreshScan(clearAll: Boolean = false) {
        repository.refreshScan(clearAll = clearAll)
    }

    fun selectDevice(device: HeroBeacon) {
        val isCurrentlyFound = (device.rssi in -40..0)
        val initialProximity = when {
            isCurrentlyFound -> "Found"
            device.rssi in -75..-41 -> "Near"
            else -> "Far"
        }
        lastState = initialProximity
        _uiState.update {
            it.copy(
                selectedDevice = device,
                liveRssi = device.rssi,
                proximityState = initialProximity,
                isDeviceFoundChecked = isCurrentlyFound,
                showFoundPopup = false
            )
        }
        repository.startScan()
    }

    fun clearSelectedDevice() {
        _uiState.update { it.copy(selectedDevice = null, showFoundPopup = false) }
        lastState = null
    }

    private fun triggerDeviceFound(name: String, address: String) {
        repository.markDeviceFound(address, name)
        _uiState.update {
            it.copy(
                showFoundPopup = true,
                foundDeviceName = name,
                foundDeviceAddress = address,
                isDeviceFoundChecked = true
            )
        }
    }

    fun dismissPopup() {
        _uiState.update { it.copy(showFoundPopup = false) }
    }

    // ==================== 3-MINUTE BOMB DEFUSAL METHODS ====================

    fun startBombMission(deviceName: String, deviceAddress: String) {
        bombTimerJob?.cancel()
        _uiState.update {
            it.copy(
                bombStatus = BombDefusalStatus.DEFUSING,
                bombTimeRemainingSeconds = 180, // 3 Minutes countdown
                bombTargetDeviceName = deviceName,
                bombTargetDeviceAddress = deviceAddress,
                bombLiveRssi = -99
            )
        }
        repository.startScan()

        bombTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                _uiState.update { current ->
                    if (current.bombStatus != BombDefusalStatus.DEFUSING) {
                        return@update current
                    }
                    val newTime = current.bombTimeRemainingSeconds - 1
                    if (newTime <= 0) {
                        // 3 Minutes elapsed: BOMB BLASTS! Game Over, User loses!
                        current.copy(
                            bombTimeRemainingSeconds = 0,
                            bombStatus = BombDefusalStatus.EXPLODED
                        )
                    } else {
                        current.copy(bombTimeRemainingSeconds = newTime)
                    }
                }
                if (_uiState.value.bombStatus != BombDefusalStatus.DEFUSING) {
                    break
                }
            }
        }
    }

    fun stopBombMission() {
        bombTimerJob?.cancel()
        bombTimerJob = null
        _uiState.update {
            it.copy(
                bombStatus = BombDefusalStatus.IDLE,
                bombTimeRemainingSeconds = 180,
                bombTargetDeviceName = null,
                bombTargetDeviceAddress = null,
                bombLiveRssi = -99
            )
        }
    }

    fun restartBombMission() {
        val name = _uiState.value.bombTargetDeviceName ?: return
        val address = _uiState.value.bombTargetDeviceAddress ?: return
        startBombMission(name, address)
    }

    override fun onCleared() {
        super.onCleared()
        bombTimerJob?.cancel()
        repository.onCleared()
    }
}

class BleGameViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BleGameViewModel::class.java)) {
            return BleGameViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
    }
}
