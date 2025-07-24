package com.dev.anderson.geradorfrases.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dev.anderson.geradorfrases.notifications.NotificationManager
import com.dev.anderson.geradorfrases.notifications.PermissionManager
import com.dev.anderson.geradorfrases.repository.PhraseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime

class PhraseViewModel(private val repository: PhraseRepository,
                      private val context: Context) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

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
        loadSettings()
    }

    fun loadCategories() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val categoriesList = repository.getAllCategories()
                _categories.value = categoriesList

                // Se já há uma categoria selecionada, carregar suas subcategorias
                val currentCategory = _uiState.value.selectedCategory
                if (currentCategory.isNotEmpty()) {
                    loadSubcategories(currentCategory)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao carregar categorias: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ✅ Método para carregar subcategorias
    fun loadSubcategories(category: String) {
        println("DEBUG: PhraseViewModel.loadSubcategories() chamado para: '$category'")

        if (category.isEmpty()) {
            println("DEBUG: Categoria vazia, limpando subcategorias")
            _subcategories.value = emptyList()
            return
        }

        viewModelScope.launch {
            try {
                val result = repository.getSubcategories(category)
                _subcategories.value = result

                println("DEBUG: ${result.size} subcategorias carregadas:")
                result.forEachIndexed { index, subcategory ->
                    println("DEBUG: [$index] '$subcategory'")
                }

                _subcategories.value = result

            } catch (e: Exception) {
                println("ERROR: Erro ao carregar subcategorias: ${e.message}")
                _subcategories.value = emptyList()
            }
        }
    }

    // ✅ Método para definir frase vinda da notificação
    fun setCurrentPhraseFromNotification(text: String, reference: String, category: String) {
        val notificationPhrase = Phrase(
            id = 0,
            text = text,
            reference = reference,
            category = category,
            subcategory = "",
            explanation = "",
            tags = "",
            isFavorite = false,
            timesViewed = 0,
            dateAdded = System.currentTimeMillis().toString()
        )

        _currentPhrase.value = notificationPhrase
        println("DEBUG: Frase da notificação definida no ViewModel: '$text'")
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

    fun updateCategory(category: String) {
        println("DEBUG: ConfigViewModel.updateCategory() chamado com: '$category'")

        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            selectedSubcategory = "" // Limpar subcategoria ao mudar categoria
        )

        // Carregar subcategorias da nova categoria
        if (category.isNotEmpty()) {
            loadSubcategories(category)
        } else {
            _subcategories.value = emptyList()
        }

        saveSettings()
    }

    fun updateSubcategory(subcategory: String) {
        println("DEBUG: ConfigViewModel.updateSubcategory() chamado com: '$subcategory'")

        _uiState.value = _uiState.value.copy(selectedSubcategory = subcategory)
        saveSettings()
    }

    fun updateNotificationTime(time: LocalTime) {
        println("DEBUG: ConfigViewModel.updateNotificationTime() chamado com: $time")

        _uiState.value = _uiState.value.copy(notificationTime = time)
        saveSettings()

        // Reagendar notificação com novo horário
        if (_uiState.value.receiveNotifications) {
            scheduleNotification()
        }
    }

    // ✅ MÉTODO CORRIGIDO COM VERIFICAÇÃO DE PERMISSÃO
    fun updateReceiveNotifications(receive: Boolean) {
        println("DEBUG: ConfigViewModel.updateReceiveNotifications() chamado com: $receive")

        // Verificar permissão antes de ativar
        if (receive && !PermissionManager.hasNotificationPermission(context)) {
            println("DEBUG: Permissão de notificação não concedida")
            // Não ativar se não tem permissão - a UI deve solicitar permissão
            return
        }

        _uiState.value = _uiState.value.copy(receiveNotifications = receive)
        saveSettings()

        if (receive) {
            scheduleNotification()

            // ✅ Mostrar notificação de teste imediata
//            val notificationManager = NotificationManager(context)
//            notificationManager.showTestNotification()

            println("DEBUG: Notificações ativadas e teste enviado")
        } else {
            cancelNotification()
            println("DEBUG: Notificações desativadas")
        }
    }

    private fun scheduleNotification() {
        try {
            val notificationManager = NotificationManager(context)
            val category = _uiState.value.selectedCategory

            notificationManager.scheduleNotification(
                time = _uiState.value.notificationTime,
                category = category
            )

            println("DEBUG: Notificação agendada para ${_uiState.value.notificationTime} com categoria '$category'")
        } catch (e: Exception) {
            println("ERROR: Erro ao agendar notificação: ${e.message}")
        }
    }

    private fun cancelNotification() {
        try {
            val notificationManager = NotificationManager(context)
            notificationManager.cancelNotification()
            println("DEBUG: Notificação cancelada")
        } catch (e: Exception) {
            println("ERROR: Erro ao cancelar notificação: ${e.message}")
        }
    }

    private fun saveSettings() {
        try {
            val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString("selected_category", _uiState.value.selectedCategory)
                putString("selected_subcategory", _uiState.value.selectedSubcategory)
                putString("notification_time", _uiState.value.notificationTime.toString())
                putBoolean("receive_notifications", _uiState.value.receiveNotifications)
                putBoolean("share_as_image", _uiState.value.shareAsImage)
                putBoolean("dark_mode", _uiState.value.darkMode)
                putString("selected_language", _uiState.value.selectedLanguage)
                apply()
            }
            println("DEBUG: Configurações salvas")
        } catch (e: Exception) {
            println("ERROR: Erro ao salvar configurações: ${e.message}")
        }
    }

    private fun loadSettings() {
        try {
            val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

            val savedCategory = sharedPref.getString("selected_category", "") ?: ""
            val savedSubcategory = sharedPref.getString("selected_subcategory", "") ?: ""
            val savedTime = sharedPref.getString("notification_time", "09:00")?.let {
                try {
                    LocalTime.parse(it)
                } catch (e: Exception) {
                    LocalTime.of(9, 0)
                }
            } ?: LocalTime.of(9, 0)
            val receiveNotifications = sharedPref.getBoolean("receive_notifications", false)
            val shareAsImage = sharedPref.getBoolean("share_as_image", false)
            val darkMode = sharedPref.getBoolean("dark_mode", true)
            val selectedLanguage = sharedPref.getString("selected_language", "pt") ?: "pt"

            _uiState.value = _uiState.value.copy(
                selectedCategory = savedCategory,
                selectedSubcategory = savedSubcategory,
                notificationTime = savedTime,
                receiveNotifications = receiveNotifications,
                shareAsImage = shareAsImage,
                darkMode = darkMode,
                selectedLanguage = selectedLanguage
            )

            // Se há categoria salva, carregar suas subcategorias
            if (savedCategory.isNotEmpty()) {
                loadSubcategories(savedCategory)
            }

            println("DEBUG: Configurações carregadas - Categoria: '$savedCategory', Notificações: $receiveNotifications")
        } catch (e: Exception) {
            println("ERROR: Erro ao carregar configurações: ${e.message}")
        }
    }

    // Métodos adicionais que podem ser úteis
    fun updateShareAsImage(shareAsImage: Boolean) {
        _uiState.value = _uiState.value.copy(shareAsImage = shareAsImage)
        saveSettings()
    }

    fun updateDarkMode(darkMode: Boolean) {
        _uiState.value = _uiState.value.copy(darkMode = darkMode)
        saveSettings()
    }

    fun updateLanguage(language: String) {
        _uiState.value = _uiState.value.copy(selectedLanguage = language)
        saveSettings()
    }

    // Método para teste manual de notificação
    fun testNotification() {
        val notificationManager = NotificationManager(context)
        notificationManager.showTestNotification()
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

data class ConfigUiState(
    val selectedCategory: String = "",
    val selectedSubcategory: String = "",
    val notificationTime: LocalTime = LocalTime.of(9, 0),
    val receiveNotifications: Boolean = false,
    val shareAsImage: Boolean = false,
    val darkMode: Boolean = true,
    val selectedLanguage: String = "pt"
)