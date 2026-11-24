package com.smartkeyboard.engine

/** Pesos de S = αL + βC + γP + δU y parámetros de la búsqueda (ajustables experimentalmente). */
class EngineConfig(
    val alpha: Float = 1f,
    val beta: Float = 1f,
    val gamma: Float = 0.85f,
    val delta: Float = 1f,
    val missingLetterCost: Float = 0.35f,
    /** Penalización por letra que falta que se mantiene incluso en el último tramo: una palabra completa tecleada gana a sus completaciones. */
    val completionKeep: Float = 1.0f,
    val ghostCost: Float = 2.5f,
    val ghostMinCoherence: Float = 1.5f,
    val ghostWords: List<String> = listOf("una", "un", "de", "la", "el", "en", "que", "los", "las", "y", "a", "para", "con", "por", "del", "al", "mi", "tu", "su"),
    val beamWidth: Int = 12,
    /** Peso de la concordancia de género/número (0 = desactivada). */
    val agreementWeight: Float = 1.8f,
    val segmenter: SegmenterConfig = SegmenterConfig(),
)

/**
 * Motor de predicción continua. Cada pulsación añade una capa al Beam Search (incremental);
 * retroceso la elimina; la interpretación vigente es siempre provisional y reversible.
 */
class PredictionEngine(
    private val main: Lexicon,
    lm: LanguageModel,
    userModel: UserModel = NoUserModel,
    val config: EngineConfig = EngineConfig(),
) {
    private val analyzer = ContextAnalyzer(lm, userModel)
    private val segmenter = WordSegmenter(listOf(main), config.segmenter)
    @Volatile private var userLex: Lexicon? = null
    private val agreement = AgreementModel { segmenter.lexicons }

    private val scorer = object : Scorer {
        override fun transition(prev: Word, c: Candidate): Float {
            val w = c.word
            val l = -c.edit - c.abbrev - c.missing * config.missingLetterCost
            return config.alpha * l +
                config.beta * analyzer.coherence(prev, w) +
                config.gamma * w.logP +
                config.delta * analyzer.personal(prev, w) +
                config.agreementWeight * agreement.bonus(prev, w)
        }
        override fun coherence(a: Word, b: Word) = analyzer.coherence(a, b)
        override val ghostCost get() = config.ghostCost
        override val ghostMinCoherence get() = config.ghostMinCoherence
        override fun finalAdjust(c: Candidate) = config.alpha * (c.missing * config.missingLetterCost * (1f - config.completionKeep) + c.abbrev)
    }
    private val beam = BeamSearch(segmenter, scorer, beamWidth = config.beamWidth)
    private var context: Word = Word.START

    init { beam.ghosts = config.ghostWords.mapNotNull { w -> main.idOf(w).takeIf { it >= 0 }?.let { lookup(w) } } }

    val typed: String get() = beam.typed
    var beamWidth: Int
        get() = beam.beamWidth
        set(v) { beam.beamWidth = v.coerceIn(2, 64) }

    /** ¿Es una palabra completa del léxico principal o del vocabulario personal? */
    fun isKnownWord(w: String): Boolean = segmenter.lexicons.any { it.contains(w.lowercase()) }

    fun setUserModel(m: UserModel) { analyzer.user = m }

    /** Léxico de vocabulario personal (ids a partir de main.size). Reconstruir al aprender palabras nuevas. */
    fun setUserLexicon(lex: Lexicon?) {
        userLex = lex
        segmenter.lexicons = if (lex == null) listOf(main) else listOf(main, lex)
        agreement.invalidate()
        replay()
    }

    private fun lookup(prevLower: String): Word {
        for (lx in segmenter.lexicons) {
            val gid = lx.idOf(prevLower)
            if (gid >= 0) { val l = gid - lx.base; return Word(lx.surface(l), gid, lx.logP(l), lx.keyLength(l)) }
        }
        return Word(prevLower, Word.LITERAL, -13f, prevLower.length)
    }

    /** Últimas palabras de la frase en curso: fijan el género/número vigente para la concordancia. Llamar antes de [setContext]. */
    fun setDiscourse(recentWords: List<String>) { agreement.discourse = SpanishMorph.contextFrom(recentWords) }

    /** Palabra anterior ya confirmada (null = inicio de frase). Reinicia la composición. */
    fun setContext(prevWord: String?) {
        context = if (prevWord.isNullOrBlank()) Word.START else lookup(prevWord.lowercase())
        beam.reset(context)
    }

    fun type(c: Char): Interpretation? { beam.push(c); return best() }
    fun backspace(): Interpretation? { beam.pop(); return best() }
    fun clear() = beam.reset(context)

    fun best(): Interpretation? = beam.best(1).firstOrNull()
    fun interpretations(max: Int = 3): List<Interpretation> = beam.best(max)

    /** Recalcula con las letras actuales (tras cambiar el léxico de usuario). */
    private fun replay() {
        val letters = beam.typed
        beam.reset(context)
        for (c in letters) beam.push(c)
    }

    /** Confirma las primeras [wordCount] palabras de la mejor interpretación; devuelve el nº de letras consumidas. */
    fun commitPrefix(wordCount: Int): Int {
        val top = beam.finals().firstOrNull() ?: return 0
        val interp = beam.toInterpretation(top)
        if (wordCount <= 0 || wordCount >= interp.words.size) return 0
        val consumed = interp.spans[wordCount - 1].last + 1
        val rest = beam.typed.substring(consumed)
        context = lookup(interp.words[wordCount - 1].lowercase())
        beam.reset(context)
        for (c in rest) beam.push(c)
        return consumed
    }

    companion object {
        fun load(words: java.io.InputStream, bigrams: java.io.InputStream?, config: EngineConfig = EngineConfig()): PredictionEngine {
            val lex = Lexicon.fromStream(words)
            return PredictionEngine(lex, NgramLanguageModel(lex, bigrams), NoUserModel, config)
        }
    }
}
