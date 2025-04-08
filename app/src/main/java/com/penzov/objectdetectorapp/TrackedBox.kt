package com.penzov.objectdetectorapp

// Добавил TrackedBox - это обычный BoundingBox, но с трекинг ID

data class TrackedBox(
    val trackId: Int, // ID трека
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val confidence: Float,
    val classId: Int,
    val label: String
) {
    val x2: Float get() = x + width
    val y2: Float get() = y + height

    // Центр - может использоваться для сравнения объектов
    val centerX: Float get() = x + width / 2
    val centerY: Float get() = y + height / 2
}
