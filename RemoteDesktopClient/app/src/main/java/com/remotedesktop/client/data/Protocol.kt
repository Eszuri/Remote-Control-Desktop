package com.remotedesktop.client.data

import kotlinx.serialization.Serializable

@Serializable
data class ClientMessage(
    val type: String,
    val pin: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val dx: Int? = null,
    val dy: Int? = null,
    val button: String? = null,
    val action: String? = null,
    val code: Int? = null,
    val text: String? = null,
    val name: String? = null,
    val quality: Int? = null,
    val scale: Double? = null,
    val fps: Int? = null,
    val timestamp: Long? = null
)

@Serializable
data class ServerResponse(
    val type: String,
    val success: Boolean = false,
    val message: String? = null,
    val serverName: String? = null,
    val screenWidth: Int = 1920,
    val screenHeight: Int = 1080,
    val timestamp: Long = 0
)

@Serializable
data class DiscoveredServer(
    val type: String = "REMOTE_SERVER_INFO",
    val serverName: String,
    val ip: String,
    val port: Int,
    val hasPin: Boolean
)

enum class TouchMode {
    TRACKPAD,
    DIRECT_TOUCH
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    ERROR
}
