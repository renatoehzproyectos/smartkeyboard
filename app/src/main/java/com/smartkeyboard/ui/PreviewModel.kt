package com.smartkeyboard.ui

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Modelo (sin Android) de la barra de vista previa. Cada letra es un «glifo» con posición, opacidad y énfasis propios
 * que se suavizan hacia su destino. Tres clases de texto: confirmado (énfasis medio), tecleado (pleno) y predicho (tenue).
 * Al cambiar el texto se conservan los glifos comunes, de modo que una letra predicha que tecleas pasa de tenue a plena
 * sin moverse; los que sobran se desvanecen y los nuevos suben desde abajo. La palabra en curso se centra en la barra.
 */
class PreviewModel(private val widthOf: (Char) -> Float) {
    class Glyph(val ch: Char) {
        var tx = 0f; var x = 0f
        var alpha = 0f; var ta = 1f
        var dy = 0f; var tdy = 0f
        var emph = EMPH_COMMITTED; var tEmph = EMPH_COMMITTED
    }

    val live = ArrayList<Glyph>()
    val dying = ArrayList<Glyph>()
    var contentW = 0f; private set
    var scroll = 0f; private set
    var caretX = 0f; private set
    var runL = 0f; private set
    var runR = 0f; private set
    var runA = 0f; private set
    var lastChangeMs = 0L; private set
    /** Ancho visible del texto (lo fija la vista). */
    var innerW = 1000f

    private var caretT = 0f
    private var focusT = 0f
    private var runLT = 0f; private var runRT = 0f; private var runAT = 0f

    private fun prefix(text: String, k: Int): Int {
        var m = 0
        while (k + m < live.size && m < text.length && live[k + m].ch == text[m]) m++
        return m
    }

    private fun die(g: Glyph, toLeft: Boolean) {
        g.ta = 0f
        if (toLeft) g.tx -= 22f else g.tdy = -12f
        dying.add(g)
    }

    /** [text] completo; [compStart, ghostStart) es lo tecleado de la palabra en curso y [ghostStart, n) lo predicho. */
    fun update(text: String, compStart: Int, ghostStart: Int, nowMs: Long) {
        val n = text.length
        val cs = compStart.coerceIn(0, n); val gs = ghostStart.coerceIn(cs, n)
        val same = live.size == n && live.indices.all { live[it].ch == text[it] }
        var k = 0
        var m = prefix(text, 0)
        if (!same && m < live.size) {
            // ¿Mismo texto desplazado a la izquierda (ventana de contexto)? Entonces los primeros glifos salen por la izquierda.
            for (kk in 1..min(live.size, 40)) {
                val mm = prefix(text, kk)
                if (mm == live.size - kk && mm >= 1) { k = kk; m = mm; break }
            }
        }
        if (!same) lastChangeMs = nowMs
        val kept = ArrayList<Glyph>(n)
        for ((i, g) in live.withIndex()) when {
            i < k -> die(g, toLeft = true)
            i < k + m -> kept.add(g)
            else -> die(g, toLeft = false)
        }
        live.clear(); live.addAll(kept)
        val fresh = ArrayList<Glyph>()
        for (i in m until n) Glyph(text[i]).also { it.dy = 14f; live.add(it); fresh.add(it) }

        var x = 0f
        for ((i, g) in live.withIndex()) {
            g.tEmph = when { i < cs -> EMPH_COMMITTED; i < gs -> EMPH_TYPED; else -> EMPH_GHOST }
            g.tx = x; x += widthOf(g.ch)
        }
        for (g in fresh) { g.x = g.tx; g.emph = g.tEmph }
        contentW = x
        caretT = if (gs < n) live[gs].tx else contentW          // el cursor va entre lo tecleado y lo predicho: got|as
        if (cs < n) { runLT = live[cs].tx; runRT = contentW; runAT = 1f; focusT = (runLT + runRT) / 2f }
        else { runAT = 0f; focusT = caretT }
        if (runA < 0.01f && runAT > 0f) { runL = runLT; runR = runRT }
    }

    private fun rate(dt: Float, tau: Float) = 1f - exp(-dt / tau)

    private fun scrollTarget() = focusT - innerW / 2f

    /** Avanza la animación [dt] segundos. */
    fun step(dt: Float) {
        val aPos = rate(dt, 0.060f); val aAlpha = rate(dt, 0.070f); val aEmph = rate(dt, 0.140f)
        for (g in live) {
            g.x += (g.tx - g.x) * aPos; g.dy += (g.tdy - g.dy) * aPos
            g.alpha += (g.ta - g.alpha) * aAlpha; g.emph += (g.tEmph - g.emph) * aEmph
        }
        val it = dying.iterator()
        while (it.hasNext()) {
            val g = it.next()
            g.x += (g.tx - g.x) * aPos; g.dy += (g.tdy - g.dy) * aPos; g.alpha += (g.ta - g.alpha) * aAlpha
            if (g.alpha < 0.03f) it.remove()
        }
        caretX += (caretT - caretX) * rate(dt, 0.050f)
        scroll += (scrollTarget() - scroll) * rate(dt, 0.110f)
        val aRun = rate(dt, 0.075f)
        runL += (runLT - runL) * aRun; runR += (runRT - runR) * aRun; runA += (runAT - runA) * aRun
    }

    fun isSettled(): Boolean {
        if (dying.isNotEmpty()) return false
        if (abs(caretX - caretT) > 0.3f || abs(scroll - scrollTarget()) > 0.3f || abs(runA - runAT) > 0.01f) return false
        return live.all { abs(it.x - it.tx) < 0.3f && it.alpha > 0.97f && abs(it.dy - it.tdy) < 0.3f && abs(it.emph - it.tEmph) < 0.02f }
    }

    companion object {
        const val EMPH_COMMITTED = 0.70f
        const val EMPH_TYPED = 1.00f
        const val EMPH_GHOST = 0.30f
    }
}
