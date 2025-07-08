package com.example.znc_app.data

import android.content.Context
import android.util.Log
import com.example.znc_app.ui.theme.TCPCommunicator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TcpRepository(context: Context) {

    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var tcpCommunicator: TCPCommunicator? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _receivedData = MutableStateFlow(intArrayOf())
    val receivedData = _receivedData.asStateFlow()

    fun connect(ip: String, port: Int) {
        if (tcpCommunicator?.isconnect() == true) {
            disconnect()
        }

        _connectionState.value = ConnectionState.Connecting
        tcpCommunicator = TCPCommunicator(ip, port, object : TCPCommunicator.TCPListener {
            override fun onMessageReceived(message: IntArray) {
                if (message.isNotEmpty()) {
                    when {
                        message.size == 1 && message[0] == 0x12 -> {
                            _connectionState.value = ConnectionState.Connected(System.currentTimeMillis())
                            saveLastIp(ip)
                        }
                        message.size == 1 && message[0] == 0x21 -> {
                            _connectionState.value = ConnectionState.Disconnected
                        }
                        else -> {
                            _receivedData.value = message
                        }
                    }
                }
            }

            override fun onError(e: Exception) {
                Log.e("TcpRepository", "Connection error", e)
                _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            }
        })
        tcpCommunicator?.connect()
    }

    fun sendData(data: ByteArray) {
        if (tcpCommunicator?.isconnect() == true) {
            tcpCommunicator?.sendBytes(data)
        } else {
            Log.w("TcpRepository", "Not connected. Cannot send data.")
            _connectionState.value = ConnectionState.Error("Not connected")
        }
    }

    fun disconnect() {
        tcpCommunicator?.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    // --- SharedPreferences Logic ---

    fun saveLastIp(ip: String) {
        sharedPreferences.edit().putString(LAST_IP, ip).apply()
    }

    fun getLastIp(): String {
        return sharedPreferences.getString(LAST_IP, "192.168.1.1") ?: "192.168.1.1"
    }

    fun saveButtonState(col: Int, row: Int) {
        val key = "button_$col"
        sharedPreferences.edit().putInt(key, row).apply()
        Log.i("TcpRepository", "save $key row $row")
    }

    fun readButtonState(): IntArray {
        val states = IntArray(4)
        for (i in 0..3) {
            val key = "button_$i"
            states[i] = sharedPreferences.getInt(key, 0)
        }
        return states
    }

    fun saveColorModes(modes: List<List<Float>>) {
        val serialized = modes.joinToString(";") { list ->
            list.joinToString(",")
        }
        sharedPreferences.edit().putString(KEY_COLOR_MODES, serialized).apply()
    }

    fun loadColorModes(): List<List<Float>> {
        val serialized = sharedPreferences.getString(KEY_COLOR_MODES, null)
        if (serialized == null) {
            return getDefaultColorModes()
        }
        return try {
            serialized.split(';').map { group ->
                group.split(',').map { it.toFloat() }
            }
        } catch (e: Exception) {
            Log.e("TcpRepository", "Failed to parse color modes", e)
            getDefaultColorModes()
        }
    }

    private fun getDefaultColorModes(): List<List<Float>> {
        return listOf(
            listOf(1f, 0f, 0f, 1f), // Red
            listOf(0f, 1f, 0f, 1f), // Green
            listOf(0f, 0f, 1f, 1f), // Blue
            listOf(1f, 1f, 1f, 1f)  // White
        )
    }

    companion object {
        private const val PREFS_NAME = "MyPrefs"
        private const val LAST_IP = "last_ip"
        private const val KEY_COLOR_MODES = "key_color_modes"
    }
}

sealed class ConnectionState {
    data class Connected(val connectionTime: Long) : ConnectionState()
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}