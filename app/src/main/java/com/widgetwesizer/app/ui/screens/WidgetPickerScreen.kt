package com.widgetwesizer.app.ui.screens

import android.appwidget.AppWidgetManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.widgetwesizer.app.data.model.WidgetEntry
import com.widgetwesizer.app.data.model.WidgetProviderItem
import com.widgetwesizer.app.ui.viewmodel.WidgetBoardViewModel
import com.widgetwesizer.app.widget.ConfigureResult
import com.widgetwesizer.app.widget.WidgetManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerScreen(
    viewModel: WidgetBoardViewModel,
    widgetManager: WidgetManager,
    onNavigateBack: () -> Unit,
    onOpenPermissionTip: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedForAction by remember { mutableStateOf<WidgetProviderItem?>(null) }

    val allWidgets = remember { viewModel.getAllAvailableWidgets(context) }
    val grouped = remember(allWidgets) {
        allWidgets.groupBy { it.appLabel }.toSortedMap(compareBy { it.lowercase() })
    }

    // Destination choice dialog
    selectedForAction?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedForAction = null },
            title = { Text(item.label) },
            text = { Text("Add this widget to your WidgetWesizer board?") },
            confirmButton = {
                TextButton(onClick = {
                    selectedForAction = null
                    bindAndAddWidget(
                        context = context,
                        item = item,
                        widgetManager = widgetManager,
                        viewModel = viewModel,
                        onSuccess = onNavigateBack,
                        onError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                    )
                }) { Text("Add to Board") }
            },
            dismissButton = {
                TextButton(onClick = { selectedForAction = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Add Widget") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenPermissionTip) {
                        Icon(Icons.Outlined.Info, contentDescription = "Skip permission pop-ups")
                    }
                }
            )
        }
    ) { padding ->
        if (allWidgets.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No widgets found on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                grouped.forEach { (appName, items) ->
                    item {
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        HorizontalDivider()
                    }
                    items(items) { widgetItem ->
                        WidgetPickerItem(
                            item = widgetItem,
                            onSelect = { selectedForAction = widgetItem }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPickerItem(
    item: WidgetProviderItem,
    onSelect: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val densityDpi = context.resources.displayMetrics.densityDpi

    val icon: Drawable? = remember(item.provider) {
        try {
            item.providerInfo?.loadIcon(context, densityDpi)
                ?: pm.getApplicationIcon(item.provider.packageName)
        } catch (e: Exception) { null }
    }

    val minWDp = (item.minWidthPx * 160f / densityDpi).toInt()
    val minHDp = (item.minHeightPx * 160f / densityDpi).toInt()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        setImageDrawable(icon)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                },
                modifier = Modifier.size(48.dp)
            )
        } else {
            Box(modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = item.provider.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        if (minWDp > 0 || minHDp > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "Min: $minWDp × $minHDp dp",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private fun bindAndAddWidget(
    context: android.content.Context,
    item: WidgetProviderItem,
    widgetManager: WidgetManager,
    viewModel: WidgetBoardViewModel,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    // Widget size options are in dp, not px.
    val densityDpi = context.resources.displayMetrics.densityDpi
    val minWDp = (item.minWidthPx * 160f / densityDpi).coerceAtLeast(40f)
    val minHDp = (item.minHeightPx * 160f / densityDpi).coerceAtLeast(40f)
    val options = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, minWDp.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWDp.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, minHDp.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHDp.toInt())
    }

    val appWidgetId = try {
        widgetManager.allocateWidgetId()
    } catch (e: Exception) {
        onError("Couldn't add widget: ${e.message ?: "unknown error"}")
        return
    }

    fun fail(msg: String) {
        widgetManager.deleteWidget(appWidgetId)
        onError(msg)
    }

    widgetManager.bindWidget(appWidgetId, item.provider, options) { bound ->
        if (!bound) {
            fail("Permission to add widgets was not granted")
            return@bindWidget
        }

        // Add to the board before opening the widget's setup screen, so the widget isn't lost
        // if setup hands off to another screen or the app is closed in the background.
        run {
            val entry = WidgetEntry(
                appWidgetId = appWidgetId,
                packageName = item.provider.packageName,
                className = item.provider.className,
                label = item.label,
                widthDp = minWDp,
                heightDp = minHDp,
                offsetXDp = 16f,
                offsetYDp = 16f,
                minWidthDp = minWDp,
                minHeightDp = minHDp
            )
            viewModel.addWidget(entry)
        }

        // Re-read provider info after binding; it can be null for providers that weren't
        // in installedProviders when the picker loaded.
        val providerInfo = item.providerInfo
            ?: AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)
        if (providerInfo?.configure != null) {
            widgetManager.launchConfigureActivity(appWidgetId, providerInfo) { result ->
                when (result) {
                    ConfigureResult.OK -> Unit
                    ConfigureResult.CANCELED -> viewModel.postBoardMessage(
                        WidgetBoardViewModel.BoardMessage(
                            "Setup for \"${item.label}\" was closed. If it looks empty, long-press it and tap ⚙ to set it up.",
                            removeWidgetId = appWidgetId
                        )
                    )
                    ConfigureResult.UNAVAILABLE -> {
                        viewModel.removeWidget(appWidgetId)
                        viewModel.postBoardMessage(
                            WidgetBoardViewModel.BoardMessage("Couldn't open setup for \"${item.label}\"")
                        )
                    }
                }
            }
        }
        onSuccess()
    }
}
