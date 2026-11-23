package com.smartkeyboard.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class PhraseDao {
    @Query("SELECT * FROM user_phrases ORDER BY count DESC LIMIT 5000") abstract fun topPhrases(): List<UserPhraseEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract fun insertPhrase(e: UserPhraseEntity): Long
    @Query("UPDATE user_phrases SET count = count + :d, lastUsed = :t WHERE phrase = :p") abstract fun bumpPhrase(p: String, d: Int, t: Long)
    @Transaction open fun addPhrase(p: String, d: Int, t: Long) { if (insertPhrase(UserPhraseEntity(p, d, t)) == -1L) bumpPhrase(p, d, t) }
    @Query("DELETE FROM user_phrases WHERE count <= 1 AND lastUsed < :cutoff") abstract fun prunePhrases(cutoff: Long)

    @Query("SELECT COALESCE(SUM(count), 0) FROM corrections") abstract fun correctionCount(): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract fun insertCorrection(e: CorrectionEntity): Long
    @Query("UPDATE corrections SET count = count + :d, lastUsed = :t WHERE typed = :a AND chosen = :b") abstract fun bumpCorrection(a: String, b: String, d: Int, t: Long)
    @Transaction open fun addCorrection(a: String, b: String, d: Int, t: Long) { if (insertCorrection(CorrectionEntity(a, b, d, t)) == -1L) bumpCorrection(a, b, d, t) }
}
