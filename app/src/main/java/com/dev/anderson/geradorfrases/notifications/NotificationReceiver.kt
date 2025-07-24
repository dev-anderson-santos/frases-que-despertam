package com.dev.anderson.geradorfrases.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dev.anderson.geradorfrases.MainActivity
import com.dev.anderson.geradorfrases.data.PhrasesDatabase
import com.dev.anderson.geradorfrases.repository.PhraseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "daily_phrases_channel"
        const val EXTRA_PHRASE_TEXT = "phrase_text"
        const val EXTRA_PHRASE_REFERENCE = "phrase_reference"
        const val EXTRA_PHRASE_CATEGORY = "phrase_category"
    }

    override fun onReceive(context: Context, intent: Intent) {
        println("DEBUG: NotificationReceiver.onReceive() chamado")

        val category = intent.getStringExtra("category") ?: ""
//        val isTest = intent.getBooleanExtra("test_notification", false)

//        println("DEBUG: Categoria recebida: '$category'")
//        println("DEBUG: É teste: $isTest")

//        if (isTest) {
//            // Notificação de teste imediata
//            showNotification(context, "🧪 Teste de Notificação", "Frases que Despertam")
//            return
//        }

        // Buscar frase do banco de dados em background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = PhrasesDatabase.getDatabase(context)
                val repository = PhraseRepository(database.phraseDao())

                // ✅ USAR ASYNC/AWAIT PARA SUSPEND FUNCTIONS
                val phrase = async {
                    when {
                        category.isEmpty() -> repository.getRandomPhrase("Motivação")
                        else -> repository.getRandomPhrase(category)
                    }
                }.await()

                phrase?.let {
                    showNotification(
                        context = context,
                        phraseText = it.text,
                        reference = it.reference,
                        category = it.category
                    )
                } ?: run {
                    showNotification(
                        context = context,
                        phraseText = "Tenha um ótimo dia!",
                        reference = "Frases que Despertam",
                        category = "Motivação"
                    )
                }
            } catch (e: Exception) {
                // Em caso de erro, mostrar notificação genérica
                showNotification(
                    context = context,
                    phraseText = "Tenha um ótimo dia!",
                    reference = "Frases que Despertam",
                    category = "Motivação"
                )
            }
        }
    }

    private fun showNotification(
        context: Context,
        phraseText: String,
        reference: String,
        category: String
    ) {
        // Intent para abrir o app quando tocar na notificação
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // ✅ Passar dados da frase para o app
            putExtra(EXTRA_PHRASE_TEXT, phraseText)
            putExtra(EXTRA_PHRASE_REFERENCE, reference)
            putExtra(EXTRA_PHRASE_CATEGORY, category)
            putExtra("from_notification", true) // Flag para identificar origem
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(), // ID único baseado no tempo,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("\uD83D\uDCAB Frase do Dia")
            .setContentText(phraseText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("\"$phraseText\"\n\n- $reference"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(1001, notification)
            println("DEBUG: Notificação da frase exibida com sucesso")
        } catch (e: SecurityException) {
            println("ERROR: Sem permissão para notificação: ${e.message}")
        }
    }
}