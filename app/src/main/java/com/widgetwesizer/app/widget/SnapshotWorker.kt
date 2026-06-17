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
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
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
        if (widgets.isEmpty()) return Result.success()

        val bitmap = withContext(Dispatchers.Main) { renderBoard() }
        if (bitmap != null) {
            BoardSnapshot.save(ctx, bitmap)
        }
        // Always push — even if render failed, refresh the widget with the last saved snapshot
        BoardSnapshot.pushToHomeScreenWidgets(ctx)
        return Result.success()
    }

    private fun renderBoard(): Bitmap? {
        val awm = AppWidgetManager.getInstance(ctx)
        val density = ctx.resources.displayMetrics.density

        // Use the same host ID so the AppWidget service routes cached RemoteViews to us
        val host = object : AppWidgetHost(ctx, WidgetBoardHost.HOST_ID) {
            override fun onCreateView(
                context: Context,
                appWidgetId: Int,
                appWidget: AppWidgetProviderInfo?
            ): AppWidgetHostView = AppWidgetHostView(context)
        }

        return try {
            val widgets = WidgetRepository(ctx).getWidgets()
                .let { kotlinx.coroutines.runBlocking { it.first() } }

            // Register views in the host's internal map BEFORE startListening so that
            // the synchronous cached-RemoteViews delivery inside startListening hits them
            val entries = widgets.map { entry ->
                entry to host.createView(ctx, entry.appWidgetId, awm.getAppWidgetInfo(entry.appWidgetId))
            }

            // startListening delivers cached RemoteViews synchronously to already-created views
            host.startListening()

            // Board canvas is 2000dp; scale to fit our 720×400 snapshot budget
            val boardDp = 2000f
            val maxW = 720
            val maxH = 400
            val scale = minOf(maxW / (boardDp * density), maxH / (boardDp * density))

            val bmpW = (boardDp * density * scale).toInt().coerceAtLeast(1)
            val bmpH = (boardDp * density * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

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

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SnapshotWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
