package com.widgetwesizer.app.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.widgetwesizer.app.data.model.ViewportEntry
import com.widgetwesizer.app.ui.viewmodel.WidgetBoardViewModel
import com.widgetwesizer.app.widget.WidgetManager
import kotlin.math.roundToInt

private const val BOARD_SIZE_DP = 2000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    viewModel: WidgetBoardViewModel,
    widgetManager: WidgetManager,
    onNavigateToPicker: () -> Unit
) {
    val context = LocalContext.current
    val widgets by viewModel.widgets.collectAsState()
    val removedNames by viewModel.removedWidgetNames.collectAsState()
    val viewport by viewModel.viewport.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showGrid by remember { mutableStateOf(false) }
    var snapToGrid by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.cleanupRemovedProviders(context)
    }

    LaunchedEffect(removedNames) {
        if (removedNames.isNotEmpty()) {
            snackbarHostState.showSnackbar("Removed unavailable widget(s): ${removedNames.joinToString(", ")}")
            viewModel.clearRemovedWidgetNames()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("WidgetWesizer") },
                actions = {
                    // Toggle grid lines + labels + viewport overlay
                    IconButton(onClick = { showGrid = !showGrid }) {
                        Icon(
                            imageVector = if (showGrid) Icons.Filled.GridOn else Icons.Filled.GridOff,
                            contentDescription = if (showGrid) "Hide grid" else "Show grid"
                        )
                    }
                    // Toggle snap-to-grid for widgets
                    IconButton(onClick = { snapToGrid = !snapToGrid }) {
                        Icon(
                            imageVector = if (snapToGrid) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = if (snapToGrid) "Snap on" else "Snap off",
                            tint = if (snapToGrid) MaterialTheme.colorScheme.primary
                                   else LocalContentColor.current
                        )
                    }
                    // Add widget
                    IconButton(onClick = onNavigateToPicker) {
                        Icon(Icons.Filled.Add, contentDescription = "Add widget")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState())
        ) {
            val canvasSize = BOARD_SIZE_DP.dp

            Box(modifier = Modifier.size(canvasSize)) {
                if (showGrid) {
                    GridOverlay(modifier = Modifier.fillMaxSize())
                }

                widgets.forEach { entry ->
                    key(entry.appWidgetId) {
                        WidgetCard(
                            entry = entry,
                            widgetManager = widgetManager,
                            snapToGrid = snapToGrid,
                            onUpdateBounds = { id, w, h, x, y ->
                                viewModel.updateWidgetBounds(id, w, h, x, y)
                            },
                            onRemove = { viewModel.removeWidget(it) }
                        )
                    }
                }

                if (showGrid) {
                    ViewportOverlay(
                        viewport = viewport,
                        onViewportChange = { viewModel.updateViewport(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun GridOverlay(modifier: Modifier = Modifier) {
    val gridColorArgb = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f).toArgb()
    val labelColorArgb = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f).toArgb()
    val gridColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)

    val density = LocalDensity.current
    val cellPx = with(density) { CELL_DP.dp.toPx() }
    val labelSizePx = with(density) { 9.sp.toPx() }

    Canvas(modifier = modifier) {
        val numCols = (size.width / cellPx).toInt()
        val numRows = (size.height / cellPx).toInt()

        for (col in 0..numCols) {
            drawLine(gridColor, Offset(col * cellPx, 0f), Offset(col * cellPx, size.height), strokeWidth = 1f)
        }
        for (row in 0..numRows) {
            drawLine(gridColor, Offset(0f, row * cellPx), Offset(size.width, row * cellPx), strokeWidth = 1f)
        }

        val paint = Paint().apply {
            color = labelColorArgb
            textSize = labelSizePx
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }
        val fm = paint.fontMetrics
        val textHeight = fm.descent - fm.ascent

        val nativeCanvas = drawContext.canvas.nativeCanvas
        for (col in 0 until numCols.coerceAtMost(26)) {
            val label = ('A' + col).toString()
            val lw = paint.measureText(label)
            nativeCanvas.drawText(label, col * cellPx + (cellPx - lw) / 2f, labelSizePx + 4f, paint)
        }
        for (row in 0 until numRows) {
            val label = (row + 1).toString()
            val y = row * cellPx + cellPx / 2f + textHeight / 2f - fm.descent
            nativeCanvas.drawText(label, 4f, y, paint)
        }
    }
}

private fun viewportLabel(vpX: Float, vpY: Float, vpW: Float, vpH: Float): String {
    val c1 = 'A' + (vpX / CELL_DP).toInt().coerceIn(0, 25)
    val r1 = (vpY / CELL_DP).toInt() + 1
    val c2 = 'A' + ((vpX + vpW - 1f) / CELL_DP).toInt().coerceIn(0, 25)
    val r2 = ((vpY + vpH - 1f) / CELL_DP).toInt() + 1
    return "$c1$r1:$c2$r2"
}

@Composable
private fun ViewportOverlay(
    viewport: ViewportEntry,
    onViewportChange: (ViewportEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    var vpX by remember(viewport) { mutableFloatStateOf(viewport.x) }
    var vpY by remember(viewport) { mutableFloatStateOf(viewport.y) }
    var vpW by remember(viewport) { mutableFloatStateOf(viewport.width) }
    var vpH by remember(viewport) { mutableFloatStateOf(viewport.height) }

    fun snapCell(v: Float) = (v / CELL_DP).roundToInt() * CELL_DP
    fun snapCellSize(v: Float) = ((v / CELL_DP).roundToInt() * CELL_DP).coerceAtLeast(CELL_DP)

    val label = remember(vpX, vpY, vpW, vpH) { viewportLabel(vpX, vpY, vpW, vpH) }
    val vpColor = Color(0xFF1976D2)

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        with(density) { vpX.dp.roundToPx() },
                        with(density) { vpY.dp.roundToPx() }
                    )
                }
                .wrapContentSize()
        ) {
            Box(
                modifier = Modifier
                    .width(vpW.dp)
                    .height(vpH.dp)
                    .background(vpColor.copy(alpha = 0.08f))
                    .border(2.dp, vpColor.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                vpX = snapCell(vpX)
                                vpY = snapCell(vpY)
                                onViewportChange(ViewportEntry(vpX, vpY, vpW, vpH))
                            }
                        ) { change, drag ->
                            change.consume()
                            vpX = (vpX + with(density) { drag.x.toDp().value }).coerceAtLeast(0f)
                            vpY = (vpY + with(density) { drag.y.toDp().value }).coerceAtLeast(0f)
                        }
                    }
            ) {
                Text(
                    text = label,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .background(vpColor.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = vpColor
                )
            }

            ViewportResizeHandles(
                width = vpW,
                height = vpH,
                density = density,
                color = vpColor,
                onResize = { dw, dh, dx, dy ->
                    if (dw != 0f) vpW = snapCellSize(vpW + dw)
                    if (dh != 0f) vpH = snapCellSize(vpH + dh)
                    if (dx != 0f) vpX = snapCell((vpX + dx).coerceAtLeast(0f))
                    if (dy != 0f) vpY = snapCell((vpY + dy).coerceAtLeast(0f))
                },
                onResizeEnd = { onViewportChange(ViewportEntry(vpX, vpY, vpW, vpH)) }
            )
        }
    }
}

@Composable
private fun ViewportResizeHandles(
    width: Float,
    height: Float,
    density: androidx.compose.ui.unit.Density,
    color: Color,
    onResize: (dw: Float, dh: Float, dx: Float, dy: Float) -> Unit,
    onResizeEnd: () -> Unit
) {
    val handleSize = 20.dp
    val half = with(density) { handleSize.toPx() / 2 }

    data class Handle(val xF: Float, val yF: Float, val dw: Float, val dh: Float, val dx: Float, val dy: Float)

    listOf(
        Handle(0f, 0f, -1f, -1f, 1f, 1f),
        Handle(1f, 0f, 1f, -1f, 0f, 1f),
        Handle(1f, 1f, 1f, 1f, 0f, 0f),
        Handle(0f, 1f, -1f, 1f, 1f, 0f),
    ).forEach { h ->
        val xOff = with(density) { (width * h.xF).dp.roundToPx() - half.roundToInt() }
        val yOff = with(density) { (height * h.yF).dp.roundToPx() - half.roundToInt() }
        Box(
            modifier = Modifier
                .offset { IntOffset(xOff, yOff) }
                .size(handleSize)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.85f))
                .pointerInput(width, height) {
                    detectDragGestures(onDragEnd = onResizeEnd) { change, drag ->
                        change.consume()
                        val dpX = with(density) { drag.x.toDp().value }
                        val dpY = with(density) { drag.y.toDp().value }
                        onResize(dpX * h.dw, dpY * h.dh, dpX * h.dx, dpY * h.dy)
                    }
                }
        )
    }
}
