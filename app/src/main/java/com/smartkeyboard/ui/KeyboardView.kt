package com.smartkeyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/**
 * Teclado flotante dibujado en Canvas. La geometría (tarjeta, teclas, barra de herramientas y barra inferior)
 * está medida sobre la captura de referencia de 1220×2712 px y se escala con la pantalla:
 * tarjeta en x=84, y=590, 1003×724 px  →  6,89 % / 21,76 % de la pantalla, 82,2 % × 26,7 %.
 */
class KeyboardView(context: Context) : View(context) {
    interface Listener {
        fun onChar(c: Char, shifted: Boolean)
        fun onText(text: String)
        fun onSpace()
        fun onBackspace()
        fun onEnter()
        fun onOpenSettings()
        fun onHide()
        fun onSwitchKeyboard()
        fun onSuggestion(index: Int)
        fun onToggleManual()
        fun onDebug()
    }

    var listener: Listener? = null
    var haptics = true
    var debugOn = false
        set(v) { field = v; invalidate() }
    var manualMode = false
        set(v) { field = v; invalidate() }
    var enterLabel: String? = null
        set(v) { field = v; invalidate() }

    /** Rectángulo de la tarjeta en coordenadas de la vista (para la región táctil de la ventana). */
    val cardRect = RectF()

    private enum class Layer { LETTERS, SYMBOLS, SYMBOLS2, EMOJI }
    private enum class Shift { OFF, ONCE, LOCK }
    private enum class Style { DARK, TAN }
    private enum class Kind { CHAR, TEXT, SHIFT, DEL, SPACE, ENTER, TO_SYM, TO_SYM2, TO_ABC, EMOJI, COMMA, PERIOD }

    private class Key(val kind: Kind, val label: String, val x: Float, val y: Float, val w: Float, val h: Float,
                      val style: Style = Style.DARK, val radius: Float = 20f, val hint: String? = null)

    private companion object {
        const val REF_W = 1003f; const val REF_H = 724f
        const val SCREEN_W = 1220f; const val SCREEN_H = 2712f
        const val CARD_LEFT = 84f; const val CARD_TOP = 590f
        const val PITCH = 96.9f; const val KW = 85f; const val X0 = 23f
        val ROW_Y = floatArrayOf(134f, 255f, 378f, 501f); val ROW_H = floatArrayOf(97f, 99f, 99f, 99f)
        const val TOOLBAR_H = 120f
        // Centros de los iconos de la barra superior (px de referencia).
        val TOOL_X = floatArrayOf(64f, 209f, 355f, 500f, 646f, 792f); const val TOOL_Y = 68f
        const val MIC_X = 938.5f; const val MIC_R = 38.5f
        const val NAV_Y = 666f; const val CHEVRON_X = 81f; const val GLOBE_X = 921f; const val PILL_X = 501f
    }

    private fun letters(s: String, row: Int, x0: Float, hints: String? = null): List<Key> =
        s.mapIndexed { i, c -> Key(Kind.CHAR, c.toString(), x0 + i * PITCH, ROW_Y[row], KW, ROW_H[row], hint = hints?.getOrNull(i)?.toString()) }

    private fun emojis(s: List<String>, row: Int, x0: Float) =
        s.mapIndexed { i, e -> Key(Kind.TEXT, e, x0 + i * PITCH, ROW_Y[row], KW, ROW_H[row]) }

    private fun bottomRow(left: Key) = listOf(
        left,
        Key(Kind.COMMA, ",", 167f, ROW_Y[3], 85f, ROW_H[3], Style.TAN),
        Key(Kind.EMOJI, "", 264f, ROW_Y[3], 85f, ROW_H[3]),
        Key(Kind.SPACE, "", 361f, ROW_Y[3], 377f, ROW_H[3]),
        Key(Kind.PERIOD, ".", 749f, ROW_Y[3], 86f, ROW_H[3], Style.TAN),
        Key(Kind.ENTER, "", 846f, ROW_Y[3], 135f, ROW_H[3], Style.TAN, ROW_H[3] / 2))

    private fun pill(kind: Kind, label: String) = Key(kind, label, 23f, ROW_Y[3], 133f, ROW_H[3], Style.TAN, ROW_H[3] / 2)
    private fun side(kind: Kind, label: String, x: Float, w: Float) = Key(kind, label, x, ROW_Y[2], w, ROW_H[2], Style.TAN, 26f)
    private val del = side(Kind.DEL, "", 846f, 135f)

    private val EMOJI1 = "😀😂😍🥰😎🤔😢😡👍🙏".codePoints().toArray().map { String(Character.toChars(it)) }
    private val EMOJI2 = listOf("👏", "🔥", "❤️", "🎉", "✨", "💯", "😅", "🙌", "😉", "🤗")
    private val EMOJI3 = listOf("😴", "🤝", "👌", "💪", "🌹", "☕", "🍕")

    private val layouts: Map<Layer, List<Key>> = mapOf(
        Layer.LETTERS to (letters("qwertyuiop", 0, X0, "1234567890") + letters("asdfghjklñ", 1, X0) +
            side(Kind.SHIFT, "", 23f, 133f) + letters("zxcvbnm", 2, 168f) + del + bottomRow(pill(Kind.TO_SYM, "?123"))),
        Layer.SYMBOLS to (letters("1234567890", 0, X0) + letters("@#$%&-+()/", 1, X0) +
            side(Kind.TO_SYM2, "=\\<", 23f, 133f) + letters("*\"':;!?", 2, 168f) + del + bottomRow(pill(Kind.TO_ABC, "ABC"))),
        Layer.SYMBOLS2 to (letters("[]{}#%^*+=", 0, X0) + letters("_\\|~<>€£¥•", 1, X0) +
            side(Kind.TO_SYM, "?123", 23f, 133f) + letters("¿¡.,?!'", 2, 168f) + del + bottomRow(pill(Kind.TO_ABC, "ABC"))),
        Layer.EMOJI to (emojis(EMOJI1, 0, X0) + emojis(EMOJI2, 1, X0) +
            side(Kind.TO_ABC, "ABC", 23f, 133f) + emojis(EMOJI3, 2, 168f) + del + bottomRow(pill(Kind.TO_SYM, "?123"))),
    )

    private var layer = Layer.LETTERS
    private var shift = Shift.OFF
    private var autoShift = false
    private var overrideOff = false
    private var lastShiftTap = 0L
    private val pressed = HashMap<Int, Any>() // pointerId -> Key | "tool:n" | "nav:n" | "sug:n"
    private var colors = ThemeManager.colors(context)
    private var suggestions: List<String> = emptyList()

    private val density = resources.displayMetrics.density
    private val strip = PreviewStrip(density)
    private val stripRect = RectF()
    private val stripH get() = 54f * density
    private val stripGap get() = 8f * density

    /** Vista previa sobre el teclado: texto reciente + palabra en curso ([composingLen] últimas letras). */
    fun setPreview(text: String, composingLen: Int, allowed: Boolean) {
        strip.setAllowed(allowed)
        strip.setText(if (allowed) text else "", composingLen, SystemClock.uptimeMillis())
        invalidate()
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val handler = Handler(Looper.getMainLooper())
    private var repeating = false
    private val repeat = object : Runnable { override fun run() { if (repeating) { listener?.onBackspace(); handler.postDelayed(this, 55) } } }

    private var sx = 1f; private var sy = 1f
    private var cardTopInView = 0f
    private var extra = 0; private var grew = 0

    private val shifted get() = shift != Shift.OFF || (autoShift && !overrideOff)

    fun applyTheme() { colors = ThemeManager.colors(context); invalidate() }
    fun setSuggestions(list: List<String>) { suggestions = list.take(3); invalidate() }
    fun setAutoShift(v: Boolean) { if (autoShift != v) { autoShift = v; if (!v) overrideOff = false; invalidate() } }
    fun resetState() { layer = Layer.LETTERS; shift = Shift.OFF; overrideOff = false; pressed.clear(); repeating = false; invalidate() }

    // ---------------- geometría ----------------
    private fun screen(): Pair<Int, Int> {
        val wm = context.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        else DisplayMetrics().also { @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
    }

    /** Posición y tamaño de la tarjeta en pantalla (px): mismas proporciones que la captura. */
    private fun cardOnScreen(): RectF {
        val (w, h) = screen()
        return if (w <= h) {
            val l = CARD_LEFT / SCREEN_W * w; val t = CARD_TOP / SCREEN_H * h
            RectF(l, t, l + REF_W / SCREEN_W * w, t + REF_H / SCREEN_H * h)
        } else { // horizontal: misma proporción de tarjeta, centrada y apoyada abajo
            val ch = 0.58f * h; val cw = ch * REF_W / REF_H
            val l = (w - cw) / 2; val t = h - ch - 0.04f * h
            RectF(l, t, l + cw, t + ch)
        }
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val (_, sh) = screen()
        val top = cardOnScreen().top
        setMeasuredDimension(MeasureSpec.getSize(wSpec), (sh - top + extra + stripH + stripGap).toInt())
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        post { alignToScreen() }
    }

    /** La ventana del IME se ancla abajo: se mide dónde queda y se desplaza la tarjeta hasta su y de referencia. */
    private fun alignToScreen() {
        val loc = IntArray(2); getLocationOnScreen(loc)
        val want = cardOnScreen().top
        val off = want - loc[1]
        if (off < -1f && grew < 3) { extra += ceil(-off).toInt(); grew++; requestLayout(); return }
        cardTopInView = off.coerceAtLeast(0f)
        updateCard(); invalidate()
    }

    private fun updateCard() {
        val c = cardOnScreen()
        cardRect.set(c.left, cardTopInView, c.right, cardTopInView + c.height())
        sx = c.width() / REF_W; sy = c.height() / REF_H
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { updateCard() }

    // ---------------- dibujo ----------------
    override fun onDraw(canvas: Canvas) {
        updateCard()
        canvas.save()
        canvas.translate(cardRect.left, cardRect.top); canvas.scale(sx, sy)
        paint.style = Paint.Style.FILL; paint.color = colors.card
        canvas.drawRoundRect(0f, 0f, REF_W, REF_H, 46f, 46f, paint)
        drawToolbar(canvas)
        for (k in layouts.getValue(layer)) drawKey(canvas, k)
        drawNav(canvas)
        canvas.restore()
        val bottom = (cardRect.top - stripGap).coerceAtLeast(stripH)
        stripRect.set(cardRect.left, bottom - stripH, cardRect.right, bottom)
        if (strip.draw(canvas, stripRect, colors, SystemClock.uptimeMillis())) {
            postInvalidateOnAnimation(stripRect.left.toInt() - 4, stripRect.top.toInt() - 4, stripRect.right.toInt() + 4, stripRect.bottom.toInt() + 8)
        }
    }

    private fun txt(c: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.CENTER) {
        paint.style = Paint.Style.FILL; paint.color = color; paint.textSize = size; paint.textAlign = align
        paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        c.drawText(s, cx, cy - (paint.ascent() + paint.descent()) / 2, paint)
    }

    private fun strokeStyle(color: Int, w: Float) { stroke.color = color; stroke.strokeWidth = w }

    private fun drawKey(c: Canvas, k: Key) {
        val down = pressed.containsValue(k)
        val tan = k.style == Style.TAN
        paint.style = Paint.Style.FILL
        paint.color = if (tan) (if (down) colors.tanPressed else colors.tan) else (if (down) colors.keyPressed else colors.key)
        c.drawRoundRect(k.x, k.y, k.x + k.w, k.y + k.h, k.radius, k.radius, paint)
        val cx = k.x + k.w / 2; val cy = k.y + k.h / 2
        val ink = if (tan) colors.onTan else colors.text
        when (k.kind) {
            Kind.CHAR -> {
                txt(c, if (layer == Layer.LETTERS && shifted) k.label.uppercase() else k.label, cx, cy, 58f, colors.text)
                k.hint?.let { txt(c, it, k.x + k.w - 9f, k.y + 20f, 27f, colors.hint, align = Paint.Align.RIGHT) }
            }
            Kind.TEXT -> txt(c, k.label, cx, cy, 54f, colors.text)
            Kind.COMMA -> txt(c, ",", cx, cy + 14f, 58f, ink)
            Kind.PERIOD -> txt(c, ".", cx, cy + 14f, 58f, ink)
            Kind.TO_SYM, Kind.TO_SYM2, Kind.TO_ABC -> txt(c, k.label, cx, cy, if (k.label.length > 3) 44f else 46f, ink)
            Kind.SPACE -> txt(c, "Español", cx, cy, 36f, colors.text)
            Kind.SHIFT -> drawShift(c, cx, cy, ink)
            Kind.DEL -> drawBackspace(c, cx, cy, ink)
            Kind.ENTER -> {
                val l = enterLabel
                if (l != null) txt(c, l, cx, cy, 40f, ink, bold = true) else drawEnter(c, cx, cy, ink)
            }
            Kind.EMOJI -> drawSmiley(c, cx, cy, colors.text)
        }
    }

    private fun drawShift(c: Canvas, cx: Float, cy: Float, ink: Int) {
        val p = Path().apply {
            moveTo(cx, cy - 24f); lineTo(cx + 26f, cy + 2f); lineTo(cx + 11f, cy + 2f); lineTo(cx + 11f, cy + 24f)
            lineTo(cx - 11f, cy + 24f); lineTo(cx - 11f, cy + 2f); lineTo(cx - 26f, cy + 2f); close()
        }
        if (shifted) { paint.style = Paint.Style.FILL; paint.color = ink; c.drawPath(p, paint) }
        else { strokeStyle(ink, 5f); c.drawPath(p, stroke) }
        if (shift == Shift.LOCK) { paint.style = Paint.Style.FILL; paint.color = colors.tan; c.drawRect(cx - 8f, cy + 8f, cx + 8f, cy + 15f, paint) }
    }

    private fun drawBackspace(c: Canvas, cx: Float, cy: Float, ink: Int) {
        val p = Path().apply {
            moveTo(cx - 30f, cy); lineTo(cx - 12f, cy - 22f); lineTo(cx + 30f, cy - 22f)
            lineTo(cx + 30f, cy + 22f); lineTo(cx - 12f, cy + 22f); close()
        }
        strokeStyle(ink, 5f); c.drawPath(p, stroke)
        c.drawLine(cx - 2f, cy - 9f, cx + 16f, cy + 9f, stroke); c.drawLine(cx + 16f, cy - 9f, cx - 2f, cy + 9f, stroke)
    }

    private fun drawEnter(c: Canvas, cx: Float, cy: Float, ink: Int) {
        strokeStyle(ink, 5f)
        val p = Path().apply { moveTo(cx + 26f, cy - 18f); lineTo(cx + 26f, cy + 2f); lineTo(cx - 24f, cy + 2f) }
        c.drawPath(p, stroke)
        val a = Path().apply { moveTo(cx - 10f, cy - 12f); lineTo(cx - 24f, cy + 2f); lineTo(cx - 10f, cy + 16f) }
        c.drawPath(a, stroke)
    }

    private fun drawSmiley(c: Canvas, cx: Float, cy: Float, color: Int) {
        strokeStyle(color, 4.5f); c.drawCircle(cx, cy, 21f, stroke)
        paint.style = Paint.Style.FILL; paint.color = color
        c.drawCircle(cx - 8f, cy - 6f, 2.8f, paint); c.drawCircle(cx + 8f, cy - 6f, 2.8f, paint)
        c.drawArc(RectF(cx - 11f, cy - 8f, cx + 11f, cy + 11f), 20f, 140f, false, stroke)
    }

    private fun drawToolbar(c: Canvas) {
        if (suggestions.isNotEmpty()) {
            val colW = (REF_W - 40f) / 3
            suggestions.forEachIndexed { i, s ->
                val cx = 20f + colW * i + colW / 2
                if (pressed.containsValue("sug:$i")) { paint.style = Paint.Style.FILL; paint.color = colors.key; c.drawRoundRect(20f + colW * i + 6f, 22f, 20f + colW * (i + 1) - 6f, 114f, 28f, 28f, paint) }
                val shown = if (s.length > 22) "…" + s.takeLast(21) else s
                txt(c, shown, cx, TOOL_Y + 14f, if (s.length > 16) 38f else 46f, if (i == 0) colors.accent else colors.text, bold = i == 0)
            }
            return
        }
        val w = colors.text
        // 1 · cuadrícula de apps
        paint.style = Paint.Style.FILL; paint.color = w
        for (dx in intArrayOf(-12, 12)) for (dy in intArrayOf(-12, 12))
            c.drawRoundRect(TOOL_X[0] + dx - 10f, TOOL_Y + dy - 10f, TOOL_X[0] + dx + 10f, TOOL_Y + dy + 10f, 5f, 5f, paint)
        // 2 · pegatina
        strokeStyle(w, 5f)
        c.drawRoundRect(TOOL_X[1] - 22f, TOOL_Y - 22f, TOOL_X[1] + 22f, TOOL_Y + 22f, 9f, 9f, stroke)
        paint.color = w; c.drawCircle(TOOL_X[1] - 8f, TOOL_Y - 5f, 3f, paint); c.drawCircle(TOOL_X[1] + 8f, TOOL_Y - 5f, 3f, paint)
        c.drawArc(RectF(TOOL_X[1] - 11f, TOOL_Y - 6f, TOOL_X[1] + 11f, TOOL_Y + 12f), 20f, 140f, false, stroke)
        // 3 · ajustes (engranaje)
        drawGear(c, TOOL_X[2], TOOL_Y, w)
        // 4 · modo manual (lápiz): escribe sin predicción y aprende lo que tecleas
        val px = TOOL_X[3]
        if (manualMode) { paint.style = Paint.Style.FILL; paint.color = colors.accent; c.drawCircle(px, TOOL_Y, 40f, paint) }
        val pen = if (manualMode) colors.card else w
        strokeStyle(pen, 6f)
        c.drawLine(px - 20f, TOOL_Y + 20f, px + 16f, TOOL_Y - 16f, stroke)
        c.drawLine(px - 24f, TOOL_Y + 24f, px - 12f, TOOL_Y + 22f, stroke)
        c.drawLine(px - 24f, TOOL_Y + 24f, px - 22f, TOOL_Y + 12f, stroke)
        // 5 · depuración (bicho): toca para iniciar el registro; con el registro activo, toca para copiarlo
        val bx = TOOL_X[4]
        paint.style = Paint.Style.FILL; paint.color = w
        c.drawOval(RectF(bx - 14f, TOOL_Y - 14f, bx + 14f, TOOL_Y + 24f), paint)
        c.drawCircle(bx, TOOL_Y - 17f, 9f, paint)
        strokeStyle(w, 4f)
        for (dy in intArrayOf(-6, 6, 18)) { c.drawLine(bx - 14f, TOOL_Y + dy, bx - 26f, TOOL_Y + dy - 5f, stroke); c.drawLine(bx + 14f, TOOL_Y + dy, bx + 26f, TOOL_Y + dy - 5f, stroke) }
        if (debugOn) { paint.color = 0xFFE53935.toInt(); c.drawCircle(bx + 24f, TOOL_Y - 24f, 11f, paint) }
        // 6 · paleta
        paint.style = Paint.Style.FILL; paint.color = w; c.drawCircle(TOOL_X[5], TOOL_Y, 24f, paint)
        paint.color = colors.card
        c.drawCircle(TOOL_X[5] - 9f, TOOL_Y - 8f, 4f, paint); c.drawCircle(TOOL_X[5] + 4f, TOOL_Y - 13f, 4f, paint)
        c.drawCircle(TOOL_X[5] + 14f, TOOL_Y - 3f, 4f, paint); c.drawCircle(TOOL_X[5] - 14f, TOOL_Y + 5f, 4f, paint)
        c.drawCircle(TOOL_X[5] + 8f, TOOL_Y + 14f, 7f, paint)
        // 7 · micrófono
        paint.color = if (pressed.containsValue("tool:6")) colors.keyPressed else colors.key
        c.drawCircle(MIC_X, TOOL_Y, MIC_R, paint)
        paint.color = w; c.drawRoundRect(MIC_X - 8f, TOOL_Y - 22f, MIC_X + 8f, TOOL_Y + 6f, 8f, 8f, paint)
        strokeStyle(w, 4f); c.drawArc(RectF(MIC_X - 15f, TOOL_Y - 12f, MIC_X + 15f, TOOL_Y + 14f), 0f, 180f, false, stroke)
        c.drawLine(MIC_X, TOOL_Y + 14f, MIC_X, TOOL_Y + 22f, stroke)
    }

    private fun drawGear(c: Canvas, cx: Float, cy: Float, color: Int) {
        paint.style = Paint.Style.FILL; paint.color = color
        c.save(); c.translate(cx, cy)
        for (i in 0 until 8) { c.drawRoundRect(-6f, -25f, 6f, -12f, 3f, 3f, paint); c.rotate(45f) }
        c.drawCircle(0f, 0f, 19f, paint)
        paint.color = colors.card; c.drawCircle(0f, 0f, 8f, paint)
        c.restore()
    }

    private fun drawSpark(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val p = Path().apply {
            moveTo(cx, cy - r); quadTo(cx + r * 0.15f, cy - r * 0.15f, cx + r, cy); quadTo(cx + r * 0.15f, cy + r * 0.15f, cx, cy + r)
            quadTo(cx - r * 0.15f, cy + r * 0.15f, cx - r, cy); quadTo(cx - r * 0.15f, cy - r * 0.15f, cx, cy - r); close()
        }
        paint.style = Paint.Style.FILL; paint.color = color; c.drawPath(p, paint)
    }

    private fun drawNav(c: Canvas) {
        val g = colors.hint
        strokeStyle(g, 5f)
        val p = Path().apply { moveTo(CHEVRON_X - 14f, NAV_Y - 6f); lineTo(CHEVRON_X, NAV_Y + 8f); lineTo(CHEVRON_X + 14f, NAV_Y - 6f) }
        c.drawPath(p, stroke)
        paint.style = Paint.Style.FILL; paint.color = colors.pill
        c.drawRoundRect(PILL_X - 86f, NAV_Y - 5f, PILL_X + 86f, NAV_Y + 5f, 5f, 5f, paint)
        strokeStyle(g, 4f)
        c.drawCircle(GLOBE_X, NAV_Y, 23f, stroke)
        c.drawOval(RectF(GLOBE_X - 10f, NAV_Y - 23f, GLOBE_X + 10f, NAV_Y + 23f), stroke)
        c.drawLine(GLOBE_X - 23f, NAV_Y, GLOBE_X + 23f, NAV_Y, stroke)
        c.drawLine(GLOBE_X - 20f, NAV_Y - 11f, GLOBE_X + 20f, NAV_Y - 11f, stroke)
        c.drawLine(GLOBE_X - 20f, NAV_Y + 11f, GLOBE_X + 20f, NAV_Y + 11f, stroke)
    }

    // ---------------- táctil ----------------
    private fun hit(x: Float, y: Float): Any? {
        val rx = (x - cardRect.left) / sx; val ry = (y - cardRect.top) / sy
        if (rx < 0 || ry < 0 || rx > REF_W || ry > REF_H) return null
        if (ry < TOOLBAR_H) {
            if (suggestions.isNotEmpty()) return "sug:${(((rx - 20f) / ((REF_W - 40f) / 3)).toInt()).coerceIn(0, 2)}"
            val all = TOOL_X + MIC_X
            var best = -1; var bd = Float.MAX_VALUE
            all.forEachIndexed { i, cx -> val d = abs(cx - rx); if (d < bd) { bd = d; best = i } }
            return if (bd < 70f) "tool:$best" else null
        }
        if (ry > 625f) return when { rx < 170f -> "nav:0"; rx > 840f -> "nav:2"; else -> null }
        var best: Key? = null; var bd = Float.MAX_VALUE
        for (k in layouts.getValue(layer)) {
            val dx = maxOf(k.x - rx, 0f, rx - (k.x + k.w)); val dy = maxOf(k.y - ry, 0f, ry - (k.y + k.h))
            val d = dx * dx + dy * dy
            if (d < bd) { bd = d; best = k }
        }
        return if (bd < 20f * 20f) best else null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val t = hit(e.getX(i), e.getY(i))
                if (t != null) {
                    pressed[e.getPointerId(i)] = t
                    if (haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    fire(t)
                    if (t is Key && t.kind == Kind.DEL) { repeating = true; handler.postDelayed(repeat, 380) }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val t = pressed.remove(e.getPointerId(e.actionIndex))
                if (t is Key && t.kind == Kind.DEL) { repeating = false; handler.removeCallbacks(repeat) }
            }
            MotionEvent.ACTION_CANCEL -> { pressed.clear(); repeating = false; handler.removeCallbacks(repeat) }
        }
        invalidate()
        return true
    }

    private fun fire(t: Any) {
        val l = listener ?: return
        if (t is String) {
            val n = t.substringAfter(':').toInt()
            when {
                t.startsWith("sug:") -> l.onSuggestion(n)
                t.startsWith("nav:") -> when (n) { 0 -> l.onHide(); 2 -> l.onSwitchKeyboard() }
                t.startsWith("tool:") -> when (n) { 2 -> l.onOpenSettings(); 3 -> l.onToggleManual(); 4 -> l.onDebug() } // resto de iconos: solo visuales
            }
            return
        }
        val k = t as Key
        when (k.kind) {
            Kind.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                if (shift == Shift.OFF && autoShift && !overrideOff) overrideOff = true
                else shift = when {
                    shift == Shift.ONCE && now - lastShiftTap < 350 -> Shift.LOCK
                    shift == Shift.OFF -> Shift.ONCE
                    else -> Shift.OFF
                }
                lastShiftTap = now
            }
            Kind.DEL -> l.onBackspace()
            Kind.SPACE -> l.onSpace()
            Kind.ENTER -> l.onEnter()
            Kind.TO_SYM -> layer = Layer.SYMBOLS
            Kind.TO_SYM2 -> layer = Layer.SYMBOLS2
            Kind.TO_ABC -> layer = Layer.LETTERS
            Kind.EMOJI -> layer = if (layer == Layer.EMOJI) Layer.LETTERS else Layer.EMOJI
            Kind.TEXT -> l.onText(k.label)
            Kind.COMMA, Kind.PERIOD -> l.onChar(k.label[0], false)
            Kind.CHAR -> {
                val up = layer == Layer.LETTERS && shifted
                val ch = k.label[0]
                l.onChar(if (up) ch.uppercaseChar() else ch, up)
                if (shift == Shift.ONCE) shift = Shift.OFF
                overrideOff = false
            }
        }
    }
}
