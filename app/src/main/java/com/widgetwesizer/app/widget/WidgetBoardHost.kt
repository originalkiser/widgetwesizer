package com.widgetwesizer.app.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

class WidgetBoardHost(context: Context) : AppWidgetHost(context, HOST_ID) {

    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        return WidgetBoardHostView(context)
    }

    fun startListening() {
        try {
            super.startListening()
        } catch (e: Exception) {
            // Permission may not be granted yet; ignore until granted
        }
    }

    fun stopListening() {
        try {
            super.stopListening()
        } catch (e: Exception) {
            // Ignore
        }
    }

    companion object {
        const val HOST_ID = 1001
    }
}
