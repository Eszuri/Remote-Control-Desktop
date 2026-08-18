package com.remotedesktop.client.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.remotedesktop.client.data.TouchMode
import com.remotedesktop.client.ui.theme.*
import com.remotedesktop.client.viewmodel.RemoteViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun RemoteScreen(
    viewModel: RemoteViewModel,
    modifier: Modifier = Modifier
) {
    val touchMode by viewModel.touchMode.collectAsState()
    val measuredFps by viewModel.measuredFps.collectAsState()
    val measuredLatency by viewModel.measuredLatency.collectAsState()
    val serverInfo by viewModel.serverInfo.collectAsState()

    var showControls by remember { mutableStateOf(false) }
    var showKeyboardInput by remember { mutableStateOf(false) }
    var keyboardText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Hardware-Accelerated Zero-Latency SurfaceView Screen
        DirectSurfaceRenderer(
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(touchMode) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (touchMode == TouchMode.DIRECT_TOUCH) {
                                val normX = (offset.x / size.width.toFloat()).toDouble().coerceIn(0.0, 1.0)
                                val normY = (offset.y / size.height.toFloat()).toDouble().coerceIn(0.0, 1.0)
                                viewModel.sendMouseMoveAbsolute(normX, normY)
                                viewModel.sendMouseClick("left", "click")
                            } else {
                                viewModel.sendMouseClick("left", "click")
                            }
                        },
                        onDoubleTap = { offset ->
                            if (touchMode == TouchMode.DIRECT_TOUCH) {
                                val normX = (offset.x / size.width.toFloat()).toDouble().coerceIn(0.0, 1.0)
                                val normY = (offset.y / size.height.toFloat()).toDouble().coerceIn(0.0, 1.0)
                                viewModel.sendMouseMoveAbsolute(normX, normY)
                                viewModel.sendMouseClick("left", "dblclick")
                            } else {
                                viewModel.sendMouseClick("left", "dblclick")
                            }
                        },
                        onLongPress = { offset ->
                            if (touchMode == TouchMode.DIRECT_TOUCH) {
                                val normX = (offset.x / size.width.toFloat()).toDouble().coerceIn(0.0, 1.0)
                                val normY = (offset.y / size.height.toFloat()).toDouble().coerceIn(0.0, 1.0)
                                viewModel.sendMouseMoveAbsolute(normX, normY)
                                viewModel.sendMouseClick("right", "click")
                            } else {
                                viewModel.sendMouseClick("right", "click")
                            }
                        }
                    )
                }
                .pointerInput(touchMode) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (touchMode == TouchMode.TRACKPAD) {
                            val dx = (dragAmount.x * 1.5f).roundToInt()
                            val dy = (dragAmount.y * 1.5f).roundToInt()
                            viewModel.sendMouseMoveDelta(dx, dy)
                        } else {
                            val normX = (change.position.x / size.width.toFloat()).toDouble().coerceIn(0.0, 1.0)
                            val normY = (change.position.y / size.height.toFloat()).toDouble().coerceIn(0.0, 1.0)
                            viewModel.sendMouseMoveAbsolute(normX, normY)
                        }
                    }
                }
        )

        // Single Floating Toggle Button (Controls Hide / Unhide)
        Surface(
            color = if (showControls) PrimaryBlue.copy(alpha = 0.9f) else CardBg.copy(alpha = 0.4f),
            shape = CircleShape,
            border = BorderStroke(1.dp, if (showControls) PrimaryBlue else Color(0x55334155)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .size(40.dp)
        ) {
            IconButton(
                onClick = { showControls = !showControls },
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = if (showControls) Icons.Default.Fullscreen else Icons.Default.Tune,
                    contentDescription = if (showControls) "Hide Controls" else "Show Controls",
                    tint = if (showControls) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Top HUD Overlay (Animated Visibility when showControls is true)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Row(
                modifier = Modifier
                    .padding(top = 16.dp, start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = CardBg.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(AccentGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${serverInfo?.serverName ?: "PC"} • $measuredFps FPS • ${measuredLatency}ms",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Mode Toggle Button (Trackpad / Direct Touch)
                Surface(
                    color = CardBg.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    IconButton(
                        onClick = {
                            val nextMode = if (touchMode == TouchMode.TRACKPAD) TouchMode.DIRECT_TOUCH else TouchMode.TRACKPAD
                            viewModel.setTouchMode(nextMode)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (touchMode == TouchMode.TRACKPAD) Icons.Default.TouchApp else Icons.Default.Mouse,
                            contentDescription = "Toggle Mode",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Disconnect Button
                Surface(
                    color = ErrorRed.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.disconnect() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Disconnect",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Pop-up Virtual Keyboard Text Input Dialog
        if (showKeyboardInput) {
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            Surface(
                color = CardBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.Center)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = keyboardText,
                        onValueChange = { keyboardText = it },
                        placeholder = { Text("Type text to send to PC...") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (keyboardText.isNotEmpty()) {
                                viewModel.sendTextInput(keyboardText)
                                keyboardText = ""
                            }
                        })
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (keyboardText.isNotEmpty()) {
                                viewModel.sendTextInput(keyboardText)
                                keyboardText = ""
                            }
                            showKeyboardInput = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("Send", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Bottom Controls Overlay (Animated Visibility when showControls is true)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Windows Shortcuts Chip Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBg.copy(alpha = 0.92f))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ShortcutChip("Win+D") { viewModel.sendShortcut("win_d") }
                    ShortcutChip("Alt+Tab") { viewModel.sendShortcut("alt_tab") }
                    ShortcutChip("Ctrl+C") { viewModel.sendShortcut("ctrl_c") }
                    ShortcutChip("Ctrl+V") { viewModel.sendShortcut("ctrl_v") }
                    ShortcutChip("Ctrl+Z") { viewModel.sendShortcut("ctrl_z") }
                    ShortcutChip("Ctrl+A") { viewModel.sendShortcut("ctrl_a") }
                    ShortcutChip("Esc") { viewModel.sendShortcut("esc") }
                    ShortcutChip("Enter") { viewModel.sendShortcut("enter") }
                    ShortcutChip("Bksp") { viewModel.sendShortcut("backspace") }
                    ShortcutChip("Tab") { viewModel.sendShortcut("tab") }
                    ShortcutChip("Space") { viewModel.sendShortcut("space") }
                    ShortcutChip("▲") { viewModel.sendShortcut("arrow_up") }
                    ShortcutChip("▼") { viewModel.sendShortcut("arrow_down") }
                    ShortcutChip("◀") { viewModel.sendShortcut("arrow_left") }
                    ShortcutChip("▶") { viewModel.sendShortcut("arrow_right") }
                }

                // Bottom Action Bar
                Surface(
                    color = CardBg.copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.sendMouseClick("left", "click") },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("Left Click", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { showKeyboardInput = !showKeyboardInput },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.Keyboard, contentDescription = "Keyboard", tint = PrimaryBlue)
                        }

                        IconButton(
                            onClick = { viewModel.sendMouseScroll(-120) },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll Up", tint = TextPrimary)
                        }

                        IconButton(
                            onClick = { viewModel.sendMouseScroll(120) },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll Down", tint = TextPrimary)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { viewModel.sendMouseClick("right", "click") },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("Right Click", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectSurfaceRenderer(
    viewModel: RemoteViewModel,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    AndroidView(
        factory = { context ->
            FastStreamSurfaceView(context).apply {
                coroutineScope.launch(Dispatchers.Default) {
                    viewModel.wsManager.frameFlow.collect { bitmap ->
                        renderBitmap(bitmap)
                    }
                }
            }
        },
        modifier = modifier
    )
}

private class FastStreamSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val destRect = Rect()
    private var isSurfaceReady = false

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isSurfaceReady = true
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        destRect.set(0, 0, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isSurfaceReady = false
    }

    fun renderBitmap(bitmap: Bitmap) {
        if (!isSurfaceReady || bitmap.isRecycled) return

        try {
            val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                holder.lockHardwareCanvas()
            } else {
                holder.lockCanvas()
            }

            if (canvas != null) {
                try {
                    canvas.drawBitmap(bitmap, null, destRect, paint)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
        } catch (e: Exception) {
        }
    }
}

@Composable
private fun ShortcutChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        color = CardBg,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier.height(32.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 10.dp)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
