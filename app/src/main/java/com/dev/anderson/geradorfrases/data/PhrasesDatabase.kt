package com.dev.anderson.geradorfrases.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dev.anderson.geradorfrases.dao.PhraseDao
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Phrase::class],
    version = 1,
    exportSchema = false
)
abstract class PhrasesDatabase : RoomDatabase() {
    abstract fun phraseDao(): PhraseDao

    companion object {
        @Volatile
        private var INSTANCE: PhrasesDatabase? = null

        fun getDatabase(context: Context): PhrasesDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PhrasesDatabase::class.java,
                    "phrases_database"
                )
                    .addCallback(DatabaseCallback(context))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(private val context: Context) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // Executar pré-população em background
            CoroutineScope(Dispatchers.IO).launch {
                populateDatabase(INSTANCE!!)
            }
        }

        private suspend fun populateDatabase(database: PhrasesDatabase) {
            val dao = database.phraseDao()

            // Carregar dados do JSON em assets
            try {
                val jsonString = context.assets.open("phrases.json").bufferedReader().use { it.readText() }
                val gson = Gson()
                val phrasesList = gson.fromJson(jsonString, Array<Phrase>::class.java).toList()
                dao.insertAll(phrasesList)
            } catch (e: Exception) {
                // Se falhar, usar dados padrão
                dao.insertAll(getDefaultPhrases())
            }
        }

        private fun getDefaultPhrases(): List<Phrase> {
            return listOf(
                Phrase(
                    text = "Porque Deus amou o mundo de tal maneira que deu o seu Filho unigênito, para que todo aquele que nele crê não pereça, mas tenha a vida eterna.",
                    reference = "João 3:16",
                    category = "Versículos Bíblicos",
                    subcategory = "Amor de Deus",
                    explanation = "Este é um dos versículos mais conhecidos da Bíblia. Ele revela o amor incondicional de Deus pela humanidade e sua provisão de salvação através de Jesus Cristo. Demonstra que o amor de Deus não é baseado em nossos méritos, mas em sua graça.",
                    tags = "amor, salvação, Jesus, vida eterna, graça"
                ),
                Phrase(
                    text = "Confia no Senhor de todo o teu coração e não te estribes no teu próprio entendimento.",
                    reference = "Provérbios 3:5",
                    category = "Versículos Bíblicos",
                    subcategory = "Confiança",
                    explanation = "Este versículo ensina sobre a importância de confiar em Deus completamente, mesmo quando não entendemos as circunstâncias. Nos lembra que nossa sabedoria é limitada, mas Deus vê o quadro completo.",
                    tags = "confiança, sabedoria, fé, entendimento"
                ),
                Phrase(
                    text = "Posso todas as coisas em Cristo que me fortalece.",
                    reference = "Filipenses 4:13",
                    category = "Versículos Bíblicos",
                    subcategory = "Força e Perseverança",
                    explanation = "Paulo escreveu isso enquanto estava na prisão, mostrando que nossa força não vem de circunstâncias externas, mas da nossa relação com Cristo. Não significa que podemos fazer qualquer coisa, mas que podemos enfrentar qualquer situação com a força de Cristo.",
                    tags = "força, perseverança, Cristo, capacidade"
                ),
                Phrase(
                    text = "A disciplina é a ponte entre objetivos e conquistas.",
                    reference = "Jim Rohn",
                    category = "Motivação",
                    subcategory = "Autodisciplina",
                    explanation = "A disciplina é o que transforma sonhos em realidade. Ela é a ação consistente que conecta onde estamos com onde queremos estar. Sem disciplina, os objetivos permanecem apenas boas intenções.",
                    tags = "disciplina, objetivos, conquistas, persistência"
                ),
                Phrase(
                    text = "O sucesso não é final, o fracasso não é fatal: é a coragem de continuar que conta.",
                    reference = "Winston Churchill",
                    category = "Motivação",
                    subcategory = "Perseverança",
                    explanation = "Esta frase nos lembra que tanto o sucesso quanto o fracasso são temporários. O que realmente importa é nossa capacidade de continuar tentando, aprendendo e crescendo, independentemente dos resultados.",
                    tags = "sucesso, fracasso, coragem, persistência"
                )
            )
        }
    }
}