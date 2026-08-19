package com.remotedesktop.client

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.remotedesktop.client.data.ConnectionState
import com.remotedesktop.client.data.PermissionState
import com.remotedesktop.client.ui.components.PermissionCenter
import com.remotedesktop.client.ui.screens.ConnectionScreen
import com.remotedesktop.client.ui.screens.RemoteScreen
import com.remotedesktop.client.ui.theme.DarkBg
import com.remotedesktop.client.ui.theme.RemoteDesktopTheme
import com.remotedesktop.client.viewmodel.RemoteViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: RemoteViewModel by viewModels()
    private var permissionState by mutableStateOf(PermissionState())
    private var isInPipModeState by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshPermissionState()
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshPermissionState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, false)
        } catch (e: Exception) {
        }

        refreshPermissionState()

        setContent {
            RemoteDesktopTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBg
                ) {
                    val connectionState by viewModel.connectionState.collectAsState()

                    LaunchedEffect(connectionState, isInPipModeState) {
                        if (!isInPipModeState) {
                            requestedOrientation = if (connectionState == ConnectionState.CONNECTED) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        }
                    }

                    if (connectionState == ConnectionState.CONNECTED) {
                        RemoteScreen(
                            viewModel = viewModel,
                            isInPipMode = isInPipModeState,
                            onEnterPip = { enterFloatingPipMode() }
                        )
                    } else {
                        ConnectionScreen(
                            viewModel = viewModel,
                            permissionState = permissionState,
                            onRequestNotifications = ::requestNotificationPermission,
                            onOpenNotificationSettings = ::openNotificationSettings,
                            onRequestCamera = ::requestCameraPermission,
                            onOpenOverlaySettings = ::openOverlaySettings,
                            onRefreshPermissions = ::refreshPermissionState
                        )
                    }
                }
            }
        }
    }

    fun enterFloatingPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val serverInfo = viewModel.serverInfo.value
                val width = serverInfo?.screenWidth ?: 16
                val height = serverInfo?.screenHeight ?: 9
                val rational = try {
                    Rational(width.coerceAtLeast(1), height.coerceAtLeast(1))
                } catch (e: Exception) {
                    Rational(16, 9)
                }

                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(rational)
                    .build()

                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                try {
                    @Suppress("DEPRECATION")
                    enterPictureInPictureMode()
                } catch (e2: Exception) {
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState = isInPictureInPictureMode
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isInPipModeState) {
            try {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } catch (e: Exception) {
            }
        }
    }

    private fun refreshPermissionState() {
        val notificationsGranted = NotificationManagerCompat.from(this).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        val cameraGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val overlayGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

        permissionState = PermissionState(
            notificationsGranted = notificationsGranted,
            cameraGranted = cameraGranted,
            overlayGranted = overlayGranted
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                openNotificationSettings()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            openNotificationSettings()
        }
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            refreshPermissionState()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openNotificationSettings() {
        val notificationIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        if (notificationIntent.resolveActivity(packageManager) != null) {
            startActivity(notificationIntent)
        } else {
            openAppDetailsSettings()
        }
    }

    private fun openOverlaySettings() {
        val overlayIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        if (overlayIntent.resolveActivity(packageManager) != null) {
            startActivity(overlayIntent)
        } else {
            openAppDetailsSettings()
        }
    }

    private fun openAppDetailsSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            )
        )
    }
}
