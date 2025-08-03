package com.dev.anderson.geradorfrases.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dev.anderson.geradorfrases.data.Phrase

@Dao
interface PhraseDao {
    @Query("SELECT * FROM phrases WHERE category = :category ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomByCategory(category: String): Phrase?

    @Query("SELECT * FROM phrases WHERE category = :category AND subcategory = :subcategory ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomBySubcategory(category: String, subcategory: String): Phrase?

    @Query("SELECT DISTINCT category FROM phrases ORDER BY category")
    suspend fun getAllCategories(): List<String>

//    @Query("SELECT DISTINCT subcategory FROM phrases WHERE category = :category ORDER BY subcategory")
    @Query("""
        SELECT DISTINCT subcategory 
        FROM phrases 
        WHERE category = :category 
        AND subcategory IS NOT NULL 
        AND subcategory != '' 
        AND TRIM(subcategory) != ''
        ORDER BY subcategory
    """)
    suspend fun getSubcategoriesByCategory(category: String): List<String>

    @Query("SELECT * FROM phrases WHERE text LIKE '%' || :searchTerm || '%' OR explanation LIKE '%' || :searchTerm || '%' OR tags LIKE '%' || :searchTerm || '%'")
    suspend fun searchPhrases(searchTerm: String): List<Phrase>

    @Query("SELECT * FROM phrases WHERE isFavorite = 1 ORDER BY dateAdded DESC")
    suspend fun getFavorites(): List<Phrase>

    @Query("SELECT * FROM phrases ORDER BY timesViewed DESC LIMIT 10")
    suspend fun getMostViewed(): List<Phrase>

    @Update
    suspend fun updatePhrase(phrase: Phrase)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(phrases: List<Phrase>)

    @Query("UPDATE phrases SET timesViewed = timesViewed + 1 WHERE id = :id")
    suspend fun incrementViews(id: Long)

    @Query("UPDATE phrases SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean)

    @Query("SELECT * FROM phrases WHERE category = :category")
    suspend fun getAllByCategory(category: String): List<Phrase>

    @Query("SELECT * FROM phrases WHERE category = :category AND subcategory = :subcategory")
    suspend fun getAllBySubcategory(category: String, subcategory: String): List<Phrase>

    @Query("SELECT * FROM phrases WHERE category = :category ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomPhraseByCategory(category: String): Phrase?

    @Query("SELECT explanation FROM phrases WHERE id = :id")
    suspend fun getExplanationById(id: Long): String?

    @Query("SELECT EXISTS(SELECT 1 FROM phrases WHERE text = :phraseText AND isFavorite = 1)")
    suspend fun isPhraseInFavorites(phraseText: String): Boolean

    @Query("SELECT * FROM phrases WHERE text = :text LIMIT 1")
    suspend fun findPhraseByText(text: String): Phrase?

    @Query("SELECT * FROM phrases WHERE text LIKE :pattern LIMIT 1")
    suspend fun findSimilarPhrase(pattern: String): Phrase?

    @Query("SELECT * FROM phrases WHERE id = :id LIMIT 1")
    suspend fun getPhraseById(id: Long): Phrase?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPhrase(phrase: Phrase): Long
}