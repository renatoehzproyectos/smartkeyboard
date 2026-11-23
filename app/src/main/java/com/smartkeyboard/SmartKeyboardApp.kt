package com.smartkeyboard

import android.app.Application
import android.content.Context
import com.smartkeyboard.database.AppDatabase
import com.smartkeyboard.database.RoomUserStore
import com.smartkeyboard.engine.Lexicon
import com.smartkeyboard.learning.LearningEngine
import java.util.concurrent.Executors

/** Ajustes del usuario (SharedPreferences, solo en el dispositivo). */
object Prefs {
    const val NAME = "settings"
    const val PREDICTIONS = "predictions"
    const val LEARNING = "learning"
    const val HAPTIC = "haptic"
    const val BEAM = "beam"
    fun get(c: Context) = c.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}

/** Léxico principal compartido para que el aprendizaje sepa qué palabras son nuevas. */
object EngineHolder { @Volatile var lexicon: Lexicon? = null }

class SmartKeyboardApp : Application() {
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "sk-io").apply { isDaemon = true } }

    val learning: LearningEngine by lazy {
        val store = RoomUserStore(AppDatabase.get(this))
        LearningEngine(
            store,
            isKnownWord = { EngineHolder.lexicon?.contains(it) ?: true },
            lexiconBase = { EngineHolder.lexicon?.size ?: 0 },
            runIo = { job -> io.execute { try { job() } catch (_: Throwable) { } } },
        )
    }

    fun runIo(job: () -> Unit) = io.execute { try { job() } catch (_: Throwable) { } }

    companion object { fun of(c: Context) = c.applicationContext as SmartKeyboardApp }
}
