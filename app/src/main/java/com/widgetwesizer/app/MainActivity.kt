package com.widgetwesizer.app

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
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
import com.widgetwesizer.app.widget.BoardSnapshot
import com.widgetwesizer.app.widget.SnapshotWorker
import com.widgetwesizer.app.widget.WidgetManager

class MainActivity : ComponentActivity() {

    private val widgetManager by lazy { WidgetManager.getInstance(this) }

    private val bindWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val appWidgetId = result.data?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, -1
        ) ?: -1
        if (result.resultCode == RESULT_OK && appWidgetId != -1) {
            widgetManager.onBindResult(appWidgetId, success = true)
        } else if (appWidgetId != -1) {
            widgetManager.onBindResult(appWidgetId, success = false)
        }
    }

    private val configureWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val appWidgetId = result.data?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, -1
        ) ?: -1
        if (result.resultCode == RESULT_OK && appWidgetId != -1) {
            widgetManager.onConfigureResult(appWidgetId, success = true)
        } else if (appWidgetId != -1) {
            widgetManager.onConfigureResult(appWidgetId, success = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        widgetManager.setBindLauncher(bindWidgetLauncher)
        widgetManager.setConfigureLauncher(configureWidgetLauncher)
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

    override fun onStart() {
        super.onStart()
        widgetManager.startListening()
    }

    override fun onPause() {
        super.onPause()
        captureBoard()
    }

    override fun onStop() {
        super.onStop()
        widgetManager.stopListening()
    }

    private fun captureBoard() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val decorView = window.decorView
        val w = decorView.width
        val h = decorView.height
        if (w == 0 || h == 0) return

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        PixelCopy.request(window, bitmap, { result ->
            if (result == PixelCopy.SUCCESS) {
                BoardSnapshot.save(applicationContext, bitmap)
                BoardSnapshot.pushToHomeScreenWidgets(applicationContext)
            }
            bitmap.recycle()
        }, Handler(Looper.getMainLooper()))
    }
}
