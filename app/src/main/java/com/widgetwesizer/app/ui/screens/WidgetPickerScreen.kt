package com.widgetwesizer.app.ui.screens

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import com.widgetwesizer.app.data.model.WidgetEntry
import com.widgetwesizer.app.ui.viewmodel.WidgetBoardViewModel
import com.widgetwesizer.app.widget.WidgetManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerScreen(
    viewModel: WidgetBoardViewModel,
    widgetManager: WidgetManager,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val allWidgets = remember {
        viewModel.getAllAvailableWidgets(context)
            .sortedWith(compareBy({ getAppLabel(context, it) }, { it.loadLabel(context.packageManager) }))
    }

    val grouped = remember(allWidgets) {
        allWidgets.groupBy { getAppLabel(context, it) }.toSortedMap()
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
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            grouped.forEach { (appName, widgets) ->
                item {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    HorizontalDivider()
                }
                items(widgets) { info ->
                    WidgetPickerItem(
                        info = info,
                        onSelect = {
                            bindAndAddWidget(
                                context = context,
                                info = info,
                                widgetManager = widgetManager,
                                viewModel = viewModel,
                                onSuccess = onNavigateBack,
                                onError = { msg ->
                                    // Show error via snackbar after returning from composable
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetPickerItem(
    info: AppWidgetProviderInfo,
    onSelect: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val icon: Drawable? = remember(info) {
        try { info.loadIcon(context, context.resources.displayMetrics.densityDpi) }
        catch (e: Exception) { null }
    }
    val label = info.loadLabel(pm)
    val minW = info.minWidth
    val minH = info.minHeight

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
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = info.provider.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = "Min: ${pxToDp(context, minW)} × ${pxToDp(context, minH)} dp",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

private fun bindAndAddWidget(
    context: android.content.Context,
    info: AppWidgetProviderInfo,
    widgetManager: WidgetManager,
    viewModel: WidgetBoardViewModel,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val appWidgetId = widgetManager.allocateWidgetId()
    val provider = ComponentName(info.provider.packageName, info.provider.className)
    val options = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, info.minWidth)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, info.minWidth)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, info.minHeight)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, info.minHeight)
    }

    widgetManager.bindWidget(appWidgetId, provider, options) { bound ->
        if (!bound) {
            widgetManager.deleteWidget(appWidgetId)
            onError("Failed to bind widget")
            return@bindWidget
        }
        widgetManager.launchConfigureActivity(appWidgetId, info) { configured ->
            if (!configured) {
                onError("Widget configuration cancelled")
                return@launchConfigureActivity
            }
            val densityDpi = context.resources.displayMetrics.densityDpi
            val minWDp = (info.minWidth * 160f / densityDpi)
            val minHDp = (info.minHeight * 160f / densityDpi)
            val entry = WidgetEntry(
                appWidgetId = appWidgetId,
                packageName = info.provider.packageName,
                className = info.provider.className,
                label = info.loadLabel(context.packageManager),
                widthDp = minWDp.coerceAtLeast(40f),
                heightDp = minHDp.coerceAtLeast(40f),
                offsetXDp = 16f,
                offsetYDp = 16f,
                minWidthDp = minWDp,
                minHeightDp = minHDp
            )
            viewModel.addWidget(entry)
            onSuccess()
        }
    }
}

private fun getAppLabel(context: android.content.Context, info: AppWidgetProviderInfo): String {
    return try {
        val appInfo = context.packageManager.getApplicationInfo(info.provider.packageName, 0)
        context.packageManager.getApplicationLabel(appInfo).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        info.provider.packageName
    }
}

private fun pxToDp(context: android.content.Context, px: Int): Int {
    return (px * 160f / context.resources.displayMetrics.densityDpi).toInt()
}
