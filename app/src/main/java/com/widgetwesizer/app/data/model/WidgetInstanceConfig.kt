package com.widgetwesizer.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WidgetInstanceConfig(
    val appWidgetId: Int,
    val gridSelectionId: Int
)
