package com.dev.anderson.geradorfrases

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.dev.anderson.geradorfrases.data.Phrase
import com.dev.anderson.geradorfrases.data.PhrasesDatabase
import com.dev.anderson.geradorfrases.repository.PhraseRepository
import com.dev.anderson.geradorfrases.data.PhraseViewModel
import com.dev.anderson.geradorfrases.notifications.NotificationManager
import com.dev.anderson.geradorfrases.notifications.NotificationReceiver
import com.dev.anderson.geradorfrases.viewmodel.PhraseViewModelFactory
import com.dev.anderson.geradorfrases.ui.theme.FrasesQueDespertamTheme
import com.dev.anderson.geradorfrases.util.BackgroundType
import com.dev.anderson.geradorfrases.util.ImageSharingUtils
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.time.LocalTime
import java.time.format.DateTimeFormatter


class MainActivity : ComponentActivity() {

    private lateinit var phraseViewModel: PhraseViewModel
    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    // flag de sessão: só pode exibir 1 vez por execução do app
    private var hasShownRewardedAdThisSession = false

//    companion object {
//        private const val REQUEST_CODE_NOTIFICATION_PERMISSION = 1003
//    }

    private lateinit var notificationManager: NotificationManager

    // ✅ MODERNA FORMA DE SOLICITAR PERMISSÕES
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "✓ Permissão de notificação concedida", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "⚠️ Permissão de notificação negada", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("ResourceAsColor")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ VERIFICAR PERMISSÕES LOGO NO INÍCIO
        checkNotificationPermission()

        // ✅ Verificar se veio de uma notificação
        val fromNotification = intent.getBooleanExtra("from_notification", false)
        val notificationPhraseText = intent.getStringExtra(NotificationReceiver.EXTRA_PHRASE_TEXT)
        val notificationPhraseReference = intent.getStringExtra(NotificationReceiver.EXTRA_PHRASE_REFERENCE)
        val notificationPhraseCategory = intent.getStringExtra(NotificationReceiver.EXTRA_PHRASE_CATEGORY)

        // Inicializar gerenciador de notificações
        notificationManager = NotificationManager(this)

        // Configurar ViewModel
        setupViewModel()

        MobileAds.initialize(this)
        loadRewardedAd()

        // habilita edge-to-edge, deixando o status e navigation bar transparentes
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                scrim = android.R.color.transparent
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim     = android.R.color.transparent,
                darkScrim = android.R.color.transparent
            )
        )

        setContent {
            // 1) LIFT: todos os estados que antes ficavam dentro do AppScreen
            var darkMode by rememberSaveable { mutableStateOf(true) }
            var receiveNotifications by rememberSaveable { mutableStateOf(true) }
            var notificationTime by rememberSaveable { mutableStateOf(LocalTime.of(9, 0)) }
            var shareAsImage by rememberSaveable { mutableStateOf(false) }
            var cfgCategory by rememberSaveable { mutableStateOf("") }
            var cfgSubcategory by rememberSaveable { mutableStateOf("") }
            var selectedLanguage by rememberSaveable { mutableStateOf("Português") }
            var selectedTab by rememberSaveable { mutableStateOf(0) }

            // ✅ ESTADO PARA FRASE DA NOTIFICAÇÃO
            var notificationPhrase by remember {
                mutableStateOf(
                    if (fromNotification && notificationPhraseText != null) {
                        NotificationPhrase(
                            text = notificationPhraseText,
                            reference = notificationPhraseReference ?: "",
                            category = notificationPhraseCategory ?: ""
                        )
                    } else null
                )
            }

            // estados vindos do ViewModel
            val categories       by phraseViewModel.categories.observeAsState(emptyList())
            val subcategories    by phraseViewModel.subcategories.observeAsState(emptyList())
            val currentPhrase    by phraseViewModel.currentPhrase.observeAsState()
            val favorites        by phraseViewModel.favorites.observeAsState(emptyList())
            val searchResults    by phraseViewModel.searchResults.observeAsState(emptyList())
            val isLoadingState   by phraseViewModel.isLoading.observeAsState(false)
            val errorMessage     by phraseViewModel.errorMessage.observeAsState()

            // ✅ CARREGAR PREFERÊNCIAS SALVAS
            LaunchedEffect(Unit) {
                loadPreferences { prefs ->
                    darkMode = prefs.getBoolean("dark_mode", true)
                    receiveNotifications = prefs.getBoolean("receive_notifications", true)
                    val hour = prefs.getInt("notification_hour", 9)
                    val minute = prefs.getInt("notification_minute", 0)
                    notificationTime = LocalTime.of(hour, minute)
                    cfgCategory = prefs.getString("notification_category", "") ?: ""
                    cfgSubcategory = prefs.getString("notification_subcategory", "") ?: ""
                    shareAsImage = prefs.getBoolean("share_as_image", false)
                }
            }

            // ✅ QUANDO VEM DE NOTIFICAÇÃO, IR PARA ABA PRINCIPAL E CARREGAR FRASE
            LaunchedEffect(fromNotification, notificationPhraseText) {
                if (fromNotification && notificationPhraseText != null && notificationPhrase != null) {
                    selectedTab = 0 // Ir para aba principal

                    // Definir a frase no ViewModel
                    phraseViewModel.setCurrentPhraseFromNotification(
                        text = notificationPhrase!!.text,
                        reference = notificationPhrase!!.reference,
                        category = notificationPhrase!!.category
                    )

                    println("DEBUG: ✅ Frase da notificação processada: '${notificationPhrase!!.text}'")

                    // Limpar para não processar novamente
                    notificationPhrase = null
                }
            }

            FrasesQueDespertamTheme(darkTheme = darkMode) {
                AppScreen(
                    // ---- CONTROLES GERAIS ----
                    darkMode               = darkMode,
                    onDarkModeChange       = {
                        darkMode = it
                        savePreference("dark_mode", it)
                    },

                    receiveNotifications   = receiveNotifications,
                    onReceiveNotificationsChange = {
                        receiveNotifications = it
                        savePreference("receive_notifications", it)

                        // ✅ CONFIGURAR OU CANCELAR NOTIFICAÇÕES
                        if (it) {
                            notificationManager.scheduleNotification(notificationTime, cfgCategory)
                        } else {
                            notificationManager.cancelNotification()
                        }
                    },

                    notificationTime       = notificationTime,
                    onTimeChange = { newTime ->
                        notificationTime = newTime
                        savePreference("notification_hour", newTime.hour)
                        savePreference("notification_minute", newTime.minute)

                        // ✅ REAGENDAR NOTIFICAÇÃO COM NOVO HORÁRIO
                        if (receiveNotifications) {
                            notificationManager.scheduleNotification(newTime, cfgCategory)
                        }
                    },

                    shareAsImage           = shareAsImage,
                    onShareAsImageChange = {
                        shareAsImage = it
                        savePreference("share_as_image", it)
                        println("DEBUG: shareAsImage salvo: $it")
                    },

                    cfgCategory            = cfgCategory,
                    onCategoryChange = { newCategory ->
                        cfgCategory = newCategory
                        cfgSubcategory = "" // ✅ Limpar subcategoria ao trocar categoria
                        savePreference("notification_category", newCategory)
                        savePreference("notification_subcategory", "") // ✅ Salvar subcategoria vazia

                        // ✅ CARREGAR SUBCATEGORIAS DA NOVA CATEGORIA
                        if (newCategory.isNotEmpty()) {
                            phraseViewModel.loadSubcategories(newCategory)
                        }

                        // ✅ ATUALIZAR CATEGORIA DA NOTIFICAÇÃO
                        if (receiveNotifications) {
                            notificationManager.scheduleNotification(notificationTime, newCategory)
                        }
                    },

                    cfgSubcategory         = cfgSubcategory,
                    onSubcategoryChange = { newSubcategory ->
                        cfgSubcategory = newSubcategory
                        savePreference("notification_subcategory", newSubcategory)
                    },

                    selectedLanguage       = selectedLanguage,
                    onLanguageChange       = { selectedLanguage = it },

                    selectedTab            = selectedTab,
                    onTabSelected          = { selectedTab = it },

                    onRateAppClick         = { rateApp() },
                    onHelpClick            = { /* abre FAQ, etc */ },

                    // ---- DADOS DO VIEWMODEL ----
                    categories       = categories,
                    subcategories    = subcategories,
                    currentPhrase    = currentPhrase,
                    favorites        = favorites,
                    searchResults    = searchResults,
                    isLoading        = isLoadingState,
                    errorMessage     = errorMessage,

                    // ---- CALLBACKS DE FRASES ----
                    onGeneratePhrase    = { phraseViewModel.loadRandomPhrase(cfgCategory) },
                    onPhraseOfDay       = { phraseViewModel.loadPhraseOfTheDay() },
                    onToggleFavorite    = { phraseViewModel.toggleFavorite(it) },
                    onShare             = { sharePhrase(it) },
//                    onCopy              = { copyPhrase(it) },
                    onUnlock            = { unlockExplanation(it) },

                    // ---- BUSCA ----
                    onSearch            = { phraseViewModel.searchPhrases(it) },

                )
            }
        }
    }

    // ✅ Tratar quando o app já está aberto e recebe nova notificação
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val fromNotification = intent.getBooleanExtra("from_notification", false)
        val phraseText = intent.getStringExtra(NotificationReceiver.EXTRA_PHRASE_TEXT)
        val phraseReference = intent.getStringExtra(NotificationReceiver.EXTRA_PHRASE_REFERENCE)
        val phraseCategory = intent.getStringExtra(NotificationReceiver.EXTRA_PHRASE_CATEGORY)

        if (fromNotification && phraseText != null) {
            println("DEBUG: ✅ Nova notificação recebida com app aberto: '$phraseText'")

            // Definir frase diretamente no ViewModel
            phraseViewModel.setCurrentPhraseFromNotification(
                text = phraseText,
                reference = phraseReference ?: "",
                category = phraseCategory ?: ""
            )
        }
    }

    private fun setupViewModel() {
        val database = PhrasesDatabase.getDatabase(this)
        val repository = PhraseRepository(database.phraseDao())
        val factory = PhraseViewModelFactory(repository, applicationContext)
        phraseViewModel = ViewModelProvider(this, factory)[PhraseViewModel::class.java]
    }
    private fun loadPreferences(callback: (android.content.SharedPreferences) -> Unit) {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        callback(prefs)
    }

    private fun savePreference(key: String, value: Any) {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
                is String -> putString(key, value)
            }
            apply()
        }
    }

    // ===== 5. VERIFICAÇÃO DE PERMISSÕES (Android 13+) =====
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permissão já concedida
                }
                else -> {
                    // Solicitar permissão
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AppScreen(
        // ---- CONTROLES GERAIS ----
        darkMode: Boolean,
        onDarkModeChange: (Boolean) -> Unit,
        receiveNotifications: Boolean,
        onReceiveNotificationsChange: (Boolean) -> Unit,
        notificationTime: LocalTime,
        onTimeChange: (LocalTime) -> Unit,
        shareAsImage: Boolean,
        onShareAsImageChange: (Boolean) -> Unit,
        cfgCategory: String,
        onCategoryChange: (String) -> Unit,
        cfgSubcategory: String,
        onSubcategoryChange: (String) -> Unit,
        selectedLanguage: String,
        onLanguageChange: (String) -> Unit,
        selectedTab: Int,
        onTabSelected: (Int) -> Unit,
        onRateAppClick: () -> Unit,
        onHelpClick: () -> Unit,

        // ---- DADOS DO VIEWMODEL ----
        categories: List<String>,
        subcategories: List<String>,
        currentPhrase: Phrase?,
        favorites: List<Phrase>,
        searchResults: List<Phrase>,
        isLoading: Boolean,
        errorMessage: String?,

        // ---- CALLBACKS DE FRASES ----
        onGeneratePhrase: () -> Unit,
        onPhraseOfDay: () -> Unit,
        onToggleFavorite: (Phrase) -> Unit,
        onShare: (Phrase) -> Unit,
//        onCopy: (Phrase) -> Unit,
        onUnlock: (Phrase) -> Unit,

        // ---- BUSCA ----
        onSearch: (String) -> Unit
    ) {
        val context = LocalContext.current
        //var selectedTab by remember { mutableStateOf(0) }

        // Observar dados do ViewModel
//        val categories by phraseViewModel.categories.observeAsState(emptyList())
//        val subcategories by phraseViewModel.subcategories.observeAsState(emptyList())
//        val currentPhrase by phraseViewModel.currentPhrase.observeAsState()
//        val favorites by phraseViewModel.favorites.observeAsState(emptyList())
        val isLoadingState by phraseViewModel.isLoading.observeAsState(false)
//        val errorMessage by phraseViewModel.errorMessage.observeAsState()

        // Estados locais
        var selectedCategory by remember { mutableStateOf("") }
        var selectedSubcategory by remember { mutableStateOf("") }
        var showCategoryDropdown by remember { mutableStateOf(false) }
        var showSubcategoryDropdown by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }

        // Estados locais pra ConfigScreen
//        var cfgCategory       by remember { mutableStateOf("") }
//        var cfgSubcategory    by remember { mutableStateOf("") }
//        var shareAsImage      by remember { mutableStateOf(false) }
//        var notificationTime  by remember { mutableStateOf(LocalTime.of(9, 0)) }
//        var receiveNotifications by remember { mutableStateOf(true) }
//        var darkMode by remember { mutableStateOf(true) }
//        var selectedLanguage by remember { mutableStateOf("Português") }

//        val searchResults by phraseViewModel.searchResults.observeAsState(emptyList())

        // Carregar categorias na inicialização
        LaunchedEffect(Unit) {
            phraseViewModel.loadCategories()
        }

        // Observar mudanças na categoria selecionada
        LaunchedEffect(selectedCategory) {
            if (selectedCategory.isNotEmpty()) {
                phraseViewModel.loadSubcategories(selectedCategory)
            }
        }

        LaunchedEffect(selectedTab) {
            if (selectedTab == 1) {
                phraseViewModel.loadFavorites()
            }
        }

        // Mostrar mensagens de erro
        errorMessage?.let { message ->
            LaunchedEffect(message) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                phraseViewModel.clearError()
            }
        }

        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF101010)) {
                    val navItems = listOf(
                        "Frases" to Icons.Default.Home,
                        "Favoritos" to Icons.Default.Favorite,
                        "Buscar" to Icons.Default.Search,
                        "Configurações" to Icons.Default.Settings
                    )

                    navItems.forEachIndexed { index, (label, icon) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = label, tint = Color.White) },
                            label = { Text(label, color = Color.White) },
                            selected = selectedTab == index,
                            onClick = { onTabSelected(index) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF00BFFF),
                                unselectedIconColor = Color.Gray,
                                selectedTextColor = Color(0xFF00BFFF),
                                unselectedTextColor = Color.Gray,
                                indicatorColor = Color(0xFF1A1A1A)
                            )
                        )
                    }
                }
            },
            containerColor = Color(0xFF101010)
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color(0xFF101010))
                    .systemBarsPadding()
            ) {
                when (selectedTab) {
                    0 -> PhrasesScreen(
                        categories = categories,
                        subcategories = subcategories,
                        currentPhrase = currentPhrase,
                        isLoading = isLoadingState,
                        selectedCategory = selectedCategory,
                        selectedSubcategory = selectedSubcategory,
                        showCategoryDropdown = showCategoryDropdown,
                        showSubcategoryDropdown = showSubcategoryDropdown,
                        onCategorySelected = { category ->
                            selectedCategory = category
                            selectedSubcategory = ""
                            showCategoryDropdown = false
                        },
                        onSubcategorySelected = { subcategory ->
                            selectedSubcategory = subcategory
                            showSubcategoryDropdown = false
                        },
                        onShowCategoryDropdown = { showCategoryDropdown = it },
                        onShowSubcategoryDropdown = { showSubcategoryDropdown = it },
                        onGeneratePhrase = {
                            if (selectedCategory.isNotEmpty()) {
                                if (selectedSubcategory.isNotEmpty()) {
                                    phraseViewModel.loadRandomPhraseBySubcategory(selectedCategory, selectedSubcategory)
                                } else {
                                    phraseViewModel.loadRandomPhrase(selectedCategory)
                                }
                            } else {
                                phraseViewModel.loadPhraseOfTheDay()
                            }
                        },
                        onPhraseOfDay = { phraseViewModel.loadPhraseOfTheDay() },
                        onToggleFavorite = { phrase ->
                            phraseViewModel.toggleFavorite(phrase)
                        },
                        onShare = { phrase -> sharePhrase(phrase) },
                        onUnlock = { phrase -> unlockExplanation(phrase) },
                        phraseViewModel = phraseViewModel
                    )
                    1 -> FavoritesScreen(
                        favorites = favorites,
                        onRemoveFavorite = { phrase ->
                            phraseViewModel.toggleFavorite(phrase)
                        },
                        onShare = { phrase -> sharePhrase(phrase) },
                        onUnlock = { phrase -> unlockExplanation(phrase) }
                    )
                    2 -> SearchScreen(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onSearch              = { phraseViewModel.searchPhrases(it) },
                        searchResults         = searchResults,
                        onShare               = { sharePhrase(it) },
                        onToggleFavorite      = { phraseViewModel.toggleFavorite(it) },
                        onUnlock              = { unlockExplanation(it) }
                    )
                    3 -> ConfigScreen(
                        // Parâmetros originais
                        categories = categories,
                        subcategories = subcategories,
                        selectedCategory = cfgCategory,
                        selectedSubcategory = cfgSubcategory,
                        notificationTime = notificationTime,
                        shareAsImage = shareAsImage,

                        // Novos parâmetros - você precisa criar essas variáveis
                        receiveNotifications = receiveNotifications,
                        darkMode = darkMode,
                        selectedLanguage = selectedLanguage,

                        // Callbacks originais
                        onCategoryChange = onCategoryChange,
                        onSubcategoryChange = onSubcategoryChange,
                        onTimeChange = onTimeChange,
                        onShareAsImageChange = onShareAsImageChange,

                        // Novos callbacks
                        onReceiveNotificationsChange = onReceiveNotificationsChange,
                        onDarkModeChange = onDarkModeChange,
                        onLanguageChange = onLanguageChange,
                        onFavoritesClick = { onTabSelected(1) },
                        onRateAppClick = {
                            // Implementar abertura da Play Store
                            // Exemplo: abrir Play Store para avaliar o app
                        },
                        onHelpClick = {
                            // Implementar tela de ajuda ou abrir URL
                            // Exemplo: abrir FAQ ou tela de suporte
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun PhrasesScreen(
        categories: List<String>,
        subcategories: List<String>,
        currentPhrase: Phrase?,
        isLoading: Boolean,
        selectedCategory: String,
        selectedSubcategory: String,
        showCategoryDropdown: Boolean,
        showSubcategoryDropdown: Boolean,
        onCategorySelected: (String) -> Unit,
        onSubcategorySelected: (String) -> Unit,
        onShowCategoryDropdown: (Boolean) -> Unit,
        onShowSubcategoryDropdown: (Boolean) -> Unit,
        onGeneratePhrase: () -> Unit,
        onPhraseOfDay: () -> Unit,
        onToggleFavorite: (Phrase) -> Unit,
        onShare: (Phrase) -> Unit,
        onUnlock: (Phrase) -> Unit,
        isFromNotification: Boolean = false,
        onNotificationPhraseProcessed: () -> Unit = {},
        phraseViewModel: PhraseViewModel
    ) {

        // ✅ Processar frase da notificação uma única vez
        LaunchedEffect(isFromNotification) {
            if (isFromNotification) {
                // A frase já foi definida no ViewModel via setCurrentPhraseFromNotification
                onNotificationPhraseProcessed() // Marcar como processada
                println("DEBUG: Frase da notificação processada na PhraseScreen")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Dropdowns para categoria e subcategoria
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Dropdown Categoria
                Box(modifier = Modifier.weight(1f)) {
//                    CategoryDropdown(
//                        categories = categories,
//                        selected = selectedCategory,
//                        onSelect = onCategorySelected
//                    )
                    CategoryAndSubcategoryFilters(
                        categories          = categories,
                        subcategories       = subcategories,
                        selectedCategory    = selectedCategory,
                        selectedSubcategory = selectedSubcategory,
                        onCategorySelected  = onCategorySelected,
                        onSubcategorySelected = onSubcategorySelected
                    )
                }

                // Dropdown Subcategoria
//                Box(modifier = Modifier.weight(1f)) {
//                    SubcategoryDropdown(
//                        subcategories        = subcategories,
//                        selectedCategory     = selectedCategory,
//                        selectedSubcategory  = selectedSubcategory,
//                        onSubcategorySelected = { newSub ->
//                            selectedSubcategory = newSub
//                        }
//                    )
//                    OutlinedButton(
//                        onClick = { onShowSubcategoryDropdown(true) },
//                        modifier = Modifier.fillMaxWidth(),
//                        enabled = selectedCategory.isNotEmpty(),
//                        colors = ButtonDefaults.outlinedButtonColors(
//                            contentColor = Color.White
//                        )
//                    ) {
//                        Text(
//                            text = selectedSubcategory.ifEmpty { "Subcategoria" },
//                            modifier = Modifier.weight(1f)
//                        )
//                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Expandir")
//                    }
//
//                    DropdownMenu(
//                        expanded = showSubcategoryDropdown,
//                        onDismissRequest = { onShowSubcategoryDropdown(false) },
//                        modifier = Modifier
//                            .heightIn(max = 250.dp)      // nunca passa de 250dp de altura
//                    ) {
//                        subcategories.forEach { subcategory ->
//                            DropdownMenuItem(
//                                text = { Text(subcategory) },
//                                onClick = { onSubcategorySelected(subcategory) }
//                            )
//                        }
//                    }
//                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botões de ação principal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onGeneratePhrase,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00BFFF)
                    ),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Gerar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gerar Frase")
                }

                Button(
                    onClick = onPhraseOfDay,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.Today, contentDescription = "Frase do Dia")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Frase do Dia")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Card da frase
            currentPhrase?.let { phrase ->
                PhraseCard(
                    phrase = phrase,
                    onToggleFavorite = onToggleFavorite,
                    onShare = onShare,
                    onUnlock = onUnlock
                )
            } ?: run {
                // Estado vazio
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Toque em 'Gerar Frase' para começar!",
                            color = Color.Gray,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun PhraseCard(
        phrase: Phrase,
        onToggleFavorite: (Phrase) -> Unit,
        onShare: (Phrase) -> Unit,
        onUnlock: (Phrase) -> Unit
    ) {
        var showExplanation by remember { mutableStateOf(false) }
        var showBackgroundDialog by remember { mutableStateOf(false) }
        val context = LocalContext.current

        // ✅ Estado local para feedback imediato do favorito
        var isFavoriteLocal by remember(phrase.isFavorite) { mutableStateOf(phrase.isFavorite) }

        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        var isUnlocked by remember(phrase.text) {
            mutableStateOf(prefs.getStringSet("desbloqueadas", emptySet())?.contains(phrase.text) == true)
        }
        val shareAsImage = prefs.getBoolean("share_as_image", false)

        LaunchedEffect(phrase.text) {
            val unlocked = prefs.getStringSet("desbloqueadas", emptySet())?.contains(phrase.text) == true
            if (unlocked != isUnlocked) {
                isUnlocked = unlocked
                println("DEBUG: ✅ Estado de desbloqueio atualizado para '${phrase.text}': $isUnlocked")
            }
        }

        // Dialog de seleção de fundo
        if (showBackgroundDialog) {
            BackgroundSelectorDialog(
                phrase = phrase,
                onDismiss = { showBackgroundDialog = false },
                onBackgroundSelected = { backgroundType ->
                    val imageSharingUtils = ImageSharingUtils(context)
                    imageSharingUtils.sharePhrase(phrase, backgroundType)
                    showBackgroundDialog = false
                }
            )
        }

        // ✅ Atualizar estado quando a activity retoma (volta do anúncio)
        DisposableEffect(phrase.text) {
            val activity = context as? ComponentActivity
            val listener = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    // Verificar novamente se foi desbloqueado
                    val updatedUnlocked = prefs.getStringSet("desbloqueadas", emptySet())?.contains(phrase.text) == true
                    if (updatedUnlocked != isUnlocked) {
                        isUnlocked = updatedUnlocked
                        println("DEBUG: Estado de desbloqueio atualizado para: $isUnlocked")
                    }
                }
            }

            activity?.lifecycle?.addObserver(listener)

            onDispose {
                activity?.lifecycle?.removeObserver(listener)
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Texto da frase
                Text(
                    text = "\"${phrase.text}\"",
                    color = Color.White,
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Referência/Autor
                Text(
                    text = "- ${phrase.reference}",
                    color = Color.LightGray,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Categoria
                Text(
                    text = "${phrase.category} • ${phrase.subcategory}",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Explicação (se desbloqueada)
                if (isUnlocked && showExplanation) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color.Gray.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = phrase.explanation,
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ─── BOTÃO DE DESBLOQUEIO ───
                if (!isUnlocked) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUnlock(phrase) }
                            .background(
                                Color(0xFFFFA500).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Desbloquear",
                            tint = Color(0xFFFFA500),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "🎬 Assista um anúncio",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Desbloqueie a explicação desta frase",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    // ✅ Mostrar botão de "Ver explicação completa" quando desbloqueado
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onUnlock(phrase) } // Vai abrir a tela de explicação
                                .background(
                                    Color(0xFF4CAF50).copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Ver explicação completa",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column (modifier = Modifier.weight(1f)) {
                                Text(
                                    "📖 Ver explicação completa",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Abrir tela dedicada com explicação detalhada",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Botões de ação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Favoritar
                    ActionButton(
                        icon = if (isFavoriteLocal) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favoritar",
                        tint = if (isFavoriteLocal) Color(0xFFFF5252) else Color.White,
                        onClick = {
                            // ✅ Atualizar estado local imediatamente para feedback visual
                            isFavoriteLocal = !isFavoriteLocal
                            // Chamar callback para atualizar no ViewModel
                            onToggleFavorite(phrase)
                        }
                    )

                    // Compartilhar
                    ActionButton(
                        icon = Icons.Default.Share,
                        contentDescription = "Compartilhar",
                        onClick = {
                            println("DEBBUG: Compartilhando a frase como imagem: " + shareAsImage)
                            if (shareAsImage) {
                                showBackgroundDialog = true
                            } else {
                                onShare(phrase)
                            }
                        }
                    )

                    // Copiar
                    ActionButton(
                        icon = Icons.Default.ContentCopy,
                        contentDescription = "Copiar",
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("frase", "${phrase.text}\n\n- ${phrase.reference}")
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Frase copiada!", Toast.LENGTH_SHORT).show()
                        }
                    )

                    // Explicação
                    ActionButton(
                        icon = if (isUnlocked) Icons.Default.Info else Icons.Default.Lock,
                        contentDescription = if (isUnlocked) "Ver Explicação" else "Desbloquear",
                        tint = if (isUnlocked) Color(0xFF4CAF50) else Color(0xFFFFA500),
                        onClick = { onUnlock(phrase) }
                    )
                }
            }
        }
    }

    @Composable
    private fun ActionButton(
        icon: ImageVector,
        contentDescription: String,
        tint: Color = Color.White,
        onClick: () -> Unit
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .background(
                    Color(0xFF2A2A2A),
                    shape = CircleShape
                )
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    @Composable
    private fun FavoritesScreen(
        favorites: List<Phrase>,
        onRemoveFavorite: (Phrase) -> Unit,
        onShare: (Phrase) -> Unit,
        onUnlock: (Phrase) -> Unit
    ) {
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Nenhuma frase nos favoritos",
                        color = Color.Gray,
                        fontSize = 18.sp
                    )
                    Text(
                        "Adicione frases que você gosta tocando no ❤️",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(favorites) { phrase ->
                    PhraseCard(
                        phrase = phrase,
                        onToggleFavorite = onRemoveFavorite,
                        onShare = onShare,
                        onUnlock = onUnlock
                    )
                }
            }
        }
    }

    @Composable
    private fun SearchScreen(
        searchQuery: String,
        onSearchQueryChange: (String) -> Unit,
        onSearch: (String) -> Unit,
        searchResults: List<Phrase>,
        onShare: (Phrase) -> Unit,
        onToggleFavorite: (Phrase) -> Unit,
        onUnlock: (Phrase) -> Unit
    ) {
        // ✅ Estado para controlar se está fazendo busca
        var isSearching by remember { mutableStateOf(false) }

        // ✅ Limpar campo ao sair da tela
        DisposableEffect(Unit) {
            onDispose {
                // Limpa o campo quando sai da tela
                onSearchQueryChange("")
            }
        }

        // ✅ Busca automática quando digitar 3+ caracteres
        LaunchedEffect(searchQuery) {
            if (searchQuery.length >= 3) {
                isSearching = true
                // Pequeno delay para evitar muitas buscas durante digitação rápida
                kotlinx.coroutines.delay(500)
                if (searchQuery.length >= 3) { // Verificar novamente após delay
                    onSearch(searchQuery)
                }
                isSearching = false
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Barra de busca
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text("Buscar frases...") },
                placeholder = { Text("Digite pelo menos 3 caracteres", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color(0xFF00BFFF),
                    unfocusedLabelColor = Color.Gray,
                    focusedBorderColor = Color(0xFF00BFFF),
                    unfocusedBorderColor = Color.Gray
                ),
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF00BFFF),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = {
                                if (searchQuery.length >= 3) {
                                    onSearch(searchQuery)
                                }
                            },
                            enabled = searchQuery.length >= 3
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = if (searchQuery.length >= 3) Color.White else Color.Gray
                            )
                        }
                    }
                },
                singleLine = true
            )

            // ✅ Mensagem orientativa
            if (searchQuery.isNotEmpty() && searchQuery.length < 3) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFFFA500),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Digite pelo menos 3 caracteres para buscar (${searchQuery.length}/3)",
                        color = Color(0xFFFFA500),
                        fontSize = 12.sp,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ✅ Resultados da busca com diferentes estados
            when {
                // Estado inicial - nenhuma busca feita ainda
                searchQuery.isBlank() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Busque por frases inspiradoras",
                                color = Color.Gray,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Digite palavras-chave para encontrar\nfrases que tocam o coração",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                // Aguardando digitação de mais caracteres
                searchQuery.length < 3 -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = Color(0xFFFFA500),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Continue digitando...",
                                color = Color.White,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Mais ${3 - searchQuery.length} caractere${if (3 - searchQuery.length == 1) "" else "s"} para buscar",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Carregando resultados
                isSearching -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF00BFFF),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Buscando frases...",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                // Nenhum resultado encontrado
                searchResults.isEmpty() && searchQuery.length >= 3 && !isSearching -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Nenhuma frase encontrada",
                                color = Color.White,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tente usar outras palavras-chave",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )

                            // ✅ Sugestões de busca
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Sugestões:",
                                color = Color(0xFF00BFFF),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val suggestions = listOf("amor", "vida", "sucesso", "felicidade", "motivação")
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(suggestions) { suggestion ->
                                    AssistChip(
                                        onClick = { onSearchQueryChange(suggestion) },
                                        label = {
                                            Text(
                                                text = suggestion,
                                                color = Color.White,
                                                fontSize = 12.sp
                                            )
                                        },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = Color(0xFF333333),
                                            labelColor = Color.White
                                        ),
                                        border = AssistChipDefaults.assistChipBorder(true)
                                    )
                                }
                            }
                        }
                    }
                }

                // Resultados encontrados
                else -> {
                    Column {
                        // ✅ Header com contagem de resultados
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${searchResults.size} frase${if (searchResults.size == 1) "" else "s"} encontrada${if (searchResults.size == 1) "" else "s"}",
                                color = Color(0xFF00BFFF),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )

                            // Botão para limpar busca
                            if (searchQuery.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        onSearchQueryChange("")
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = Color.Gray
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Limpar",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Limpar", fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Lista de resultados
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchResults) { phrase ->
                                PhraseCard(
                                    phrase = phrase,
                                    onToggleFavorite = onToggleFavorite,
                                    onShare = onShare,
                                    onUnlock = onUnlock
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ConfigScreen(
        categories: List<String>,
        subcategories: List<String>,
        selectedCategory: String,
        selectedSubcategory: String,
        notificationTime: LocalTime,
        shareAsImage: Boolean,
        receiveNotifications: Boolean,
        darkMode: Boolean,
        selectedLanguage: String,
        onCategoryChange: (String) -> Unit,
        onSubcategoryChange: (String) -> Unit,
        onTimeChange: (LocalTime) -> Unit,
        onShareAsImageChange: (Boolean) -> Unit,
        onReceiveNotificationsChange: (Boolean) -> Unit,
        onDarkModeChange: (Boolean) -> Unit,
        onLanguageChange: (String) -> Unit,
        onFavoritesClick: () -> Unit,
        onRateAppClick: () -> Unit,
        onHelpClick: () -> Unit
    ) {
        val uiState by phraseViewModel.uiState.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Seção Notificações
            ConfigSection(
                title = "Notificações",
                titleColor = Color(0xFF00BFFF)
            ) {
                ConfigToggleItem(
                    title = "Receber frases diárias",
                    subtitle = "Envie notificações diárias com novas frases",
                    isChecked = uiState.receiveNotifications,
                    onCheckedChange = phraseViewModel::updateReceiveNotifications
                )

                if (uiState.receiveNotifications) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TimeField(
                        label = "Horário",
                        time = notificationTime,
                        onTimePicked = onTimeChange
                    )

                    // opcional: reforçar na UI o horário escolhido
                    Text(
                        text = "Você receberá frases às ${notificationTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Dropdown Categoria
                    DropdownField(
                        label = "Categoria",
                        items = categories,
                        selected = selectedCategory,
                        onSelect = onCategoryChange
                    )

                    if (selectedCategory.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        // Dropdown Subcategoria
                        DropdownField(
                            label = "Subcategoria",
                            items = subcategories,
                            selected = selectedSubcategory,
                            onSelect = onSubcategoryChange
                        )
                    }
                }
            }

            ConfigToggleItem(
                title = "Compartilhar como imagem",
                subtitle = "Criar imagens bonitas ao compartilhar frases",
                isChecked = shareAsImage,
                onCheckedChange = { newValue ->
                    println("DEBUG: Mudando shareAsImage para: $newValue")
                    onShareAsImageChange(newValue)
                }
            )

            if (shareAsImage) {
                // Spacer(modifier = Modifier.height(8.dp))

                // Informação adicional quando ativado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF00BFFF),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Você poderá escolher o fundo da imagem ao compartilhar",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Seção Mais
            ConfigSection(
                title = "Mais",
                titleColor = Color(0xFF00BFFF)
            ) {
                ConfigClickableItem(
                    title = "Favoritos",
                    onClick = onFavoritesClick
                )

                Spacer(modifier = Modifier.height(8.dp))

                ConfigClickableItem(
                    title = "Classifique o app",
                    onClick = onRateAppClick
                )

                Spacer(modifier = Modifier.height(8.dp))

                ConfigClickableItem(
                    title = "Ajuda",
                    onClick = onHelpClick
                )
            }
        }
    }

    @Composable
    fun ConfigSection(
        title: String,
        titleColor: Color = Color.White,
        content: @Composable ColumnScope.() -> Unit
    ) {
        Column {
            Text(
                text = title,
                color = titleColor,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E1E)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }

    @Composable
    fun ConfigToggleItem(
        title: String,
        subtitle: String,
        isChecked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        modifier: Modifier = Modifier
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF00BFFF),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color(0xFF333333)
                )
            )
        }
    }

    @Composable
    fun ConfigSelectItem(
        title: String,
        selectedValue: String,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = selectedValue,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
    }

    @Composable
    fun ConfigClickableItem(
        title: String,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Gray
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DropdownField(
        label: String,
        items: List<String>,
        selected: String,
        onSelect: (String) -> Unit,
        modifier: Modifier = Modifier,
        placeholder: String = "Selecione $label"
    ) {
        var expanded by remember { mutableStateOf(false) }

        Column(modifier = modifier) {
            // Label
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Dropdown Box
            Box (
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selected.ifEmpty { placeholder },
                    onValueChange = { }, // Read-only
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            println("DEBUG: DropdownField '$label' clicado, expandindo...")
                            expanded = !expanded
                        },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00BFFF),
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = if (selected.isEmpty()) Color.Gray else Color.White,
                        cursorColor = Color(0xFF00BFFF),
                        focusedLabelColor = Color(0xFF00BFFF),
                        unfocusedLabelColor = Color.Gray
                    ),
                    trailingIcon = {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.clickable { expanded = !expanded }
                        )
                    },
                    singleLine = true,
                    enabled = false
                )

                // Dropdown Menu
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .background(Color(0xFF1E1E1E))
                ) {
                    // Opção padrão "Todas" ou "Nenhuma"
                    if (label == "Categoria") {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Todas as categorias",
                                    color = Color.White
                                )
                            },
                            onClick = {
                                onSelect("")
                                expanded = false
                            }
                        )
                        HorizontalDivider(color = Color.Gray)
                    }

                    // Lista de itens
                    items.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = item,
                                    color = if (item == selected) Color(0xFF00BFFF) else Color.White
                                )
                            },
                            onClick = {
                                println("DEBUG: Item '$item' selecionado em '$label'")
                                onSelect(item)
                                expanded = false
                            },
                            modifier = Modifier.background(
                                if (item == selected) Color(0xFF00BFFF).copy(alpha = 0.1f) else Color.Transparent
                            )
                        )
                    }

                    // Se não há itens disponíveis
                    if (items.isEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Nenhum item disponível",
                                    color = Color.Gray
                                )
                            },
                            onClick = { expanded = false },
                            enabled = false
                        )
                    }
                }
            }
        }
    }

    // ===== MODIFICAÇÃO NO TIMEFIELD =====
    @Composable
    fun TimeField(
        label: String,
        time: LocalTime,
        onTimePicked: (LocalTime) -> Unit,
        modifier: Modifier = Modifier
    ) {
        var showTimePicker by remember { mutableStateOf(false) }
        val context = LocalContext.current

        Column(modifier = modifier) {
            // Label
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTimePicker = true } // ✅ Clique em toda a área
            ) {
                // Time Input Field
                OutlinedTextField(
                    value = time.format(DateTimeFormatter.ofPattern("HH:mm")),
                    onValueChange = { }, // Read-only
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker = true },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00BFFF),
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF00BFFF),
                        focusedLabelColor = Color(0xFF00BFFF),
                        unfocusedLabelColor = Color.Gray
                    ),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Selecionar horário",
                            tint = Color.Gray,
                            modifier = Modifier.clickable { showTimePicker = true }
                        )
                    },
                    singleLine = true,
                    enabled = false
                )
            }
        }

        // Time Picker Dialog
        if (showTimePicker) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Material3 Time Picker para API 23+
                TimePickerDialog(
                    onDismiss = { showTimePicker = false },
                    onTimeSelected = { selectedTime ->
                        onTimePicked(selectedTime)
                        showTimePicker = false
                    },
                    initialTime = time
                )
            } else {
                // Fallback para versões mais antigas
                val timePickerDialog = android.app.TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        onTimePicked(LocalTime.of(hourOfDay, minute))
                        showTimePicker = false
                    },
                    time.hour,
                    time.minute,
                    true // 24 hour format
                )

                LaunchedEffect(showTimePicker) {
                    timePickerDialog.show()
                    timePickerDialog.setOnDismissListener {
                        showTimePicker = false
                    }
                }
            }
        }
    }

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    fun TimePickerDialog(
        onDismiss: () -> Unit,
        onTimeSelected: (LocalTime) -> Unit,
        initialTime: LocalTime
    ) {
        val timePickerState = rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeSelected(LocalTime.of(timePickerState.hour, timePickerState.minute))
                    }
                ) {
                    Text("OK", color = Color(0xFF00BFFF))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar", color = Color.Gray)
                }
            },
            text = {
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(
                        timeSelectorSelectedContainerColor = Color(0xFF00BFFF),
                        timeSelectorSelectedContentColor = Color.White,
                        timeSelectorUnselectedContainerColor = Color(0xFF333333),
                        timeSelectorUnselectedContentColor = Color.Gray
                    )
                )
            },
            containerColor = Color(0xFF1E1E1E),
            textContentColor = Color.White
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CategoryDropdown(
        categories: List<String>,
        selected: String,
        onSelect: (String) -> Unit
    ) {
        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            // Esse menuAnchor() faz o drop apontar pra esse TextField
            OutlinedTextField(
                value = selected.ifEmpty { "Categorias" },
                onValueChange = { /* read-only */ },
                readOnly = true,
                label     = { Text("Categorias") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier  = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 250.dp) // limite pra ficar rolável
            ) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category) },
                        onClick = {
                            onSelect(category)
                            expanded = false
                        }
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SubcategoryDropdown(
        subcategories: List<String>,
        selectedCategory: String,
        selectedSubcategory: String,
        onSubcategorySelected: (String) -> Unit
    ) {
        // estado do menu
        var expanded by remember { mutableStateOf(false) }
        // só habilita se houver categoria
        val enabled = selectedCategory.isNotEmpty()

        // Box que “ancora” o menu
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // O campo propriamente dito
            OutlinedTextField(
                value = selectedSubcategory.ifEmpty { "Subcategoria" },
                onValueChange = { /* não muda direto, só pelo menu */ },
                readOnly = true,
                label = { Text("Subcategoria") },
                enabled = enabled,          // habilita/desabilita o campo
                trailingIcon = {
                    // só recebe o parâmetro `expanded`
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier.fillMaxWidth()
            )

            // o menu suspenso
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                subcategories.forEach { sub ->
                    DropdownMenuItem(
                        text = { Text(sub) },
                        onClick = {
                            onSubcategorySelected(sub)
                            expanded = false
                        }
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CategoryAndSubcategoryFilters(
        categories: List<String>,
        subcategories: List<String>,
        selectedCategory: String,
        selectedSubcategory: String,
        onCategorySelected: (String) -> Unit,
        onSubcategorySelected: (String) -> Unit
    ) {
        var catExpanded by remember { mutableStateOf(false) }
        var subExpanded by remember { mutableStateOf(false) }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── CATEGORIAS ──
            ExposedDropdownMenuBox(
                expanded = catExpanded,
                onExpandedChange = { catExpanded = !catExpanded }
            ) {
                OutlinedTextField(
                    value = selectedCategory.ifEmpty { "Categorias" },
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Categorias") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(catExpanded) },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(
                        // texto digitado
                        focusedTextColor    = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor  = MaterialTheme.colorScheme.onSurfaceVariant,
                        // cursor e indicadores
                        cursorColor             = MaterialTheme.colorScheme.primary,
                        focusedIndicatorColor   = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(),
                        // label
                        focusedLabelColor       = MaterialTheme.colorScheme.onSurface,
                        unfocusedLabelColor     = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryEditable, true)
                )
                ExposedDropdownMenu(
                    expanded = catExpanded,
                    onDismissRequest = { catExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                        // limita a altura do menu e torna-o scrollável
                        .heightIn(max = 280.dp)
                        // aplica a mesma cor de fundo do seu Surface
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    DropdownMenuItem(
                        text = { Text("Todas as categorias") },
                        onClick = {
                            onCategorySelected("")
                            catExpanded = false
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                onCategorySelected(cat)
                                catExpanded = false
                            }
                        )
                    }
                }
            }

            // ── SUBCATEGORIAS ──
            ExposedDropdownMenuBox(
                expanded = subExpanded,
                onExpandedChange = {
                    if (selectedCategory.isNotEmpty()) subExpanded = !subExpanded
                }
            ) {
                OutlinedTextField(
                    value = selectedSubcategory.ifEmpty { "Subcategoria" },
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Subcategoria") },
                    enabled = selectedCategory.isNotEmpty(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(subExpanded) },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(
                        // texto digitado
                        focusedTextColor    = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor  = MaterialTheme.colorScheme.onSurfaceVariant,
                        // cursor e indicadores
                        cursorColor             = MaterialTheme.colorScheme.primary,
                        focusedIndicatorColor   = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(),
                        // label
                        focusedLabelColor       = MaterialTheme.colorScheme.onSurface,
                        unfocusedLabelColor     = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryEditable, true)
                )
                ExposedDropdownMenu(
                    expanded = subExpanded,
                    onDismissRequest = { subExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                        // limita a altura do menu e torna-o scrollável
                        .heightIn(max = 280.dp)
                        // aplica a mesma cor de fundo do seu Surface
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    subcategories.forEach { sub ->
                        DropdownMenuItem(
                            text = { Text(sub) },
                            onClick = {
                                onSubcategorySelected(sub)
                                subExpanded = false
                            }
                        )
                    }
                    if (subcategories.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Nenhum disponível", color = Color.Gray) },
                            onClick = { subExpanded = false },
                            enabled = false
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun BackgroundSelectorDialog(
        phrase: Phrase,
        onDismiss: () -> Unit,
        onBackgroundSelected: (BackgroundType) -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    "Escolha o fundo da imagem",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(BackgroundType.values()) { backgroundType ->
                        BackgroundOption(
                            backgroundType = backgroundType,
                            onClick = {
                                onBackgroundSelected(backgroundType)
                                onDismiss()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF00BFFF)
                    )
                ) {
                    Text("Cancelar")
                }
            },
            containerColor = Color(0xFF1E1E1E),
            textContentColor = Color.White
        )
    }

    @Composable
    fun BackgroundOption(
        backgroundType: BackgroundType,
        onClick: () -> Unit
    ) {
        val (name, description, colors) = when (backgroundType) {
            BackgroundType.FLOWERS_FIELD -> Triple(
                "🌸 Campo de Flores",
                "Fundo natural com flores delicadas",
                listOf(Color(0xFFE8F5E8), Color(0xFF4A7C59))
            )
            BackgroundType.SUNSET_SKY -> Triple(
                "🌅 Céu do Pôr do Sol",
                "Gradiente dourado e laranja",
                listOf(Color(0xFFFFE4B5), Color(0xFF4B0082))
            )
            BackgroundType.NATURE_GREEN -> Triple(
                "🌿 Natureza Verde",
                "Verde suave e relaxante",
                listOf(Color(0xFF90EE90), Color(0xFF006400))
            )
            BackgroundType.OCEAN_WAVES -> Triple(
                "🌊 Ondas do Oceano",
                "Azul marinho profundo",
                listOf(Color(0xFF87CEEB), Color(0xFF191970))
            )
            BackgroundType.MOUNTAIN_VIEW -> Triple(
                "🏔️ Vista das Montanhas",
                "Tons terrosos e acolhedores",
                listOf(Color(0xFFFFE4E1), Color(0xFF8B4513))
            )
            BackgroundType.GRADIENT_PURPLE -> Triple(
                "💜 Gradiente Roxo",
                "Elegante e sofisticado",
                listOf(Color(0xFFE6E6FA), Color(0xFF4B0082))
            )
            BackgroundType.GRADIENT_BLUE -> Triple(
                "💙 Gradiente Azul",
                "Azul suave e tranquilo",
                listOf(Color(0xFFE0F6FF), Color(0xFF4682B4))
            )
            BackgroundType.SOLID_ELEGANT -> Triple(
                "✨ Elegante Sólido",
                "Fundo sólido sofisticado",
                listOf(Color(0xFF2C3E50))
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2A2A2A)
            ),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Preview do fundo
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(
                            brush = if (colors.size > 1) {
                                Brush.verticalGradient(colors)
                            } else {
                                Brush.verticalGradient(listOf(colors[0], colors[0]))
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = name,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    Text(
                        text = description,
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    // Função auxiliar para mostrar o dialog
    @Composable
    fun ShareWithBackgroundSelection(
        phrase: Phrase,
        context: Context,
        showDialog: Boolean,
        onDismiss: () -> Unit
    ) {
        if (showDialog) {
            BackgroundSelectorDialog(
                phrase = phrase,
                onDismiss = onDismiss,
                onBackgroundSelected = { backgroundType ->
                    val imageSharingUtils = ImageSharingUtils(context)
                    imageSharingUtils.sharePhrase(phrase, backgroundType)
                }
            )
        }
    }

    private fun rateApp() {
        val uri = Uri.parse("market://details?id=$packageName")
        startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun sharePhrase(phrase: Phrase) {
        // ✅ Compartilhamento como texto (método atual)
        // A lógica de imagem já está no PhraseCard
        val shareText = "${phrase.text}\n\n- ${phrase.reference}"
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, "Compartilhar frase"))
    }

//    @RequiresApi(Build.VERSION_CODES.Q)
//    private fun showBackgroundSelectorDialog(phrase: Phrase) {
//        val backgroundTypes = BackgroundType.values()
//        val options = arrayOf(
//            "🌸 Campo de Flores",
//            "🌅 Céu do Pôr do Sol",
//            "🌿 Natureza Verde",
//            "🌊 Ondas do Oceano",
//            "🏔️ Vista das Montanhas",
//            "💜 Gradiente Roxo",
//            "💙 Gradiente Azul",
//            "✨ Elegante Sólido"
//        )
//
//        val builder = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_DayNight)
//        builder.setTitle("✨ Escolha o estilo da sua imagem")
//        builder.setItems(options) { _, which ->
//            val selectedBackground = backgroundTypes[which]
//            val imageSharingUtils = ImageSharingUtils(this)
//            imageSharingUtils.sharePhrase(phrase, selectedBackground)
//        }
//        builder.setNegativeButton("Cancelar", null)
//        builder.show()
//    }

    private fun unlockExplanation(phrase: Phrase) {
        val context = this
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isUnlocked = prefs.getStringSet("desbloqueadas", emptySet())?.contains(phrase.text) == true

        if (isUnlocked) {
            // Já desbloqueado, abrir explicação diretamente
            openExplanationActivity(phrase)
            return
        }

        if (rewardedAd != null) {
            println("DEBUG: Mostrando anúncio recompensado...")

            // ✅ Configurar callback ANTES de mostrar o anúncio
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    println("DEBUG: Anúncio foi fechado")
                    // Recarregar novo anúncio para próxima vez
                    rewardedAd = null
                    loadRewardedAd()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    println("DEBUG: ❌ Falha ao mostrar anúncio: ${error.message}")
                    // Em caso de erro, mostrar a explicação mesmo assim (boa experiência do usuário)
                    markPhraseUnlocked(phrase.text, prefs)
                    openExplanationActivity(phrase)

                    // Tentar recarregar anúncio
                    rewardedAd = null
                    loadRewardedAd()
                }

                override fun onAdShowedFullScreenContent() {
                    println("DEBUG: Anúncio exibido com sucesso")
                }
            }

            // Mostrar anúncio
            rewardedAd?.show(this) { rewardItem ->
                println("DEBUG: ✅ Usuário ganhou recompensa: ${rewardItem.amount} ${rewardItem.type}")

                // ✅ Salvar como desbloqueada
                markPhraseUnlocked(phrase.text, prefs)

                // ✅ Mostrar feedback
                Toast.makeText(context, "✅ Explicação desbloqueada!", Toast.LENGTH_SHORT).show()

                // ✅ Aguardar um momento antes de abrir a activity (evita conflitos)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    openExplanationActivity(phrase)
                }, 500)
            }
        } else {
            // ✅ Anúncio não disponível - melhor experiência do usuário
            val message = if (isLoading) {
                "⏳ Anúncio carregando... Tente novamente em alguns segundos."
            } else {
                "❌ Anúncio não disponível no momento. Desbloqueando gratuitamente!"
            }

            Toast.makeText(context, message, Toast.LENGTH_LONG).show()

            if (!isLoading) {
                // ✅ Se não tem anúncio, desbloquear gratuitamente (boa experiência)
                markPhraseUnlocked(phrase.text, prefs)
                openExplanationActivity(phrase)

                // Tentar carregar anúncio para próximas vezes
                loadRewardedAd()
            }
        }
    }

    // ✅ FUNÇÃO AUXILIAR PARA ABRIR A EXPLICAÇÃO
    private fun openExplanationActivity(phrase: Phrase) {
        println("DEBUG: Abrindo ExplanationActivity2 com phraseId=${phrase.id}")

        val intent = Intent(this, ExplanationActivity2::class.java).apply {
            putExtra("phraseId", phrase.id)
            putExtra("phraseText", phrase.text)
            putExtra("phrase", phrase.text) // Manter compatibilidade

            // ✅ Flags para evitar problemas de navegação
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            println("DEBUG: ❌ Erro ao abrir ExplanationActivity2: ${e.message}")
            Toast.makeText(this, "Erro ao abrir explicação", Toast.LENGTH_SHORT).show()
        }
    }

    private fun markPhraseUnlocked(phraseText: String, prefs: SharedPreferences) {
        try {
            val currentUnlocked = prefs.getStringSet("desbloqueadas", mutableSetOf())?.toMutableSet()
                ?: mutableSetOf()

            if (currentUnlocked.add(phraseText)) {
                prefs.edit().putStringSet("desbloqueadas", currentUnlocked).apply()
                println("DEBUG: ✅ Frase marcada como desbloqueada: '$phraseText'")
            } else {
                println("DEBUG: Frase já estava desbloqueada: '$phraseText'")
            }
        } catch (e: Exception) {
            println("DEBUG: ❌ Erro ao marcar frase como desbloqueada: ${e.message}")
        }
    }

    private fun loadRewardedAd() {
        if (isLoading || rewardedAd != null) return
        isLoading = true

        println("DEBUG: Iniciando carregamento do anúncio...")

        RewardedAd.load(
            this,
            "ca-app-pub-3940256099942544/5224354917", // ID de teste
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoading = false

                    // ✅ Configurar callback para eventos do anúncio
                    rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            println("DEBUG: Anúncio foi fechado pelo usuário")
                            // Recarregar novo anúncio para próxima vez
                            rewardedAd = null
                            loadRewardedAd()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            println("DEBUG: ❌ Falha ao mostrar anúncio: ${error.message}")
                            rewardedAd = null
                            loadRewardedAd()
                        }

                        override fun onAdShowedFullScreenContent() {
                            println("DEBUG: Anúncio foi exibido")
                        }
                    }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isLoading = false

                    // ✅ Tentar recarregar após alguns segundos
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        loadRewardedAd()
                    }, 5000)
                }
            }
        )
    }
}

// 5. Data class para frase da notificação
data class NotificationPhrase(
    val text: String,
    val reference: String,
    val category: String
)