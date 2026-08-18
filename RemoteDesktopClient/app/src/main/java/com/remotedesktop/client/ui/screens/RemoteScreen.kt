package com.remotedesktop.client.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewConfiguration
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.remotedesktop.client.data.TouchMode
import com.remotedesktop.client.ui.theme.*
import com.remotedesktop.client.viewmodel.RemoteViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
        val context = LocalContext.current
        var zoomScale by remember { mutableFloatStateOf(MIN_ZOOM) }
        var panOffset by remember { mutableStateOf(Offset.Zero) }
        var viewportSize by remember { mutableStateOf(IntSize.Zero) }
        val latestZoomScale = rememberUpdatedState(zoomScale)
        val latestPanOffset = rememberUpdatedState(panOffset)
        val latestViewportSize = rememberUpdatedState(viewportSize)
        val applyTransform = rememberUpdatedState<(Float, Offset) -> Unit> { scale, offset ->
            zoomScale = scale
            panOffset = offset
        }
        val touchSlop = remember(context) { ViewConfiguration.get(context).scaledTouchSlop }
        val longPressTimeout = remember { ViewConfiguration.getLongPressTimeout().toLong() }
        val doubleTapTimeout = remember { ViewConfiguration.getDoubleTapTimeout().toLong() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    viewportSize = size
                    panOffset = clampPanOffset(zoomScale, panOffset, size)
                }
                .pointerInput(touchMode) {
                    coroutineScope {
                        var pendingTapJob: Job? = null
                        var lastTapTime = 0L
                        var lastTapPosition = Offset.Unspecified

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val firstPointerId = down.id
                            val initialPosition = down.position
                            var lastPosition = down.position
                            var isDragged = false
                            var isMultiTouch = false
                            var longPressTriggered = false
                            var gestureScale = latestZoomScale.value
                            var gesturePan = latestPanOffset.value
                            var previousCentroid = Offset.Unspecified
                            var previousDistance = 0f
                            var pointerReleased = false

                            val longPressJob = launch {
                                delay(longPressTimeout)
                                if (!isDragged && !isMultiTouch && !pointerReleased) {
                                    longPressTriggered = true
                                    if (touchMode == TouchMode.DIRECT_TOUCH) {
                                        sendAbsolutePointer(
                                            initialPosition,
                                            gestureScale,
                                            gesturePan,
                                            latestViewportSize.value,
                                            viewModel
                                        )
                                    }
                                    viewModel.sendMouseClick("right", "click")
                                }
                            }

                            while (!pointerReleased) {
                                val event = awaitPointerEvent()
                                val activePointers = event.changes.filter { it.pressed }

                                if (activePointers.size >= 2) {
                                    if (!isMultiTouch) {
                                        isMultiTouch = true
                                        longPressJob.cancel()
                                        isDragged = true
                                        previousCentroid = activePointers.centroid()
                                        previousDistance = activePointers.distance()
                                    } else {
                                        val centroid = activePointers.centroid()
                                        val distance = activePointers.distance()
                                        val zoomChange = if (previousDistance > 0f) {
                                            (distance / previousDistance).coerceIn(0.85f, 1.18f)
                                        } else {
                                            1f
                                        }
                                        val nextScale = (gestureScale * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                        val centroidDelta = centroid - previousCentroid
                                        val scaleRatio = nextScale / gestureScale
                                        val nextPan = centroid - (centroid - gesturePan) * scaleRatio + centroidDelta
                                        gestureScale = nextScale
                                        gesturePan = clampPanOffset(
                                            nextScale,
                                            nextPan,
                                            latestViewportSize.value
                                        )
                                        applyTransform.value(gestureScale, gesturePan)
                                        previousCentroid = centroid
                                        previousDistance = distance
                                    }

                                    event.changes.forEach { it.consume() }
                                    continue
                                }

                                if (isMultiTouch) {
                                    event.changes.forEach { it.consume() }
                                    if (activePointers.isEmpty()) {
                                        pointerReleased = true
                                    }
                                    continue
                                }

                                val pointerChange = event.changes.firstOrNull { it.id == firstPointerId }
                                if (pointerChange == null) {
                                    pointerReleased = true
                                    continue
                                }

                                val currentPosition = pointerChange.position
                                val movement = currentPosition - lastPosition
                                if (pointerChange.pressed) {
                                    if (!isDragged && (currentPosition - initialPosition).getDistance() > touchSlop) {
                                        isDragged = true
                                        longPressJob.cancel()
                                    }

                                    if (isDragged && movement != Offset.Zero) {
                                        pointerChange.consume()
                                        if (touchMode == TouchMode.TRACKPAD) {
                                            viewModel.sendMouseMoveDelta(
                                                (movement.x * 1.5f).roundToInt(),
                                                (movement.y * 1.5f).roundToInt()
                                            )
                                        } else {
                                            sendAbsolutePointer(
                                                currentPosition,
                                                gestureScale,
                                                gesturePan,
                                                latestViewportSize.value,
                                                viewModel
                                            )
                                        }
                                    }
                                    lastPosition = currentPosition
                                } else {
                                    pointerReleased = true
                                    longPressJob.cancel()
                                    pointerChange.consume()
                                    if (!isDragged && !longPressTriggered) {
                                        val now = System.currentTimeMillis()
                                        val isDoubleTap = lastTapTime > 0L &&
                                            now - lastTapTime <= doubleTapTimeout &&
                                            lastTapPosition != Offset.Unspecified &&
                                            (currentPosition - lastTapPosition).getDistance() <= touchSlop * 2f

                                        pendingTapJob?.cancel()
                                        if (isDoubleTap) {
                                            if (touchMode == TouchMode.DIRECT_TOUCH) {
                                                sendAbsolutePointer(
                                                    currentPosition,
                                                    gestureScale,
                                                    gesturePan,
                                                    latestViewportSize.value,
                                                    viewModel
                                                )
                                            }
                                            viewModel.sendMouseClick("left", "dblclick")
                                            lastTapTime = 0L
                                            lastTapPosition = Offset.Unspecified
                                        } else {
                                            lastTapTime = now
                                            lastTapPosition = currentPosition
                                            pendingTapJob = launch {
                                                delay(doubleTapTimeout)
                                                if (touchMode == TouchMode.DIRECT_TOUCH) {
                                                    sendAbsolutePointer(
                                                        currentPosition,
                                                        gestureScale,
                                                        gesturePan,
                                                        latestViewportSize.value,
                                                        viewModel
                                                    )
                                                }
                                                viewModel.sendMouseClick("left", "click")
                                                lastTapTime = 0L
                                                lastTapPosition = Offset.Unspecified
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            DirectSurfaceRenderer(
                viewModel = viewModel,
                zoomScale = zoomScale,
                panOffset = panOffset,
                modifier = Modifier.fillMaxSize()
            )
        }

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

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 8f

private fun List<PointerInputChange>.centroid(): Offset {
    if (isEmpty()) return Offset.Zero
    return Offset(
        sumOf { it.position.x.toDouble() }.toFloat() / size,
        sumOf { it.position.y.toDouble() }.toFloat() / size
    )
}

private fun List<PointerInputChange>.distance(): Float {
    if (size < 2) return 0f
    return (this[0].position - this[1].position).getDistance()
}

private fun clampPanOffset(scale: Float, offset: Offset, viewportSize: IntSize): Offset {
    if (viewportSize.width <= 0 || viewportSize.height <= 0) return Offset.Zero

    val scaledWidth = viewportSize.width * scale
    val scaledHeight = viewportSize.height * scale
    val minX = minOf(0f, viewportSize.width - scaledWidth)
    val minY = minOf(0f, viewportSize.height - scaledHeight)

    return Offset(
        offset.x.coerceIn(minX, 0f),
        offset.y.coerceIn(minY, 0f)
    )
}

private fun sendAbsolutePointer(
    position: Offset,
    zoomScale: Float,
    panOffset: Offset,
    viewportSize: IntSize,
    viewModel: RemoteViewModel
) {
    if (viewportSize.width <= 0 || viewportSize.height <= 0) return

    val contentX = ((position.x - panOffset.x) / zoomScale)
        .coerceIn(0f, viewportSize.width.toFloat())
    val contentY = ((position.y - panOffset.y) / zoomScale)
        .coerceIn(0f, viewportSize.height.toFloat())

    viewModel.sendMouseMoveAbsolute(
        (contentX / viewportSize.width).toDouble(),
        (contentY / viewportSize.height).toDouble()
    )
}

@Composable
private fun DirectSurfaceRenderer(
    viewModel: RemoteViewModel,
    zoomScale: Float,
    panOffset: Offset,
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
        update = { surfaceView ->
            surfaceView.setDisplayTransform(zoomScale, panOffset)
        },
        modifier = modifier
    )
}

private data class DisplayTransform(
    val scale: Float = MIN_ZOOM,
    val offset: Offset = Offset.Zero
)

private class FastStreamSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val destRect = Rect()
    private val renderLock = Any()
    @Volatile private var isSurfaceReady = false
    @Volatile private var displayTransform = DisplayTransform()
    @Volatile private var latestBitmap: Bitmap? = null

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isSurfaceReady = true
        post { redrawLatestFrame() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        synchronized(renderLock) {
            destRect.set(0, 0, width, height)
        }
        post { redrawLatestFrame() }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isSurfaceReady = false
    }

    fun setDisplayTransform(scale: Float, offset: Offset) {
        displayTransform = DisplayTransform(
            scale = scale.coerceIn(MIN_ZOOM, MAX_ZOOM),
            offset = offset
        )
        post { redrawLatestFrame() }
    }

    fun renderBitmap(bitmap: Bitmap) {
        if (bitmap.isRecycled) return

        synchronized(renderLock) {
            latestBitmap = bitmap
            drawBitmap(bitmap)
        }
    }

    private fun redrawLatestFrame() {
        synchronized(renderLock) {
            latestBitmap?.let { bitmap ->
                if (!bitmap.isRecycled) {
                    drawBitmap(bitmap)
                }
            }
        }
    }

    private fun drawBitmap(bitmap: Bitmap) {
        if (!isSurfaceReady || bitmap.isRecycled || destRect.isEmpty) return

        try {
            val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                holder.lockHardwareCanvas()
            } else {
                holder.lockCanvas()
            }

            if (canvas != null) {
                try {
                    val transform = displayTransform
                    val savedState = canvas.save()
                    canvas.drawColor(android.graphics.Color.BLACK)
                    canvas.translate(transform.offset.x, transform.offset.y)
                    canvas.scale(transform.scale, transform.scale)
                    canvas.drawBitmap(bitmap, null, destRect, paint)
                    canvas.restoreToCount(savedState)
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
