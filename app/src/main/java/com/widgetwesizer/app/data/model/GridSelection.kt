package com.widgetwesizer.app.data.model

import kotlinx.serialization.Serializable

const val BOARD_CELL_DP = 100f

@Serializable
data class GridSelection(
    val id: Int,
    val cols: Int = 2,
    val rows: Int = 2,
    val boardX: Float = 0f,
    val boardY: Float = 0f
) {
    val widthDp: Float get() = cols * BOARD_CELL_DP
    val heightDp: Float get() = rows * BOARD_CELL_DP
}
