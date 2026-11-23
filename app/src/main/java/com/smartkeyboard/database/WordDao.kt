package com.smartkeyboard.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class WordDao {
    // --- word_frequency ---
    @Query("SELECT * FROM word_frequency ORDER BY count DESC LIMIT :limit")
    abstract fun topFrequencies(limit: Int): List<WordFrequencyEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract fun insertFreq(e: WordFrequencyEntity): Long
    @Query("UPDATE word_frequency SET count = count + :d, lastUsed = :t WHERE word = :w")
    abstract fun bumpFreq(w: String, d: Int, t: Long)
    @Transaction open fun addFreq(w: String, d: Int, t: Long) { if (insertFreq(WordFrequencyEntity(w, d, t)) == -1L) bumpFreq(w, d, t) }

    // --- user_words ---
    @Query("SELECT * FROM user_words") abstract fun allUserWords(): List<UserWordEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract fun insertUserWord(e: UserWordEntity): Long
    @Query("UPDATE user_words SET count = count + :d, lastUsed = :t WHERE word = :w")
    abstract fun bumpUserWord(w: String, d: Int, t: Long)
    @Transaction open fun addUserWord(w: String, d: Int, t: Long) { if (insertUserWord(UserWordEntity(w, d, t)) == -1L) bumpUserWord(w, d, t) }

    // --- ngrams ---
    @Query("SELECT * FROM ngrams ORDER BY count DESC LIMIT :limit") abstract fun topNgrams(limit: Int): List<NgramEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract fun insertNgram(e: NgramEntity): Long
    @Query("UPDATE ngrams SET count = count + :d, lastUsed = :t WHERE prev = :p AND word = :w")
    abstract fun bumpNgram(p: String, w: String, d: Int, t: Long)
    @Transaction open fun addNgram(p: String, w: String, d: Int, t: Long) { if (insertNgram(NgramEntity(p, w, d, t)) == -1L) bumpNgram(p, w, d, t) }

    // --- preferences ---
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract fun setPreference(e: PreferenceEntity)
    @Query("SELECT value FROM preferences WHERE `key` = :k") abstract fun getPreference(k: String): String?

    // --- mantenimiento: olvida lo escaso y antiguo para no sobreajustar ni crecer sin límite ---
    @Query("DELETE FROM word_frequency WHERE count <= 1 AND lastUsed < :cutoff") abstract fun pruneFreq(cutoff: Long)
    @Query("DELETE FROM ngrams WHERE count <= 1 AND lastUsed < :cutoff") abstract fun pruneNgrams(cutoff: Long)
    @Query("DELETE FROM user_words WHERE count <= 1 AND lastUsed < :cutoff") abstract fun pruneUserWords(cutoff: Long)
}
