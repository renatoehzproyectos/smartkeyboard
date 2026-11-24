package com.smartkeyboard.ui

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Modelo (sin Android) de la barra de vista previa: cada letra es un «glifo» con posición, opacidad y desplazamiento
 * propios que se suavizan hacia su destino. Al cambiar el texto se conservan los glifos comunes (no se redibujan),
 * los que sobran se desvanecen y los nuevos aparecen subiendo desde abajo. El desplazamiento y el cursor también
 * se deslizan, así el texto fluye en vez de saltar.
 */
class PreviewModel(private val widthOf: (Char) -> Float) {
    class Glyph(val ch: Char) {
        var tx = 0f; var x = 0f
        var alpha = 0f; var ta = 1f
        var dy = 0f; var tdy = 0f
        var comp = 0f; var tComp = 0f
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

    fun update(text: String, compLen: Int, nowMs: Long) {
        val n = text.length
        val same = live.size == n && live.indices.all { live[it].ch == text[it] }
        var k = 0
        var m = prefix(text, 0)
        if (!same && m < live.size) {
            // ¿Es el mismo texto desplazado a la izquierda (ventana de contexto)? Entonces los primeros glifos salen por la izquierda.
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

        val compStart = n - compLen.coerceIn(0, n)
        var x = 0f
        for ((i, g) in live.withIndex()) {
            g.tComp = if (i >= compStart && compLen > 0) 1f else 0f
            g.tx = x; x += widthOf(g.ch)
        }
        for (g in fresh) { g.x = g.tx; g.comp = g.tComp }
        contentW = x
        if (compLen > 0 && compStart < live.size) { runLT = live[compStart].tx; runRT = contentW; runAT = 1f } else runAT = 0f
        if (live.isEmpty() && same) { /* nada */ }
        if (runA < 0.01f && runAT > 0f) { runL = runLT; runR = runRT }
    }

    private fun rate(dt: Float, tau: Float) = 1f - exp(-dt / tau)

    /** Avanza la animación [dt] segundos. */
    fun step(dt: Float) {
        val aPos = rate(dt, 0.060f); val aAlpha = rate(dt, 0.070f); val aComp = rate(dt, 0.140f)
        for (g in live) {
            g.x += (g.tx - g.x) * aPos; g.dy += (g.tdy - g.dy) * aPos
            g.alpha += (g.ta - g.alpha) * aAlpha; g.comp += (g.tComp - g.comp) * aComp
        }
        val it = dying.iterator()
        while (it.hasNext()) {
            val g = it.next()
            g.x += (g.tx - g.x) * aPos; g.dy += (g.tdy - g.dy) * aPos; g.alpha += (g.ta - g.alpha) * aAlpha
            if (g.alpha < 0.03f) it.remove()
        }
        caretX += (contentW - caretX) * rate(dt, 0.050f)
        val tScroll = max(0f, contentW + CARET_PAD - innerW)
        scroll += (tScroll - scroll) * rate(dt, 0.110f)
        val aRun = rate(dt, 0.075f)
        runL += (runLT - runL) * aRun; runR += (runRT - runR) * aRun; runA += (runAT - runA) * aRun
    }

    fun isSettled(): Boolean {
        if (dying.isNotEmpty()) return false
        if (kotlin.math.abs(caretX - contentW) > 0.3f) return false
        if (kotlin.math.abs(scroll - max(0f, contentW + CARET_PAD - innerW)) > 0.3f) return false
        if (kotlin.math.abs(runA - runAT) > 0.01f) return false
        return live.all { kotlin.math.abs(it.x - it.tx) < 0.3f && it.alpha > 0.97f && kotlin.math.abs(it.dy - it.tdy) < 0.3f && kotlin.math.abs(it.comp - it.tComp) < 0.02f }
    }

    companion object { const val CARET_PAD = 14f }
}
