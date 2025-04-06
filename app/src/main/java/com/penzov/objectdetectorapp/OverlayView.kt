package com.penzov.objectdetectorapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paintBox = Paint().apply {
        color = Color.RED
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
        setShadowLayer(2f, 1f, 1f, Color.BLACK) // тень для читаемости
    }

    private var boxes: List<BoundingBox> = emptyList()
    private var inferenceTimeMs: Long = 0

    fun setBoxes(boxes: List<BoundingBox>, inferenceTimeMs: Long) {
        this.boxes = boxes
        this.inferenceTimeMs = inferenceTimeMs
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (box in boxes) {
            // Преобразуем нормализованные координаты (0..1) в пиксели
            val left = box.x * width
            val top = box.y * height
            val right = (box.x + box.width) * width
            val bottom = (box.y + box.height) * height

            canvas.drawRect(left, top, right, bottom, paintBox)

            val label = "${box.label} ${(box.confidence * 100).toInt()}%"
            canvas.drawText(label, left + 4, top - 10, paintText)
        }

        // Отображаем время инференса в правом нижнем углу
        val timeText = "⏱ ${inferenceTimeMs} ms"
        val textWidth = paintTime.measureText(timeText)
        canvas.drawText(timeText, width - textWidth - 16f, height - 12f, paintTime)
    }
}

