package com.smartkeyboard.engine

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.exp
import kotlin.math.ln

/** Palabra candidata. gid >= 0: id global del léxico; -1: literal desconocida; -2: inicio de frase. */
class Word(val surface: String, val gid: Int, val logP: Float, val len: Int) {
    val lower: String = surface.lowercase()
    companion object {
        const val LITERAL = -1
        const val BOS = -2
        val START = Word("<s>", BOS, 0f, 0)
    }
}

/** Modelo lingüístico intercambiable (hoy n-gramas ligeros; mañana puede ser un modelo neuronal). */
interface LanguageModel {
    /** Aporte de coherencia contextual (log-lift de P(w|prev) respecto a P(w)). */
    fun contextLift(prev: Word, w: Word): Float
}

/** Preferencias aprendidas del usuario (implementadas por LearningEngine). */
interface UserModel {
    fun unigramBoost(lower: String): Float
    fun bigramBoost(prevLower: String, lower: String): Float
}

object NoUserModel : UserModel {
    override fun unigramBoost(lower: String) = 0f
    override fun bigramBoost(prevLower: String, lower: String) = 0f
}

/** Bigramas interpolados con unigramas del léxico. Completamente offline. */
class NgramLanguageModel(private val lex: Lexicon, bigramStream: InputStream?) : LanguageModel {
    private val pair = HashMap<Long, Int>(1 shl 16)
    private val ctxTotal = IntArray(lex.size + 1)
    private val uni = FloatArray(lex.size) { exp(lex.logP(it)) }

    init {
        if (bigramStream != null) BufferedReader(InputStreamReader(bigramStream, Charsets.UTF_8), 1 shl 16).useLines { lines ->
            for (line in lines) {
                val p = line.split(' ')
                if (p.size != 3) continue
                val a = if (p[0] == "<s>") lex.size else lex.idOf(p[0])
                val b = lex.idOf(p[1])
                val c = p[2].toIntOrNull() ?: continue
                if (a < 0 || b < 0) continue
                pair[key(a, b)] = c
                ctxTotal[a] += c
            }
        }
    }

    private fun key(a: Int, b: Int) = (a.toLong() shl 32) or b.toLong()

    override fun contextLift(prev: Word, w: Word): Float {
        val a = if (prev.gid == Word.BOS) lex.size else prev.gid
        val b = w.gid
        if (a < 0 || a > lex.size || b < 0 || b >= lex.size) return 0f
        val total = ctxTotal[a]
        if (total == 0) return 0f
        val lambda = minOf(0.7f, total / (total + 3f))
        val c = pair[key(a, b)] ?: 0
        val ratio = (c.toFloat() / total) / uni[b]
        return ln(1f + lambda * (ratio - 1f)).coerceAtMost(6f)
    }
}
