package com.smartkeyboard.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min

/** Dibuja la vista previa del texto («techo» del teclado) con cursor suave, letras que fluyen y la palabra en curso resaltada. */
class PreviewStrip(private val density: Float) {
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 21f * density
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val model = PreviewModel { c -> tp.measureText(c.toString()) }
    private val tmp = RectF()
    private var last = 0L
    private var visible = 0f
    private var targetVisible = 0f

    fun setAllowed(v: Boolean) { targetVisible = if (v) 1f else 0f }

    fun setText(text: String, compLen: Int, now: Long) {
        model.update(text, compLen, now)
        last = 0L // evita un salto de tiempo tras un periodo sin dibujar
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        val i = 1f - t
        return Color.rgb((Color.red(a) * i + Color.red(b) * t).toInt(), (Color.green(a) * i + Color.green(b) * t).toInt(), (Color.blue(a) * i + Color.blue(b) * t).toInt())
    }

    private fun withA(c: Int, a: Float) = (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255f).toInt() shl 24)

    /** Dibuja en [r]; devuelve true si hay que seguir pidiendo fotogramas (animación en curso o cursor parpadeando). */
    fun draw(c: Canvas, r: RectF, col: KeyboardColors, now: Long): Boolean {
        val dt = if (last == 0L) 0.016f else min((now - last) / 1000f, 0.05f)
        last = now
        visible += (targetVisible - visible) * (1f - exp(-dt / 0.12f))
        if (visible < 0.01f && targetVisible == 0f) return false

        val pad = 20f * density
        val left = r.left + pad; val right = r.right - pad
        model.innerW = right - left
        model.step(dt)

        val rad = r.height() / 2f
        fill.style = Paint.Style.FILL
        tmp.set(r.left, r.top + density, r.right, r.bottom + 2f * density)
        fill.color = withA(0x000000, 0.10f * visible); c.drawRoundRect(tmp, rad, rad, fill)   // sombra suave
        fill.color = withA(col.card, visible); c.drawRoundRect(r, rad, rad, fill)

        c.save(); c.clipRect(left, r.top, right, r.bottom)
        val cy = r.centerY()
        val base = cy - (tp.ascent() + tp.descent()) / 2f
        val h = r.height()

        if (model.runA > 0.01f) {   // «ficha» de la palabra en curso
            tmp.set(left + model.runL - model.scroll - 8f * density, cy - 0.30f * h, left + model.runR - model.scroll + 8f * density, cy + 0.30f * h)
            fill.color = withA(col.accent, 0.16f * model.runA * visible)
            c.drawRoundRect(tmp, 0.30f * h, 0.30f * h, fill)
        }

        val edge = 30f * density
        fun glyph(g: PreviewModel.Glyph) {
            val gx = left + g.x - model.scroll
            val fadeL = ((gx - left) / edge).coerceIn(0f, 1f)
            val a = g.alpha * fadeL * visible
            if (a < 0.01f) return
            tp.color = withA(mix(col.hint, col.text, g.comp), a)
            c.drawText(g.ch.toString(), gx, base + g.dy * density / 2f, tp)
        }
        for (g in model.dying) glyph(g)
        for (g in model.live) glyph(g)

        val idle = now - model.lastChangeMs
        val blink = if (idle < 500L) 1f else 0.55f + 0.45f * cos(((idle - 500L) / 1100f) * 2f * PI.toFloat())
        val cx = left + model.caretX - model.scroll + 2f * density
        tmp.set(cx, cy - 0.28f * h, cx + 2.6f * density, cy + 0.28f * h)
        fill.color = withA(col.accent, blink * visible)
        c.drawRoundRect(tmp, 2f * density, 2f * density, fill)
        c.restore()

        return !model.isSettled() || kotlin.math.abs(visible - targetVisible) > 0.01f || idle < 8000L
    }
}
