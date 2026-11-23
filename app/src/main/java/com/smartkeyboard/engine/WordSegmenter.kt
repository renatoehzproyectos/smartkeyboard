package com.smartkeyboard.engine

class Candidate(val word: Word, val start: Int, val end: Int, val edit: Float, val missing: Int) {
    /** Penalización por abreviar una palabra en mitad del texto (se anula si es el último tramo). */
    var abbrev: Float = 0f
}

class SegmenterConfig(
    val completions: Int = 16,
    val minFuzzyLen: Int = 4,
    val transposeCost: Float = 3.5f,
    val doubledCost: Float = 0.8f,
    val extraCost: Float = 4.5f,
    val neighborCost: Float = 4.0f,
    val missingLetterCost: Float = 4.5f,
    val abbrev1: Float = 5f,
    val abbrev2: Float = 3f,
    val abbrev3: Float = 1.5f,
    val abbrevN: Float = 0.6f,
    val literalBase: Float = 20f,
    val literalPerLetter: Float = 1.2f,
)

/**
 * Genera, para un tramo de letras [i, n), las palabras que pueden corresponderle:
 * palabras completas, prefijos de palabras más largas (palabra incompleta), variantes con
 * un error de tecleo (letra duplicada/extra, omitida, vecina, transpuesta) y, como último
 * recurso, la secuencia literal. La elección entre segmentaciones la hace BeamSearch.
 */
class WordSegmenter(lexicons: List<Lexicon>, val cfg: SegmenterConfig = SegmenterConfig()) {
    @Volatile var lexicons: List<Lexicon> = lexicons
        set(v) { field = v; cache.clear() }
    private val cache = HashMap<Int, Word>()

    private fun wordOf(lex: Lexicon, local: Int): Word {
        val gid = lex.base + local
        return cache.getOrPut(gid) { Word(lex.surface(local), gid, lex.logP(local), lex.keyLength(local)) }
    }

    fun candidatesEndingAt(keys: CharSequence, raw: CharSequence, i: Int, n: Int): List<Candidate> {
        val lexs = lexicons
        val best = HashMap<Int, Candidate>()
        val segLen = n - i
        fun put(lex: Lexicon, local: Int, edit: Float, matchedLen: Int) {
            val w = wordOf(lex, local)
            val c = Candidate(w, i, n, edit, (w.len - matchedLen).coerceAtLeast(0))
            if (c.missing > 0) c.abbrev = when (segLen) { 1 -> cfg.abbrev1; 2 -> cfg.abbrev2; 3 -> cfg.abbrev3; else -> cfg.abbrevN }
            val old = best[w.gid]
            if (old == null || old.edit > edit || (old.edit == edit && old.missing > c.missing)) best[w.gid] = c
        }
        fun fromNode(lex: Lexicon, node: Int, edit: Float, matchedLen: Int) {
            if (node < 0) return
            for (id in lex.terminals(node)) put(lex, id, edit, matchedLen)
            if (lex.hasChildren(node)) {
                var added = 0
                for (id in lex.topWords(node)) {
                    if (lex.keyLength(id) > matchedLen && added < cfg.completions) { put(lex, id, edit, matchedLen); added++ }
                }
            }
        }
        for (lex in lexs) {
            fromNode(lex, lex.walk(lex.root, keys, i, n), 0f, segLen)
            if (segLen >= cfg.minFuzzyLen) {
                val seg = keys.subSequence(i, n).toString()
                // letra extra / duplicada
                for (k in seg.indices) {
                    val doubled = (k > 0 && seg[k - 1] == seg[k]) || (k + 1 < seg.length && seg[k + 1] == seg[k])
                    val v = seg.removeRange(k, k + 1)
                    fromNode(lex, lex.walk(lex.root, v, 0, v.length), if (doubled) cfg.doubledCost else cfg.extraCost, v.length)
                }
                // transposición
                for (k in 0 until seg.length - 1) {
                    if (seg[k] == seg[k + 1]) continue
                    val v = StringBuilder(seg).also { val t = it[k]; it[k] = it[k + 1]; it[k + 1] = t }
                    fromNode(lex, lex.walk(lex.root, v, 0, v.length), cfg.transposeCost, v.length)
                }
                // sustitución por tecla vecina
                for (k in seg.indices) for (r in TextNorm.neighbors(seg[k])) {
                    val v = StringBuilder(seg).also { it.setCharAt(k, r) }
                    fromNode(lex, lex.walk(lex.root, v, 0, v.length), cfg.neighborCost, v.length)
                }
                // letra omitida
                for (k in 1 until seg.length) {
                    val n1 = lex.walk(lex.root, seg, 0, k)
                    if (n1 < 0) continue
                    lex.forEachChild(n1) { _, c ->
                        fromNode(lex, lex.walk(c, seg, k, seg.length), cfg.missingLetterCost, seg.length + 1)
                    }
                }
            }
        }
        val out = ArrayList<Candidate>(best.values)
        // Literal (palabra desconocida, nombre propio, etc.)
        val lit = raw.subSequence(i, n).toString()
        out.add(Candidate(Word(lit, Word.LITERAL, -(cfg.literalBase + cfg.literalPerLetter * segLen), segLen), i, n, 0f, 0))
        return out
    }
}
