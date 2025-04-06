package com.penzov.objectdetectorapp

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

class YoloV8Classifier(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String,
    private val onResult: (List<BoundingBox>, Long) -> Unit,
    private val onEmpty: () -> Unit
) {

    private var interpreter: Interpreter
    private var labels = listOf<String>()

    private var tensorWidth = 0
    private var tensorHeight = 0
    private var numChannel = 0
    private var numElements = 0

    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(0f, 255f))
        .add(CastOp(DataType.FLOAT32))
        .build()

    init {
        Log.d("ClassifierInit", "🧠 Начинаем инициализацию")
        Log.d("ClassifierInit", "📦 Загружаем модель: $modelPath")
        Log.d("ClassifierInit", "📃 Загружаем labels: $labelPath")

        val compatList = CompatibilityList()
        val options = Interpreter.Options().apply {
            if (compatList.isDelegateSupportedOnThisDevice) {
                val delegateOptions = compatList.bestOptionsForThisDevice
                addDelegate(GpuDelegate(delegateOptions))
                Log.d("ClassifierInit", "⚡ Используем GPU")
            } else {
                setNumThreads(4)
                Log.d("ClassifierInit", "⚠️ Используем только CPU")
            }
        }

        val model = FileUtil.loadMappedFile(context, modelPath)
        interpreter = Interpreter(model, options)
        Log.d("ClassifierInit", "✅ Interpreter создан")

        val inputShape = interpreter.getInputTensor(0).shape()
        val outputShape = interpreter.getOutputTensor(0).shape()
        tensorWidth = inputShape[1]
        tensorHeight = inputShape[2]

        numChannel = outputShape[1]
        numElements = outputShape[2]

        labels = FileUtil.loadLabels(context, labelPath)
    }

    fun runInference(bitmap: Bitmap) {
        val start = SystemClock.uptimeMillis()

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, tensorWidth, tensorHeight, false)
        val tensorImage = TensorImage(DataType.FLOAT32).apply {
            load(resizedBitmap)
        }
        val processedImage = imageProcessor.process(tensorImage)

        val output = TensorBuffer.createFixedSize(
            intArrayOf(1, numChannel, numElements), DataType.FLOAT32
        )
        interpreter.run(processedImage.buffer, output.buffer)

        val result = processOutput(output.floatArray)
        val duration = SystemClock.uptimeMillis() - start

        if (result.isEmpty()) {
            onEmpty()
        } else {
            onResult(result, duration)
        }
    }


    private fun processOutput(array: FloatArray): List<BoundingBox> {
        val boxes = mutableListOf<BoundingBox>()

        for (i in 0 until numElements) {
            var maxConf = CONFIDENCE_THRESHOLD
            var maxIdx = -1
            var j = 4
            var arrayIdx = i + numElements * j
            while (j < numChannel) {
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx]
                    maxIdx = j - 4
                }
                j++
                arrayIdx += numElements
            }

            if (maxIdx != -1) {
                val cx = array[i]
                val cy = array[i + numElements]
                val w = array[i + numElements * 2]
                val h = array[i + numElements * 3]

                val x1 = cx - w / 2
                val y1 = cy - h / 2

                if (x1 in 0f..1f && y1 in 0f..1f && (x1 + w) in 0f..1f && (y1 + h) in 0f..1f) {
                    boxes.add(
                        BoundingBox(
                            x = x1,
                            y = y1,
                            width = w,
                            height = h,
                            confidence = maxConf,
                            classId = maxIdx,
                            label = labels.getOrElse(maxIdx) { "unknown" }
                        )
                    )
                }
            }
        }

        return applyNMS(boxes)
    }


    private fun applyNMS(boxes: List<BoundingBox>): List<BoundingBox> {
        val sorted = boxes.sortedByDescending { it.confidence }.toMutableList()

        val selected = mutableListOf<BoundingBox>()

        while (sorted.isNotEmpty()) {
            val first = sorted.removeAt(0)
            selected.add(first)

            val iter = sorted.iterator()
            while (iter.hasNext()) {
                val next = iter.next()
                if (iou(first, next) >= IOU_THRESHOLD) {
                    iter.remove()
                }
            }
        }

        return selected
    }

    private fun iou(a: BoundingBox, b: BoundingBox): Float {
        val x1 = maxOf(a.x, b.x)
        val y1 = maxOf(a.y, b.y)
        val x2 = minOf(a.x2, b.x2)
        val y2 = minOf(a.y2, b.y2)

        val interWidth = maxOf(0f, x2 - x1)
        val interHeight = maxOf(0f, y2 - y1)
        val interArea = interWidth * interHeight

        val areaA = a.width * a.height
        val areaB = b.width * b.height

        return interArea / (areaA + areaB - interArea)
    }


    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.3f
        private const val IOU_THRESHOLD = 0.5f
    }
}



