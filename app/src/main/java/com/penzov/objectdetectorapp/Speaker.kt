package com.penzov.objectdetectorapp

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class Speaker(context: Context) {

    private lateinit var tts: TextToSpeech
    private var lastSpokenText: String? = null
    private var lastSpokenTime: Long = 0
    private val speakIntervalMs: Long = 10000L // ⏱ Задержка между озвучками

    init {
        var tempTts: TextToSpeech? = null

        tempTts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tempTts?.language = Locale("ru")
            }
        }

        tts = tempTts
    }

    fun speakObjectCounts(labels: List<String>) {
        if (labels.isEmpty()) return

        val grouped = labels.groupingBy { it }.eachCount()

        val sentence = grouped.entries.joinToString(", ") { (label, count) ->
            if (count == 1) label else "${numberToText(count)} $label"
        }

        val currentTime = System.currentTimeMillis()
        val shouldSpeak = sentence != lastSpokenText || (currentTime - lastSpokenTime > speakIntervalMs)

        if (shouldSpeak) {
            tts.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, "tts_id")
            lastSpokenText = sentence
            lastSpokenTime = currentTime
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


