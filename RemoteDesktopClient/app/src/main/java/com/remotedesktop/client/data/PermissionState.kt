package com.remotedesktop.client.data

data class PermissionState(
    val notificationsGranted: Boolean = false,
    val cameraGranted: Boolean = false,
    val overlayGranted: Boolean = false
) {
    val requiredGranted: Boolean
        get() = notificationsGranted
}
