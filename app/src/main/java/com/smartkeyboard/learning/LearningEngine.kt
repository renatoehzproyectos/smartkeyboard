package com.smartkeyboard.learning

import com.smartkeyboard.engine.Lexicon
import com.smartkeyboard.engine.UserModel
import com.smartkeyboard.ime.Learner
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln
import kotlin.math.min

/**
 * Aprendizaje dual y continuo, 100 % local.
 *  - Pasivo (peso 1): lo que se escribe habitualmente.
 *  - Activo (peso 3): predicciones elegidas; correcciones (peso 4).
 * Implementa [UserModel]: sus refuerzos entran en la puntuación como el término U.
 */
class LearningEngine(
    private val store: UserStore,
    private val isKnownWord: (String) -> Boolean,
    private val lexiconBase: () -> Int,
    private val runIo: (() -> Unit) -> Unit = { it() },
) : Learner, UserModel {
    companion object {
        const val PASSIVE = 1
        const val ACTIVE = 3
        const val CORRECTION = 4
        private const val UNI_CAP = 3.0f
        private const val BI_CAP = 4.0f
    }

    @Volatile override var enabled: Boolean = true
    @Volatile var onVocabularyChanged: ((Lexicon?) -> Unit)? = null

    private val uni = ConcurrentHashMap<String, Int>()
    private val bi = ConcurrentHashMap<String, Int>()
    private val phrases = ConcurrentHashMap<String, Int>()
    private val phrasePairs = ConcurrentHashMap<String, Int>()
    val vocabulary = UserVocabulary()
    val corrections = CorrectionTracker(store, runIo)

    val learnedWords: Int get() = uni.size
    val learnedPairs: Int get() = bi.size

    fun load() {
        uni.putAll(store.loadWordFreq(30_000))
        bi.putAll(store.loadNgrams(60_000))
        vocabulary.load(store.loadUserWords())
        phrases.putAll(store.loadPhrases())
        phrases.forEach { (p, c) -> if (c >= 3) markPhrase(p) }
        corrections.init(store.loadCorrectionCount())
        store.prune()
        publishVocabulary()
    }

    private fun markPhrase(p: String) {
        val w = p.split(' ')
        for (i in 0 until w.size - 1) phrasePairs.merge(w[i] + " " + w[i + 1], 1, Int::plus)
    }

    private fun publishVocabulary() { onVocabularyChanged?.invoke(vocabulary.buildLexicon(lexiconBase())) }

    // ---- UserModel ----
    override fun unigramBoost(lower: String): Float {
        val c = uni[lower] ?: return 0f
        return min(UNI_CAP, 0.7f * ln(1f + c))
    }

    override fun bigramBoost(prevLower: String, lower: String): Float {
        val key = "$prevLower $lower"
        val c = bi[key] ?: return 0f
        val p = phrasePairs[key] ?: 0
        return min(BI_CAP, 1.0f * ln(1f + c) + 0.3f * ln(1f + p))
    }

    // ---- Learner ----
    override fun observe(prev: String?, words: List<String>, weight: Int) {
        if (!enabled || words.isEmpty()) return
        var p = prev?.lowercase()
        var vocabChanged = false
        val clean = ArrayList<String>(words.size)
        for (raw in words) {
            val w = raw.lowercase()
            if (w.length > 24 || w.isEmpty()) { p = null; continue }
            clean.add(w)
            uni.merge(w, weight, Int::plus)
            runIo { store.bumpFreq(w, weight) }
            if (!isKnownWord(w)) {
                if (vocabulary.add(w, weight)) vocabChanged = true
                runIo { store.bumpUserWord(w, weight) }
            }
            if (p != null) {
                val q = p
                bi.merge("$q $w", weight, Int::plus)
                runIo { store.bumpNgram(q, w, weight) }
            }
            p = w
        }
        if (clean.size >= 3) {
            val phrase = clean.take(6).joinToString(" ")
            val c = phrases.merge(phrase, 1, Int::plus) ?: 1
            if (c == 3) markPhrase(phrase)
            runIo { store.bumpPhrase(phrase, 1) }
        }
        if (vocabChanged) publishVocabulary()
    }

    override fun correction(typed: String, chosen: String, prev: String?) {
        if (!enabled) return
        corrections.record(typed, chosen)
        observe(prev, chosen.split(' ').filter { it.isNotBlank() }, CORRECTION)
    }

    /** Borra todos los datos personalizados (memoria y base de datos). */
    fun clearAll() {
        uni.clear(); bi.clear(); phrases.clear(); phrasePairs.clear(); vocabulary.clear(); corrections.reset()
        runIo { store.clearAll() }
        publishVocabulary()
    }
}
