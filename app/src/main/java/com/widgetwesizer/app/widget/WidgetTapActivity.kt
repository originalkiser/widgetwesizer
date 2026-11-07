package com.widgetwesizer.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.widgetwesizer.app.MainActivity
import com.widgetwesizer.app.data.repository.WidgetRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Invisible activity that forwards a tap on the home screen widget to the real widget.
 *
 * The home screen widget is a snapshot image covered by a grid of tap zones. A tap opens
 * this activity with the zone's position; it loads the board widget under that spot
 * off screen, taps the nearest button in it (so the widget's own action runs, e.g. a
 * Google Home light toggle), and closes. If there's no button there, it opens that
 * widget's app instead, like before.
 */
class WidgetTapActivity : ComponentActivity() {

    private lateinit var container: FrameLayout
    private val host by lazy { AppWidgetHost(applicationContext, TAP_HOST_ID) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        // Host views must be in a window to lay out and run their click handlers;
        // keep them invisible to the user.
        container = FrameLayout(this).apply { alpha = 0f }
        setContentView(container)

        lifecycleScope.launch {
            try {
                handleTap()
            } catch (e: Exception) {
                Log.w(TAG, "Tap forwarding failed", e)
                openApp(null)
            }
            delay(FINISH_DELAY_MS)  // let posted click handlers run before closing
            finish()
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private suspend fun handleTap() {
        val selectionId = intent.getIntExtra(EXTRA_SELECTION_ID, 1)
        val fx = intent.getFloatExtra(EXTRA_FX, 0.5f)
        val fy = intent.getFloatExtra(EXTRA_FY, 0.5f)
        val cellFw = intent.getFloatExtra(EXTRA_CELL_FW, 0f)
        val cellFh = intent.getFloatExtra(EXTRA_CELL_FH, 0f)

        val repo = WidgetRepository(this)
        val selection = repo.getGridSelections().first().find { it.id == selectionId }
        val entries = repo.getWidgets().first()
        if (selection == null) {
            openApp(null)
            return
        }

        // Tap point and zone half-size in board dp.
        val xDp = selection.boardX + fx * selection.widthDp
        val yDp = selection.boardY + fy * selection.heightDp
        val slopXDp = cellFw * selection.widthDp / 2f
        val slopYDp = cellFh * selection.heightDp / 2f

        // Widgets overlapping the tap zone, topmost (drawn last) first.
        val candidates = entries.filter { e ->
            xDp + slopXDp > e.offsetXDp && xDp - slopXDp < e.offsetXDp + e.widthDp &&
                yDp + slopYDp > e.offsetYDp && yDp - slopYDp < e.offsetYDp + e.heightDp
        }.reversed()

        val density = resources.displayMetrics.density
        val awm = AppWidgetManager.getInstance(this)
        for (entry in candidates) {
            val info = awm.getAppWidgetInfo(entry.appWidgetId) ?: continue
            val view = host.createView(this, entry.appWidgetId, info)
            container.addView(
                view,
                FrameLayout.LayoutParams(
                    (entry.widthDp * density).toInt().coerceAtLeast(1),
                    (entry.heightDp * density).toInt().coerceAtLeast(1)
                )
            )
            withTimeoutOrNull(LAYOUT_TIMEOUT_MS) { awaitLayout(view) }

            val x = (xDp - entry.offsetXDp) * density
            val y = (yDp - entry.offsetYDp) * density
            // Lists can fill in a moment after layout, so look a few times.
            for (wait in RETRY_DELAYS_MS) {
                delay(wait)
                val point = WidgetTouch.findTapPoint(view, x, y, slopXDp * density, slopYDp * density)
                if (point != null) {
                    WidgetTouch.tap(view, point.first, point.second)
                    // Refresh the home screen snapshot once the widget has reacted.
                    SnapshotWorker.scheduleOneTime(this, delaySeconds = 3)
                    return
                }
            }
            container.removeView(view)
        }

        openApp(BoardSnapshot.launchIntentAt(this, entries, xDp, yDp))
    }

    private fun openApp(launch: Intent?) {
        val intent = (launch ?: Intent(this, MainActivity::class.java)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not open app", e)
        }
    }

    private suspend fun awaitLayout(view: View) {
        if (view.isLaidOut && !view.isLayoutRequested) return
        suspendCancellableCoroutine { cont ->
            val listener = object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View, l: Int, t: Int, r: Int, b: Int, oldL: Int, oldT: Int, oldR: Int, oldB: Int
                ) {
                    v.removeOnLayoutChangeListener(this)
                    if (cont.isActive) cont.resume(Unit)
                }
            }
            view.addOnLayoutChangeListener(listener)
            cont.invokeOnCancellation { view.removeOnLayoutChangeListener(listener) }
        }
    }

    companion object {
        private const val TAG = "WidgetTapActivity"
        // Separate host id so this never replaces the board's or the snapshot worker's views.
        private const val TAP_HOST_ID = 1003
        private val RETRY_DELAYS_MS = longArrayOf(0, 250, 600)
        private const val FINISH_DELAY_MS = 400L
        private const val LAYOUT_TIMEOUT_MS = 1000L

        private const val EXTRA_SELECTION_ID = "selection_id"
        private const val EXTRA_FX = "fx"
        private const val EXTRA_FY = "fy"
        private const val EXTRA_CELL_FW = "cell_fw"
        private const val EXTRA_CELL_FH = "cell_fh"

        /** PendingIntent for one tap zone; positions are fractions of the selection. */
        fun pendingIntent(
            context: Context,
            appWidgetId: Int,
            selectionId: Int,
            fx: Float,
            fy: Float,
            cellFw: Float,
            cellFh: Float,
            cellIndex: Int
        ): PendingIntent {
            val intent = Intent(context, WidgetTapActivity::class.java).apply {
                // Unique data per zone so each gets its own PendingIntent.
                data = Uri.parse("widgetwesizer://tap/$appWidgetId/$cellIndex")
                putExtra(EXTRA_SELECTION_ID, selectionId)
                putExtra(EXTRA_FX, fx)
                putExtra(EXTRA_FY, fy)
                putExtra(EXTRA_CELL_FW, cellFw)
                putExtra(EXTRA_CELL_FH, cellFh)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            return PendingIntent.getActivity(
                context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
