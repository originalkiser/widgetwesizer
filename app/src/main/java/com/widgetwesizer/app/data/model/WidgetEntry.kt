package com.widgetwesizer.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WidgetEntry(
    val appWidgetId: Int,
    val packageName: String,
    val className: String,
    val label: String,
    val widthDp: Float,
    val heightDp: Float,
    val offsetXDp: Float = 0f,
    val offsetYDp: Float = 0f,
    val minWidthDp: Float,
    val minHeightDp: Float,
)
