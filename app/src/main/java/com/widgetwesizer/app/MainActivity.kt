package com.widgetwesizer.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.widgetwesizer.app.ui.navigation.NavGraph
import com.widgetwesizer.app.ui.theme.WidgetWesizerTheme
import com.widgetwesizer.app.ui.viewmodel.WidgetBoardViewModel
import com.widgetwesizer.app.widget.SnapshotWorker
import com.widgetwesizer.app.widget.WidgetManager

class MainActivity : ComponentActivity() {

    private val widgetManager by lazy { WidgetManager.getInstance(this) }

    private val bindWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        widgetManager.onBindResult(result.resultCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        widgetManager.setBindLauncher(bindWidgetLauncher)
        widgetManager.attachActivity(this)
        SnapshotWorker.schedule(this)

        setContent {
            WidgetWesizerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: WidgetBoardViewModel = viewModel(
                        factory = WidgetBoardViewModel.Factory(application, widgetManager)
                    )
                    NavGraph(viewModel = viewModel, widgetManager = widgetManager)
                }
            }
        }
    }

    // Configure activities are started via AppWidgetHost, which reports back here.
    @Deprecated("Needed for AppWidgetHost.startAppWidgetConfigureActivityForResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == WidgetManager.REQUEST_CONFIGURE) {
            widgetManager.onConfigureResult(resultCode)
        }
    }

    override fun onDestroy() {
        widgetManager.detachActivity(this)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        widgetManager.startListening()
    }

    override fun onPause() {
        super.onPause()
        // Trigger a background capture so the home screen widget refreshes when we leave
        SnapshotWorker.scheduleOneTime(this)
    }

    override fun onStop() {
        super.onStop()
        widgetManager.stopListening()
    }
}
