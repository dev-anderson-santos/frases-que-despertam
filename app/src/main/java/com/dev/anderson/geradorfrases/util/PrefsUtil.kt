package com.dev.anderson.geradorfrases.util

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import com.dev.anderson.geradorfrases.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

fun salvarFavorito(context: Context, frase: String) {
    val prefs: SharedPreferences =
        context.getSharedPreferences("frases_prefs", Context.MODE_PRIVATE)
    val set = prefs.getStringSet("favoritas", mutableSetOf<String>())!!.toMutableSet()
    if (set.contains(frase)) {
        Toast.makeText(context, "Já está entre os favoritos", Toast.LENGTH_SHORT).show()
    } else {
        set.add(frase)
        prefs.edit().putStringSet("favoritas", set).apply()
        Toast.makeText(context, "Frase salva nos favoritos!", Toast.LENGTH_SHORT).show()
    }
}

/** Busca a explicação “ao vivo” da OpenAI usando gpt-3.5-turbo */
suspend fun fetchAIExplanation(frase: String): String {
    val client = OkHttpClient()
    val apiKey  = BuildConfig.OPENAI_API_KEY
    val payload = JSONObject().apply {
        put("model", "gpt-3.5-turbo")
        put("messages", JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", "Explique esta frase de forma motivacional: \"$frase\"")
            })
        })
    }

    val body = payload
        .toString()
        .toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url("https://api.openai.com/v1/chat/completions")
        .addHeader("Authorization", "Bearer $apiKey")
        .post(body)
        .build()

    client.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) throw Exception("OpenAI ${resp.code}: ${resp.message}")
        val text = JSONObject(resp.body!!.string())
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
        return text.trim()
    }
}