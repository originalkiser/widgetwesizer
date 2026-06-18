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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
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
import com.widgetwesizer.app.data.model.GridSelection
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
    val gridSelections by viewModel.gridSelections.collectAsState()
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
                    // Show/hide grid lines + selection overlays
                    IconButton(onClick = { showGrid = !showGrid }) {
                        Icon(
                            imageVector = if (showGrid) Icons.Filled.GridOn else Icons.Filled.GridOff,
                            contentDescription = if (showGrid) "Hide grid" else "Show grid"
                        )
                    }
                    // Snap widgets to grid
                    IconButton(onClick = { snapToGrid = !snapToGrid }) {
                        Icon(
                            imageVector = if (snapToGrid) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = if (snapToGrid) "Snap on" else "Snap off",
                            tint = if (snapToGrid) MaterialTheme.colorScheme.primary
                                   else LocalContentColor.current
                        )
                    }
                    // Add a new grid selection to the board
                    IconButton(onClick = {
                        showGrid = true
                        viewModel.addGridSelection()
                    }) {
                        Icon(Icons.Filled.Crop, contentDescription = "Add grid selection")
                    }
                    // Add a board widget
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
            Box(modifier = Modifier.size(BOARD_SIZE_DP.dp)) {
                if (showGrid) {
                    GridOverlay(modifier = Modifier.fillMaxSize())
                }

                widgets.forEach { entry ->
                    key(entry.appWidgetId) {
                        WidgetCard(
                            entry = entry,
                            widgetManager = widgetManager,
                            snapToGrid = snapToGrid,
                            gridSelections = gridSelections,
                            onUpdateBounds = { id, w, h, x, y ->
                                viewModel.updateWidgetBounds(id, w, h, x, y)
                            },
                            onRemove = { viewModel.removeWidget(it) }
                        )
                    }
                }

                if (showGrid) {
                    gridSelections.forEach { selection ->
                        key(selection.id) {
                            GridSelectionOverlay(
                                selection = selection,
                                onSelectionChange = { viewModel.updateGridSelection(it) },
                                onRemove = { viewModel.removeGridSelection(it) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
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

private fun selectionLabel(sel: GridSelection): String {
    val c1 = 'A' + (sel.boardX / CELL_DP).toInt().coerceIn(0, 25)
    val r1 = (sel.boardY / CELL_DP).toInt() + 1
    val c2 = 'A' + ((sel.boardX + sel.widthDp - 1f) / CELL_DP).toInt().coerceIn(0, 25)
    val r2 = ((sel.boardY + sel.heightDp - 1f) / CELL_DP).toInt() + 1
    return "#${sel.id}  $c1$r1:$c2$r2"
}

@Composable
private fun GridSelectionOverlay(
    selection: GridSelection,
    onSelectionChange: (GridSelection) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    var bx by remember(selection) { mutableFloatStateOf(selection.boardX) }
    var by_ by remember(selection) { mutableFloatStateOf(selection.boardY) }
    var cols by remember(selection) { mutableIntStateOf(selection.cols) }
    var rows by remember(selection) { mutableIntStateOf(selection.rows) }

    val w = cols * CELL_DP
    val h = rows * CELL_DP

    fun snapCell(v: Float) = (v / CELL_DP).roundToInt() * CELL_DP
    fun clampCols(c: Int) = c.coerceIn(1, 4)
    fun clampRows(r: Int) = r.coerceIn(1, 5)

    val label = remember(bx, by_, cols, rows) { selectionLabel(selection.copy(boardX = bx, boardY = by_, cols = cols, rows = rows)) }

    // Each selection gets a distinct hue based on its id
    val hue = (selection.id * 137.5f) % 360f
    val selColor = Color.hsl(hue, 0.6f, 0.4f)

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        with(density) { bx.dp.roundToPx() },
                        with(density) { by_.dp.roundToPx() }
                    )
                }
                .wrapContentSize()
        ) {
            // Selection rectangle
            Box(
                modifier = Modifier
                    .width(w.dp)
                    .height(h.dp)
                    .background(selColor.copy(alpha = 0.08f))
                    .border(2.dp, selColor.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                bx = snapCell(bx)
                                by_ = snapCell(by_)
                                onSelectionChange(selection.copy(boardX = bx, boardY = by_, cols = cols, rows = rows))
                            }
                        ) { change, drag ->
                            change.consume()
                            bx = (bx + with(density) { drag.x.toDp().value }).coerceAtLeast(0f)
                            by_ = (by_ + with(density) { drag.y.toDp().value }).coerceAtLeast(0f)
                        }
                    }
            ) {
                Text(
                    text = label,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .background(selColor.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = selColor
                )
            }

            // Delete button at top-right corner
            IconButton(
                onClick = { onRemove(selection.id) },
                modifier = Modifier
                    .offset {
                        IntOffset(
                            with(density) { (w - 8f).dp.roundToPx() },
                            with(density) { (-8f).dp.roundToPx() }
                        )
                    }
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(selColor.copy(alpha = 0.85f))
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove selection ${selection.id}",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }

            // Corner resize handles
            SelectionResizeHandles(
                cols = cols,
                rows = rows,
                density = density,
                color = selColor,
                onResize = { dc, dr, dx, dy ->
                    if (dc != 0) cols = clampCols(cols + dc)
                    if (dr != 0) rows = clampRows(rows + dr)
                    if (dx != 0f) bx = snapCell((bx + dx).coerceAtLeast(0f))
                    if (dy != 0f) by_ = snapCell((by_ + dy).coerceAtLeast(0f))
                },
                onResizeEnd = {
                    onSelectionChange(selection.copy(boardX = bx, boardY = by_, cols = cols, rows = rows))
                }
            )
        }
    }
}

@Composable
private fun SelectionResizeHandles(
    cols: Int,
    rows: Int,
    density: androidx.compose.ui.unit.Density,
    color: Color,
    onResize: (dc: Int, dr: Int, dx: Float, dy: Float) -> Unit,
    onResizeEnd: () -> Unit
) {
    val w = cols * CELL_DP
    val h = rows * CELL_DP
    val handleSize = 20.dp
    val half = with(density) { handleSize.toPx() / 2 }

    data class Handle(val xF: Float, val yF: Float, val dcSign: Int, val drSign: Int, val moveX: Boolean, val moveY: Boolean)

    listOf(
        Handle(0f, 0f, -1, -1, true, true),   // NW: shrink/grow left+top
        Handle(1f, 0f, 1, -1, false, true),   // NE: grow/shrink right, move top
        Handle(1f, 1f, 1, 1, false, false),   // SE: grow right+bottom
        Handle(0f, 1f, -1, 1, true, false),   // SW: shrink left, grow bottom
    ).forEach { handle ->
        val xOff = with(density) { (w * handle.xF).dp.roundToPx() - half.roundToInt() }
        val yOff = with(density) { (h * handle.yF).dp.roundToPx() - half.roundToInt() }

        var accumulated by remember { mutableFloatStateOf(0f) }
        var accumulatedY by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .offset { IntOffset(xOff, yOff) }
                .size(handleSize)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.85f))
                .pointerInput(cols, rows) {
                    detectDragGestures(
                        onDragStart = { accumulated = 0f; accumulatedY = 0f },
                        onDragEnd = { accumulated = 0f; accumulatedY = 0f; onResizeEnd() }
                    ) { change, drag ->
                        change.consume()
                        val dpX = with(density) { drag.x.toDp().value }
                        val dpY = with(density) { drag.y.toDp().value }

                        accumulated += dpX * handle.dcSign
                        accumulatedY += dpY * handle.drSign

                        val dc = (accumulated / CELL_DP).toInt()
                        val dr = (accumulatedY / CELL_DP).toInt()

                        if (dc != 0) {
                            accumulated -= dc * CELL_DP
                            val moveXDp = if (handle.moveX) -(dc * CELL_DP) else 0f
                            onResize(dc, 0, moveXDp, 0f)
                        }
                        if (dr != 0) {
                            accumulatedY -= dr * CELL_DP
                            val moveYDp = if (handle.moveY) -(dr * CELL_DP) else 0f
                            onResize(0, dr, 0f, moveYDp)
                        }
                    }
                }
        )
    }
}
