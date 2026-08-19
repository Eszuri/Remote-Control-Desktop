package com.remotedesktop.client.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remotedesktop.client.data.PermissionState
import com.remotedesktop.client.ui.theme.CardBg
import com.remotedesktop.client.ui.theme.ErrorRed
import com.remotedesktop.client.ui.theme.PrimaryBlue
import com.remotedesktop.client.ui.theme.TextPrimary
import com.remotedesktop.client.ui.theme.TextSecondary

@Composable
fun PermissionCenter(
    state: PermissionState,
    onRequestNotifications: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestCamera: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "App permissions",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Required for background connection and QR scanning",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh permissions",
                        tint = PrimaryBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            PermissionRow(
                icon = Icons.Default.Notifications,
                title = "Notifications",
                description = "Required to keep the PC connection active in the background.",
                granted = state.notificationsGranted,
                required = true,
                actionLabel = if (state.notificationsGranted) null else "Allow",
                onAction = if (state.notificationsGranted) null else onRequestNotifications,
                onOpenSettings = if (state.notificationsGranted) null else onOpenNotificationSettings
            )
            Spacer(modifier = Modifier.height(8.dp))
            PermissionRow(
                icon = Icons.Default.CameraAlt,
                title = "Camera",
                description = "Needed only when scanning a server QR code.",
                granted = state.cameraGranted,
                required = false,
                actionLabel = if (state.cameraGranted) null else "Allow",
                onAction = if (state.cameraGranted) null else onRequestCamera,
                onOpenSettings = null
            )
            Spacer(modifier = Modifier.height(8.dp))
            PermissionRow(
                icon = Icons.AutoMirrored.Filled.Launch,
                title = "Floating window",
                description = "Optional. The current connection does not require an overlay.",
                granted = state.overlayGranted,
                required = false,
                actionLabel = if (state.overlayGranted) null else "Open settings",
                onAction = if (state.overlayGranted) null else onOpenOverlaySettings,
                onOpenSettings = null
            )

            if (!state.requiredGranted) {
                Text(
                    text = "Grant notification access before connecting to a PC.",
                    color = ErrorRed,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    required: Boolean,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    onOpenSettings: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (granted) Icons.Default.CheckCircle else if (required) Icons.Default.WarningAmber else icon,
            contentDescription = null,
            tint = if (granted) Color(0xFF4ADE80) else if (required) ErrorRed else PrimaryBlue,
            modifier = Modifier.padding(end = 10.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (granted) "Enabled • $description" else description,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(actionLabel, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        } else if (onOpenSettings != null) {
            OutlinedButton(
                onClick = onOpenSettings,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("Open settings", color = PrimaryBlue, fontSize = 11.sp)
            }
        }
    }
}
