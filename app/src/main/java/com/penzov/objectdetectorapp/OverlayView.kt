package com.penzov.objectdetectorapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paintBox = Paint().apply {
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val paintText = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        style = Paint.Style.FILL
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }

    private val paintTime = Paint().apply {
        color = Color.LTGRAY
        textSize = 20f // маленький шрифт
        style = Paint.Style.FILL
        isAntiAlias = true
        setShadowLayer(2f, 1f, 1f, Color.BLACK)
    }

    // 🧠 Храню список трекнутых объектов и время инференса
    private var boxes: List<TrackedBox> = emptyList()
    private var inferenceTimeMs: Long = 0

    // 🎨 Кэш цветов по trackId, чтобы каждый объект был своим цветом
    private val trackColors = mutableMapOf<Int, Int>()

    fun setBoxes(boxes: List<TrackedBox>, inferenceTimeMs: Long) {
        this.boxes = boxes
        this.inferenceTimeMs = inferenceTimeMs
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (box in boxes) {
            val left = box.x * width
            val top = box.y * height
            val right = (box.x + box.width) * width
            val bottom = (box.y + box.height) * height

            // 🎨 Получаю цвет для трека
            paintBox.color = getColorForTrack(box.trackId)

            // 🟥 Рисую рамку
            canvas.drawRect(left, top, right, bottom, paintBox)

            // 🏷 Метка + % + ID
            val label = "${box.label} ${(box.confidence * 100).toInt()}% (#${box.trackId})"
            canvas.drawText(label, left + 4, top - 10, paintText)
        }

        // ⏱ Время инференса в правом нижнем углу
        val timeText = "⏱ ${inferenceTimeMs} ms"
        val textWidth = paintTime.measureText(timeText)
        canvas.drawText(timeText, width - textWidth - 16f, height - 12f, paintTime)
    }

    // 🎨 Генерирую уникальный цвет на основе trackId (всегда одинаковый)
    private fun getColorForTrack(trackId: Int): Int {
        return trackColors.getOrPut(trackId) {
            val hue = (trackId * 57) % 360 // разный угол оттенка
            Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.8f, 1f))
        }
    }
}


