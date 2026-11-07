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
import com.widgetwesizer.app.data.model.GridSelection
import com.widgetwesizer.app.data.model.WidgetEntry
import com.widgetwesizer.app.data.repository.WidgetRepository
import kotlinx.coroutines.flow.first
import java.io.File

object BoardSnapshot {

    // Cap the larger bitmap dimension to keep RemoteViews memory reasonable
    private const val MAX_DIM = 1080

    // Upper bound on tap zones per home screen widget
    private const val MAX_HOTSPOTS = 96

    private fun filename(selectionId: Int) = "board_snapshot_$selectionId.png"

    fun save(context: Context, bitmap: Bitmap, selectionId: Int) {
        try {
            File(context.filesDir, filename(selectionId)).outputStream().use { out ->
                // PNG preserves the alpha channel so the transparent board background
                // shows through on the home screen
                scaleSafe(bitmap).compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (_: Exception) {}
    }

    fun load(context: Context, selectionId: Int): Bitmap? = try {
        val png = File(context.filesDir, filename(selectionId))
        val legacyJpg = File(context.filesDir, "board_snapshot_$selectionId.jpg")
        val legacyOld = File(context.filesDir, "board_snapshot.jpg")
        val file = when {
            png.exists() -> png
            legacyJpg.exists() -> legacyJpg           // migrate from old JPEG format
            selectionId == 1 && legacyOld.exists() -> legacyOld
            else -> null
        } ?: return null
        BitmapFactory.decodeFile(file.absolutePath)
    } catch (_: Exception) { null }

    suspend fun pushToHomeScreenWidgets(context: Context) {
        val awm = AppWidgetManager.getInstance(context)
        val ids = awm.getAppWidgetIds(ComponentName(context, WidgetWezizerAppWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val repo = WidgetRepository(context)
        val configs = repo.getWidgetInstanceConfigs().first()
        val selections = repo.getGridSelections().first()
        val entries = repo.getWidgets().first()

        for (appWidgetId in ids) {
            val selectionId = configs.find { it.appWidgetId == appWidgetId }?.gridSelectionId ?: 1
            awm.updateAppWidget(appWidgetId, buildViews(context, appWidgetId, selectionId, selections, entries))
        }
    }

    /**
     * Home screen widget for one grid selection: the board snapshot, covered by a grid of
     * invisible tap zones that forward taps to the real widgets (see WidgetTapActivity).
     * Before any snapshot exists, the whole widget just opens the app.
     */
    fun buildViews(
        context: Context,
        appWidgetId: Int,
        selectionId: Int,
        selections: List<GridSelection>,
        entries: List<WidgetEntry>
    ): RemoteViews {
        val bitmap = load(context, selectionId)
        val selection = selections.find { it.id == selectionId }
        if (bitmap == null || selection == null) {
            val views = if (bitmap != null) snapshotViews(context, bitmap) else placeholderViews(context)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                resolveClickIntent(context, appWidgetId, selectionId, selections, entries)
            )
            return views
        }

        val views = snapshotViews(context, bitmap)
        views.removeAllViews(R.id.widget_hotspots)
        views.setViewVisibility(R.id.widget_hotspots, android.view.View.VISIBLE)

        // Zones per board cell: finer for small selections, capped to keep the widget light.
        val boardCells = selection.cols * selection.rows
        val perCell = kotlin.math.sqrt(MAX_HOTSPOTS.toFloat() / boardCells).toInt().coerceIn(1, 4)
        val cols = selection.cols * perCell
        val rows = selection.rows * perCell

        for (r in 0 until rows) {
            val row = RemoteViews(context.packageName, R.layout.widget_hotspot_row)
            for (c in 0 until cols) {
                val cell = RemoteViews(context.packageName, R.layout.widget_hotspot_cell)
                cell.setOnClickPendingIntent(
                    R.id.hotspot_cell,
                    WidgetTapActivity.pendingIntent(
                        context, appWidgetId, selectionId,
                        fx = (c + 0.5f) / cols, fy = (r + 0.5f) / rows,
                        cellFw = 1f / cols, cellFh = 1f / rows,
                        cellIndex = r * cols + c
                    )
                )
                row.addView(R.id.hotspot_row, cell)
            }
            views.addView(R.id.widget_hotspots, row)
        }
        return views
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
        views.setViewVisibility(R.id.widget_hotspots, android.view.View.GONE)
        views.setInt(R.id.widget_snapshot, "setVisibility", android.view.View.GONE)
        views.setInt(R.id.widget_placeholder, "setVisibility", android.view.View.VISIBLE)
        return views
    }

    /**
     * Returns a PendingIntent that opens the app most prominently displayed in the given
     * grid selection (largest overlap area). Falls back to opening WidgetWesizer itself
     * if no board widgets overlap the selection.
     */
    fun resolveClickIntent(
        context: Context,
        appWidgetId: Int,
        selectionId: Int,
        selections: List<GridSelection>,
        entries: List<WidgetEntry>
    ): PendingIntent {
        val selection = selections.find { it.id == selectionId }

        val launchIntent: Intent? = if (selection != null) {
            val selL = selection.boardX
            val selT = selection.boardY
            val selR = selL + selection.widthDp
            val selB = selT + selection.heightDp

            // Pick the board widget with the largest intersection area
            entries.mapNotNull { entry ->
                val iL = maxOf(selL, entry.offsetXDp)
                val iT = maxOf(selT, entry.offsetYDp)
                val iR = minOf(selR, entry.offsetXDp + entry.widthDp)
                val iB = minOf(selB, entry.offsetYDp + entry.heightDp)
                if (iR > iL && iB > iT) ((iR - iL) * (iB - iT)) to entry.packageName else null
            }.maxByOrNull { it.first }?.second?.let { pkg ->
                context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
        } else null

        val intent = launchIntent ?: Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            context, appWidgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Launch intent for the topmost board widget at the given board point (dp), if any. */
    fun launchIntentAt(context: Context, entries: List<WidgetEntry>, xDp: Float, yDp: Float): Intent? =
        entries.lastOrNull { e ->
            xDp >= e.offsetXDp && xDp < e.offsetXDp + e.widthDp &&
                yDp >= e.offsetYDp && yDp < e.offsetYDp + e.heightDp
        }?.let { context.packageManager.getLaunchIntentForPackage(it.packageName) }

    private fun scaleSafe(src: Bitmap): Bitmap {
        // Scale uniformly so the larger dimension is at most MAX_DIM, preserving aspect ratio
        val ratio = MAX_DIM.toFloat() / maxOf(src.width, src.height)
        if (ratio >= 1f) return src
        return Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt().coerceAtLeast(1),
            (src.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
