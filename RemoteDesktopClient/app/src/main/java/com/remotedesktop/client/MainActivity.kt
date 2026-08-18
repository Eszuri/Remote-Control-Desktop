package com.remotedesktop.client

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.remotedesktop.client.data.ConnectionState
import com.remotedesktop.client.ui.screens.ConnectionScreen
import com.remotedesktop.client.ui.screens.RemoteScreen
import com.remotedesktop.client.ui.theme.DarkBg
import com.remotedesktop.client.ui.theme.RemoteDesktopTheme
import com.remotedesktop.client.viewmodel.RemoteViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, false)
        } catch (e: Exception) {
        }

        setContent {
            RemoteDesktopTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBg
                ) {
                    val connectionState by viewModel.connectionState.collectAsState()

                    if (connectionState == ConnectionState.CONNECTED) {
                        RemoteScreen(viewModel = viewModel)
                    } else {
                        ConnectionScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            try {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } catch (e: Exception) {
            }
        }
    }
}
