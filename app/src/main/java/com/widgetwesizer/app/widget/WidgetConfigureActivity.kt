package com.widgetwesizer.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.widgetwesizer.app.data.model.GridSelection
import com.widgetwesizer.app.data.model.WidgetInstanceConfig
import com.widgetwesizer.app.data.repository.WidgetRepository
import com.widgetwesizer.app.ui.theme.WidgetWesizerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WidgetConfigureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Default: user cancelled
        setResult(RESULT_CANCELED)

        setContent {
            WidgetWesizerTheme {
                ConfigureScreen(
                    appWidgetId = appWidgetId,
                    onDone = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigureScreen(appWidgetId: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val repository = remember { WidgetRepository(context) }
    val scope = rememberCoroutineScope()

    var selections by remember { mutableStateOf(emptyList<GridSelection>()) }
    LaunchedEffect(Unit) {
        repository.getGridSelections().collect { selections = it }
    }

    fun pick(selection: GridSelection) {
        scope.launch {
            val configs = repository.getWidgetInstanceConfigs().first().toMutableList()
            configs.removeAll { it.appWidgetId == appWidgetId }
            configs.add(WidgetInstanceConfig(appWidgetId, selection.id))
            repository.saveWidgetInstanceConfigs(configs)

            // Push size hints so the launcher places the widget at the right cell count
            val cellDp = 74
            val awm = AppWidgetManager.getInstance(context)
            val opts = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, selection.cols * cellDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, selection.cols * cellDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, selection.rows * cellDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, selection.rows * cellDp)
            }
            awm.updateAppWidgetOptions(appWidgetId, opts)

            SnapshotWorker.scheduleOneTime(context)

            val resultIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            activity?.setResult(Activity.RESULT_OK, resultIntent)
            onDone()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Choose Widget Source") }) }
    ) { padding ->
        if (selections.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No grid selections defined.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Open WidgetWesizer, show the grid, and add selections using the Crop button.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(selections) { selection ->
                    ListItem(
                        headlineContent = { Text("Selection ${selection.id}") },
                        supportingContent = { Text("${selection.cols} × ${selection.rows} cells") },
                        modifier = Modifier.clickable { pick(selection) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
