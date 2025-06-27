package com.example.znc_app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    val sliders: List<Float> = listOf(0f, 0f), // Red, Yellow sliders
    val activeMode: Int = 0, // 0: none, 1-4 for Red, Yellow, Bao, Zi
    val modeValues: List<List<Float>> = listOf(listOf(0f, 0f), listOf(0f, 0f), listOf(0f, 0f), listOf(0f, 0f)),
    val manualInputValue: String = "0",
    val activeSliderIndex: Int = 0
)

data class UiState(
    val ipAddress: String = "192.168.1.1",
    val connectionState: com.example.znc_app.data.ConnectionState = com.example.znc_app.data.ConnectionState.Disconnected,
    val selectedButtons: List<Int> = listOf(0, 0, 0, 0), // 每列选中的行号 (0表示未选)
    val colorState: ColorScreenState = ColorScreenState(),
    val delay: String = "---ms"
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val tcpRepository = TcpRepository(application)
    private var heartBeatTimer: Timer? = null

    // UI状态流
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // 用于UI回滚的临时状态
    private var lastSentButtonState: List<Int>? = null

    init {
        // 初始化时从SharedPreferences加载状态
        val initialIp = tcpRepository.getLastIp()
        val initialButtons = tcpRepository.readButtonState().toList()
        _uiState.update { it.copy(ipAddress = initialIp, selectedButtons = initialButtons) }

        // 监听Repository的数据
        viewModelScope.launch {
            tcpRepository.receivedData.collect { data ->
                handleReceivedData(data)
            }
        }

        viewModelScope.launch {
            tcpRepository.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                if (state is com.example.znc_app.data.ConnectionState.Connected) {
                    startHeartbeat()
                } else {
                    stopHeartbeat()
                }
            }
        }
    }

    fun onIpAddressChanged(newIp: String) {
        _uiState.update { it.copy(ipAddress = newIp) }
    }

    fun connect() {
        val ip = _uiState.value.ipAddress
        val port = 45678 // 将端口号修改为45678
        Log.d("MainViewModel", "Attempting to connect to $ip:$port")
        if (ip.isNotBlank()) {
            tcpRepository.connect(ip, port)
            tcpRepository.saveLastIp(ip)
        }
    }

    fun disconnect() {
        tcpRepository.disconnect()
    }

    // --- Button Screen Logic ---
    fun onButtonSelected(col: Int, row: Int) {
        val currentSelection = _uiState.value.selectedButtons.toMutableList()
        val previousSelection = currentSelection.toList() // 保存当前状态以备回滚

        val newRow = if (currentSelection[col] == row) 0 else row
        currentSelection[col] = newRow

        _uiState.update { it.copy(selectedButtons = currentSelection) }
        lastSentButtonState = previousSelection

        val command = byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x00.toByte()) +
                currentSelection.map { it.toByte() }.toByteArray()
        tcpRepository.sendData(command)
        
        tcpRepository.saveButtonState(col, newRow)
    }

    // --- Color Screen Logic ---
    fun onColorSliderChanged(sliderIndex: Int, value: Float) {
        val newSliders = _uiState.value.colorState.sliders.toMutableList()
        newSliders[sliderIndex] = value
        
        val activeMode = _uiState.value.colorState.activeMode
        val newModeValues = _uiState.value.colorState.modeValues.map { it.toMutableList() }.toMutableList()
        if (activeMode > 0) {
            newModeValues[activeMode - 1][sliderIndex] = value
        }

        _uiState.update {
            it.copy(
                colorState = it.colorState.copy(
                    sliders = newSliders,
                    modeValues = newModeValues,
                    manualInputValue = value.toInt().toString(),
                    activeSliderIndex = sliderIndex
                )
            )
        }
    }

    fun onColorModeSelected(modeIndex: Int) {
        val currentMode = _uiState.value.colorState.activeMode
        val newMode = if (currentMode == modeIndex) 0 else modeIndex

        val newSliders = if (newMode > 0) {
            _uiState.value.colorState.modeValues[newMode - 1]
        } else {
            _uiState.value.colorState.sliders // Or reset to a default
        }

        _uiState.update {
            it.copy(
                colorState = it.colorState.copy(
                    activeMode = newMode,
                    sliders = newSliders
                )
            )
        }
    }
    
    fun onManualColorInput(value: String) {
        val intValue = value.toIntOrNull() ?: 0
        val clampedValue = intValue.coerceIn(0, 255)
        val activeSlider = _uiState.value.colorState.activeSliderIndex
        onColorSliderChanged(activeSlider, clampedValue.toFloat())
    }

    fun onColorValueIncrement() {
        val activeSlider = _uiState.value.colorState.activeSliderIndex
        val currentValue = _uiState.value.colorState.sliders[activeSlider]
        val newValue = (currentValue + 1).coerceAtMost(255f)
        onColorSliderChanged(activeSlider, newValue)
    }

    fun onColorValueDecrement() {
        val activeSlider = _uiState.value.colorState.activeSliderIndex
        val currentValue = _uiState.value.colorState.sliders[activeSlider]
        val newValue = (currentValue - 1).coerceAtLeast(0f)
        onColorSliderChanged(activeSlider, newValue)
    }


    // --- Network Data Handling ---
    private fun handleReceivedData(data: IntArray) {
        if (data.contentEquals(intArrayOf(0xAA, 0x55, 0x01))) { // Heartbeat ack
            val connectionTime = (uiState.value.connectionState as? com.example.znc_app.data.ConnectionState.Connected)?.connectionTime ?: return
            val currentDelay = System.currentTimeMillis() - connectionTime
            _uiState.update { it.copy(delay = "$currentDelay ms") }
        } else if (data.size == 4) { // Button state sync
            val serverState = data.toList()
            lastSentButtonState = null
            _uiState.update { it.copy(selectedButtons = serverState) }
        }
        // TODO: Handle color data sync from server
    }
    
    private fun startHeartbeat() {
        stopHeartbeat()
        heartBeatTimer = Timer()
        heartBeatTimer?.scheduleAtFixedRate(0, 5000) {
            val currentState = _uiState.value
            val colorState = currentState.colorState
            val colorCommand = if (colorState.activeMode > 0) {
                colorState.modeValues[colorState.activeMode - 1].map { it.toInt().toByte() }
            } else {
                listOf<Byte>(0, 0)
            }

            val heartBeatCommand = byteArrayOf(
                0xAA.toByte(), 0x55.toByte(), 0x00.toByte()
            ) + currentState.selectedButtons.map { it.toByte() }.toByteArray() +
                    ByteArray(colorCommand.size) { colorCommand[it] } + colorState.activeMode.toByte()

            tcpRepository.sendData(heartBeatCommand)
        }
    }

    private fun stopHeartbeat() {
        heartBeatTimer?.cancel()
        heartBeatTimer = null
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}