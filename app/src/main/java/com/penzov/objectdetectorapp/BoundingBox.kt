package com.penzov.objectdetectorapp

data class BoundingBox(
    val x: Float,         // верхний левый угол
    val y: Float,
    val width: Float,     // ширина (x2 - x1)
    val height: Float,    // высота (y2 - y1)
    val confidence: Float,
    val classId: Int,
    val label: String
) {
    val x2 get() = x + width
    val y2 get() = y + height
}




