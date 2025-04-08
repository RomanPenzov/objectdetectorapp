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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.penzov.objectdetectorapp.ui.theme.ObjectDetectorAppTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var speaker: Speaker
    private lateinit var notifier: TelegramNotifier

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("DEBUG", "🚀 MainActivity запускается")

        speaker = Speaker(this)
        notifier = TelegramNotifier(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            ObjectDetectorAppTheme {
                val navController = rememberNavController()

                val prefs = getSharedPreferences("detector_prefs", MODE_PRIVATE)
                var confidence by remember {
                    mutableFloatStateOf(prefs.getFloat("confidence", 0.3f))
                }
                LaunchedEffect(confidence) {
                    prefs.edit().putFloat("confidence", confidence).apply()
                }

                var selectedLabel by remember {
                    mutableStateOf(prefs.getString("model_label", "Плохое зрение") ?: "Плохое зрение")
                }
                LaunchedEffect(selectedLabel) {
                    prefs.edit().putString("model_label", selectedLabel).apply()
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        navController = navController,
                        speaker = speaker,
                        notifier = notifier,
                        confidence = confidence,
                        selectedLabel = selectedLabel,
                        onConfidenceChanged = { confidence = it },
                        onModelSelected = { selectedLabel = it }
                    )
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
fun AppNavigation(
    navController: NavHostController,
    speaker: Speaker,
    notifier: TelegramNotifier,
    confidence: Float,
    selectedLabel: String,
    onConfidenceChanged: (Float) -> Unit,
    onModelSelected: (String) -> Unit
) {
    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainDetectorScreen(
                speaker = speaker,
                notifier = notifier,
                confidence = confidence,
                selectedLabel = selectedLabel,
                onModelSelected = onModelSelected,
                onSettingsClick = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                notifier = notifier,
                confidence = confidence,
                onConfidenceChanged = onConfidenceChanged,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun MainDetectorScreen(
    speaker: Speaker,
    notifier: TelegramNotifier,
    confidence: Float,
    selectedLabel: String,
    onModelSelected: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val models = mapOf(
        "Плохое зрение" to ("model_blind.tflite" to "labels_blind.txt"),
        "Дети" to ("model_child.tflite" to "labels_child.txt"),
        "Пожилые" to ("model_pensioner.tflite" to "labels_pensioner.txt"),
        "Тест" to ("model_test.tflite" to "labels_test.txt")
    )

    val (modelName, labelName) = models[selectedLabel]!!

    var outputText by remember { mutableStateOf("Ожидание инференса...") }
    var trackedBoxes by remember { mutableStateOf(listOf<TrackedBox>()) }
    var inferenceTime by remember { mutableStateOf(0L) }

    val classifierState = remember { mutableStateOf<YoloV8Classifier?>(null) }

    LaunchedEffect(modelName, confidence) {
        classifierState.value = YoloV8Classifier(
            context = context,
            modelPath = modelName,
            labelPath = labelName,
            confidenceThreshold = confidence,
            onResult = { boxes, timeMs ->
                trackedBoxes = boxes
                inferenceTime = timeMs
                outputText = "⏱ ${timeMs}мс, Объектов: ${boxes.size}"

                speaker.speakNewObjects(boxes)

                val labels = boxes.map { it.label }
                val grouped = labels.groupingBy { it }.eachCount()
                val message = grouped.entries.joinToString(", ") { (label, count) ->
                    if (count == 1) label else "$count $label"
                }
                notifier.sendToAll("📷 Обнаружены: $message")
            },
            onEmpty = {
                trackedBoxes = emptyList()
                inferenceTime = 0L
                outputText = "⏱ Нет объектов"
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            var expanded by remember { mutableStateOf(false) }
            Button(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f)) {
                Text(text = selectedLabel)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                models.keys.forEach {
                    DropdownMenuItem(
                        text = { Text(it) },
                        onClick = {
                            onModelSelected(it)
                            expanded = false
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onSettingsClick) {
                Text("⚙ Настройки")
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

        Text(text = outputText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth())
    }
}

