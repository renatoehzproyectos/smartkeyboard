package com.smartkeyboard.learning

/** Persistencia del aprendizaje (Room en producción, memoria en pruebas). Las llamadas son bloqueantes. */
interface UserStore {
    fun loadWordFreq(limit: Int): Map<String, Int>
    fun loadUserWords(): Map<String, Int>
    fun loadNgrams(limit: Int): Map<String, Int>      // clave "previa palabra"
    fun loadPhrases(): Map<String, Int>
    fun loadCorrectionCount(): Int
    fun bumpFreq(word: String, d: Int)
    fun bumpUserWord(word: String, d: Int)
    fun bumpNgram(prev: String, word: String, d: Int)
    fun bumpPhrase(phrase: String, d: Int)
    fun bumpCorrection(typed: String, chosen: String, d: Int)
    fun clearAll()
    fun prune()
}

class InMemoryStore : UserStore {
    val freq = HashMap<String, Int>(); val words = HashMap<String, Int>(); val ngrams = HashMap<String, Int>()
    val phrases = HashMap<String, Int>(); val corrections = HashMap<String, Int>()
    override fun loadWordFreq(limit: Int) = HashMap(freq)
    override fun loadUserWords() = HashMap(words)
    override fun loadNgrams(limit: Int) = HashMap(ngrams)
    override fun loadPhrases() = HashMap(phrases)
    override fun loadCorrectionCount() = corrections.values.sum()
    override fun bumpFreq(word: String, d: Int) { freq.merge(word, d, Int::plus) }
    override fun bumpUserWord(word: String, d: Int) { words.merge(word, d, Int::plus) }
    override fun bumpNgram(prev: String, word: String, d: Int) { ngrams.merge("$prev $word", d, Int::plus) }
    override fun bumpPhrase(phrase: String, d: Int) { phrases.merge(phrase, d, Int::plus) }
    override fun bumpCorrection(typed: String, chosen: String, d: Int) { corrections.merge("$typed\u0000$chosen", d, Int::plus) }
    override fun clearAll() { freq.clear(); words.clear(); ngrams.clear(); phrases.clear(); corrections.clear() }
    override fun prune() {}
}
