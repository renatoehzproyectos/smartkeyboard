package com.smartkeyboard.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_words")
data class UserWordEntity(@PrimaryKey val word: String, val count: Int, val lastUsed: Long)

@Entity(tableName = "word_frequency")
data class WordFrequencyEntity(@PrimaryKey val word: String, val count: Int, val lastUsed: Long)

@Entity(tableName = "ngrams", primaryKeys = ["prev", "word"], indices = [Index("prev")])
data class NgramEntity(val prev: String, val word: String, val count: Int, val lastUsed: Long)

@Entity(tableName = "user_phrases")
data class UserPhraseEntity(@PrimaryKey val phrase: String, val count: Int, val lastUsed: Long)

@Entity(tableName = "corrections", primaryKeys = ["typed", "chosen"])
data class CorrectionEntity(val typed: String, val chosen: String, val count: Int, val lastUsed: Long)

@Entity(tableName = "preferences")
data class PreferenceEntity(@PrimaryKey val key: String, val value: String)
