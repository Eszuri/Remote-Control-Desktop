package com.remotedesktop.client.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.remotedesktop.client.data.ClientMessage
import com.remotedesktop.client.data.ConnectionState
import com.remotedesktop.client.data.ServerResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

class WebSocketManager(
    private val scope: CoroutineScope
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(5, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    @Volatile
    private var lastMessageTimestamp = 0L

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _serverInfo = MutableStateFlow<ServerResponse?>(null)
    val serverInfo: StateFlow<ServerResponse?> = _serverInfo.asStateFlow()

    private val _measuredLatency = MutableStateFlow(1L)
    val measuredLatency: StateFlow<Long> = _measuredLatency.asStateFlow()

    // Conflated frame flow (drop stale frames immediately if decoder/render is busy)
    private val _frameFlow = MutableSharedFlow<Bitmap>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val frameFlow: SharedFlow<Bitmap> = _frameFlow.asSharedFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val decodeOptions = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.RGB_565 // 50% memory saving & faster decoding
        inMutable = true
    }

    fun connect(wsUrl: String) {
        disconnect()

        _connectionState.value = ConnectionState.CONNECTING
        _errorMessage.value = null
        lastMessageTimestamp = System.currentTimeMillis()

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                lastMessageTimestamp = System.currentTimeMillis()
                _connectionState.value = ConnectionState.CONNECTED
                sendDirect(ClientMessage(type = "auth"))
                startPingLoop()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                lastMessageTimestamp = System.currentTimeMillis()
                try {
                    val res = json.decodeFromString<ServerResponse>(text)
                    when (res.type) {
                        "auth_result" -> {
                            if (res.success) {
                                _connectionState.value = ConnectionState.CONNECTED
                                _serverInfo.value = res
                            } else {
                                _errorMessage.value = res.message ?: "Koneksi ditolak oleh server"
                                disconnect(forcedReason = _errorMessage.value)
                            }
                        }
                        "server_stopped" -> {
                            val msg = res.message ?: "Server remote telah dimatikan dari sisi PC host."
                            _errorMessage.value = msg
                            disconnect(forcedReason = msg)
                        }
                        "pong" -> {
                            val now = System.currentTimeMillis()
                            val rtt = (now - res.timestamp).coerceAtLeast(1)
                            _measuredLatency.value = rtt
                            Log.i("RemoteDesktop", "[Latency Monitor] Ping RTT: ${rtt}ms")
                        }
                    }
                } catch (e: Exception) {
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                lastMessageTimestamp = System.currentTimeMillis()
                val data = bytes.toByteArray()
                if (data.size > 9 && data[0] == 0x53.toByte()) { // 'S' header
                    try {
                        val jpegBytes = data.copyOfRange(9, data.size)
                        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, decodeOptions)
                        if (bitmap != null) {
                            _frameFlow.tryEmit(bitmap)
                        }
                    } catch (e: Exception) {
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                val msg = if (reason.isNotBlank() && reason != "User disconnected") reason else "Server terputus dari sisi PC host."
                if (_errorMessage.value == null) {
                    _errorMessage.value = msg
                }
                _connectionState.value = ConnectionState.DISCONNECTED
                stopPingLoop()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                val msg = if (reason.isNotBlank() && reason != "User disconnected") reason else "Server terputus dari sisi PC host."
                if (_errorMessage.value == null) {
                    _errorMessage.value = msg
                }
                _connectionState.value = ConnectionState.DISCONNECTED
                stopPingLoop()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val msg = if (_errorMessage.value != null) _errorMessage.value else "Koneksi terputus: Server PC host tidak aktif atau jaringan terputus."
                _errorMessage.value = msg
                _connectionState.value = ConnectionState.ERROR
                stopPingLoop()
            }
        })
    }

    private fun startPingLoop() {
        stopPingLoop()
        lastMessageTimestamp = System.currentTimeMillis()

        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000)
                if (_connectionState.value == ConnectionState.CONNECTED) {
                    val now = System.currentTimeMillis()

                    // Watchdog: If no response/frame received from server for > 3.5 seconds, trigger disconnect immediately
                    if (now - lastMessageTimestamp > 3500) {
                        Log.w("RemoteDesktop", "Watchdog: Server unresponsive for >3.5s. Disconnecting.")
                        val msg = "Koneksi terputus: Server PC host tidak merespons."
                        _errorMessage.value = msg
                        disconnect(forcedReason = msg)
                        break
                    }

                    val pingMsg = ClientMessage(
                        type = "ping",
                        timestamp = now,
                        clientLatency = _measuredLatency.value
                    )
                    sendDirect(pingMsg)
                }
            }
        }
    }

    private fun stopPingLoop() {
        pingJob?.cancel()
        pingJob = null
    }

    // Direct synchronous send (zero dispatch latency, non-blocking via OkHttp)
    fun sendDirect(message: ClientMessage) {
        try {
            val jsonStr = json.encodeToString(message)
            webSocket?.send(jsonStr)
        } catch (e: Exception) {
        }
    }

    fun send(message: ClientMessage) {
        sendDirect(message)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun disconnect(forcedReason: String? = null) {
        stopPingLoop()
        try {
            webSocket?.close(1000, forcedReason ?: "User disconnected")
            webSocket = null
        } catch (e: Exception) {
        }
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
