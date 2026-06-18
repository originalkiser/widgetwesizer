package com.widgetwesizer.app.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.widgetwesizer.app.data.model.GridSelection
import com.widgetwesizer.app.data.model.WidgetEntry
import com.widgetwesizer.app.data.repository.WidgetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SnapshotWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val widgets = WidgetRepository(ctx).getWidgets().first()
        val selections = WidgetRepository(ctx).getGridSelections().first()

        if (widgets.isNotEmpty() && selections.isNotEmpty()) {
            for (selection in selections) {
                val bitmap = withContext(Dispatchers.Main) { renderBoard(widgets, selection) }
                if (bitmap != null) {
                    BoardSnapshot.save(ctx, bitmap, selection.id)
                }
            }
        }

        BoardSnapshot.pushToHomeScreenWidgets(ctx)
        return Result.success()
    }

    private fun renderBoard(widgets: List<WidgetEntry>, selection: GridSelection): Bitmap? {
        val awm = AppWidgetManager.getInstance(ctx)
        val density = ctx.resources.displayMetrics.density

        val host = object : AppWidgetHost(ctx, WORKER_HOST_ID) {
            override fun onCreateView(
                context: Context,
                appWidgetId: Int,
                appWidget: AppWidgetProviderInfo?
            ): AppWidgetHostView = AppWidgetHostView(context)
        }

        return try {
            val bmpW = selection.cols * CELL_RENDER_PX
            val bmpH = selection.rows * CELL_RENDER_PX
            val scale = CELL_RENDER_PX / (BOARD_CELL_PX * density)

            val entries = widgets.mapNotNull { entry ->
                val info = awm.getAppWidgetInfo(entry.appWidgetId) ?: return@mapNotNull null
                entry to host.createView(ctx, entry.appWidgetId, info)
            }

            host.startListening()

            val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            canvas.translate(
                -(selection.boardX * density * scale),
                -(selection.boardY * density * scale)
            )

            for ((entry, view) in entries) {
                val wPx = (entry.widthDp * density * scale).toInt().coerceAtLeast(1)
                val hPx = (entry.heightDp * density * scale).toInt().coerceAtLeast(1)
                val xPx = (entry.offsetXDp * density * scale).toInt()
                val yPx = (entry.offsetYDp * density * scale).toInt()

                view.measure(
                    View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY)
                )
                view.layout(0, 0, wPx, hPx)

                canvas.save()
                canvas.translate(xPx.toFloat(), yPx.toFloat())
                view.draw(canvas)
                canvas.restore()
            }

            bitmap
        } catch (e: Exception) {
            null
        } finally {
            try { host.stopListening() } catch (_: Exception) {}
        }
    }

    companion object {
        private const val WORK_NAME = "board_snapshot_periodic"
        private const val WORK_NAME_ONCE = "board_snapshot_once"
        private const val WORKER_HOST_ID = 1002
        private const val BOARD_CELL_PX = 100f
        private const val CELL_RENDER_PX = 360

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SnapshotWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun scheduleOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<SnapshotWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONCE, ExistingWorkPolicy.REPLACE, request
            )
        }
    }
}
