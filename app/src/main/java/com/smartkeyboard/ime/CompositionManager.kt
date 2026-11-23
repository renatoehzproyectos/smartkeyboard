package com.smartkeyboard.ime

import com.smartkeyboard.engine.Interpretation
import com.smartkeyboard.engine.PredictionEngine

/**
 * Gestiona el texto provisional (composing) frente al confirmado.
 * Solo toca la región de composición: nunca reescribe texto ya confirmado
 * (salvo reabrir la última palabra con Retroceso, a petición explícita del usuario).
 */
class CompositionManager(
    private val sink: InputSink,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile var engine: PredictionEngine? = null
    var learner: Learner? = null
    var policy: FieldPolicy = FieldPolicy.FULL
    var onSuggestions: (List<String>) -> Unit = {}

    var composing = false; private set

    /** Modo manual: se escribe tal cual (sin predicción ni corrección) y cada palabra terminada se aprende. */
    var manual = false; private set
    private val mbuf = StringBuilder()
    private var mctx: String? = null

    fun setManual(v: Boolean) {
        if (v == manual) return
        if (v) { onExternalChange(); onSuggestions(emptyList()) } else flushManual()
        mbuf.setLength(0); manual = v
    }

    private fun flushManual() {
        if (mbuf.isEmpty()) return
        val w = mbuf.toString(); mbuf.setLength(0)
        learnerIfOn()?.observe(mctx, listOf(w), LearningEngineWeights.MANUAL)
    }
    private var capFirst = false
    private val capPositions = HashSet<Int>()
    private var context: String? = null
    private var shown: Interpretation? = null
    private var suggestions: List<Interpretation> = emptyList()
    private var reopenedFrom: String? = null

    private class LastCommit(val text: String, val letters: String, val context: String?, val capFirst: Boolean, val caps: Set<Int>, val time: Long)
    private var last: LastCommit? = null

    companion object {
        const val STABILIZE_LETTERS = 32
        const val REOPEN_WINDOW_MS = 10_000L
        private const val SENTENCE_END = ".!?¿¡:;\n"

        /** Palabra anterior a partir del texto previo al cursor (null = inicio de frase o contexto desconocido). */
        fun contextWord(before: String): String? {
            if (before.isEmpty()) return null
            if (!before.last().isWhitespace()) return null
            val t = before.trimEnd()
            if (t.isEmpty() || before.last() == '\n' || t.last() in SENTENCE_END) return null
            val tok = t.takeLastWhile { it.isLetter() }
            return tok.ifEmpty { null }
        }
    }

    private fun active() = policy.predict && engine != null

    // ---------- entrada ----------
    fun onLetter(c: Char, shift: Boolean) {
        if (manual) {
            if (mbuf.isEmpty()) mctx = contextWord(sink.textBefore(48))
            val ch = if (shift) c.uppercaseChar() else c
            sink.commit(ch.toString()); mbuf.append(ch)
            return
        }
        val e = engine
        if (!policy.predict || e == null) { sink.commit(if (shift) c.uppercaseChar().toString() else c.toString()); return }
        if (!composing) {
            val before = sink.textBefore(48)
            context = contextWord(before)
            e.setContext(context)
            capFirst = shift || sink.autoCapitalize()
            capPositions.clear()
            composing = true
            reopenedFrom = null
        } else if (shift) capPositions.add(e.typed.length)
        e.type(c)
        stabilizeIfLong(e)
        render()
    }

    fun onSpace() {
        if (manual) { flushManual(); sink.commit(" "); return }
        if (composing) finalize("", " ", active = false) else sink.commit(" ")
    }

    fun onPunctuation(ch: String) {
        if (manual) { flushManual(); sink.commit(ch); return }
        if (composing) finalize("", ch, active = false) else sink.commit(ch)
    }

    fun onEnter() {
        if (manual) { flushManual(); sink.sendEnter(); return }
        if (composing) finalize("", "", active = false)
        sink.sendEnter()
    }

    fun onBackspace() {
        if (manual) {
            if (mbuf.isNotEmpty()) mbuf.setLength(mbuf.length - 1)
            sink.sendBackspace(); return
        }
        val e = engine
        if (composing && e != null) {
            e.backspace()
            if (e.typed.isEmpty()) {
                composing = false; shown = null; suggestions = emptyList()
                sink.showComposing(""); sink.finishComposing(); onSuggestions(emptyList())
            } else render()
            return
        }
        val lc = last
        if (lc != null && e != null && policy.predict && clock() - lc.time <= REOPEN_WINDOW_MS && sink.textBefore(lc.text.length) == lc.text) {
            // Reabre la última interpretación para poder reinterpretarla (reversibilidad).
            sink.deleteBefore(lc.text.length)
            e.setContext(lc.context)
            context = lc.context; capFirst = lc.capFirst
            capPositions.clear(); capPositions.addAll(lc.caps)
            for (ch in lc.letters) e.type(ch)
            composing = true
            reopenedFrom = lc.text.trim()
            last = null
            render()
            return
        }
        last = null
        sink.sendBackspace()
    }

    /** El usuario toca una sugerencia (elección activa: más peso de aprendizaje). */
    fun onSuggestion(index: Int) {
        if (!composing) return
        val s = suggestions.getOrNull(index) ?: return
        val top = shown
        val text = render(s)
        val rejected = if (index != 0 && top != null) top else null
        finalize(text, " ", active = true, chosen = s, rejectedTop = rejected?.let { render(it) })
    }

    // ---------- ciclo de vida ----------
    /** Cursor movido o foco perdido: se conserva lo mostrado, se aprende pasivamente y se abandona la composición. */
    fun onExternalChange() {
        if (manual) { mbuf.setLength(0); return }
        if (!composing) return
        val text = render(shown ?: return)
        learn(shown!!, text, 1)
        sink.finishComposing()
        resetState()
    }

    fun reset() { resetState(); last = null; onSuggestions(emptyList()) }

    // ---------- internos ----------
    private fun resetState() { composing = false; shown = null; suggestions = emptyList(); capPositions.clear(); reopenedFrom = null; engine?.clear() }

    private fun capitalize(w: String) = if (w.isEmpty()) w else w.substring(0, 1).uppercase() + w.substring(1)

    private fun render(i: Interpretation): String {
        val words = i.words.mapIndexed { idx, w ->
            val span = i.spans[idx]
            val up = (idx == 0 && capFirst) || (!span.isEmpty() && capPositions.any { it in span })
            if (up) capitalize(w) else w
        }
        return words.joinToString(" ")
    }

    private fun render() {
        val e = engine ?: return
        val list = e.interpretations(3)
        if (list.isEmpty()) return
        suggestions = list; shown = list[0]
        sink.showComposing(render(list[0]))
        onSuggestions(list.map { render(it) })
    }

    private fun finalize(chosenText: String, trailing: String, active: Boolean, chosen: Interpretation? = null, rejectedTop: String? = null) {
        val e = engine ?: return
        val interp = chosen ?: shown ?: return
        val text = if (chosenText.isNotEmpty()) chosenText else render(interp)
        sink.commit(text + trailing)
        val letters = e.typed
        last = LastCommit(text + trailing, letters, context, capFirst, HashSet(capPositions), clock())
        learn(interp, text, if (active) LearningEngineWeights.ACTIVE else LearningEngineWeights.PASSIVE)
        val from = reopenedFrom
        if (from != null && !from.equals(text, ignoreCase = true)) learnerIfOn()?.correction(from, text, context)
        if (rejectedTop != null) learnerIfOn()?.correction(rejectedTop, text, context)
        composing = false; shown = null; suggestions = emptyList(); capPositions.clear(); reopenedFrom = null
        e.clear()
        onSuggestions(emptyList())
    }

    private fun learnerIfOn(): Learner? = learner?.takeIf { policy.learn && it.enabled }

    private fun learn(interp: Interpretation, text: String, weight: Int) {
        learnerIfOn()?.observe(context, interp.words, weight)
    }

    /** Si la composición es larga, confirma las palabras más antiguas (ya estables) y sigue con el resto. */
    private fun stabilizeIfLong(e: PredictionEngine) {
        if (e.typed.length < STABILIZE_LETTERS) return
        val top = e.best() ?: return
        if (top.words.size < 4) return
        val k = top.words.size - 2
        val prefixWords = top.words.take(k)
        val prefixText = render(Interpretation(top.words.take(k), top.spans.take(k), top.score))
        val consumed = e.commitPrefix(k)
        if (consumed <= 0) return
        sink.commit("$prefixText ")
        learnerIfOn()?.observe(context, prefixWords, LearningEngineWeights.PASSIVE)
        context = prefixWords.last()
        val shifted = capPositions.filter { it >= consumed }.map { it - consumed }
        capPositions.clear(); capPositions.addAll(shifted)
        capFirst = false
    }
}

/** Pesos expuestos a la composición sin depender del módulo de aprendizaje. */
object LearningEngineWeights { const val PASSIVE = 1; const val ACTIVE = 3; const val MANUAL = 4 }
