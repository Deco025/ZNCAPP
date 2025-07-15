package com.example.znc_app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.znc_app.data.ConnectionState
import com.example.znc_app.data.CommandData
import com.example.znc_app.data.TcpRepository
import com.example.znc_app.data.UpdateParamsCommand
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

// 定义UI状态的数据类
data class ColorScreenState(
    val sliders: List<Float> = listOf(0f, 0f, 0f, 0f), // b*, a*, global_speed, turn_speed
    val activeMode: Int = 0, // 0: none, 1-4 for Red, Yellow, Bao, Zi
    val modeValues: List<List<Float>> = List(4) { listOf(0f, 0f, 0f, 0f) },
    val manualInputValue: String = "0",
    val activeSliderIndex: Int = 0
)

data class UiState(
    val ipSegments: List<String> = listOf("192", "168", "1", "105"),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val selectedButtons: List<Int> = listOf(0, 0, 0, 0), // 每列选中的行号 (0表示未选)
    val colorState: ColorScreenState = ColorScreenState(),
    val delay: String = "-- ms",
    val isBusy: Boolean = false, // State lock for connect/disconnect
    val isActionButtonPressed: Boolean = false
)

/**
 * Represents one-time events sent from the ViewModel to the UI.
 */
sealed class ViewEvent {
    data object Vibrate : ViewEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val tcpRepository = TcpRepository(application)
    private var syncJob: Job? = null
    private val lastReceivedTime = AtomicLong(0) // Kept for delay calculation, but not for timeout
    private val lastSentTime = AtomicLong(0)
    private var syncPacketCounter = 0
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ViewEvent>()
    val events: SharedFlow<ViewEvent> = _events.asSharedFlow()

    val ipAddress: String
        get() = uiState.value.ipSegments.joinToString(".")

    init {
        val initialIp = tcpRepository.getLastIp()
        val initialButtons = tcpRepository.readButtonState().toList()
        val initialColorModes = tcpRepository.loadColorModes()
        _uiState.update {
            it.copy(
                ipSegments = initialIp.split('.').take(4).map { segment -> segment.trim() },
                selectedButtons = initialButtons,
                colorState = it.colorState.copy(modeValues = initialColorModes)
            )
        }

        viewModelScope.launch {
            tcpRepository.receivedData.collect { jsonString ->
                handleReceivedData(jsonString)
            }
        }

        viewModelScope.launch {
            tcpRepository.connectionState.collect { state ->
                // When connection state changes, update the busy flag
                val isBusy = state is ConnectionState.Connecting || state is ConnectionState.Disconnecting
                _uiState.update { it.copy(connectionState = state, isBusy = isBusy) }

                if (state is ConnectionState.Connected) {
                    startStateSyncTimer()
                } else {
                    stopStateSyncTimer()
                }
            }
        }
    }

    fun onIpSegmentChanged(index: Int, value: String) {
        if (index in 0..3) {
            // Allow empty string for clearing the field, otherwise validate
            val sanitizedValue = value.filter { it.isDigit() }
            if (sanitizedValue.isEmpty() || (sanitizedValue.toIntOrNull() ?: 256) <= 255) {
                val newSegments = _uiState.value.ipSegments.toMutableList()
                newSegments[index] = sanitizedValue
                _uiState.update { it.copy(ipSegments = newSegments) }
            }
        }
    }

    fun onConnectButtonClicked() {
        if (_uiState.value.isBusy) return // Prevent action if busy
        
        when (_uiState.value.connectionState) {
            is ConnectionState.Connected -> {
                lastSentTime.set(0)
                // lastReceivedTime is reset when connecting, no need to reset here
                tcpRepository.disconnect()
            }
            is ConnectionState.Disconnected, is ConnectionState.Error -> {
                val ip = ipAddress
                val port = 45678
                Log.d("MainViewModel", "Attempting to connect to $ip:$port")
                if (ip.matches(Regex("(\\d{1,3}\\.){3}\\d{1,3}"))) {
                    tcpRepository.connect(ip, port)
                    tcpRepository.saveLastIp(ip)
                } else {
                    // Re-enable button if IP is invalid by doing nothing,
                    // as isBusy is already false for Disconnected/Error states.
                     Log.w("MainViewModel", "Invalid IP address format: $ip")
                }
            }
            // Do nothing if Connecting or Disconnecting
            else -> {}
        }
    }

    // --- UI Event Handlers (Optimistic Updates Only) ---
    fun onButtonSelected(col: Int, row: Int) {
        val currentSelection = _uiState.value.selectedButtons.toMutableList()
        val newRow = if (currentSelection[col] == row) 0 else row
        currentSelection[col] = newRow
        _uiState.update { it.copy(selectedButtons = currentSelection) }
        tcpRepository.saveButtonState(col, newRow)
    }

    fun onColorSliderChanged(sliderIndex: Int, value: Float) {
        val colorState = _uiState.value.colorState
        val newSliders = colorState.sliders.toMutableList().also { it[sliderIndex] = value }
        val newModeValues = colorState.modeValues.map { it.toMutableList() }.toMutableList().also {
            if (colorState.activeMode > 0) {
                it[colorState.activeMode - 1][sliderIndex] = value
            }
        }
        _uiState.update {
            it.copy(
                colorState = colorState.copy(
                    sliders = newSliders,
                    modeValues = newModeValues,
                    manualInputValue = value.toInt().toString(),
                    activeSliderIndex = sliderIndex
                )
            )
        }
    }

    fun onColorModeSelected(modeIndex: Int) {
        val colorState = _uiState.value.colorState
        val newMode = if (colorState.activeMode == modeIndex) 0 else modeIndex
        val newSliders = if (newMode > 0) colorState.modeValues[newMode - 1] else colorState.sliders
        _uiState.update {
            it.copy(colorState = colorState.copy(activeMode = newMode, sliders = newSliders))
        }
    }
    
    fun onManualColorInput(value: String) {
        val intValue = value.toIntOrNull() ?: 0
        onColorSliderChanged(_uiState.value.colorState.activeSliderIndex, intValue.toFloat().coerceIn(0f, 255f))
    }

    fun onColorValueIncrement() {
        val colorState = _uiState.value.colorState
        val newValue = (colorState.sliders[colorState.activeSliderIndex] + 1).coerceAtMost(255f)
        onColorSliderChanged(colorState.activeSliderIndex, newValue)
    }

    fun onColorValueDecrement() {
        val colorState = _uiState.value.colorState
        val newValue = (colorState.sliders[colorState.activeSliderIndex] - 1).coerceAtLeast(0f)
        onColorSliderChanged(colorState.activeSliderIndex, newValue)
    }

    fun saveColorModes() {
        tcpRepository.saveColorModes(uiState.value.colorState.modeValues)
    }

    fun onActionButtonPressed() {
        _uiState.update { it.copy(isActionButtonPressed = true) }
        viewModelScope.launch {
            _events.emit(ViewEvent.Vibrate)
        }
    }

    fun onActionButtonReleased() {
        _uiState.update { it.copy(isActionButtonPressed = false) }
    }

    // --- Network Data Handling & Timers ---
    private fun handleReceivedData(jsonString: String) {
        if (jsonString.isBlank()) return
    
        Log.d("MainViewModel", "Received JSON: $jsonString")
        lastReceivedTime.set(System.currentTimeMillis())
        if (lastSentTime.get() > 0) {
            val delayValue = System.currentTimeMillis() - lastSentTime.get()
            _uiState.update { it.copy(delay = "${delayValue}ms") }
            lastSentTime.set(0) // Reset after calculating
        }
    
        // TODO: Add full JSON parsing logic here to handle status_response
        // For now, we just log it. The sending part is the priority.
        // Example of how parsing would look:
        // try {
        //     val response = Json.decodeFromString<StatusResponse>(jsonString)
        //     // update _uiState with response.data
        // } catch (e: Exception) {
        //     Log.e("MainViewModel", "Failed to parse received JSON", e)
        // }
    }
    
    private fun startStateSyncTimer() {
        stopStateSyncTimer() // Ensure previous jobs are cancelled
        syncPacketCounter = 0
    
        // Simplified heartbeat job without timeout mechanism
        syncJob = viewModelScope.launch {
            while (isActive) {
                // Every 2 seconds, request status to keep the connection alive
                // and get potential updates from the server.
                // Otherwise, send parameter updates.
                if (syncPacketCounter % 20 == 0) {
                    requestStatus()
                } else {
                    sendFullPacket()
                }
                syncPacketCounter++
                delay(100)
            }
        }
    }

    private fun sendFullPacket() {
        val currentState = _uiState.value
        val colorState = currentState.colorState
    
        // Mapping from UI state to the protocol's data model.
        // This is a temporary mapping based on the available UI sliders.
        // This might need refinement based on actual device behavior.
        val sliders = if (colorState.activeMode > 0) {
            colorState.modeValues[colorState.activeMode - 1]
        } else {
            colorState.sliders
        }
    
        val commandData = CommandData(
            crossroadTurns = currentState.selectedButtons,
            bStar = sliders.getOrElse(0) { 0f }.toInt(),
            aStar = sliders.getOrElse(1) { 0f }.toInt(),
            globalSpeed = sliders.getOrElse(2) { 0f }.toInt(),
            turnSpeed = sliders.getOrElse(3) { 0f }.toInt(),
            imageMode = colorState.activeMode,
            actionButtonHold = if (currentState.isActionButtonPressed) 1 else 0,
            networkDelay = currentState.delay.removeSuffix("ms").trim().toIntOrNull() ?: 0
        )
    
        val commandPacket = UpdateParamsCommand(data = commandData)
    
        lastSentTime.set(System.currentTimeMillis())
        tcpRepository.sendUpdateCommand(commandPacket)
    }
    
    fun requestStatus() {
        lastSentTime.set(System.currentTimeMillis())
        tcpRepository.sendGetStatusCommand()
    }

    private fun stopStateSyncTimer() {
        syncJob?.cancel()
        syncJob = null
    }

    private fun disconnect() {
        tcpRepository.disconnect()
        lastSentTime.set(0)
        _uiState.update { it.copy(delay = "-- ms") }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}