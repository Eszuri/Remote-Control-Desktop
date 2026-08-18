package com.remotedesktop.client.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotedesktop.client.data.ConnectionState
import com.remotedesktop.client.ui.components.QrScannerDialog
import com.remotedesktop.client.ui.theme.*
import com.remotedesktop.client.viewmodel.RemoteViewModel

@Composable
fun ConnectionScreen(
    viewModel: RemoteViewModel,
    modifier: Modifier = Modifier
) {
    val ip by viewModel.ipAddress.collectAsState()
    val port by viewModel.port.collectAsState()
    val pin by viewModel.pin.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val discoveredServers by viewModel.discoveredServers.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    var showQrScanner by remember { mutableStateOf(false) }

    if (showQrScanner) {
        QrScannerDialog(
            onDismiss = { showQrScanner = false },
            onQrScanned = { scannedIp, scannedPort, scannedPin ->
                viewModel.setIp(scannedIp)
                viewModel.setPort(scannedPort)
                viewModel.setPin(scannedPin)
                viewModel.connect()
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(24.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Computer,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "PC Remote Control",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Connect to Windows Server",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            // Quick Scan QR Button in Header
            FilledTonalButton(
                onClick = { showQrScanner = true },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = CardBg),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR", tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan QR", color = PrimaryBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Error message banner
        if (errorMessage != null) {
            Surface(
                color = ErrorRed.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ErrorRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = ErrorRed
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = ErrorRed,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Connection Form Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Connection Details",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    TextButton(
                        onClick = { showQrScanner = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Camera Scan", color = PrimaryBlue, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = ip,
                    onValueChange = { viewModel.setIp(it) },
                    label = { Text("Server IP Address") },
                    leadingIcon = { Icon(Icons.Default.Wifi, contentDescription = null, tint = PrimaryBlue) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = port,
                        onValueChange = { viewModel.setPort(it) },
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { viewModel.setPin(it) },
                        label = { Text("PIN Code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { viewModel.connect() },
                    enabled = connectionState != ConnectionState.CONNECTING && connectionState != ConnectionState.AUTHENTICATING,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.AUTHENTICATING) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Connecting...", color = Color.Black, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // LAN Auto Discovery Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Nearby PC Servers (Wi-Fi)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            TextButton(
                onClick = { viewModel.searchServers() },
                enabled = !isSearching
            ) {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PrimaryBlue)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scanning...", color = PrimaryBlue, fontSize = 13.sp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Scan LAN", color = PrimaryBlue, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (discoveredServers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSearching) "Searching for Windows servers on local network..." else "No servers found yet. Tap 'Scan QR' or 'Scan LAN'.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(discoveredServers) { server ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.connectToDiscovered(server.ip, server.port)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = PrimaryBlue.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.DesktopWindows, contentDescription = null, tint = PrimaryBlue)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = server.serverName,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "${server.ip}:${server.port}",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Button(
                                onClick = { viewModel.connectToDiscovered(server.ip, server.port) },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Connect", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
