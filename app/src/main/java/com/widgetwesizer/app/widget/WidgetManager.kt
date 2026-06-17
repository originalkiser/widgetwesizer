package com.widgetwesizer.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher

class WidgetManager private constructor(private val context: Context) {

    private val host = WidgetBoardHost(context)
    private val appWidgetManager = AppWidgetManager.getInstance(context)

    private var bindLauncher: ActivityResultLauncher<Intent>? = null
    private var configureLauncher: ActivityResultLauncher<Intent>? = null

    private val pendingBindCallbacks = mutableMapOf<Int, (Boolean) -> Unit>()
    private val pendingConfigureCallbacks = mutableMapOf<Int, (Boolean) -> Unit>()

    fun setBindLauncher(launcher: ActivityResultLauncher<Intent>) {
        bindLauncher = launcher
    }

    fun setConfigureLauncher(launcher: ActivityResultLauncher<Intent>) {
        configureLauncher = launcher
    }

    fun startListening() = host.startListening()
    fun stopListening() = host.stopListening()

    fun allocateWidgetId(): Int = host.allocateAppWidgetId()

    fun bindWidget(
        appWidgetId: Int,
        provider: ComponentName,
        options: Bundle,
        onResult: (Boolean) -> Unit
    ) {
        val bound = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider, options)
        if (bound) {
            onResult(true)
        } else {
            pendingBindCallbacks[appWidgetId] = onResult
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
            }
            bindLauncher?.launch(intent)
        }
    }

    fun launchConfigureActivity(
        appWidgetId: Int,
        info: AppWidgetProviderInfo,
        onResult: (Boolean) -> Unit
    ) {
        val configureComponent = info.configure ?: run {
            onResult(true)
            return
        }
        pendingConfigureCallbacks[appWidgetId] = onResult
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = configureComponent
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        configureLauncher?.launch(intent)
    }

    fun deleteWidget(appWidgetId: Int) {
        host.deleteAppWidgetId(appWidgetId)
    }

    fun createHostView(
        context: Context,
        appWidgetId: Int,
        info: AppWidgetProviderInfo
    ): WidgetBoardHostView {
        return host.createView(context, appWidgetId, info) as WidgetBoardHostView
    }

    fun updateWidgetSize(appWidgetId: Int, widthDp: Int, heightDp: Int) {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        }
        appWidgetManager.updateAppWidgetOptions(appWidgetId, options)
    }

    fun onBindResult(appWidgetId: Int, success: Boolean) {
        pendingBindCallbacks.remove(appWidgetId)?.invoke(success)
    }

    fun onConfigureResult(appWidgetId: Int, success: Boolean) {
        val cb = pendingConfigureCallbacks.remove(appWidgetId)
        if (!success) {
            deleteWidget(appWidgetId)
        }
        cb?.invoke(success)
    }

    companion object {
        @Volatile
        private var instance: WidgetManager? = null

        fun getInstance(context: Context): WidgetManager {
            return instance ?: synchronized(this) {
                instance ?: WidgetManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
