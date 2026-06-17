package com.widgetwesizer.app.ui.viewmodel

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.appwidget.AppWidgetHost
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.widgetwesizer.app.data.model.WidgetEntry
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

    fun getAllAvailableWidgets(context: Context): List<AppWidgetProviderInfo> {
        // installedProviders returns WIDGET_CATEGORY_HOME_SCREEN, which is the category
        // every third-party widget declares. The BIND_APPWIDGET permission (granted via
        // grantbind) is what gates whether third-party providers appear in the list.
        return AppWidgetManager.getInstance(context).installedProviders
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
