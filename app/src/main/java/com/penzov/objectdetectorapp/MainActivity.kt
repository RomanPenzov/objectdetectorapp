// расширяю MainActivity, добавляя поддержку выбора источника камеры (встроенная / RTSP)
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem

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

                // 🧠 Сохраняю чувствительность модели
                var confidence by remember {
                    mutableFloatStateOf(prefs.getFloat("confidence", 0.3f))
                }
                LaunchedEffect(confidence) {
                    prefs.edit().putFloat("confidence", confidence).apply()
                }

                // 📂 Сохраняю выбранную модель
                var selectedLabel by remember {
                    mutableStateOf(prefs.getString("model_label", "Плохое зрение") ?: "Плохое зрение")
                }
                LaunchedEffect(selectedLabel) {
                    prefs.edit().putString("model_label", selectedLabel).apply()
                }

                // 📷 Сохраняю выбранный источник камеры
                var cameraSource by remember {
                    mutableStateOf(CameraSourceType.valueOf(prefs.getString("camera_source", "INTERNAL")!!))
                }
                LaunchedEffect(cameraSource) {
                    prefs.edit().putString("camera_source", cameraSource.name).apply()
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        navController = navController,
                        speaker = speaker,
                        notifier = notifier,
                        confidence = confidence,
                        selectedLabel = selectedLabel,
                        cameraSource = cameraSource,
                        onConfidenceChanged = { confidence = it },
                        onModelSelected = { selectedLabel = it },
                        onCameraSourceChanged = { cameraSource = it }
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
    cameraSource: CameraSourceType,
    onConfidenceChanged: (Float) -> Unit,
    onModelSelected: (String) -> Unit,
    onCameraSourceChanged: (CameraSourceType) -> Unit
) {
    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainDetectorScreen(
                speaker = speaker,
                notifier = notifier,
                confidence = confidence,
                selectedLabel = selectedLabel,
                cameraSource = cameraSource,
                onModelSelected = onModelSelected,
                onSettingsClick = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                notifier = notifier,
                confidence = confidence,
                onConfidenceChanged = onConfidenceChanged,
                cameraSource = cameraSource,
                onCameraSourceChanged = onCameraSourceChanged,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

// 🧠 MainDetectorScreen (финальная версия с поддержкой RTSP)
// В этой версии добавляю переключение между камерой телефона (CameraX) и внешней WiFi-камерой (RTSP)
// Использую YoloV8Classifier, OverlayView, Speaker и TelegramNotifier как раньше

@Composable
fun MainDetectorScreen(
    speaker: Speaker,
    notifier: TelegramNotifier,
    confidence: Float,
    selectedLabel: String,
    cameraSource: CameraSourceType,
    onModelSelected: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Карта с путями к моделям TFLite и их метками
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

    // 🎯 Инициализирую YoloV8Classifier при изменении модели или confidence
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

                // 🗣️ Озвучиваю новые объекты
                speaker.speakNewObjects(boxes)

                // 📬 Формирую сообщение для Telegram
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

        if (cameraSource == CameraSourceType.INTERNAL) {
            // 📸 Встроенная камера через CameraX
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
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        } else {
            // 🌐 Внешняя RTSP WiFi камера через ExoPlayer и TextureView
            var analyzer by remember { mutableStateOf<RtspFrameAnalyzer?>(null) }

            AndroidView(
                factory = { ctx ->
                    val textureView = android.view.TextureView(ctx)
                    val overlay = OverlayView(ctx)

                    val frameLayout = FrameLayout(ctx).apply {
                        addView(textureView)
                        addView(overlay)
                    }

                    //val player = com.google.android.exoplayer2.ExoPlayer.Builder(ctx).build()
                    //val mediaItem = com.google.android.exoplayer2.MediaItem.fromUri("rtsp://192.168.10.1:554")
                    val player = ExoPlayer.Builder(ctx).build()
                    val mediaItem = MediaItem.fromUri("rtsp://192.168.10.1:554")
                    player.setMediaItem(mediaItem)
                    player.setVideoTextureView(textureView)
                    player.prepare()
                    player.playWhenReady = true

                    analyzer = RtspFrameAnalyzer(textureView) { bitmap ->
                        classifierState.value?.runInference(bitmap)
                        overlay.setBoxes(trackedBoxes, inferenceTime)
                    }
                    analyzer?.start()

                    frameLayout
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
                update = {}
            )

            // 📌 Не забыть остановить анализ при уходе с экрана
            DisposableEffect(Unit) {
                onDispose {
                    analyzer?.stop()
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = outputText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth())
    }
}


