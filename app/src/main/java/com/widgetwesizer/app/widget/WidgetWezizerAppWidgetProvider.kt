package com.widgetwesizer.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.widgetwesizer.app.MainActivity
import com.widgetwesizer.app.R
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
                val configs = WidgetRepository(context).getWidgetInstanceConfigs().first()
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                for (appWidgetId in appWidgetIds) {
                    val selectionId = configs.find { it.appWidgetId == appWidgetId }?.gridSelectionId ?: 1
                    val bitmap = BoardSnapshot.load(context, selectionId)
                    val views = if (bitmap != null) BoardSnapshot.snapshotViews(context, bitmap)
                               else BoardSnapshot.placeholderViews(context)
                    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
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
