package com.dev.anderson.geradorfrases

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.dev.anderson.geradorfrases.data.PhraseViewModel
import com.dev.anderson.geradorfrases.data.PhrasesDatabase
import com.dev.anderson.geradorfrases.repository.PhraseRepository
import com.dev.anderson.geradorfrases.ui.theme.FrasesQueDespertamTheme
import com.dev.anderson.geradorfrases.viewmodel.PhraseViewModelFactory
import kotlinx.coroutines.delay

class ExplanationActivity2 : ComponentActivity() {

    private val viewModel: PhraseViewModel by lazy {
        ViewModelProvider(
            this,
            PhraseViewModelFactory(PhraseRepository(PhrasesDatabase.getDatabase(this).phraseDao()), applicationContext)
        )[PhraseViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val phrase   = intent.getStringExtra("phrase")
        val phraseId   = intent.getLongExtra("phraseId", -1L)
        val phraseText = intent.getStringExtra("phraseText") ?: ""
        println("DEBUG Frase: " + phrase)

        setContent {
            FrasesQueDespertamTheme {
                ExplanationScreen(
                    phraseText  = phraseText,
                    viewModel   = viewModel,
                    phraseId    = phraseId,
                    onBack      = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplanationScreen(
    phraseText: String,
    viewModel: PhraseViewModel,
    phraseId: Long,
    onBack: () -> Unit
) {
    // Estados locais para controle
    var isLoadingExplanation by remember { mutableStateOf(false) }
    var explanationError by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableStateOf(0) }

    // observa o LiveData
    val explanation by viewModel.explanation.observeAsState()

    // ✅ CARREGAMENTO COM RETRY E TIMEOUT
    LaunchedEffect(phraseId, retryCount) {
        isLoadingExplanation = true
        explanationError = null

        try {
            if (phraseId > 0) {
                // ✅ Tentar carregar pelo ID primeiro
                viewModel.loadExplanation(phraseId)
            } else if (phraseText.isNotEmpty()) {
                // ✅ Fallback: carregar pelo texto da frase
                viewModel.loadExplanationByText(phraseText)
            } else {
                explanationError = "Informações da frase não disponíveis"
                isLoadingExplanation = false
                return@LaunchedEffect
            }

            // ✅ Aguardar resultado com timeout reduzido
            var timeoutCount = 0
            while (explanation == null && timeoutCount < 100) { // 10 segundos (100 * 100ms)
                delay(100)
                timeoutCount++
            }

            if (explanation == null) {
                explanationError = "Não foi possível carregar a explicação. Verifique sua conexão."
                println("DEBUG: Timeout ao carregar explicação")
            } else {
                println("DEBUG: Explicação carregada com sucesso")
            }

        } catch (e: Exception) {
            explanationError = "Erro ao carregar explicação: ${e.message}"
            println("DEBUG: Erro ao carregar explicação: ${e.message}")
        } finally {
            isLoadingExplanation = false
        }
    }

    // ✅ OBSERVAR MUDANÇAS NA EXPLICAÇÃO
    LaunchedEffect(explanation) {
        if (explanation != null) {
            isLoadingExplanation = false
            explanationError = null
            println("DEBUG: Explicação carregada com sucesso: ${explanation?.take(50)}...")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Explicação", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A)
                )
            )
        },
        containerColor = Color(0xFF101010)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card com a frase
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A1A)
                ),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Frase:",
                        color = Color(0xFF00BFFF),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\"$phraseText\"",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 28.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Card com a explicação
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A1A)
                ),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFFFA500),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Explicação:",
                            color = Color(0xFFFFA500),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when {
                        // ✅ ESTADO DE LOADING
                        isLoadingExplanation || (explanation == null && explanationError == null) -> {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF00BFFF),
                                        modifier = Modifier.size(40.dp),
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Carregando explicação...",
                                        color = Color.Gray,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Aguarde alguns instantes",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                            }
                        }
                        // ✅ ESTADO DE ERRO
                        explanationError != null -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFFFF6B6B),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Erro ao carregar explicação",
                                    color = Color(0xFFFF6B6B),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = explanationError!!,
                                    color = Color.Gray,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        retryCount++
                                        explanationError = null
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF00BFFF)
                                    )
                                ) {
                                    Text("Tentar Novamente")
                                }
                            }
                        }

                        // ✅ EXPLICAÇÃO CARREGADA
                        explanation != null -> {
                            Text(
                                text = explanation!!,
                                color = Color.White,
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                textAlign = TextAlign.Justify
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2A)),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFA500),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Esta explicação foi gerada para ajudar você a entender melhor o significado e aplicação da frase.",
                            color = Color.LightGray,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // ✅ Espaço extra no final para garantir que o scroll funcione bem
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}