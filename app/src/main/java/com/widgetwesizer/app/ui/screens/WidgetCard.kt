package com.widgetwesizer.app.ui.screens

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
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
import com.widgetwesizer.app.data.model.WidgetEntry
import com.widgetwesizer.app.widget.WidgetBoardHostView
import com.widgetwesizer.app.widget.WidgetManager
import kotlin.math.roundToInt

private const val MIN_SIZE_DP = 40f
private const val GRID_DP = 8f

@Composable
fun WidgetCard(
    entry: WidgetEntry,
    widgetManager: WidgetManager,
    snapToGrid: Boolean,
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
        AppWidgetManager.getInstance(context).installedProviders.find {
            it.provider.packageName == entry.packageName && it.provider.className == entry.className
        }
    }

    fun snap(value: Float) = if (snapToGrid) {
        (value / GRID_DP).roundToInt() * GRID_DP
    } else value

    fun snapSize(value: Float) = if (snapToGrid) {
        ((value / GRID_DP).roundToInt() * GRID_DP).coerceAtLeast(MIN_SIZE_DP)
    } else value.coerceAtLeast(MIN_SIZE_DP)

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
            .offset { IntOffset(with(density) { offsetX.dp.roundToPx() }, with(density) { offsetY.dp.roundToPx() }) }
            .wrapContentSize()
    ) {
        // Edit mode toolbar above card
        if (isEditMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-40).dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "W: ${width.toInt()}dp  H: ${height.toInt()}dp",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove widget",
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { isEditMode = false },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Done,
                        contentDescription = "Done editing",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .width(width.dp)
                .height(height.dp)
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(isEditMode) {
                    if (!isEditMode) {
                        detectTapGestures(onLongPress = { isEditMode = true })
                    }
                }
                .let { mod ->
                    if (!isEditMode) {
                        mod.pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    onUpdateBounds(entry.appWidgetId, width, height, offsetX, offsetY)
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                val newX = snap(offsetX + with(density) { dragAmount.x.toDp().value })
                                val newY = snap(offsetY + with(density) { dragAmount.y.toDp().value })
                                offsetX = newX.coerceAtLeast(0f)
                                offsetY = newY.coerceAtLeast(0f)
                            }
                        }
                    } else mod
                }
        ) {
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

            // Edit mode scrim
            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )
            }
        }

        // Resize handles (only in edit mode)
        if (isEditMode) {
            ResizeHandles(
                width = width,
                height = height,
                density = density,
                onResize = { dw, dh, dx, dy ->
                    width = snapSize(width + dw)
                    height = snapSize(height + dh)
                    if (dx != 0f) offsetX = snap((offsetX + dx).coerceAtLeast(0f))
                    if (dy != 0f) offsetY = snap((offsetY + dy).coerceAtLeast(0f))
                },
                onResizeEnd = {
                    onUpdateBounds(entry.appWidgetId, width, height, offsetX, offsetY)
                }
            )
        }
    }
}

@Composable
private fun ResizeHandles(
    width: Float,
    height: Float,
    density: androidx.compose.ui.unit.Density,
    onResize: (dw: Float, dh: Float, dx: Float, dy: Float) -> Unit,
    onResizeEnd: () -> Unit
) {
    val handleSize = 24.dp
    val halfHandle = with(density) { handleSize.toPx() / 2 }

    data class Handle(val xFraction: Float, val yFraction: Float, val dw: Float, val dh: Float, val dx: Float, val dy: Float)

    val handles = listOf(
        Handle(0f, 0f, -1f, -1f, 1f, 1f),   // NW
        Handle(0.5f, 0f, 0f, -1f, 0f, 1f),  // N
        Handle(1f, 0f, 1f, -1f, 0f, 1f),    // NE
        Handle(1f, 0.5f, 1f, 0f, 0f, 0f),   // E
        Handle(1f, 1f, 1f, 1f, 0f, 0f),     // SE
        Handle(0.5f, 1f, 0f, 1f, 0f, 0f),   // S
        Handle(0f, 1f, -1f, 1f, 1f, 0f),    // SW
        Handle(0f, 0.5f, -1f, 0f, 1f, 0f),  // W
    )

    handles.forEach { handle ->
        val xOff = with(density) { (width * handle.xFraction).dp.roundToPx() - halfHandle.roundToInt() }
        val yOff = with(density) { (height * handle.yFraction).dp.roundToPx() - halfHandle.roundToInt() }

        Box(
            modifier = Modifier
                .offset { IntOffset(xOff, yOff) }
                .size(handleSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
                .pointerInput(width, height) {
                    detectDragGestures(onDragEnd = onResizeEnd) { change, dragAmount ->
                        change.consume()
                        val dpX = with(density) { dragAmount.x.toDp().value }
                        val dpY = with(density) { dragAmount.y.toDp().value }
                        onResize(
                            dpX * handle.dw,
                            dpY * handle.dh,
                            dpX * handle.dx,
                            dpY * handle.dy
                        )
                    }
                }
        )
    }
}
