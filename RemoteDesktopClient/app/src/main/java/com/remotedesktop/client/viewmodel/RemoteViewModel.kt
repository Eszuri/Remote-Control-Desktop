package com.remotedesktop.client.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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

    val wsManager = WebSocketManager(viewModelScope)
    val discoveryManager = LanDiscoveryManager(viewModelScope)

    val connectionState = wsManager.connectionState
    val serverInfo = wsManager.serverInfo
    val errorMessage = wsManager.errorMessage
    val discoveredServers = discoveryManager.discoveredServers
    val isSearching = discoveryManager.isSearching

    private val _currentFrame = MutableStateFlow<Bitmap?>(null)
    val currentFrame: StateFlow<Bitmap?> = _currentFrame.asStateFlow()

    private val _touchMode = MutableStateFlow(TouchMode.TRACKPAD)
    val touchMode: StateFlow<TouchMode> = _touchMode.asStateFlow()

    private val _ipAddress = MutableStateFlow("192.168.1.100")
    val ipAddress: StateFlow<String> = _ipAddress.asStateFlow()

    private val _port = MutableStateFlow("9090")
    val port: StateFlow<String> = _port.asStateFlow()

    private val _pin = MutableStateFlow("123456")
    val pin: StateFlow<String> = _pin.asStateFlow()

    private val _fps = MutableStateFlow(30)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _quality = MutableStateFlow(70)
    val quality: StateFlow<Int> = _quality.asStateFlow()

    private val _hapticEnabled = MutableStateFlow(true)
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

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
                }
            }
        }
    }

    fun setIp(ip: String) { _ipAddress.value = ip }
    fun setPort(p: String) { _port.value = p }
    fun setPin(p: String) { _pin.value = p }
    fun setTouchMode(mode: TouchMode) { _touchMode.value = mode }
    fun toggleHaptic(enabled: Boolean) { _hapticEnabled.value = enabled }

    fun connect() {
        val url = "ws://${_ipAddress.value.trim()}:${_port.value.trim()}"
        wsManager.connect(url, _pin.value.trim())
    }

    fun connectToDiscovered(serverIp: String, serverPort: Int) {
        _ipAddress.value = serverIp
        _port.value = serverPort.toString()
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
        vibrateFeedback()
        wsManager.send(ClientMessage(type = "mouse_click", button = button, action = action))
    }

    fun sendMouseScroll(deltaY: Int, deltaX: Int = 0) {
        wsManager.send(ClientMessage(type = "mouse_scroll", dy = deltaY, dx = deltaX))
    }

    fun sendKeyEvent(code: Int, action: String = "press") {
        vibrateFeedback()
        wsManager.send(ClientMessage(type = "key_event", code = code, action = action))
    }

    fun sendTextInput(text: String) {
        if (text.isNotEmpty()) {
            wsManager.send(ClientMessage(type = "text_input", text = text))
        }
    }

    fun sendShortcut(shortcutName: String) {
        vibrateFeedback()
        wsManager.send(ClientMessage(type = "shortcut", name = shortcutName))
    }

    fun setQualityAndFps(q: Int, f: Int, scale: Double = 1.0) {
        _quality.value = q
        _fps.value = f
        wsManager.send(ClientMessage(type = "quality_change", quality = q, fps = f, scale = scale))
    }

    private fun vibrateFeedback() {
        if (!_hapticEnabled.value) return
        try {
            val context = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(20)
                }
            }
        } catch (e: Exception) {
        }
    }
}
