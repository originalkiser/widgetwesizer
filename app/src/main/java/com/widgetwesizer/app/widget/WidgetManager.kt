package com.widgetwesizer.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import java.lang.ref.WeakReference

enum class ConfigureResult {
    /** Setup finished (or the widget needs none). */
    OK,
    /**
     * Setup reported "cancelled". Some widgets (e.g. Google Home "Custom") close their first
     * screen and continue in another one, which also reports cancelled, so this is not
     * always a real cancel.
     */
    CANCELED,
    /** The setup screen couldn't be opened and the widget requires it. */
    UNAVAILABLE
}

class WidgetManager private constructor(private val context: Context) {

    private val host = WidgetBoardHost(context)
    private val appWidgetManager = AppWidgetManager.getInstance(context)

    private var bindLauncher: ActivityResultLauncher<Intent>? = null
    private var activityRef: WeakReference<Activity>? = null

    // Only one bind or configure flow runs at a time, so track the widget id here
    // instead of relying on the result Intent (some activities return no data).
    private var pendingBind: Pair<Int, (Boolean) -> Unit>? = null
    private var pendingConfigure: Pair<Int, (ConfigureResult) -> Unit>? = null

    fun setBindLauncher(launcher: ActivityResultLauncher<Intent>) {
        bindLauncher = launcher
    }

    fun attachActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun detachActivity(activity: Activity) {
        if (activityRef?.get() === activity) activityRef = null
    }

    fun startListening() = host.startListening()
    fun stopListening() = host.stopListening()

    fun allocateWidgetId(): Int = host.allocateAppWidgetId()

    /**
     * Binds the widget silently if allowed (adb grantbind, or "Always allow" ticked before),
     * otherwise shows Android's "Allow WidgetWesizer to create widgets?" dialog.
     */
    fun bindWidget(
        appWidgetId: Int,
        provider: ComponentName,
        options: Bundle,
        onResult: (Boolean) -> Unit
    ) {
        val bound = try {
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider, options)
        } catch (e: Exception) {
            Log.w(TAG, "bindAppWidgetIdIfAllowed failed for $provider", e)
            false
        }
        if (bound) {
            onResult(true)
            return
        }

        val launcher = bindLauncher
        if (launcher == null) {
            onResult(false)
            return
        }
        pendingBind = appWidgetId to onResult
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
        }
        try {
            launcher.launch(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not show bind dialog", e)
            pendingBind = null
            onResult(false)
        }
    }

    fun onBindResult(resultCode: Int) {
        val (_, cb) = pendingBind ?: return
        pendingBind = null
        cb(resultCode == Activity.RESULT_OK)
    }

    /**
     * Opens the widget's own setup screen, if it has one.
     *
     * Must go through AppWidgetHost.startAppWidgetConfigureActivityForResult: many providers
     * (e.g. Google Home) don't export their configure activity, so launching it directly
     * throws a SecurityException. The host API asks the system to launch it for us.
     */
    fun launchConfigureActivity(
        appWidgetId: Int,
        info: AppWidgetProviderInfo,
        onResult: (ConfigureResult) -> Unit
    ) {
        if (info.configure == null) {
            onResult(ConfigureResult.OK)
            return
        }
        val unavailable =
            if (isConfigurationOptional(info)) ConfigureResult.OK else ConfigureResult.UNAVAILABLE
        val activity = activityRef?.get()
        if (activity == null) {
            onResult(unavailable)
            return
        }
        pendingConfigure = appWidgetId to onResult
        try {
            host.startAppWidgetConfigureActivityForResult(
                activity, appWidgetId, 0, REQUEST_CONFIGURE, null
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not open configure activity for ${info.provider}", e)
            pendingConfigure = null
            // If setup is optional the widget still works with its defaults.
            onResult(unavailable)
        }
    }

    fun onConfigureResult(resultCode: Int) {
        val (_, cb) = pendingConfigure ?: return
        pendingConfigure = null
        cb(if (resultCode == Activity.RESULT_OK) ConfigureResult.OK else ConfigureResult.CANCELED)
    }

    private fun isConfigurationOptional(info: AppWidgetProviderInfo): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0

    fun deleteWidget(appWidgetId: Int) {
        try {
            host.deleteAppWidgetId(appWidgetId)
        } catch (e: Exception) {
            Log.w(TAG, "deleteAppWidgetId failed", e)
        }
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
        try {
            appWidgetManager.updateAppWidgetOptions(appWidgetId, options)
        } catch (e: Exception) {
            Log.w(TAG, "updateAppWidgetOptions failed", e)
        }
    }

    companion object {
        private const val TAG = "WidgetManager"
        const val REQUEST_CONFIGURE = 4201

        @Volatile
        private var instance: WidgetManager? = null

        fun getInstance(context: Context): WidgetManager {
            return instance ?: synchronized(this) {
                instance ?: WidgetManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
