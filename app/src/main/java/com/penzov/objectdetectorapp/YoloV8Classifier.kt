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

// Реализую детектор YOLOv8 + трекер (SimpleSortTracker) для озвучки только новых объектов
class YoloV8Classifier(
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String,
    private val onResult: (List<TrackedBox>, Long) -> Unit, // Теперь отдаю трекнутые боксы
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

    private val tracker = SimpleSortTracker() // Добавил трекер

    init {
        Log.d("ClassifierInit", "\uD83E\uDDE0 Начинаю инициализацию")
        Log.d("ClassifierInit", "\uD83D\uDCE6 Загружаю модель: $modelPath")
        Log.d("ClassifierInit", "\uD83D\uDCC3 Загружаю labels: $labelPath")

        val compatList = CompatibilityList()
        val options = Interpreter.Options().apply {
            if (compatList.isDelegateSupportedOnThisDevice) {
                val delegateOptions = compatList.bestOptionsForThisDevice
                addDelegate(GpuDelegate(delegateOptions))
                Log.d("ClassifierInit", "\u26A1 \u0418спользуем GPU")
            } else {
                setNumThreads(4)
                Log.d("ClassifierInit", "\u26A0\uFE0F \u0418спользуем CPU")
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

        val rawBoxes = processOutput(output.floatArray) // детектированные объекты
        val tracked = tracker.update(rawBoxes) // подаю в трекер

        val duration = SystemClock.uptimeMillis() - start

        if (tracked.isEmpty()) onEmpty() else onResult(tracked, duration)
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
                    val labelEn = labels.getOrElse(maxIdx) { "unknown" }
                    val labelRu = labelTranslations[labelEn] ?: labelEn
                    boxes.add(
                        BoundingBox(
                            x = x1,
                            y = y1,
                            width = w,
                            height = h,
                            confidence = maxConf,
                            classId = maxIdx,
                            label = labelRu
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
        val x2 = minOf(a.x + a.width, b.x + b.width)
        val y2 = minOf(a.y + a.height, b.y + b.height)

        val interArea = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val areaA = a.width * a.height
        val areaB = b.width * b.height

        return interArea / (areaA + areaB - interArea + 1e-5f)
    }

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.3f
        private const val IOU_THRESHOLD = 0.5f

        // Здесь словарь с переводами меток (один раз встроен внутрь класса)
        private val labelTranslations = mapOf(
            "person" to "человек",
            "bicycle" to "велосипед",
            "car" to "машина",
            "motorcycle" to "мотоцикл",
            "airplane" to "самолёт",
            "bus" to "автобус",
            "train" to "поезд",
            "truck" to "грузовик",
            "boat" to "лодка",
            "traffic light" to "светофор",
            "fire hydrant" to "пожарный гидрант",
            "stop sign" to "знак стоп",
            "parking meter" to "паркомат",
            "bench" to "лавочка",
            "bird" to "птица",
            "cat" to "кошка",
            "dog" to "собака",
            "horse" to "лошадь",
            "sheep" to "овца",
            "cow" to "корова",
            "elephant" to "слон",
            "bear" to "медведь",
            "zebra" to "зебра",
            "giraffe" to "жираф",
            "backpack" to "рюкзак",
            "umbrella" to "зонт",
            "handbag" to "сумочка",
            "tie" to "галстук",
            "suitcase" to "чемодан",
            "frisbee" to "фрисби",
            "skis" to "лыжи",
            "snowboard" to "сноуборд",
            "sports ball" to "мяч",
            "kite" to "воздушный змей",
            "baseball bat" to "бейсбольная бита",
            "baseball glove" to "бейсбольная перчатка",
            "skateboard" to "скейтборд",
            "surfboard" to "доска для серфинга",
            "tennis racket" to "теннисная ракетка",
            "bottle" to "бутылка",
            "wine glass" to "бокал",
            "cup" to "чашка",
            "fork" to "вилка",
            "knife" to "нож",
            "spoon" to "ложка",
            "bowl" to "миска",
            "banana" to "банан",
            "apple" to "яблоко",
            "sandwich" to "бутерброд",
            "orange" to "апельсин",
            "broccoli" to "брокколи",
            "carrot" to "морковь",
            "hot dog" to "хот-дог",
            "pizza" to "пицца",
            "donut" to "пончик",
            "cake" to "торт",
            "chair" to "стул",
            "couch" to "диван",
            "potted plant" to "горшечное растение",
            "bed" to "кровать",
            "dining table" to "обеденный стол",
            "toilet" to "туалет",
            "tv" to "телевизор",
            "laptop" to "ноутбук",
            "mouse" to "мышка",
            "remote" to "пульт",
            "keyboard" to "клавиатура",
            "cell phone" to "телефон",
            "microwave" to "микроволновка",
            "oven" to "духовка",
            "toaster" to "тостер",
            "sink" to "раковина",
            "refrigerator" to "холодильник",
            "book" to "книга",
            "clock" to "часы",
            "vase" to "ваза",
            "scissors" to "ножницы",
            "teddy bear" to "плюшевый медведь",
            "hair drier" to "фен",
            "toothbrush" to "зубная щётка",
            "cliff" to "утёс",
            "edge-of-a-pond" to "край водоема",
            "edge-of-the-sidewalk" to "бордюр",
            "fence" to "забор",
            "green-traffic-light-pedestrian" to "зелёный свет для пешехода",
            "hatch" to "люк",
            "pedestrian-sign" to "знак пешехода",
            "railing-fence" to "перила",
            "red-traffic-light-pedestrian" to "красный свет для пешехода",
            "snow" to "снег",
            "steps-down" to "ступени вниз",
            "steps-up" to "ступени вверх",
            "zebra-marking" to "зебра (разметка)"
        )
    }
}


