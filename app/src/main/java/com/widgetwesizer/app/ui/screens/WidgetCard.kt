package com.widgetwesizer.app.ui.screens

import android.appwidget.AppWidgetManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.widgetwesizer.app.data.model.GridSelection
import com.widgetwesizer.app.data.model.WidgetEntry
import com.widgetwesizer.app.widget.WidgetManager
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal const val CELL_DP = 100f
private const val MIN_SIZE_DP = 40f
private const val MAX_COLS = 4
private const val MAX_ROWS = 5

@Composable
fun WidgetCard(
    entry: WidgetEntry,
    widgetManager: WidgetManager,
    snapToGrid: Boolean,
    gridSelections: List<GridSelection>,
    onUpdateBounds: (Int, Float, Float, Float, Float) -> Unit,
    onRemove: (Int) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var offsetX by remember(entry.appWidgetId) { mutableFloatStateOf(entry.offsetXDp) }
    var offsetY by remember(entry.appWidgetId) { mutableFloatStateOf(entry.offsetYDp) }
    var width by remember(entry.appWidgetId) { mutableFloatStateOf(entry.widthDp) }
    var height by remember(entry.appWidgetId) { mutableFloatStateOf(entry.heightDp) }
    var isEditMode by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val providerInfo = remember(entry.appWidgetId) {
        AppWidgetManager.getInstance(context).getAppWidgetInfo(entry.appWidgetId)
    }

    LaunchedEffect(entry.appWidgetId) {
        widgetManager.updateWidgetSize(entry.appWidgetId, entry.widthDp.toInt(), entry.heightDp.toInt())
    }

    fun snap(value: Float) = if (snapToGrid) (value / CELL_DP).roundToInt() * CELL_DP else value
    fun snapWidth(value: Float) = if (snapToGrid)
        ((value / CELL_DP).roundToInt() * CELL_DP).coerceIn(CELL_DP, MAX_COLS * CELL_DP)
    else value.coerceAtLeast(MIN_SIZE_DP)
    fun snapHeight(value: Float) = if (snapToGrid)
        ((value / CELL_DP).roundToInt() * CELL_DP).coerceIn(CELL_DP, MAX_ROWS * CELL_DP)
    else value.coerceAtLeast(MIN_SIZE_DP)

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove widget?") },
            text = { Text("Remove \"${entry.label}\" from the board?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onRemove(entry.appWidgetId)
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    with(density) { offsetX.dp.roundToPx() },
                    with(density) { offsetY.dp.roundToPx() }
                )
            }
            .wrapContentSize()
    ) {
        // Edit mode toolbar above the card
        if (isEditMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-44).dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${width.toInt()}×${height.toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // One colored dot per grid selection — tap to snap widget to that selection's bounds
                gridSelections.forEach { sel ->
                    val hue = (sel.id * 137.5f) % 360f
                    val selColor = Color.hsl(hue, 0.6f, 0.4f)
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(selColor)
                            .pointerInput(sel.id) {
                                detectTapGestures {
                                    width = sel.widthDp
                                    height = sel.heightDp
                                    offsetX = sel.boardX
                                    offsetY = sel.boardY
                                    onUpdateBounds(entry.appWidgetId, width, height, offsetX, offsetY)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${sel.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                // Re-open the widget's own setup screen (e.g. Google Home favorites vs custom)
                if (providerInfo?.configure != null) {
                    IconButton(
                        onClick = {
                            widgetManager.launchConfigureActivity(entry.appWidgetId, providerInfo) { }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Widget settings", modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove widget", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = { isEditMode = false },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.Done, contentDescription = "Done editing", modifier = Modifier.size(18.dp))
                }
            }
        }

        Box(
            modifier = Modifier
                .width(width.dp)
                .height(height.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            // The actual widget view (underneath — its touch handling is blocked by the overlay below)
            if (providerInfo != null) {
                AndroidView(
                    factory = { ctx ->
                        widgetManager.createHostView(ctx, entry.appWidgetId, providerInfo)
                    },
                    modifier = Modifier
                        .width(width.dp)
                        .height(height.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Overlay sits on top of AndroidView and captures all touches.
            // Transparent in view mode (invisible but gesture-capturing);
            // dark scrim in edit mode to show the widget is selected.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isEditMode) Color.Black.copy(alpha = 0.35f) else Color.Transparent
                    )
                    .pointerInput(isEditMode, snapToGrid) {
                        if (!isEditMode) {
                            // Run long-press and drag detectors concurrently on the same pointer stream.
                            // detectDragGestures only consumes after slop is exceeded, so a stationary
                            // long press is still seen by detectTapGestures.
                            coroutineScope {
                                launch {
                                    detectTapGestures(onLongPress = { isEditMode = true })
                                }
                                launch {
                                    // Track the raw finger movement since the drag began and snap
                                    // that, so small moves add up instead of being rounded away.
                                    var startX = 0f
                                    var startY = 0f
                                    var totalX = 0f
                                    var totalY = 0f
                                    detectDragGestures(
                                        onDragStart = {
                                            startX = offsetX; startY = offsetY
                                            totalX = 0f; totalY = 0f
                                        },
                                        onDragEnd = {
                                            onUpdateBounds(entry.appWidgetId, width, height, offsetX, offsetY)
                                        }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        totalX += with(density) { dragAmount.x.toDp().value }
                                        totalY += with(density) { dragAmount.y.toDp().value }
                                        offsetX = snap(startX + totalX).coerceAtLeast(0f)
                                        offsetY = snap(startY + totalY).coerceAtLeast(0f)
                                    }
                                }
                            }
                        }
                        // In edit mode the overlay consumes touches so the widget doesn't react,
                        // and the resize handles (below) handle interaction.
                    }
            )
        }

        // Corner + edge resize handles, shown only in edit mode
        if (isEditMode) {
            ResizeHandles(
                width = width,
                height = height,
                density = density,
                currentBounds = { Bounds(offsetX, offsetY, width, height) },
                onResize = { start, handle, totalX, totalY ->
                    // Right/bottom edges stay put when dragging left/top handles.
                    val right = start.x + start.w
                    val bottom = start.y + start.h
                    val newW = snapWidth(start.w + totalX * handle.dw)
                    val newH = snapHeight(start.h + totalY * handle.dh)
                    width = newW
                    height = newH
                    if (handle.dx != 0f) offsetX = snap(right - newW).coerceAtLeast(0f)
                    if (handle.dy != 0f) offsetY = snap(bottom - newH).coerceAtLeast(0f)
                },
                onResizeEnd = {
                    onUpdateBounds(entry.appWidgetId, width, height, offsetX, offsetY)
                }
            )
        }
    }
}

internal data class Bounds(val x: Float, val y: Float, val w: Float, val h: Float)

/** Which edges a handle moves: dw/dh grow direction, dx/dy = 1 if it moves the left/top edge. */
internal data class Handle(val xF: Float, val yF: Float, val dw: Float, val dh: Float, val dx: Float, val dy: Float)

private val RESIZE_HANDLES = listOf(
    Handle(0f,   0f,   -1f, -1f, 1f, 1f),  // NW
    Handle(0.5f, 0f,    0f, -1f, 0f, 1f),  // N
    Handle(1f,   0f,    1f, -1f, 0f, 1f),  // NE
    Handle(1f,   0.5f,  1f,  0f, 0f, 0f),  // E
    Handle(1f,   1f,    1f,  1f, 0f, 0f),  // SE
    Handle(0.5f, 1f,    0f,  1f, 0f, 0f),  // S
    Handle(0f,   1f,   -1f,  1f, 1f, 0f),  // SW
    Handle(0f,   0.5f, -1f,  0f, 1f, 0f),  // W
)

@Composable
private fun ResizeHandles(
    width: Float,
    height: Float,
    density: androidx.compose.ui.unit.Density,
    currentBounds: () -> Bounds,
    onResize: (start: Bounds, handle: Handle, totalXDp: Float, totalYDp: Float) -> Unit,
    onResizeEnd: () -> Unit
) {
    val touchSize = 36.dp   // generous touch target
    val dotSize = 18.dp     // visible dot
    val halfTouch = with(density) { touchSize.toPx() / 2 }

    // Keep the gesture running for the whole drag: the pointerInput key must not change
    // while resizing, so read the latest callbacks through updated state.
    val latestBounds by rememberUpdatedState(currentBounds)
    val latestOnResize by rememberUpdatedState(onResize)
    val latestOnResizeEnd by rememberUpdatedState(onResizeEnd)

    RESIZE_HANDLES.forEach { handle ->
        val xOff = with(density) { (width * handle.xF).dp.roundToPx() - halfTouch.roundToInt() }
        val yOff = with(density) { (height * handle.yF).dp.roundToPx() - halfTouch.roundToInt() }

        Box(
            modifier = Modifier
                .offset { IntOffset(xOff, yOff) }
                .size(touchSize)
                .pointerInput(handle) {
                    var start = Bounds(0f, 0f, 0f, 0f)
                    var totalX = 0f
                    var totalY = 0f
                    detectDragGestures(
                        onDragStart = {
                            start = latestBounds()
                            totalX = 0f; totalY = 0f
                        },
                        onDragEnd = { latestOnResizeEnd() },
                        onDragCancel = { latestOnResizeEnd() }
                    ) { change, dragAmount ->
                        change.consume()
                        totalX += dragAmount.x.toDp().value
                        totalY += dragAmount.y.toDp().value
                        latestOnResize(start, handle, totalX, totalY)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
            )
        }
    }
}
