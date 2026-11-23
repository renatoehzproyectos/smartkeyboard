package com.smartkeyboard.engine

/** Puntuación de una transición prev → candidato. */
interface Scorer {
    fun transition(prev: Word, c: Candidate): Float
    /** Penalización que se anula si el candidato es el último tramo (la palabra puede estar incompleta). */
    fun finalAdjust(c: Candidate): Float
    /** Coherencia contextual a→b (log-lift). */
    fun coherence(a: Word, b: Word): Float
    /** Coste de insertar una palabra implícita (no tecleada) entre dos palabras. */
    val ghostCost: Float
    val ghostMinCoherence: Float
}

class Hyp(val prev: Hyp?, val word: Word, val start: Int, val end: Int, val score: Float, val finalAdj: Float) {
    val finalScore get() = score + finalAdj
}

class Interpretation(val words: List<String>, val spans: List<IntRange>, val score: Float) {
    val text: String get() = words.joinToString(" ")
}

/**
 * Beam Search incremental: una capa por letra escrita. Añadir una letra solo calcula la capa nueva;
 * borrar descarta la última capa. Cada capa conserva las mejores hipótesis (distintas por última palabra).
 */
class BeamSearch(
    private val segmenter: WordSegmenter,
    private val scorer: Scorer,
    @Volatile var ghosts: List<Word> = emptyList(),
    @Volatile var beamWidth: Int = 12,
    private val maxSegment: Int = 26,
) {
    private val layers = ArrayList<List<Hyp>>()
    private val keys = StringBuilder()
    private val raw = StringBuilder()

    init { reset(Word.START) }

    val length get() = keys.length
    val typed: String get() = raw.toString()

    fun reset(context: Word) {
        layers.clear(); keys.setLength(0); raw.setLength(0)
        layers.add(listOf(Hyp(null, context, 0, 0, 0f, 0f)))
    }

    fun push(c: Char) {
        raw.append(c.lowercaseChar()); keys.append(TextNorm.key(c))
        val n = keys.length
        val best = HashMap<Any, Hyp>()
        for (i in maxOf(0, n - maxSegment) until n) {
            val srcs = layers[i]
            if (srcs.isEmpty()) continue
            val gl = ghosts
            for (cand in segmenter.candidatesEndingAt(keys, raw, i, n)) {
                val fa = scorer.finalAdjust(cand)
                val k: Any = if (cand.word.gid >= 0) cand.word.gid else "L" + cand.word.surface
                for (h in srcs) {
                    val s = h.score + scorer.transition(h.word, cand)
                    val old = best[k]
                    if (old == null || s + fa > old.finalScore) best[k] = Hyp(h, cand.word, i, n, s, fa)
                }
                // Palabra implícita: sólo si el contexto la respalda por ambos lados (p. ej. «construir [una] aplicación»).
                if (i > 0 && cand.word.gid >= 0) for (g in gl) {
                    if (scorer.coherence(g, cand.word) < scorer.ghostMinCoherence) continue
                    val gc = Candidate(g, i, i, 0f, 0)
                    for (h in srcs) {
                        if (scorer.coherence(h.word, g) < scorer.ghostMinCoherence) continue
                        val gs = h.score + scorer.transition(h.word, gc) - scorer.ghostCost
                        val s = gs + scorer.transition(g, cand)
                        val old = best[k]
                        if (old == null || s + fa > old.finalScore) best[k] = Hyp(Hyp(h, g, i, i, gs, 0f), cand.word, i, n, s, fa)
                    }
                }
            }
        }
        layers.add(best.values.sortedByDescending { it.finalScore }.take(beamWidth))
    }

    fun pop() {
        if (keys.isEmpty()) return
        keys.setLength(keys.length - 1); raw.setLength(raw.length - 1)
        layers.removeAt(layers.size - 1)
    }

    fun finals(): List<Hyp> = if (keys.isEmpty()) emptyList() else layers.last().sortedByDescending { it.finalScore }

    fun toInterpretation(h: Hyp): Interpretation {
        val ws = ArrayList<String>(); val sp = ArrayList<IntRange>()
        var x: Hyp? = h
        while (x != null && x.prev != null) { ws.add(x.word.surface); sp.add(x.start until x.end); x = x.prev }
        ws.reverse(); sp.reverse()
        return Interpretation(ws, sp, h.finalScore)
    }

    fun best(count: Int): List<Interpretation> {
        val seen = HashSet<String>(); val out = ArrayList<Interpretation>()
        for (h in finals()) {
            val it = toInterpretation(h)
            if (seen.add(it.text.lowercase())) out.add(it)
            if (out.size >= count) break
        }
        return out
    }
}
