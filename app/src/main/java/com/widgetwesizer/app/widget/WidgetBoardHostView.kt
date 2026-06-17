package com.widgetwesizer.app.widget

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.widget.RemoteViews

class WidgetBoardHostView(context: Context) : AppWidgetHostView(context) {

    private var onUpdateListener: (() -> Unit)? = null

    fun setOnUpdateListener(listener: () -> Unit) {
        onUpdateListener = listener
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)
        onUpdateListener?.invoke()
    }
}
