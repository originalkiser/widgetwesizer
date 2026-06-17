package com.widgetwesizer.app.data.model

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName

data class WidgetProviderItem(
    val provider: ComponentName,
    val label: String,
    val appLabel: String,
    val minWidthPx: Int,
    val minHeightPx: Int,
    val providerInfo: AppWidgetProviderInfo?
)
