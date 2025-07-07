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
