// 📷 Класс для получения bitmap-кадров с RTSP-потока и передачи их в YoloV8Classifier
package com.penzov.objectdetectorapp

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.TextureView

class RtspFrameAnalyzer(
    private val textureView: TextureView,
    private val intervalMs: Long = 50L, // частота захвата кадров
    private val onFrame: (Bitmap) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val frameRunnable = object : Runnable {
        override fun run() {
            val bitmap = textureView.bitmap
            if (bitmap != null) onFrame(bitmap)
            handler.postDelayed(this, intervalMs)
        }
    }

    fun start() {
        handler.post(frameRunnable)
    }

    fun stop() {
        handler.removeCallbacks(frameRunnable)
    }
}