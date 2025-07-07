package com.dev.anderson.geradorfrases

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.dev.anderson.geradorfrases.data.Phrase
import com.dev.anderson.geradorfrases.data.PhrasesDatabase
import com.dev.anderson.geradorfrases.repository.PhraseRepository
import com.dev.anderson.geradorfrases.data.PhraseViewModel
import com.dev.anderson.geradorfrases.viewmodel.PhraseViewModelFactory
import com.dev.anderson.geradorfrases.ui.theme.FrasesQueDespertamTheme
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class MainActivity : ComponentActivity() {

    private var interstitialAd: InterstitialAd? = null
    private lateinit var phraseViewModel: PhraseViewModel
    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    // flag de sessão: só pode exibir 1 vez por execução do app
    private var hasShownRewardedAdThisSession = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar ViewModel
        setupViewModel()

        MobileAds.initialize(this)
        loadRewardedAd()

        setContent {
            FrasesQueDespertamTheme {
                AppScreen()
            }
        }
    }

    // sempre que a Activity entra em foreground, mostra o intersticial
    override fun onStart() {
        super.onStart()
        loadAndShowInterstitial()
    }

    private fun loadAndShowInterstitial() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            "ca-app-pub-3940256099942544/1033173712", // seu ad unit ID de interstitial
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            // se quiser, pode recarregar para a próxima exibição
                            interstitialAd = null
                        }
                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            interstitialAd = null
                        }
                    }
                    ad.show(this@MainActivity)
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    private fun setupViewModel() {
        val database = PhrasesDatabase.getDatabase(this)
        val repository = PhraseRepository(database.phraseDao())
        val factory = PhraseViewModelFactory(repository)
        phraseViewModel = ViewModelProvider(this, factory)[PhraseViewModel::class.java]
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AppScreen() {
        val context = LocalContext.current
        var selectedTab by remember { mutableStateOf(0) }

        // Observar dados do ViewModel
        val categories by phraseViewModel.categories.observeAsState(emptyList())
        val subcategories by phraseViewModel.subcategories.observeAsState(emptyList())
        val currentPhrase by phraseViewModel.currentPhrase.observeAsState()
        val favorites by phraseViewModel.favorites.observeAsState(emptyList())
        val isLoadingState by phraseViewModel.isLoading.observeAsState(false)
        val errorMessage by phraseViewModel.errorMessage.observeAsState()

        // Estados locais
        var selectedCategory by remember { mutableStateOf("") }
        var selectedSubcategory by remember { mutableStateOf("") }
        var searchQuery by remember { mutableStateOf("") }
        var showCategoryDropdown by remember { mutableStateOf(false) }
        var showSubcategoryDropdown by remember { mutableStateOf(false) }

        val searchResults by phraseViewModel.searchResults.observeAsState(emptyList())

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
                            onClick = { selectedTab = index },
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
                        onUnlock = { phrase -> unlockExplanation(phrase) }
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
                    3 -> ConfigScreen()
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
        onUnlock: (Phrase) -> Unit
    ) {
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
                    OutlinedButton(
                        onClick = { onShowCategoryDropdown(true) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = selectedCategory.ifEmpty { "Categorias" },
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Expandir")
                    }

                    DropdownMenu(
                        expanded = showCategoryDropdown,
                        onDismissRequest = { onShowCategoryDropdown(false) }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = { onCategorySelected(category) }
                            )
                        }
                    }
                }

                // Dropdown Subcategoria
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { onShowSubcategoryDropdown(true) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedCategory.isNotEmpty(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = selectedSubcategory.ifEmpty { "Subcategoria" },
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Expandir")
                    }

                    DropdownMenu(
                        expanded = showSubcategoryDropdown,
                        onDismissRequest = { onShowSubcategoryDropdown(false) }
                    ) {
                        subcategories.forEach { subcategory ->
                            DropdownMenuItem(
                                text = { Text(subcategory) },
                                onClick = { onSubcategorySelected(subcategory) }
                            )
                        }
                    }
                }
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
        val context = LocalContext.current
        val prefs = context.getSharedPreferences("frases_prefs", Context.MODE_PRIVATE)
        val isUnlocked = prefs.getStringSet("desbloqueadas", emptySet())?.contains(phrase.text) == true

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

                // Botões de ação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Favoritar
                    ActionButton(
                        icon = if (phrase.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favoritar",
                        tint = if (phrase.isFavorite) Color(0xFFFF5252) else Color.White,
                        onClick = { onToggleFavorite(phrase) }
                    )

                    // Compartilhar
                    ActionButton(
                        icon = Icons.Default.Share,
                        contentDescription = "Compartilhar",
                        onClick = { onShare(phrase) }
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
                        onClick = {
                            if (isUnlocked) {
                                showExplanation = !showExplanation
                            } else {
                                onUnlock(phrase)
                            }
                        }
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
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color(0xFF00BFFF),
                    unfocusedLabelColor = Color.Gray
                ),
                trailingIcon = {
                    IconButton(onClick = { onSearch(searchQuery) }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar", tint = Color.White)
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Aqui você pode adicionar os resultados da busca
            // observando uma LiveData de resultados do ViewModel
            when {
                // carregando se você quiser:
                searchResults.isEmpty() && searchQuery.isBlank() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Digite algo para buscar frases", color = Color.Gray)
                    }
                }
                searchResults.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nenhum resultado encontrado", color = Color.Gray)
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(searchResults) { phrase ->
                            PhraseCard(
                                phrase            = phrase,
                                onToggleFavorite  = onToggleFavorite,
                                onShare           = onShare,
                                onUnlock          = onUnlock
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ConfigScreen() {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Configurações",
                    color = Color.LightGray,
                    fontSize = 24.sp
                )
                Text(
                    "Em breve mais opções!",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        }
    }

    private fun sharePhrase(phrase: Phrase) {
        val shareText = "${phrase.text}\n\n- ${phrase.reference}"
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, "Compartilhar frase"))
    }

    private fun unlockExplanation(phrase: Phrase) {
        val context = this
        val prefs = getSharedPreferences("frases_prefs", Context.MODE_PRIVATE)
        val isUnlocked = prefs.getStringSet("desbloqueadas", emptySet())?.contains(phrase.text) == true

        if (isUnlocked) {
            // Já desbloqueado, abrir explicação diretamente
            val intent = Intent(this, ExplanationActivity2::class.java)
            intent.putExtra("phrase", phrase.text)
            intent.putExtra("explanation", phrase.explanation)
            startActivity(intent)
        }
        if (!hasShownRewardedAdThisSession && rewardedAd != null) {
            // Mostrar anúncio e desbloquear
            rewardedAd?.show(this) { _ ->
                // ganhei recompensa -> Salvar como desbloqueada
                markPhraseUnlocked(phrase.text, prefs)
                val currentUnlocked = prefs.getStringSet("desbloqueadas", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                currentUnlocked.add(phrase.text)
                prefs.edit().putStringSet("desbloqueadas", currentUnlocked).apply()

                // Abrir explicação
                val intent = Intent(this, ExplanationActivity2::class.java)
                intent.putExtra("phrase", phrase.text)
                intent.putExtra("explanation", phrase.explanation)
                startActivity(intent)

                // marco que já mostrei o ad nesta sessão
                hasShownRewardedAdThisSession = true

                // Recarregar anúncio
                rewardedAd = null
                loadRewardedAd()
            } ?: run {
                Toast.makeText(context, "Anúncio não está pronto. Tente novamente.", Toast.LENGTH_SHORT).show()
                markPhraseUnlocked(phrase.text, prefs)
                loadRewardedAd()
            }
        }
    }

    private fun markPhraseUnlocked(text: String, prefs: SharedPreferences) {
        val unlocked = prefs.getStringSet("desbloqueadas", mutableSetOf())!!.toMutableSet()
        unlocked.add(text)
        prefs.edit().putStringSet("desbloqueadas", unlocked).apply()
    }

    private fun loadRewardedAd() {
        if (isLoading || rewardedAd != null) return
        isLoading = true

        RewardedAd.load(
            this,
            "ca-app-pub-3940256099942544/5224354917", // ID de teste
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoading = false
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isLoading = false
                }
            }
        )
    }
}