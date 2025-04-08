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
        textSize = 20f
        style = Paint.Style.FILL
        isAntiAlias = true
        setShadowLayer(2f, 1f, 1f, Color.BLACK)
    }

    // 🔁 Теперь отображаю TrackedBox вместо BoundingBox
    private var boxes: List<TrackedBox> = emptyList()
    private var inferenceTimeMs: Long = 0

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

            // 🟥 Рисую рамку вокруг объекта
            canvas.drawRect(left, top, right, bottom, paintBox)

            // 🏷 Подписываю метку, уверенность и трек-ID
            val label = "${box.label} ${(box.confidence * 100).toInt()}% (#${box.trackId})"
            canvas.drawText(label, left + 4, top - 10, paintText)
        }

        // ⏱ Время инференса в правом нижнем углу
        val timeText = "⏱ ${inferenceTimeMs} ms"
        val textWidth = paintTime.measureText(timeText)
        canvas.drawText(timeText, width - textWidth - 16f, height - 12f, paintTime)
    }
}


