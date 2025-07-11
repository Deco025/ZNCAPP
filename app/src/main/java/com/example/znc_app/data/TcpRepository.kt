package com.example.znc_app.data

import android.content.Context
import android.util.Log
import com.example.znc_app.ui.theme.TCPCommunicator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class TcpRepository(context: Context) {

    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var tcpCommunicator: TCPCommunicator? = null
    @Volatile
    private var isDisconnecting = false
    private val json = Json { encodeDefaults = true }
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    // The raw received JSON string. ViewModel will be responsible for parsing.
    // Use SharedFlow for events like received data to ensure every event is delivered,
    // even if the content is the same as the previous one.
    private val _receivedData = MutableSharedFlow<String>()
    val receivedData = _receivedData.asSharedFlow()

    fun connect(ip: String, port: Int) {
        if (tcpCommunicator?.isconnect() == true || isDisconnecting) {
            Log.w("TcpRepository", "Cannot connect while another connection is active or disconnecting.")
            return
        }

        _connectionState.value = ConnectionState.Connecting
        tcpCommunicator = TCPCommunicator(ip, port, object : TCPCommunicator.TCPListener {
            override fun onMessageReceived(message: String) {
                // Basic parsing to identify connection status messages
                try {
                    val jsonElement = json.parseToJsonElement(message)
                    if (jsonElement is JsonObject) {
                        val type = jsonElement["type"]?.jsonPrimitive?.content
                        when (type) {
                            "connected" -> {
                                _connectionState.value = ConnectionState.Connected(System.currentTimeMillis())
                                saveLastIp(ip)
                            }
                            "disconnected" -> {
                                if (isDisconnecting) {
                                    // This is an expected disconnection signal
                                    _connectionState.value = ConnectionState.Disconnected
                                    isDisconnecting = false
                                    tcpCommunicator = null
                                } else {
                                    // This is an unexpected disconnection from the server
                                    _connectionState.value = ConnectionState.Disconnected
                                    tcpCommunicator = null
                                }
                            }
                            else -> {
                                // It's a data message, pass it up to the ViewModel
                                _receivedData.tryEmit(message)
                            }
                        }
                    } else {
                        // Not a JSON object, treat as a data message
                        _receivedData.tryEmit(message)
                    }
                } catch (e: Exception) {
                    // Not a valid JSON or doesn't have a "type" field, treat as data
                    _receivedData.tryEmit(message)
                }
            }

            override fun onError(e: Exception) {
                Log.e("TcpRepository", "Connection error", e)
                _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
                isDisconnecting = false
                tcpCommunicator = null
            }
        })
        tcpCommunicator?.connect()
    }

    private fun sendCommand(commandJson: String) {
        // Critical check: Do not send any commands while the disconnection process has started.
        // This prevents race conditions where a final heartbeat packet is sent to a closed socket.
        if (isDisconnecting) {
            return
        }
    
        if (tcpCommunicator?.isconnect() == true) {
            tcpCommunicator?.sendMessage(commandJson)
        } else {
            // This case can happen if a command is sent just as the connection drops,
            // but before the disconnection process is formally initiated.
            Log.w("TcpRepository", "Not connected. Cannot send command.")
            // Do not set state to Error here, as it might override a pending Disconnecting state.
            // The listener's onError or onMessageReceived("disconnected") will handle the state change.
        }
    }

    fun sendUpdateCommand(command: UpdateParamsCommand) {
        val commandJson = json.encodeToString(command)
        sendCommand(commandJson)
    }

    fun sendGetStatusCommand() {
        val commandJson = json.encodeToString(GetStatusCommand())
        sendCommand(commandJson)
    }


    fun disconnect() {
        if (isDisconnecting || tcpCommunicator == null) {
            return // Already in the process of disconnecting or not connected
        }
        isDisconnecting = true
    
        // Set state to Disconnecting. The UI will react to this intermediate state.
        _connectionState.value = ConnectionState.Disconnecting
    
        // Start the background disconnection. The listener will handle the final state change.
        tcpCommunicator?.disconnect()
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
    object Disconnecting : ConnectionState() // New state for the transition
    data class Error(val message: String) : ConnectionState()
}