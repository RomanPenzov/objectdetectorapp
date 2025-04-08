package com.penzov.objectdetectorapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    notifier: TelegramNotifier,
    confidence: Float,
    onConfidenceChanged: (Float) -> Unit,
    onBack: () -> Unit
) {
    // Состояние для нового Telegram ID
    var newRecipient by remember { mutableStateOf(TextFieldValue("")) }
    val recipients = remember { mutableStateListOf<String>().apply { addAll(notifier.getRecipients()) } }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Text("Настройки приложения", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        // 🧠 Карточка для настройки чувствительности модели (порог доверия)
        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Чувствительность детекции", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = confidence,
                    onValueChange = { onConfidenceChanged(it) },
                    valueRange = 0.1f..1f,
                    steps = 8
                )
                Text("Текущий порог: ${(confidence * 100).toInt()}%")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 💬 Карточка Telegram получателей
        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Получатели уведомлений в Telegram", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = newRecipient,
                        onValueChange = { newRecipient = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Введите Telegram ID") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val id = newRecipient.text.trim()
                        if (id.isNotEmpty() && id !in recipients) {
                            recipients.add(id)
                            notifier.addRecipient(id)
                            newRecipient = TextFieldValue("")
                        }
                    }) {
                        Text("Добавить")
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxHeight().padding(top = 8.dp)) {
                    items(recipients) { id ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("ID: $id", modifier = Modifier.weight(1f))
                            Button(onClick = {
                                recipients.remove(id)
                                notifier.removeRecipient(id)
                            }) {
                                Text("Удалить")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("← Назад")
        }
    }
}
