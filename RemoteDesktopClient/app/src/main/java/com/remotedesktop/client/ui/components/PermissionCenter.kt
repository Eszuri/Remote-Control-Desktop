package com.remotedesktop.client.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header & Title Trigger (Clickable area to Toggle Hide / Unhide)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Larger Left Icon Badge (Security / Warning)
                    Surface(
                        color = if (state.requiredGranted) Color(0xFF4ADE80).copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.requiredGranted) Icons.Default.Security else Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = if (state.requiredGranted) Color(0xFF4ADE80) else ErrorRed,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "App Permissions",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = if (state.requiredGranted) Color(0xFF14532D) else Color(0xFF7F1D1D),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = if (state.requiredGranted) "Ready" else "Action Needed",
                                    color = if (state.requiredGranted) Color(0xFF4ADE80) else Color(0xFFFCA5A5),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isExpanded) "Tap to collapse permissions" else "Tap to expand & manage permissions",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Larger Reload / Refresh Button
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.size(38.dp)
                    ) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Larger Arrow Up / Down Chevron Trigger
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Hide Permissions" else "Show Permissions",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Expandable / Collapsible Permissions Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(bottom = 12.dp)
                    )

                    PermissionRow(
                        icon = Icons.Default.Notifications,
                        title = "Notifications & Background",
                        description = "Required to keep the PC connection alive 24/7 in background.",
                        granted = state.notificationsGranted,
                        required = true,
                        actionLabel = if (state.notificationsGranted) null else "Allow",
                        onAction = if (state.notificationsGranted) null else onRequestNotifications,
                        onOpenSettings = if (state.notificationsGranted) null else onOpenNotificationSettings
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    PermissionRow(
                        icon = Icons.Default.CameraAlt,
                        title = "Camera (QR Scanner)",
                        description = "Used to scan PC connection QR codes instantly.",
                        granted = state.cameraGranted,
                        required = false,
                        actionLabel = if (state.cameraGranted) null else "Allow",
                        onAction = if (state.cameraGranted) null else onRequestCamera,
                        onOpenSettings = null
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    PermissionRow(
                        icon = Icons.AutoMirrored.Filled.Launch,
                        title = "Floating Window (Overlay)",
                        description = "Optional. Allows overlay access on older Android devices.",
                        granted = state.overlayGranted,
                        required = false,
                        actionLabel = if (state.overlayGranted) null else "Settings",
                        onAction = if (state.overlayGranted) null else onOpenOverlaySettings,
                        onOpenSettings = null
                    )

                    if (!state.requiredGranted) {
                        Text(
                            text = "⚠️ Grant notification access to enable 24/7 background remote control.",
                            color = ErrorRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
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
            modifier = Modifier
                .size(24.dp)
                .padding(end = 4.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (granted) "Granted • $description" else description,
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
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(actionLabel, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        } else if (onOpenSettings != null) {
            OutlinedButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("Settings", color = PrimaryBlue, fontSize = 11.sp)
            }
        }
    }
}
