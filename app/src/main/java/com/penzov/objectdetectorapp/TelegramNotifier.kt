package com.penzov.objectdetectorapp

import android.content.Context
import android.location.Location
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// TelegramNotifier отвечает за:
// ✅ Хранение и редактирование списка получателей Telegram (по userId)
// ✅ Отправку сообщений (текст и геолокация)
// ✅ Использует SharedPreferences для сохранения между запусками
class TelegramNotifier(private val context: Context) {

    private val prefs = context.getSharedPreferences("telegram_prefs", Context.MODE_PRIVATE)
    private val key = "recipient_ids"
    private val client = OkHttpClient()

    private val token = "7981670687:AAFVb69As3SBWof7d_3ll3O6NjaEcbEfyYc"
    private val url = "https://api.telegram.org/bot$token/sendMessage"

    // Загружаю ID из памяти
    fun getRecipients(): Set<String> {
        return prefs.getStringSet(key, emptySet()) ?: emptySet()
    }

    // Добавляю нового получателя
    fun addRecipient(id: String) {
        val updated = getRecipients().toMutableSet().apply { add(id) }
        prefs.edit().putStringSet(key, updated).apply()
    }

    // Удаляю получателя
    fun removeRecipient(id: String) {
        val updated = getRecipients().toMutableSet().apply { remove(id) }
        prefs.edit().putStringSet(key, updated).apply()
    }

    // Отправка сообщения (с текстом и, если надо, координатами)
    fun sendToAll(message: String, location: Location? = null) {
        val fullMessage = buildMessage(message, location)

        for (userId in getRecipients()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val json = JSONObject().apply {
                        put("chat_id", userId)
                        put("text", fullMessage)
                    }
                    val body = json.toString().toRequestBody("application/json".toMediaType())
                    val request = Request.Builder()
                        .url(url)
                        .post(body)
                        .build()

                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        Log.e("TelegramNotifier", "Ошибка отправки: ${response.code}")
                    } else {
                        Log.d("TelegramNotifier", "Успешно отправлено пользователю $userId")
                    }
                } catch (e: Exception) {
                    Log.e("TelegramNotifier", "Исключение при отправке: ${e.message}", e)
                }
            }
        }
    }

    // Собираю текст сообщения, включая координаты если есть
    private fun buildMessage(base: String, location: Location?): String {
        return if (location != null) {
            "$base\nГеолокация: ${location.latitude}, ${location.longitude}\nhttps://maps.google.com/?q=${location.latitude},${location.longitude}"
        } else {
            base
        }
    }
}
