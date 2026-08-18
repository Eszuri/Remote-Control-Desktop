package com.remotedesktop.client.network

import com.remotedesktop.client.data.DiscoveredServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class LanDiscoveryManager(
    private val scope: CoroutineScope
) {
    private val _discoveredServers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val discoveredServers: StateFlow<List<DiscoveredServer>> = _discoveredServers.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var discoveryJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true }

    fun startDiscovery(discoveryPort: Int = 9091) {
        if (_isSearching.value) return

        _isSearching.value = true
        _discoveredServers.value = emptyList()

        discoveryJob = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.broadcast = true
                socket.soTimeout = 2000

                val broadcastMessage = "DISCOVER_REMOTE_SERVER"
                val sendData = broadcastMessage.toByteArray()
                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                val sendPacket = DatagramPacket(sendData, sendData.size, broadcastAddr, discoveryPort)

                // Send 3 search pulses
                for (i in 0 until 3) {
                    if (!isActive) break
                    socket.send(sendPacket)

                    val buffer = ByteArray(2048)
                    val receivePacket = DatagramPacket(buffer, buffer.size)

                    val startTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startTime < 1500 && isActive) {
                        try {
                            socket.receive(receivePacket)
                            val responseText = String(receivePacket.data, 0, receivePacket.length)
                            val senderIp = receivePacket.address.hostAddress ?: ""

                            if (responseText.contains("REMOTE_SERVER_INFO")) {
                                val server = json.decodeFromString<DiscoveredServer>(responseText)
                                val serverWithIp = server.copy(ip = senderIp)

                                val currentList = _discoveredServers.value.toMutableList()
                                if (currentList.none { it.ip == serverWithIp.ip && it.port == serverWithIp.port }) {
                                    currentList.add(serverWithIp)
                                    _discoveredServers.value = currentList
                                }
                            }
                        } catch (e: Exception) {
                            // Timeout
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore socket errors
            } finally {
                socket?.close()
                _isSearching.value = false
            }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        _isSearching.value = false
    }
}
