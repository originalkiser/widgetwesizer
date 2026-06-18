package com.widgetwesizer.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.widgetwesizer.app.MainActivity
import com.widgetwesizer.app.R
import com.widgetwesizer.app.data.repository.WidgetRepository
import kotlinx.coroutines.flow.first
import java.io.File

object BoardSnapshot {

    private const val MAX_W = 720
    private const val MAX_H = 400

    private fun filename(selectionId: Int) = "board_snapshot_$selectionId.jpg"

    fun save(context: Context, bitmap: Bitmap, selectionId: Int) {
        try {
            File(context.filesDir, filename(selectionId)).outputStream().use { out ->
                scaleSafe(bitmap).compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        } catch (_: Exception) {}
    }

    fun load(context: Context, selectionId: Int): Bitmap? = try {
        val specific = File(context.filesDir, filename(selectionId))
        val legacy = File(context.filesDir, "board_snapshot.jpg")
        val file = when {
            specific.exists() -> specific
            selectionId == 1 && legacy.exists() -> legacy
            else -> null
        } ?: return null
        BitmapFactory.decodeFile(file.absolutePath)
    } catch (_: Exception) { null }

    suspend fun pushToHomeScreenWidgets(context: Context) {
        val awm = AppWidgetManager.getInstance(context)
        val ids = awm.getAppWidgetIds(ComponentName(context, WidgetWezizerAppWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val configs = WidgetRepository(context).getWidgetInstanceConfigs().first()
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        for (appWidgetId in ids) {
            val selectionId = configs.find { it.appWidgetId == appWidgetId }?.gridSelectionId ?: 1
            val bitmap = load(context, selectionId)
            val views = if (bitmap != null) snapshotViews(context, bitmap) else placeholderViews(context)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            awm.updateAppWidget(appWidgetId, views)
        }
    }

    fun snapshotViews(context: Context, bitmap: Bitmap): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_widgetwesizer)
        views.setImageViewBitmap(R.id.widget_snapshot, bitmap)
        views.setInt(R.id.widget_snapshot, "setVisibility", android.view.View.VISIBLE)
        views.setInt(R.id.widget_placeholder, "setVisibility", android.view.View.GONE)
        return views
    }

    fun placeholderViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_widgetwesizer)
        views.setInt(R.id.widget_snapshot, "setVisibility", android.view.View.GONE)
        views.setInt(R.id.widget_placeholder, "setVisibility", android.view.View.VISIBLE)
        return views
    }

    private fun scaleSafe(src: Bitmap): Bitmap {
        val ratio = minOf(MAX_W.toFloat() / src.width, MAX_H.toFloat() / src.height)
        if (ratio >= 1f) return src
        return Bitmap.createScaledBitmap(src, (src.width * ratio).toInt(), (src.height * ratio).toInt(), true)
    }
}
