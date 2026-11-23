package com.smartkeyboard.learning

import com.smartkeyboard.engine.Lexicon

/** Vocabulario personal: palabras fuera del diccionario que el usuario ha escrito de forma repetida o eligió expresamente. */
class UserVocabulary(private val minCount: Int = 2) {
    private val counts = HashMap<String, Int>()

    @Synchronized fun load(m: Map<String, Int>) { counts.clear(); counts.putAll(m) }
    @Synchronized fun clear() = counts.clear()

    /** Suma y devuelve true si la palabra acaba de entrar en el vocabulario activo. */
    @Synchronized fun add(word: String, d: Int): Boolean {
        val before = counts[word] ?: 0
        counts[word] = before + d
        return before < minCount && before + d >= minCount
    }

    @Synchronized fun activeWords(): List<Pair<String, Int>> = counts.filter { it.value >= minCount }.map { it.key to it.value }
    @Synchronized fun size() = counts.count { it.value >= minCount }

    /** Léxico superpuesto (ids desde [base]); null si no hay palabras activas. */
    fun buildLexicon(base: Int, surfaceOf: (String) -> String = { it }): Lexicon? {
        val act = activeWords()
        if (act.isEmpty()) return null
        return Lexicon.fromEntries(act.map { surfaceOf(it.first) }, act.map { it.second * 200_000f }, base, totalOverride = 1e9f)
    }
}
