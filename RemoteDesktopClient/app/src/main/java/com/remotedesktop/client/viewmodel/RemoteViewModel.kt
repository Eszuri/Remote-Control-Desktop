package com.remotedesktop.client.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.remotedesktop.client.data.ClientMessage
import com.remotedesktop.client.data.ConnectionState
import com.remotedesktop.client.data.TouchMode
import com.remotedesktop.client.network.LanDiscoveryManager
import com.remotedesktop.client.network.WebSocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RemoteViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("remote_desktop_prefs", Context.MODE_PRIVATE)

    val wsManager = WebSocketManager(viewModelScope)
    val discoveryManager = LanDiscoveryManager(viewModelScope)

    val connectionState = wsManager.connectionState
    val serverInfo = wsManager.serverInfo
    val errorMessage = wsManager.errorMessage
    val discoveredServers = discoveryManager.discoveredServers
    val isSearching = discoveryManager.isSearching
    val measuredLatency = wsManager.measuredLatency

    private val _currentFrame = MutableStateFlow<Bitmap?>(null)
    val currentFrame: StateFlow<Bitmap?> = _currentFrame.asStateFlow()

    private val savedTouchModeName = prefs.getString("pref_touch_mode", TouchMode.TRACKPAD.name) ?: TouchMode.TRACKPAD.name
    private val initialTouchMode = try { TouchMode.valueOf(savedTouchModeName) } catch (e: Exception) { TouchMode.TRACKPAD }
    private val _touchMode = MutableStateFlow(initialTouchMode)
    val touchMode: StateFlow<TouchMode> = _touchMode.asStateFlow()

    private val _ipAddress = MutableStateFlow(prefs.getString("pref_ip", "192.168.1.100") ?: "192.168.1.100")
    val ipAddress: StateFlow<String> = _ipAddress.asStateFlow()

    private val _port = MutableStateFlow(prefs.getString("pref_port", "9090") ?: "9090")
    val port: StateFlow<String> = _port.asStateFlow()

    private val _fps = MutableStateFlow(prefs.getInt("pref_fps", 60))
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _quality = MutableStateFlow(prefs.getInt("pref_quality", 70))
    val quality: StateFlow<Int> = _quality.asStateFlow()

    private var frameCounter = 0
    private var lastFpsTimestamp = System.currentTimeMillis()
    private val _measuredFps = MutableStateFlow(0)
    val measuredFps: StateFlow<Int> = _measuredFps.asStateFlow()

    init {
        viewModelScope.launch {
            wsManager.frameFlow.collect { bitmap ->
                _currentFrame.value = bitmap
                frameCounter++
                val now = System.currentTimeMillis()
                if (now - lastFpsTimestamp >= 1000) {
                    _measuredFps.value = frameCounter
                    frameCounter = 0
                    lastFpsTimestamp = now
                    Log.i("RemoteDesktop", "[1s Performance] FPS: ${_measuredFps.value} | Latency: ${measuredLatency.value} ms")
                }
            }
        }
    }

    fun setIp(ip: String) {
        _ipAddress.value = ip
        prefs.edit().putString("pref_ip", ip).apply()
    }

    fun setPort(p: String) {
        _port.value = p
        prefs.edit().putString("pref_port", p).apply()
    }

    fun setTouchMode(mode: TouchMode) {
        _touchMode.value = mode
        prefs.edit().putString("pref_touch_mode", mode.name).apply()
    }

    fun connect() {
        val ip = _ipAddress.value.trim()
        val p = _port.value.trim()
        prefs.edit()
            .putString("pref_ip", ip)
            .putString("pref_port", p)
            .apply()
        val url = "ws://$ip:$p"
        wsManager.connect(url)
    }

    fun connectToDiscovered(serverIp: String, serverPort: Int) {
        setIp(serverIp)
        setPort(serverPort.toString())
        connect()
    }

    fun disconnect() {
        wsManager.disconnect()
        _currentFrame.value = null
    }

    fun searchServers() {
        discoveryManager.startDiscovery()
    }

    // Input Actions
    fun sendMouseMoveDelta(dx: Int, dy: Int) {
        wsManager.send(ClientMessage(type = "mouse_move_delta", dx = dx, dy = dy))
    }

    fun sendMouseMoveAbsolute(normX: Double, normY: Double) {
        wsManager.send(ClientMessage(type = "mouse_move", x = normX, y = normY))
    }

    fun sendMouseClick(button: String = "left", action: String = "click") {
        wsManager.send(ClientMessage(type = "mouse_click", button = button, action = action))
    }

    fun sendMouseScroll(deltaY: Int, deltaX: Int = 0) {
        wsManager.send(ClientMessage(type = "mouse_scroll", dy = deltaY, dx = deltaX))
    }

    fun sendKeyEvent(code: Int, action: String = "press") {
        wsManager.send(ClientMessage(type = "key_event", code = code, action = action))
    }

    fun sendTextInput(text: String) {
        if (text.isNotEmpty()) {
            wsManager.send(ClientMessage(type = "text_input", text = text))
        }
    }

    fun sendShortcut(shortcutName: String) {
        wsManager.send(ClientMessage(type = "shortcut", name = shortcutName))
    }

    fun setQualityAndFps(q: Int, f: Int, scale: Double = 1.0) {
        _quality.value = q
        _fps.value = f
        prefs.edit()
            .putInt("pref_quality", q)
            .putInt("pref_fps", f)
            .apply()
        wsManager.send(ClientMessage(type = "quality_change", quality = q, fps = f, scale = scale))
    }

}
