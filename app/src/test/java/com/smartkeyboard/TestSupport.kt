package com.smartkeyboard

import com.smartkeyboard.engine.PredictionEngine
import com.smartkeyboard.ime.InputSink
import java.io.File

object TestSupport {
    private fun asset(n: String) = File("src/main/assets/$n").inputStream()
    fun newEngine(): PredictionEngine = PredictionEngine.load(asset("es_words.txt"), asset("es_bigrams.txt"))
    val shared: PredictionEngine by lazy { newEngine() }

    fun interpret(e: PredictionEngine, input: String, context: String? = null): String {
        e.setContext(context)
        for (c in input) e.type(c)
        return e.best()?.text ?: ""
    }
}

/** Campo de texto simulado: texto confirmado + región de composición. */
class FakeSink(var autoCap: Boolean = true) : InputSink {
    val committed = StringBuilder()
    var composing = ""
    val text get() = committed.toString() + composing
    var enters = 0
    var rawBackspaces = 0
    override fun showComposing(text: String) { composing = text }
    override fun commit(text: String) { committed.append(text); composing = "" }
    override fun finishComposing() { committed.append(composing); composing = "" }
    override fun deleteBefore(n: Int) { committed.setLength(maxOf(0, committed.length - n)) }
    override fun textBefore(n: Int) = text.takeLast(n)
    override fun sendBackspace() { rawBackspaces++; if (composing.isEmpty() && committed.isNotEmpty()) committed.setLength(committed.length - 1) }
    override fun sendEnter() { enters++; committed.append('\n') }
    override fun autoCapitalize() = autoCap && committed.isEmpty()
}
