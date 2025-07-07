package com.dev.anderson.geradorfrases

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dev.anderson.geradorfrases.ui.theme.FrasesQueDespertamTheme
import kotlinx.coroutines.launch

class ExplanationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Recebe a frase pela Intent
        val phrase = intent.getStringExtra("phrase") ?: ""

        setContent {
            FrasesQueDespertamTheme {
                ExplanationScreen(
                    phrase = phrase,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplanationScreen(
    phrase: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Estados para a explicação
    var explanation by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    // Carrega a explicação quando a tela é criada
    LaunchedEffect(phrase) {
        scope.launch {
            try {
                isLoading = true
                hasError = false
                // Você pode trocar por uma chamada real à API do OpenAI
//                explanation = fetchExplanationOffline(phrase)
                explanation = ""
                isLoading = false
            } catch (e: Exception) {
                hasError = true
                isLoading = false
                explanation = "Erro ao carregar explicação. Tente novamente."
            }
        }
    }

    val addToFavorites = {
        val prefs = context.getSharedPreferences("frases_prefs", Context.MODE_PRIVATE)
        val favorites = prefs.getStringSet("favoritas", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        if (favorites.add(phrase)) {
            prefs.edit().putStringSet("favoritas", favorites).apply()
            Toast.makeText(context, "Frase salva nos favoritos!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Esta frase já está nos favoritos!", Toast.LENGTH_SHORT).show()
        }
    }

    val sharePhrase = {
        val shareText = "$phrase\n\n$explanation"
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Compartilhar frase e explicação"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Text(
                        "Explicação da Frase",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF101010)
                ),
                actions = {
                    // Botão favoritar
                    IconButton(onClick = addToFavorites) {
                        Icon(
                            Icons.Default.FavoriteBorder,
                            contentDescription = "Adicionar aos favoritos",
                            tint = Color.White
                        )
                    }
                    // Botão compartilhar
                    if (!isLoading && !hasError) {
                        IconButton(onClick = sharePhrase) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Compartilhar",
                                tint = Color.White
                            )
                        }
                    }
                }
            )
        },
        containerColor = Color(0xFF101010)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF101010))
                .verticalScroll(rememberScrollState())
        ) {
            // Card da frase
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Frase:",
                        color = Color(0xFF00BFFF),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = phrase,
                        color = Color.White,
                        fontSize = 20.sp,
                        lineHeight = 28.sp,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            // Card da explicação
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                elevation = CardDefaults.cardElevation(8.dp)
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
                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF00BFFF),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        "Carregando explicação...",
                                        color = Color.LightGray,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                        hasError -> {
                            Column {
                                Text(
                                    text = explanation,
                                    color = Color(0xFFFF5252),
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                isLoading = true
                                                hasError = false
                                                explanation = fetchExplanationOffline(phrase)
                                                isLoading = false
                                            } catch (e: Exception) {
                                                hasError = true
                                                isLoading = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BFFF)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Tentar Novamente", color = Color.White)
                                }
                            }
                        }
                        else -> {
                            Text(
                                text = explanation,
                                color = Color.White,
                                fontSize = 18.sp,
                                lineHeight = 26.sp
                            )
                        }
                    }
                }
            }

            // Informação sobre IA (se implementar integração com OpenAI)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1B2A)),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
    }
}

/**
 * Função offline para gerar explicações.
 * Substitua por uma chamada real à API do OpenAI quando implementar.
 */
suspend fun fetchExplanationOffline(frase: String): String {
    // Simula um delay de carregamento
    kotlinx.coroutines.delay(1000)

    return when {
        frase.contains("Disciplina", ignoreCase = true) ->
            "A disciplina é a capacidade de fazer o que precisa ser feito, mesmo quando não temos vontade. " +
                    "Ela nos dá liberdade porque nos permite escolher nossas ações baseadas em nossos objetivos, " +
                    "não em nossos impulsos momentâneos. A preguiça, por outro lado, nos prende em um ciclo de " +
                    "procrastinação e arrependimentos, limitando nosso potencial."

        frase.contains("conforto", ignoreCase = true) ->
            "A zona de conforto é um estado psicológico onde nos sentimos seguros e sem stress, mas também " +
                    "onde não há crescimento. Nossos sonhos geralmente estão fora desta zona, exigindo que enfrentemos " +
                    "desafios e incertezas. Quando priorizamos o conforto acima de tudo, acabamos abrindo mão das " +
                    "oportunidades de crescimento pessoal e realização dos nossos objetivos."

        frase.contains("vítima", ignoreCase = true) ->
            "Ser vítima da própria história significa culpar as circunstâncias passadas pelos problemas atuais, " +
                    "sem assumir responsabilidade pelas mudanças necessárias. Embora não possamos mudar o passado, " +
                    "sempre temos o poder de escolher como reagir e que direção tomar daqui para frente. " +
                    "Assumir o controle da própria narrativa é o primeiro passo para a transformação."

        frase.contains("Motivação", ignoreCase = true) ->
            "A motivação é como uma onda de energia que vem e vai, influenciada por nosso humor e circunstâncias. " +
                    "A disciplina, por outro lado, é um sistema estruturado de hábitos e comportamentos que funciona " +
                    "independentemente de como nos sentimos. Construir disciplina significa criar rotinas e processos " +
                    "que nos mantêm em movimento mesmo quando a motivação inicial desaparece."

        frase.contains("ação", ignoreCase = true) && frase.contains("medo", ignoreCase = true) ->
            "O medo é uma emoção natural que nos paralisa diante do desconhecido ou do risco. " +
                    "A única maneira comprovada de superar o medo é através da ação. Quando agimos apesar do medo, " +
                    "descobrimos que a maioria dos nossos medos são maiores em nossa imaginação do que na realidade. " +
                    "Cada ação corajosa fortalece nossa confiança e reduz o poder que o medo tem sobre nós."

        frase.contains("sonhos", ignoreCase = true) ->
            "Esta frase destaca uma realidade do mercado de trabalho: se não perseguimos nossos próprios objetivos, " +
                    "acabamos dedicando nossa energia e tempo para realizar os sonhos de outras pessoas. " +
                    "Embora trabalhar para outros possa ser necessário, é importante manter vivos nossos próprios projetos " +
                    "e ambições, mesmo que seja através de pequenas ações diárias que nos aproximem dos nossos objetivos."

        frase.contains("desculpa", ignoreCase = true) ->
            "As desculpas são explicações que oferecemos para justificar por que algo não foi feito ou não deu certo. " +
                    "Embora possam nos fazer sentir melhor momentaneamente, elas não resolvem problemas práticos como " +
                    "pagar contas ou alcançar objetivos. O foco deve estar em encontrar soluções e tomar ações concretas, " +
                    "em vez de elaborar justificativas para a inação."

        frase.contains("salvar", ignoreCase = true) ->
            "Esta frase enfatiza a importância da autossuficiência e responsabilidade pessoal. " +
                    "Embora seja natural esperar ajuda dos outros, a verdadeira mudança sempre começa conosco. " +
                    "Ninguém se importa tanto com seus problemas quanto você mesmo, e ninguém tem mais poder " +
                    "para mudá-los do que você. A salvação vem da ação pessoal, não da espera passiva."

        frase.contains("fracassa", ignoreCase = true) ->
            "O erro é uma parte natural do processo de aprendizagem e crescimento. Cada erro nos ensina algo " +
                    "valioso e nos aproxima da solução certa. O verdadeiro fracasso acontece quando paramos de tentar, " +
                    "porque aí perdemos todas as oportunidades de aprender, crescer e eventualmente ter sucesso. " +
                    "A persistência é o que diferencia o sucesso do fracasso."

        frase.contains("Foco", ignoreCase = true) ->
            "Foco é a capacidade de direcionar nossa atenção para o que realmente importa. " +
                    "Disciplina é a prática de manter comportamentos consistentes mesmo quando é difícil. " +
                    "Constância é a persistência ao longo do tempo. Juntos, esses três elementos formam a base " +
                    "para qualquer conquista significativa. Tudo o mais são distrações que podem nos desviar do caminho."

        else ->
            "Esta frase nos convida à reflexão sobre a importância da consistência e determinação em nossas vidas. " +
                    "Ela enfatiza que o sucesso não vem de momentos isolados de inspiração, mas sim da prática diária " +
                    "de bons hábitos e da persistência diante dos desafios. É um lembrete de que temos o poder de " +
                    "moldar nossa realidade através de nossas escolhas e ações cotidianas."
    }
}

fun gerarExplicacao(frase: String, context: Context): String {
    return when {
        frase.contains("Disciplina") -> "A disciplina traz autonomia e foco, enquanto a preguiça nos limita."
        frase.contains("conforto")   -> "Ficar na zona de conforto impede o crescimento pessoal."
        else                          -> "Essa frase nos lembra da importância da consistência para atingir objetivos."
    }
}