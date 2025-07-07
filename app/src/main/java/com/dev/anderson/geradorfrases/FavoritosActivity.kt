package com.dev.anderson.geradorfrases

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dev.anderson.geradorfrases.ui.theme.FrasesQueDespertamTheme
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.launch

class FavoritosActivity : ComponentActivity() {

    private var rewardedAd: RewardedAd? = null
    private var isLoadingAd = false
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializa AdMob e SharedPreferences
        MobileAds.initialize(this)
        prefs = getSharedPreferences("frases_prefs", Context.MODE_PRIVATE)
        loadRewardedAd()

        setContent {
            FrasesQueDespertamTheme {
                FavoritosScreen()
            }
        }
    }

    @Composable
    private fun FavoritosScreen() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        // Estados reativos
        var favoritas by remember {
            mutableStateOf(prefs.getStringSet("favoritas", emptySet())?.toList() ?: emptyList())
        }
        var desbloqueadas by remember {
            mutableStateOf(prefs.getStringSet("desbloqueadas", emptySet()) ?: emptySet())
        }

        // Estados para dialogs
        var fraseSelecionada by remember { mutableStateOf<String?>(null) }
        var fraseParaDesbloquear by remember { mutableStateOf<String?>(null) }

        Scaffold (
            topBar = {
                // TopAppBar customizado para evitar API experimental
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF101010)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { finish() }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Voltar",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = "Favoritas",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        ) { paddingValues ->

            if (favoritas.isEmpty()) {
                // Estado vazio
                EmptyState(modifier = Modifier.padding(paddingValues))
            } else {
                // Lista de favoritas
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(favoritas) { frase ->
                        FraseCard(
                            frase = frase,
                            isDesbloqueada = desbloqueadas.contains(frase),
                            onCardClick = { fraseSelecionada = frase },
                            onUnlockClick = { fraseParaDesbloquear = frase },
                            onInfoClick = {
                                startActivity(
                                    Intent(this@FavoritosActivity, ExplanationActivity2::class.java)
                                        .putExtra("phrase", frase)
                                )
                            },
                            onDeleteClick = {
                                favoritas = removeFraseFromFavorites(frase)
                            }
                        )
                    }
                }
            }
        }

        // Dialog de ações da frase
        fraseSelecionada?.let { frase ->
            FraseActionsDialog(
                frase = frase,
                isDesbloqueada = desbloqueadas.contains(frase),
                onDismiss = { fraseSelecionada = null },
                onShareText = {
                    shareText(frase)
                    fraseSelecionada = null
                },
                onShareWithExplanation = {
                    if (desbloqueadas.contains(frase)) {
                        shareTextWithExplanation(frase)
                    } else {
                        showRewardedAdAndUnlock(frase) { unlockedFrase ->
                            desbloqueadas = desbloqueadas.plus(unlockedFrase)
                            shareTextWithExplanation(unlockedFrase)
                        }
                    }
                    fraseSelecionada = null
                }
            )
        }

        // Dialog de confirmação de desbloqueio
        fraseParaDesbloquear?.let { frase ->
            UnlockConfirmationDialog(
                onConfirm = {
                    showRewardedAdAndUnlock(frase) { unlockedFrase ->
                        desbloqueadas = desbloqueadas.plus(unlockedFrase)
                    }
                    fraseParaDesbloquear = null
                },
                onDismiss = { fraseParaDesbloquear = null }
            )
        }
    }

    @Composable
    private fun EmptyState(modifier: Modifier = Modifier) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.Gray
                )
                Text(
                    "Nenhuma frase favorita",
                    color = Color.Gray,
                    fontSize = 18.sp
                )
                Text(
                    "Adicione frases aos favoritos para vê-las aqui",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
    }

    @Composable
    private fun FraseCard(
        frase: String,
        isDesbloqueada: Boolean,
        onCardClick: () -> Unit,
        onUnlockClick: () -> Unit,
        onInfoClick: () -> Unit,
        onDeleteClick: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCardClick() },
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A1A)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = frase,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Botão de desbloqueio/informação
                if (isDesbloqueada) {
                    IconButton(onClick = onInfoClick) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Ver explicação",
                            tint = Color.Green
                        )
                    }
                } else {
                    IconButton(onClick = onUnlockClick) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Desbloquear",
                            tint = Color.Yellow
                        )
                    }
                }

                // Botão de deletar
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remover",
                        tint = Color.Red
                    )
                }
            }
        }
    }

    @Composable
    private fun FraseActionsDialog(
        frase: String,
        isDesbloqueada: Boolean,
        onDismiss: () -> Unit,
        onShareText: () -> Unit,
        onShareWithExplanation: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("O que deseja fazer?", color = Color.White) },
            text = { Text(frase, color = Color.White) },
            confirmButton = {
                TextButton(onClick = onShareText) {
                    Text("Compartilhar texto")
                }
            },
            dismissButton = {
                TextButton(onClick = onShareWithExplanation) {
                    Text(
                        if (isDesbloqueada) "Compartilhar + Explicação"
                        else "Desbloquear e Compartilhar"
                    )
                }
            }
        )
    }

    @Composable
    private fun UnlockConfirmationDialog(
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Desbloquear explicação?", color = Color.White) },
            text = { Text("Assistir um vídeo curto para liberar a explicação dessa frase?", color = Color.White) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text("Sim")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Não")
                }
            }
        )
    }

    // Funções auxiliares
    private fun removeFraseFromFavorites(frase: String): List<String> {
        val currentFavorites = prefs.getStringSet("favoritas", emptySet())!!.toMutableSet()
        currentFavorites.remove(frase)
        prefs.edit().putStringSet("favoritas", currentFavorites).apply()
        return currentFavorites.toList()
    }

    private fun shareText(text: String) {
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Compartilhar via"))
    }

    private fun shareTextWithExplanation(frase: String) {
        val explicacao = gerarExplicacao(frase, this)
        shareText("$frase\n\nSignificado: $explicacao")
    }

    private fun showRewardedAdAndUnlock(frase: String, onSuccess: (String) -> Unit) {
        rewardedAd?.show(this) {
            // Salva no SharedPreferences
            val currentUnlocked = prefs.getStringSet("desbloqueadas", emptySet())!!.toMutableSet()
            currentUnlocked.add(frase)
            prefs.edit().putStringSet("desbloqueadas", currentUnlocked).apply()

            Toast.makeText(this, "Explicação desbloqueada!", Toast.LENGTH_SHORT).show()
            onSuccess(frase)
            loadRewardedAd()
        } ?: run {
            Toast.makeText(this, "Vídeo não carregado. Tente novamente.", Toast.LENGTH_SHORT).show()
            loadRewardedAd()
        }
    }

    private fun loadRewardedAd() {
        if (isLoadingAd || rewardedAd != null) return

        isLoadingAd = true
        RewardedAd.load(
            this,
            "ca-app-pub-3940256099942544/5224354917", // ID de teste
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoadingAd = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isLoadingAd = false
                }
            }
        )
    }
}