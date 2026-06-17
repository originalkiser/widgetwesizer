package com.widgetwesizer.app.ui.viewmodel

import android.app.Application
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.widgetwesizer.app.data.model.WidgetEntry
import com.widgetwesizer.app.data.model.WidgetProviderItem
import com.widgetwesizer.app.data.repository.WidgetRepository
import com.widgetwesizer.app.widget.WidgetManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WidgetBoardViewModel(
    application: Application,
    private val widgetManager: WidgetManager
) : AndroidViewModel(application) {

    private val repository = WidgetRepository(application)

    val widgets: StateFlow<List<WidgetEntry>> = repository.getWidgets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isPermissionGranted = MutableStateFlow(checkPermission())
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted

    private val _removedWidgetNames = MutableStateFlow<List<String>>(emptyList())
    val removedWidgetNames: StateFlow<List<String>> = _removedWidgetNames

    fun refreshPermission() {
        _isPermissionGranted.value = checkPermission()
    }

    private fun checkPermission(): Boolean {
        val app = getApplication<Application>()
        // PackageManager.checkPermission only reflects pm-granted permissions.
        // On Android 12+, grantbind sets permission at the AppWidgetService level,
        // which bindAppWidgetIdIfAllowed checks internally. Test-bind to detect it.
        val awm = AppWidgetManager.getInstance(app)
        val providers = awm.installedProviders
        if (providers.isEmpty()) {
            return app.packageManager.checkPermission(
                "android.permission.BIND_APPWIDGET", app.packageName
            ) == PackageManager.PERMISSION_GRANTED
        }
        val tempHost = AppWidgetHost(app, 9998)
        val id = tempHost.allocateAppWidgetId()
        return try {
            val bound = awm.bindAppWidgetIdIfAllowed(id, providers.first().provider)
            tempHost.deleteAppWidgetId(id)
            bound
        } catch (e: Exception) {
            tempHost.deleteAppWidgetId(id)
            false
        }
    }

    fun addWidget(entry: WidgetEntry) {
        viewModelScope.launch {
            val current = widgets.value.toMutableList()
            current.add(entry)
            repository.saveWidgets(current)
        }
    }

    fun removeWidget(appWidgetId: Int) {
        viewModelScope.launch {
            widgetManager.deleteWidget(appWidgetId)
            val current = widgets.value.filter { it.appWidgetId != appWidgetId }
            repository.saveWidgets(current)
        }
    }

    fun updateWidgetBounds(
        appWidgetId: Int,
        widthDp: Float,
        heightDp: Float,
        offsetXDp: Float,
        offsetYDp: Float
    ) {
        viewModelScope.launch {
            val current = widgets.value.map { entry ->
                if (entry.appWidgetId == appWidgetId) {
                    entry.copy(
                        widthDp = widthDp,
                        heightDp = heightDp,
                        offsetXDp = offsetXDp,
                        offsetYDp = offsetYDp
                    )
                } else entry
            }
            repository.saveWidgets(current)
            widgetManager.updateWidgetSize(appWidgetId, widthDp.toInt(), heightDp.toInt())
        }
    }

    fun getAllAvailableWidgets(context: Context): List<WidgetProviderItem> {
        val awm = AppWidgetManager.getInstance(context)
        val pm = context.packageManager

        // AppWidgetManager.installedProviders is permission-gated: without BIND_APPWIDGET
        // granted at the PackageManager level (blocked on Android 12+), it only returns
        // system/whitelisted providers. We bypass it by querying PackageManager directly
        // for all broadcast receivers that handle ACTION_APPWIDGET_UPDATE — every widget
        // provider must declare this, so this gives us the full list with no permission gate.
        val knownMap = awm.installedProviders.associateBy { it.provider }

        val receivers = pm.queryBroadcastReceivers(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE),
            PackageManager.GET_META_DATA
        )

        return receivers.mapNotNull { ri ->
            val ai = ri.activityInfo ?: return@mapNotNull null
            val component = android.content.ComponentName(ai.packageName, ai.name)
            val providerInfo = knownMap[component]

            val appLabel = try {
                pm.getApplicationLabel(pm.getApplicationInfo(ai.packageName, 0)).toString()
            } catch (e: Exception) { ai.packageName }

            val widgetLabel = providerInfo?.loadLabel(pm)
                ?: ri.loadLabel(pm)?.toString()
                ?: appLabel

            val (minW, minH) = if (providerInfo != null) {
                providerInfo.minWidth to providerInfo.minHeight
            } else {
                parseWidgetDimensions(context, ai)
            }

            WidgetProviderItem(
                provider = component,
                label = widgetLabel,
                appLabel = appLabel,
                minWidthPx = minW,
                minHeightPx = minH,
                providerInfo = providerInfo
            )
        }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
    }

    private fun parseWidgetDimensions(
        context: Context,
        ai: android.content.pm.ActivityInfo
    ): Pair<Int, Int> {
        val xmlResId = ai.metaData
            ?.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER, 0) ?: 0
        if (xmlResId == 0) return 0 to 0
        return try {
            val pkgCtx = context.createPackageContext(ai.packageName, 0)
            val parser = pkgCtx.resources.getXml(xmlResId)
            var eventType = parser.eventType
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG
                    && parser.name == "appwidget-provider"
                ) {
                    val ta = pkgCtx.obtainStyledAttributes(
                        android.util.Xml.asAttributeSet(parser),
                        intArrayOf(android.R.attr.minWidth, android.R.attr.minHeight)
                    )
                    val w = ta.getDimensionPixelSize(0, 0)
                    val h = ta.getDimensionPixelSize(1, 0)
                    ta.recycle()
                    return w to h
                }
                eventType = parser.next()
            }
            0 to 0
        } catch (e: Exception) { 0 to 0 }
    }

    fun cleanupRemovedProviders(context: Context) {
        viewModelScope.launch {
            val pm = context.packageManager
            val current = widgets.value
            val (valid, removed) = current.partition { entry ->
                try {
                    pm.getPackageInfo(entry.packageName, 0)
                    true
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
            }
            if (removed.isNotEmpty()) {
                removed.forEach { widgetManager.deleteWidget(it.appWidgetId) }
                repository.saveWidgets(valid)
                _removedWidgetNames.value = removed.map { it.label }
            }
        }
    }

    fun clearRemovedWidgetNames() {
        _removedWidgetNames.value = emptyList()
    }

    class Factory(
        private val application: Application,
        private val widgetManager: WidgetManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WidgetBoardViewModel(application, widgetManager) as T
        }
    }
}
