package com.example.znc_app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.znc_app.data.ConnectionState
import com.example.znc_app.data.TcpRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Timer
import kotlin.concurrent.scheduleAtFixedRate

// 定义UI状态的数据类
data class ColorScreenState(
    val sliders: List<Float> = listOf(0f, 0f, 0f), // Red, Yellow, Blue sliders
    val activeMode: Int = 0, // 0: none, 1-4 for Red, Yellow, Bao, Zi
    val modeValues: List<List<Float>> = listOf(listOf(0f, 0f, 0f), listOf(0f, 0f, 0f), listOf(0f, 0f, 0f), listOf(0f, 0f, 0f)),
    val manualInputValue: String = "0",
    val activeSliderIndex: Int = 0
)

data class UiState(
    val ipAddress: String = "192.168.1.1",
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val selectedButtons: List<Int> = listOf(0, 0, 0, 0), // 每列选中的行号 (0表示未选)
    val colorState: ColorScreenState = ColorScreenState(),
    val delay: String = "---ms",
    val isBusy: Boolean = false // State lock for connect/disconnect
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val tcpRepository = TcpRepository(application)
    private var stateSyncTimer: Timer? = null
    private var timeoutTimer: Timer? = null
    private var lastReceivedTime: Long = 0
    private var sendCnt = 0

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        val initialIp = tcpRepository.getLastIp()
        val initialButtons = tcpRepository.readButtonState().toList()
        _uiState.update { it.copy(ipAddress = initialIp, selectedButtons = initialButtons) }

        viewModelScope.launch {
            tcpRepository.receivedData.collect { data ->
                handleReceivedData(data)
            }
        }

        viewModelScope.launch {
            tcpRepository.connectionState.collect { state ->
                // When connection state changes, update the busy flag
                val isBusy = state is ConnectionState.Connecting
                _uiState.update { it.copy(connectionState = state, isBusy = isBusy) }

                if (state is ConnectionState.Connected) {
                    startStateSyncTimer()
                } else {
                    stopStateSyncTimer()
                }
            }
        }
    }

    fun onIpAddressChanged(newIp: String) {
        _uiState.update { it.copy(ipAddress = newIp) }
    }

    fun onConnectButtonClicked() {
        if (_uiState.value.isBusy) return // Prevent action if busy

        val currentState = _uiState.value.connectionState
        if (currentState is ConnectionState.Connected) {
            _uiState.update { it.copy(isBusy = true) }
            tcpRepository.disconnect()
        } else {
            _uiState.update { it.copy(isBusy = true) }
            val ip = _uiState.value.ipAddress
            val port = 45678
            Log.d("MainViewModel", "Attempting to connect to $ip:$port")
            if (ip.isNotBlank()) {
                tcpRepository.connect(ip, port)
                tcpRepository.saveLastIp(ip)
            } else {
                _uiState.update { it.copy(isBusy = false) } // Re-enable button if IP is blank
            }
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

    // --- Network Data Handling & Timers ---
    private fun handleReceivedData(data: IntArray) {
        lastReceivedTime = System.currentTimeMillis()
        if (data.size == 11 && data[0] == 0xAA.toInt() && data[1] == 0x55.toInt()) {
            val serverButtons = data.slice(3..6)
            if (serverButtons != _uiState.value.selectedButtons) {
                _uiState.update { it.copy(selectedButtons = serverButtons) }
            }
        }
    }
    
    private fun startStateSyncTimer() {
        stopStateSyncTimer()
        lastReceivedTime = System.currentTimeMillis()

        stateSyncTimer = Timer()
        stateSyncTimer?.scheduleAtFixedRate(0, 100) {
            sendFullPacket()
        }

        timeoutTimer = Timer()
        timeoutTimer?.scheduleAtFixedRate(1000, 1000) {
            if (System.currentTimeMillis() - lastReceivedTime > 5000) {
                tcpRepository.disconnect()
            }
        }
    }

    private fun sendFullPacket() {
        val commandId = if (sendCnt < 2) 0x01 else 0x00
        sendCnt = (sendCnt + 1) % 3

        val currentState = _uiState.value
        val colorState = currentState.colorState

        val colorDataBytes = if (colorState.activeMode > 0) {
            val modeSliderValues = colorState.modeValues[colorState.activeMode - 1]
            byteArrayOf(
                modeSliderValues[0].toInt().toByte(),
                modeSliderValues[1].toInt().toByte(),
                modeSliderValues[2].toInt().toByte()
            )
        } else {
            byteArrayOf(0, 0, 0)
        }

        val command = ByteArray(11)
        command[0] = 0xAA.toByte()
        command[1] = 0x55.toByte()
        command[2] = commandId.toByte()
        for (i in 0..3) {
            command[3 + i] = currentState.selectedButtons[i].toByte()
        }
        command[7] = colorDataBytes[0]
        command[8] = colorDataBytes[1]
        command[9] = colorDataBytes[2]
        command[10] = colorState.activeMode.toByte()

        tcpRepository.sendData(command)
    }

    private fun stopStateSyncTimer() {
        stateSyncTimer?.cancel()
        stateSyncTimer = null
        timeoutTimer?.cancel()
        timeoutTimer = null
    }

    private fun disconnect() {
        tcpRepository.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}