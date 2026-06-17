package com.widgetwesizer.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.widgetwesizer.app.R
import java.io.File

object BoardSnapshot {

    private const val FILENAME = "board_snapshot.jpg"
    private const val MAX_W = 720
    private const val MAX_H = 400

    fun save(context: Context, bitmap: Bitmap) {
        try {
            File(context.filesDir, FILENAME).outputStream().use { out ->
                scaleSafe(bitmap).compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        } catch (_: Exception) {}
    }

    fun load(context: Context): Bitmap? = try {
        val file = File(context.filesDir, FILENAME)
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    } catch (_: Exception) { null }

    fun pushToHomeScreenWidgets(context: Context) {
        val awm = AppWidgetManager.getInstance(context)
        val ids = awm.getAppWidgetIds(ComponentName(context, WidgetWezizerAppWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val bitmap = load(context) ?: return
        val views = snapshotViews(context, bitmap)
        awm.updateAppWidget(ids, views)
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
        return Bitmap.createScaledBitmap(
            src, (src.width * ratio).toInt(), (src.height * ratio).toInt(), true
        )
    }
}
