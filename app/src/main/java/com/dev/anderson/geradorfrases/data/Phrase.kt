package com.dev.anderson.geradorfrases.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "phrases")
data class Phrase(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val text: String,
    val reference: String, // Ex: "João 3:16" ou "Provérbios 3:5-6"
    val category: String, // Ex: "Versículos Bíblicos", "Motivação", "Sabedoria"
    val subcategory: String, // Ex: "Amor de Deus", "Confiança", "Perseverança"
    val explanation: String,
    val tags: String, // Tags separadas por vírgula para busca
    val isFavorite: Boolean = false,
    val timesViewed: Int = 0,
    val dateAdded: String = System.currentTimeMillis().toString()
)