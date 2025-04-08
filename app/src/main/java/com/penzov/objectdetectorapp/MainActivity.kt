package com.penzov.objectdetectorapp

import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.penzov.objectdetectorapp.ui.theme.ObjectDetectorAppTheme
import com.penzov.objectdetectorapp.Speaker
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var speaker: Speaker // Озвучка TTS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("DEBUG", "\uD83D\uDE80 MainActivity запускается")

        speaker = Speaker(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            ObjectDetectorAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DetectorUI(speaker = speaker)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speaker.shutdown()
        cameraExecutor.shutdown()
    }
}

@Composable
fun DetectorUI(speaker: Speaker) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val models = mapOf(
        "Плохое зрение" to ("model_blind.tflite" to "labels_blind.txt"),
        "Дети" to ("model_child.tflite" to "labels_child.txt"),
        "Пожилые" to ("model_pensioner.tflite" to "labels_pensioner.txt"),
        "Тест" to ("model_test.tflite" to "labels_test.txt")
    )

    var selectedLabel by remember { mutableStateOf(models.keys.first()) }
    val (modelName, labelName) = models[selectedLabel]!!

    var outputText by remember { mutableStateOf("Ожидание инференса...") }
    var trackedBoxes by remember { mutableStateOf(listOf<TrackedBox>()) }
    var inferenceTime by remember { mutableStateOf(0L) }

    val classifierState = remember { mutableStateOf<YoloV8Classifier?>(null) }

    // Пересоздание классификатора при смене модели
    LaunchedEffect(modelName) {
        classifierState.value = YoloV8Classifier(
            context = context,
            modelPath = modelName,
            labelPath = labelName,
            onResult = { boxes, timeMs ->
                trackedBoxes = boxes
                inferenceTime = timeMs
                outputText = "⏱ ${timeMs}мс, Объектов: ${boxes.size}"

                // 🔈 Озвучка только новых объектов
                speaker.speakNewObjects(boxes)
            },
            onEmpty = {
                trackedBoxes = emptyList()
                inferenceTime = 0L
                outputText = "⏱ Нет объектов"
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            var expanded by remember { mutableStateOf(false) }

            Button(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text(text = selectedLabel)
            }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                models.keys.forEach {
                    DropdownMenuItem(
                        text = { Text(it) },
                        onClick = {
                            selectedLabel = it
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val overlay = OverlayView(ctx)

                val frameLayout = FrameLayout(ctx).apply {
                    addView(previewView)
                    addView(overlay)
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().apply {
                        setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val analyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analyzer.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                        val bitmap = previewView.bitmap
                        if (bitmap != null) {
                            classifierState.value?.runInference(bitmap)
                            overlay.setBoxes(trackedBoxes, inferenceTime)
                        }
                        imageProxy.close()
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analyzer
                        )
                    } catch (exc: Exception) {
                        Log.e("CameraX", "Ошибка камеры", exc)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                frameLayout
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = outputText,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth()
        )
    }
}




