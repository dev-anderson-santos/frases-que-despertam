package com.dev.anderson.geradorfrases.repository

import com.dev.anderson.geradorfrases.dao.PhraseDao
import com.dev.anderson.geradorfrases.data.Phrase

class PhraseRepository(private val phraseDao: PhraseDao) {

    suspend fun getRandomPhrase(category: String): Phrase? {
        return phraseDao.getRandomByCategory(category)
    }

    suspend fun getRandomPhraseBySubcategory(category: String, subcategory: String): Phrase? {
        return phraseDao.getRandomBySubcategory(category, subcategory)
    }

    suspend fun getAllCategories(): List<String> {
        return phraseDao.getAllCategories()
    }

    suspend fun getSubcategories(category: String): List<String> {
//        return phraseDao.getSubcategoriesByCategory(category)
        println("DEBUG: ===== Repository.getSubcategoriesByCategory() =====")
        println("DEBUG: Buscando subcategorias para categoria: '$category'")

        return try {
            if (category.isEmpty()) {
                println("DEBUG: Categoria vazia, retornando lista vazia")
                emptyList()
            } else {
                val result = phraseDao.getSubcategoriesByCategory(category)
                println("DEBUG: DAO retornou ${result.size} subcategorias")
                result.forEach { subcategory ->
                    println("DEBUG: Subcategoria encontrada: '$subcategory'")
                }
                result
            }
        } catch (e: Exception) {
            println("ERROR: Erro no repository: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun searchPhrases(searchTerm: String): List<Phrase> {
        return phraseDao.searchPhrases(searchTerm)
    }

    suspend fun getFavorites(): List<Phrase> {
        return phraseDao.getFavorites()
    }

    suspend fun getMostViewed(): List<Phrase> {
        return phraseDao.getMostViewed()
    }

    suspend fun incrementViews(phraseId: Long) {
        phraseDao.incrementViews(phraseId)
    }

    suspend fun toggleFavorite(phraseId: Long, isFavorite: Boolean) {
        phraseDao.toggleFavorite(phraseId, isFavorite)
    }

    // Método para obter estatísticas
    suspend fun getCategoryStats(): Map<String, Int> {
        val categories = getAllCategories()
        return categories.associateWith { category ->
            // Você pode adicionar uma query para contar frases por categoria
            0 // Placeholder - implemente a contagem se necessário
        }
    }

    // Método para obter frase do dia (baseado na data)
    suspend fun getPhraseOfTheDay(): Phrase? {
        val categories = getAllCategories()
        if (categories.isEmpty()) return null

        // Usar a data atual como seed para sempre pegar a mesma frase do dia
        val today = System.currentTimeMillis() / (1000 * 60 * 60 * 24) // Dias desde época
        val categoryIndex = (today % categories.size).toInt()
        val selectedCategory = categories[categoryIndex]

        return getRandomPhrase(selectedCategory)
    }

    suspend fun getAllByCategory(category: String): List<Phrase> =
        phraseDao.getAllByCategory(category)

    suspend fun getAllBySubcategory(category: String, subcategory: String): List<Phrase> =
        phraseDao.getAllBySubcategory(category, subcategory)
}