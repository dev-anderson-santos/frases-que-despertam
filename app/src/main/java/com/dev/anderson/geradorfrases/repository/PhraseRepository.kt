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

    suspend fun getExplanationById(id: Long): String? {
        return phraseDao.getExplanationById(id)
    }

    suspend fun isPhraseInFavorites(phraseText: String): Boolean {
        return try {
            phraseDao.isPhraseInFavorites(phraseText)
        } catch (e: Exception) {
            println("DEBUG: Erro ao verificar favorito: ${e.message}")
            false
        }
    }

    // ✅ FUNÇÃO para encontrar frase por texto exato
    suspend fun findPhraseByText(text: String): Phrase? {
        return try {
            phraseDao.findPhraseByText(text)
        } catch (e: Exception) {
            println("DEBUG: Erro ao buscar frase por texto: ${e.message}")
            null
        }
    }

    // ✅ FUNÇÃO para encontrar frase similar (opcional - para melhor UX)
    suspend fun findSimilarPhrase(text: String): Phrase? {
        return try {
            // Buscar por palavras-chave principais
            val keywords = text.split(" ").filter { it.length > 3 }.take(3)
            phraseDao.findSimilarPhrase("%${keywords.joinToString("%")}%")
        } catch (e: Exception) {
            println("DEBUG: Erro ao buscar frase similar: ${e.message}")
            null
        }
    }

    // ✅ FUNÇÃO para inserir ou obter frase existente
    suspend fun insertOrGetPhrase(phrase: Phrase): Phrase? {
        return try {
            // Primeiro verificar se já existe
            val existing = findPhraseByText(phrase.text)
            if (existing != null) {
                return existing
            }

            // Se não existe, inserir nova
            val newId = phraseDao.insertPhrase(phrase)
            phrase.copy(id = newId)
        } catch (e: Exception) {
            println("DEBUG: Erro ao inserir/obter frase: ${e.message}")
            null
        }
    }

    // ✅ FUNÇÃO para obter frase por ID
    suspend fun getPhraseById(id: Long): Phrase? {
        return try {
            phraseDao.getPhraseById(id)
        } catch (e: Exception) {
            println("DEBUG: Erro ao buscar frase por ID: ${e.message}")
            null
        }
    }

    // ✅ FUNÇÃO para atualizar frase (favoritar/desfavoritar)
    suspend fun updatePhrase(phrase: Phrase) {
        try {
            phraseDao.updatePhrase(phrase)
            println("DEBUG: Frase atualizada com sucesso: ID=${phrase.id}, isFavorite=${phrase.isFavorite}")
        } catch (e: Exception) {
            println("DEBUG: Erro ao atualizar frase: ${e.message}")
            throw e
        }
    }
}