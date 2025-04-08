package com.penzov.objectdetectorapp

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

// Обновил Speaker: теперь озвучка работает по трекнутым объектам (TrackedBox)
class Speaker(context: Context) {

    private lateinit var tts: TextToSpeech
    private var lastSpokenTime: Long = 0
    private val speakIntervalMs: Long = 3000L // ⏱ задержка между озвучками (3 сек)

    // Кэш озвученных объектов, чтобы не повторяться
    private val spokenTrackIds = mutableSetOf<Int>()

    init {
        var tempTts: TextToSpeech? = null

        tempTts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tempTts?.language = Locale("ru")
            }
        }

        tts = tempTts
    }

    // 🎤 Получаю список трекнутых боксов и озвучиваю только НОВЫЕ
    fun speakNewObjects(boxes: List<TrackedBox>) {
        if (boxes.isEmpty()) return

        val newBoxes = boxes.filter { it.trackId !in spokenTrackIds }
        if (newBoxes.isEmpty()) return

        val grouped = newBoxes.groupingBy { it.label }.eachCount()
        val sentence = grouped.entries.joinToString(", ") { (label, count) ->
            if (count == 1) label else "${numberToText(count)} $label"
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSpokenTime >= speakIntervalMs) {
            tts.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, "tts_id")
            lastSpokenTime = currentTime
            // Добавляю новые трек-ID в кэш
            spokenTrackIds.addAll(newBoxes.map { it.trackId })
        }
    }

    fun shutdown() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
    }

    private fun numberToText(n: Int): String {
        return when (n) {
            1 -> "один"
            2 -> "два"
            3 -> "три"
            4 -> "четыре"
            5 -> "пять"
            6 -> "шесть"
            7 -> "семь"
            8 -> "восемь"
            9 -> "девять"
            10 -> "десять"
            else -> n.toString()
        }
    }
}



