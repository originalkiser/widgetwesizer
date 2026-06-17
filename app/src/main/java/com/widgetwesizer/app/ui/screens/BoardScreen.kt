package com.widgetwesizer.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.widgetwesizer.app.data.model.WidgetEntry
import com.widgetwesizer.app.ui.viewmodel.WidgetBoardViewModel
import com.widgetwesizer.app.widget.WidgetManager

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
    val snackbarHostState = remember { SnackbarHostState() }
    var snapToGrid by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.cleanupRemovedProviders(context)
    }

    LaunchedEffect(removedNames) {
        if (removedNames.isNotEmpty()) {
            val names = removedNames.joinToString(", ")
            snackbarHostState.showSnackbar("Removed unavailable widget(s): $names")
            viewModel.clearRemovedWidgetNames()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("WidgetWesizer") },
                actions = {
                    IconButton(onClick = { snapToGrid = !snapToGrid }) {
                        Icon(
                            imageVector = if (snapToGrid) Icons.Filled.GridOn else Icons.Filled.GridOff,
                            contentDescription = if (snapToGrid) "Snap to grid on" else "Snap to grid off"
                        )
                    }
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
            val canvasSize = 2000.dp

            if (snapToGrid) {
                GridBackground(modifier = Modifier.size(canvasSize))
            }

            Box(modifier = Modifier.size(canvasSize)) {
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
            }
        }
    }
}

@Composable
private fun GridBackground(modifier: Modifier = Modifier) {
    val dotColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
    val gridDp = 8.dp
    Canvas(modifier = modifier) {
        val gridPx = gridDp.toPx()
        val cols = (size.width / gridPx).toInt()
        val rows = (size.height / gridPx).toInt()
        for (col in 0..cols) {
            for (row in 0..rows) {
                drawCircle(
                    color = dotColor,
                    radius = 1.5f,
                    center = Offset(col * gridPx, row * gridPx)
                )
            }
        }
    }
}
