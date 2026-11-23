package com.smartkeyboard.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.smartkeyboard.learning.UserStore

@Database(
    entities = [UserWordEntity::class, WordFrequencyEntity::class, NgramEntity::class,
        UserPhraseEntity::class, CorrectionEntity::class, PreferenceEntity::class],
    version = 1, exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun phraseDao(): PhraseDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "smartkeyboard.db")
                .build().also { instance = it }
        }
    }
}

/** UserStore sobre Room. Todas las llamadas son bloqueantes: se invocan desde el hilo de E/S. */
class RoomUserStore(private val db: AppDatabase) : UserStore {
    private val w get() = db.wordDao()
    private val p get() = db.phraseDao()
    private fun now() = System.currentTimeMillis()

    override fun loadWordFreq(limit: Int) = w.topFrequencies(limit).associate { it.word to it.count }
    override fun loadUserWords() = w.allUserWords().associate { it.word to it.count }
    override fun loadNgrams(limit: Int) = w.topNgrams(limit).associate { "${it.prev} ${it.word}" to it.count }
    override fun loadPhrases() = p.topPhrases().associate { it.phrase to it.count }
    override fun loadCorrectionCount() = p.correctionCount()
    override fun bumpFreq(word: String, d: Int) = w.addFreq(word, d, now())
    override fun bumpUserWord(word: String, d: Int) = w.addUserWord(word, d, now())
    override fun bumpNgram(prev: String, word: String, d: Int) = w.addNgram(prev, word, d, now())
    override fun bumpPhrase(phrase: String, d: Int) = p.addPhrase(phrase, d, now())
    override fun bumpCorrection(typed: String, chosen: String, d: Int) = p.addCorrection(typed, chosen, d, now())
    override fun clearAll() { db.clearAllTables() }
    override fun prune() {
        val cutoff = now() - 60L * 24 * 3600 * 1000
        w.pruneFreq(cutoff); w.pruneNgrams(cutoff); w.pruneUserWords(cutoff); p.prunePhrases(cutoff)
    }
}
