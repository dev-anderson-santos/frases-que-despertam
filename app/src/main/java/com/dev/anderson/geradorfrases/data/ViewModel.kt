package com.dev.anderson.geradorfrases.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dev.anderson.geradorfrases.repository.PhraseRepository
import kotlinx.coroutines.launch

class PhraseViewModel(private val repository: PhraseRepository) : ViewModel() {

    private val _currentPhrase = MutableLiveData<Phrase?>()
    val currentPhrase: LiveData<Phrase?> = _currentPhrase

    private val _categories = MutableLiveData<List<String>>()
    val categories: LiveData<List<String>> = _categories

    private val _subcategories = MutableLiveData<List<String>>()
    val subcategories: LiveData<List<String>> = _subcategories

    private val _favorites = MutableLiveData<List<Phrase>>()
    val favorites: LiveData<List<Phrase>> = _favorites

    private val _searchResults = MutableLiveData<List<Phrase>>()
    val searchResults: LiveData<List<Phrase>> = _searchResults

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // pools em memória
    private val poolByCategory = mutableMapOf<String, MutableList<Phrase>>()
    private val poolBySubcat   = mutableMapOf<Pair<String,String>, MutableList<Phrase>>()

    init {
        loadCategories()
        loadPhraseOfTheDay()
        loadFavorites()
    }

    fun loadCategories() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val categoriesList = repository.getAllCategories()
                _categories.value = categoriesList
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao carregar categorias: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadSubcategories(category: String) {
        viewModelScope.launch {
            try {
                val subcategoriesList = repository.getSubcategories(category)
                _subcategories.value = subcategoriesList
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao carregar subcategorias: ${e.message}"
            }
        }
    }

    fun loadRandomPhrase(category: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                // obtém ou cria um pool embaralhado
                val pool = poolByCategory.getOrPut(category) {
                    repository.getAllByCategory(category)
                        .shuffled()
                        .toMutableList()
                }
                if (pool.isEmpty()) {
                    // quando esgotar, repopula
                    pool.addAll(repository.getAllByCategory(category).shuffled())
                }
                val next = pool.removeAt(0)
                _currentPhrase.value = next
                repository.incrementViews(next.id)
            } catch(e: Exception) {
                _errorMessage.value = "Erro ao carregar frase: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadRandomPhraseBySubcategory(category: String, subcategory: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val key = category to subcategory
                val pool = poolBySubcat.getOrPut(key) {
                    repository.getAllBySubcategory(category, subcategory)
                        .shuffled()
                        .toMutableList()
                }
                if (pool.isEmpty()) {
                    pool.addAll(repository.getAllBySubcategory(category, subcategory).shuffled())
                }
                val next = pool.removeAt(0)
                _currentPhrase.value = next
                repository.incrementViews(next.id)
            } catch(e: Exception) {
                _errorMessage.value = "Erro ao carregar frase: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadPhraseOfTheDay() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val phrase = repository.getPhraseOfTheDay()
                _currentPhrase.value = phrase
                phrase?.let {
                    repository.incrementViews(it.id)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao carregar frase do dia: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchPhrases(searchTerm: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                if (searchTerm.isBlank()) {
                    _searchResults.value = emptyList()
                    return@launch
                }
                val results = repository.searchPhrases(searchTerm)
                _searchResults.value = results
            } catch (e: Exception) {
                _errorMessage.value = "Erro na busca: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadFavorites() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val favoritesList = repository.getFavorites()
                _favorites.value = favoritesList
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao carregar favoritos: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleFavorite(phrase: Phrase) {
        viewModelScope.launch {
            try {
                val newFavoriteStatus = !phrase.isFavorite
                repository.toggleFavorite(phrase.id, newFavoriteStatus)

                // Atualizar o estado local
                _currentPhrase.value?.let { current ->
                    if (current.id == phrase.id) {
                        _currentPhrase.value = current.copy(isFavorite = newFavoriteStatus)
                    }
                }

                // Recarregar favoritos se estiver na tela de favoritos
                if (_favorites.value != null) {
                    loadFavorites()
                }
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao atualizar favorito: ${e.message}"
            }
        }
    }

    fun sharePhrase(phrase: Phrase): String {
        return buildString {
            append("\"${phrase.text}\"\n\n")
            append("${phrase.reference}\n\n")
            append("${phrase.explanation}\n\n")
            append("Compartilhado via App Frases Inspiradoras")
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
