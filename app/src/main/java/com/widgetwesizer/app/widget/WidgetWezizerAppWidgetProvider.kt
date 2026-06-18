package com.widgetwesizer.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.widgetwesizer.app.data.repository.WidgetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WidgetWezizerAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = WidgetRepository(context)
                val configs = repo.getWidgetInstanceConfigs().first()
                val selections = repo.getGridSelections().first()
                val entries = repo.getWidgets().first()

                for (appWidgetId in appWidgetIds) {
                    val selectionId = configs.find { it.appWidgetId == appWidgetId }?.gridSelectionId ?: 1
                    val bitmap = BoardSnapshot.load(context, selectionId)
                    val views = if (bitmap != null) BoardSnapshot.snapshotViews(context, bitmap)
                               else BoardSnapshot.placeholderViews(context)
                    val pendingIntent = BoardSnapshot.resolveClickIntent(
                        context, appWidgetId, selectionId, selections, entries
                    )
                    views.setOnClickPendingIntent(
                        com.widgetwesizer.app.R.id.widget_root,
                        pendingIntent
                    )
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        SnapshotWorker.scheduleOneTime(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        CoroutineScope(Dispatchers.IO).launch {
            val repo = WidgetRepository(context)
            val configs = repo.getWidgetInstanceConfigs().first().toMutableList()
            configs.removeAll { it.appWidgetId in appWidgetIds }
            repo.saveWidgetInstanceConfigs(configs)
        }
    }
}
