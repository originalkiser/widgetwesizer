package com.widgetwesizer.app.widget

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

/** Helpers for tapping inside a hosted widget's view tree. */
object WidgetTouch {

    /** Sends a quick tap (down + up) at [x],[y] in [root]'s coordinates. */
    fun tap(root: View, x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, x, y, 0)
        try {
            root.dispatchTouchEvent(down)
            root.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    /**
     * Finds where to tap so a clickable view inside [root] gets the tap.
     *
     * Prefers the clickable view directly under ([x],[y]). Otherwise picks the clickable view
     * that overlaps most with the box of half-size [slopX] x [slopY] around the point, and
     * returns the centre of that overlap. Returns null if nothing clickable is near.
     */
    fun findTapPoint(root: View, x: Float, y: Float, slopX: Float, slopY: Float): Pair<Float, Float>? {
        if (hitClickable(root, x, y) != null) return x to y

        val area = Rect(
            (x - slopX).toInt(), (y - slopY).toInt(),
            (x + slopX).toInt(), (y + slopY).toInt()
        )
        var best: Rect? = null
        var bestArea = 0
        forEachClickable(root) { rect ->
            val overlap = Rect(rect)
            if (overlap.intersect(area)) {
                val a = overlap.width() * overlap.height()
                if (a > bestArea) {
                    bestArea = a
                    best = overlap
                }
            }
        }
        return best?.let { it.exactCenterX() to it.exactCenterY() }
    }

    private fun isTappable(v: View) = v.isEnabled && (v.isClickable || v.hasOnClickListeners())

    /** Deepest visible clickable view under the point (coordinates relative to [v]). */
    private fun hitClickable(v: View, x: Float, y: Float): View? {
        if (v.visibility != View.VISIBLE) return null
        if (x < 0 || y < 0 || x >= v.width || y >= v.height) return null
        if (v is ViewGroup) {
            for (i in v.childCount - 1 downTo 0) {
                val c = v.getChildAt(i)
                val hit = hitClickable(c, x + v.scrollX - c.left, y + v.scrollY - c.top)
                if (hit != null) return hit
            }
        }
        return if (isTappable(v)) v else null
    }

    /** Calls [block] with the bounds (in [root] coordinates) of every visible clickable view. */
    private fun forEachClickable(root: View, block: (Rect) -> Unit) {
        fun walk(v: View, offX: Int, offY: Int) {
            if (v.visibility != View.VISIBLE) return
            val left = offX + v.left
            val top = offY + v.top
            if (v !== root && isTappable(v)) block(Rect(left, top, left + v.width, top + v.height))
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) walk(v.getChildAt(i), left - v.scrollX, top - v.scrollY)
            }
        }
        // Root's own left/top are relative to its parent, so start from its children.
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) walk(root.getChildAt(i), -root.scrollX, -root.scrollY)
        }
    }
}
