package com.remotedesktop.client.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.remotedesktop.client.data.ClientMessage
import com.remotedesktop.client.data.ConnectionState
import com.remotedesktop.client.data.ServerResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private var webSocket: WebSocket? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _serverInfo = MutableStateFlow<ServerResponse?>(null)
    val serverInfo: StateFlow<ServerResponse?> = _serverInfo.asStateFlow()

    private val _frameFlow = MutableSharedFlow<Bitmap>(extraBufferCapacity = 1)
    val frameFlow: SharedFlow<Bitmap> = _frameFlow.asSharedFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var decodeOptions = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.RGB_565 // Faster decoding & lower memory
    }

    fun connect(wsUrl: String, pin: String) {
        disconnect()

        _connectionState.value = ConnectionState.CONNECTING
        _errorMessage.value = null

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = ConnectionState.AUTHENTICATING
                // Send authentication message
                send(ClientMessage(type = "auth", pin = pin))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val res = json.decodeFromString<ServerResponse>(text)
                    when (res.type) {
                        "auth_result" -> {
                            if (res.success) {
                                _connectionState.value = ConnectionState.CONNECTED
                                _serverInfo.value = res
                            } else {
                                _connectionState.value = ConnectionState.ERROR
                                _errorMessage.value = res.message ?: "Authentication failed (Invalid PIN)"
                            }
                        }
                        "pong" -> {
                            // Ping-pong latency measurement if needed
                        }
                    }
                } catch (e: Exception) {
                    // Ignore text parsing errors
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                if (data.size > 9 && data[0] == 0x53.toByte()) { // 'S' header
                    // Extract payload starting at index 9
                    try {
                        val jpegBytes = data.copyOfRange(9, data.size)
                        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, decodeOptions)
                        if (bitmap != null) {
                            _frameFlow.tryEmit(bitmap)
                        }
                    } catch (e: Exception) {
                        // Ignore decode error for corrupted frame
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.ERROR
                _errorMessage.value = t.localizedMessage ?: "Connection failed"
            }
        })
    }

    fun send(message: ClientMessage) {
        scope.launch(Dispatchers.IO) {
            try {
                val jsonStr = json.encodeToString(message)
                webSocket?.send(jsonStr)
            } catch (e: Exception) {
                // Ignore send error
            }
        }
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "User disconnected")
            webSocket = null
        } catch (e: Exception) {
        }
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
