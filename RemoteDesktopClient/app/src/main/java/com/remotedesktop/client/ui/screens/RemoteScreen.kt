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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
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
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MIN_ZOOM = 1.0f
private const val MAX_ZOOM = 3.0f

@Composable
fun RemoteScreen(
    viewModel: RemoteViewModel,
    isInPipMode: Boolean = false,
    onEnterPip: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val touchMode by viewModel.touchMode.collectAsState()
    val mouseSensitivity by viewModel.mouseSensitivity.collectAsState()
    val measuredFps by viewModel.measuredFps.collectAsState()
    val measuredLatency by viewModel.measuredLatency.collectAsState()
    val serverInfo by viewModel.serverInfo.collectAsState()

    var showControls by remember { mutableStateOf(false) }
    var isKeyboardOpen by remember { mutableStateOf(false) }

    val keyboardFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var directInputValue by remember { mutableStateOf(TextFieldValue("")) }

    var zoomScale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    val latestZoomScale = rememberUpdatedState(zoomScale)
    val latestPanOffset = rememberUpdatedState(panOffset)
    val latestViewportSize = rememberUpdatedState(viewportSize)
    val applyTransform = rememberUpdatedState { nextScale: Float, nextPan: Offset ->
        zoomScale = nextScale
        panOffset = nextPan
    }

    val context = LocalContext.current
    val viewConfig = remember(context) { ViewConfiguration.get(context) }
    val touchSlop = viewConfig.scaledTouchSlop.toFloat()
    val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
    val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    fun toggleKeyboard() {
        if (isKeyboardOpen) {
            keyboardController?.hide()
            isKeyboardOpen = false
        } else {
            isKeyboardOpen = true
            keyboardFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size ->
                viewportSize = size
                panOffset = clampPanOffset(zoomScale, panOffset, size)
            }
    ) {
        // Direct Hidden Soft Keyboard Input (Streams typed characters & backspaces instantly to PC)
        BasicTextField(
            value = directInputValue,
            onValueChange = { newValue ->
                val oldText = directInputValue.text
                val newText = newValue.text
                if (newText.length > oldText.length) {
                    val addedText = newText.substring(oldText.length)
                    viewModel.sendTextInput(addedText)
                } else if (newText.length < oldText.length) {
                    val diff = oldText.length - newText.length
                    repeat(diff) {
                        viewModel.sendShortcut("backspace")
                    }
                }
                if (newText.length > 40) {
                    directInputValue = TextFieldValue("")
                } else {
                    directInputValue = newValue
                }
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Default,
                autoCorrect = false
            ),
            keyboardActions = KeyboardActions(
                onDone = { viewModel.sendShortcut("enter") },
                onGo = { viewModel.sendShortcut("enter") },
                onSearch = { viewModel.sendShortcut("enter") },
                onSend = { viewModel.sendShortcut("enter") }
            ),
            modifier = Modifier
                .size(1.dp)
                .alpha(0.01f)
                .focusRequester(keyboardFocusRequester)
        )

        // Hardware-Accelerated Zero-Latency SurfaceView Screen with Pinch-to-Zoom & Pan
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                            var transformStartCentroid = Offset.Unspecified
                            var transformStartDistance = 0f
                            var transformStarted = false
                            var trackingTwoFingers = false
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
                                    if (!trackingTwoFingers) {
                                        isMultiTouch = true
                                        trackingTwoFingers = true
                                        longPressJob.cancel()
                                        isDragged = true
                                        transformStarted = false
                                        transformStartCentroid = activePointers.centroid()
                                        transformStartDistance = activePointers.distance()
                                        previousCentroid = transformStartCentroid
                                        previousDistance = transformStartDistance
                                    } else {
                                        val centroid = activePointers.centroid()
                                        val distance = activePointers.distance()
                                        val viewport = latestViewportSize.value
                                        val viewportCenter = Offset(viewport.width / 2f, viewport.height / 2f)
                                        val zoomChange: Float
                                        val panChange: Offset
                                        val zoomPivot: Offset

                                        if (!transformStarted) {
                                            val totalCentroidDelta = centroid - transformStartCentroid
                                            val totalDistanceDelta = abs(distance - transformStartDistance)
                                            if (totalCentroidDelta.getDistance() <= touchSlop &&
                                                totalDistanceDelta <= touchSlop
                                            ) {
                                                previousCentroid = centroid
                                                previousDistance = distance
                                                event.changes.forEach { it.consume() }
                                                continue
                                            }
                                            transformStarted = true
                                            zoomChange = if (transformStartDistance > 0f) {
                                                (distance / transformStartDistance).coerceIn(0.85f, 1.18f)
                                            } else {
                                                1f
                                            }
                                            panChange = totalCentroidDelta
                                            zoomPivot = transformStartCentroid
                                        } else {
                                            zoomChange = if (previousDistance > 0f) {
                                                (distance / previousDistance).coerceIn(0.85f, 1.18f)
                                            } else {
                                                1f
                                            }
                                            panChange = centroid - previousCentroid
                                            zoomPivot = previousCentroid
                                        }

                                        val nextScale = (gestureScale * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                        val scaleRatio = nextScale / gestureScale
                                        val nextPan = gesturePan + panChange +
                                            (zoomPivot - viewportCenter) * (1f - scaleRatio)
                                        gestureScale = nextScale
                                        gesturePan = clampPanOffset(
                                            nextScale,
                                            nextPan,
                                            viewport
                                        )
                                        applyTransform.value(gestureScale, gesturePan)
                                        previousCentroid = centroid
                                        previousDistance = distance
                                    }

                                    event.changes.forEach { it.consume() }
                                    continue
                                }

                                if (isMultiTouch) {
                                    trackingTwoFingers = false
                                    transformStarted = false
                                    previousCentroid = Offset.Unspecified
                                    previousDistance = 0f
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
                                                (movement.x * mouseSensitivity).roundToInt(),
                                                (movement.y * mouseSensitivity).roundToInt()
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

        // Top-Right Floating Controls: [Keyboard Toggle], [Floating PiP], & [Settings / UI Toggle]
        if (!isInPipMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Direct Keyboard Trigger (Hide / Unhide Soft Keyboard)
                Surface(
                    color = if (isKeyboardOpen) PrimaryBlue.copy(alpha = 0.95f) else CardBg.copy(alpha = 0.6f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, if (isKeyboardOpen) PrimaryBlue else Color(0x55334155)),
                    modifier = Modifier.size(40.dp)
                ) {
                    IconButton(
                        onClick = { toggleKeyboard() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = if (isKeyboardOpen) "Hide Keyboard" else "Show Keyboard",
                            tint = if (isKeyboardOpen) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 2. Floating App (Picture-in-Picture) Trigger Button
                Surface(
                    color = CardBg.copy(alpha = 0.6f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color(0x55334155)),
                    modifier = Modifier.size(40.dp)
                ) {
                    IconButton(
                        onClick = { onEnterPip() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "Float Window",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 3. Settings / UI Trigger (Hide / Unhide All Controls HUD & Shortcuts)
                Surface(
                    color = if (showControls) PrimaryBlue.copy(alpha = 0.95f) else CardBg.copy(alpha = 0.6f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, if (showControls) PrimaryBlue else Color(0x55334155)),
                    modifier = Modifier.size(40.dp)
                ) {
                    IconButton(
                        onClick = { showControls = !showControls },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (showControls) Icons.Default.Fullscreen else Icons.Default.Tune,
                            contentDescription = if (showControls) "Hide Settings" else "Show Settings",
                            tint = if (showControls) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Top HUD Overlay (Animated Visibility when showControls is true)
        if (!isInPipMode) {
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

                // Sensitivity Quick-Cycle Button
                Surface(
                    color = CardBg.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Row(
                        modifier = Modifier
                            .clickable {
                                val nextSens = when {
                                    mouseSensitivity < 1.0f -> 1.0f
                                    mouseSensitivity < 1.5f -> 1.5f
                                    mouseSensitivity < 2.0f -> 2.0f
                                    mouseSensitivity < 2.5f -> 2.5f
                                    mouseSensitivity < 3.0f -> 3.0f
                                    else -> 0.75f
                                }
                                viewModel.setMouseSensitivity(nextSens)
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Sensitivity",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${String.format(java.util.Locale.US, "%.1f", mouseSensitivity)}x",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
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
    }

    // Bottom Controls Overlay (Animated Visibility when showControls is true)
    if (!isInPipMode) {
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
                            onClick = { toggleKeyboard() },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = "Keyboard",
                                tint = if (isKeyboardOpen) PrimaryBlue else TextPrimary
                            )
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
}

private fun clampPanOffset(scale: Float, offset: Offset, size: IntSize): Offset {
    if (size.width <= 0 || size.height <= 0 || scale <= 1f) {
        return Offset.Zero
    }

    val maxPanX = (size.width * (scale - 1f)) / 2f
    val maxPanY = (size.height * (scale - 1f)) / 2f

    return Offset(
        x = offset.x.coerceIn(-maxPanX, maxPanX),
        y = offset.y.coerceIn(-maxPanY, maxPanY)
    )
}

private fun sendAbsolutePointer(
    screenPosition: Offset,
    scale: Float,
    pan: Offset,
    viewportSize: IntSize,
    viewModel: RemoteViewModel
) {
    if (viewportSize.width <= 0 || viewportSize.height <= 0) return

    val contentX = (screenPosition.x - viewportSize.width / 2f - pan.x) / scale + viewportSize.width / 2f
    val contentY = (screenPosition.y - viewportSize.height / 2f - pan.y) / scale + viewportSize.height / 2f

    val normX = (contentX / viewportSize.width.toFloat()).toDouble().coerceIn(0.0, 1.0)
    val normY = (contentY / viewportSize.height.toFloat()).toDouble().coerceIn(0.0, 1.0)

    viewModel.sendMouseMoveAbsolute(normX, normY)
}

private fun List<PointerInputChange>.centroid(): Offset {
    if (isEmpty()) return Offset.Unspecified
    var sumX = 0f
    var sumY = 0f
    forEach {
        sumX += it.position.x
        sumY += it.position.y
    }
    return Offset(sumX / size, sumY / size)
}

private fun List<PointerInputChange>.distance(): Float {
    if (size < 2) return 0f
    val first = this[0].position
    val second = this[1].position
    return (first - second).getDistance()
}

@Composable
private fun DirectSurfaceRenderer(
    viewModel: RemoteViewModel,
    zoomScale: Float,
    panOffset: Offset,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var surfaceViewRef by remember { mutableStateOf<FastStreamSurfaceView?>(null) }

    LaunchedEffect(zoomScale, panOffset) {
        surfaceViewRef?.updateTransform(zoomScale, panOffset)
    }

    AndroidView(
        factory = { context ->
            FastStreamSurfaceView(context).apply {
                surfaceViewRef = this
                updateTransform(zoomScale, panOffset)
                coroutineScope.launch(Dispatchers.Default) {
                    viewModel.wsManager.frameFlow.collect { bitmap ->
                        renderBitmap(bitmap)
                    }
                }
            }
        },
        update = { surface ->
            surface.updateTransform(zoomScale, panOffset)
        },
        modifier = modifier
    )
}

private class FastStreamSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val destRect = Rect()
    private var isSurfaceReady = false
    private var zoomScale = 1f
    private var panOffset = Offset.Zero

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

    fun updateTransform(scale: Float, pan: Offset) {
        zoomScale = scale
        panOffset = pan
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
                    canvas.save()
                    if (zoomScale != 1f || panOffset != Offset.Zero) {
                        val centerX = width / 2f
                        val centerY = height / 2f
                        canvas.translate(centerX + panOffset.x, centerY + panOffset.y)
                        canvas.scale(zoomScale, zoomScale)
                        canvas.translate(-centerX, -centerY)
                    }
                    canvas.drawBitmap(bitmap, null, destRect, paint)
                    canvas.restore()
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
