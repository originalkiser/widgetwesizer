package com.widgetwesizer.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ViewportEntry(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 400f,
    val height: Float = 250f
)
