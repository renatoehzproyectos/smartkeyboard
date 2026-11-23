package com.smartkeyboard.ui

import android.content.Context
import android.graphics.Color

/** Paleta medida de la captura de referencia (teclado flotante marrón/arena). */
class KeyboardColors(
    val card: Int, val key: Int, val keyPressed: Int, val tan: Int, val tanPressed: Int,
    val text: Int, val onTan: Int, val hint: Int, val accent: Int, val pill: Int,
)

object ThemeManager {
    private val PALETTE = KeyboardColors(
        card = Color.parseColor("#2C271F"), key = Color.parseColor("#49443A"), keyPressed = Color.parseColor("#625C4E"),
        tan = Color.parseColor("#9E8F6E"), tanPressed = Color.parseColor("#B9A982"),
        text = Color.WHITE, onTan = Color.parseColor("#141414"), hint = Color.parseColor("#D1D0CF"),
        accent = Color.parseColor("#E3D3A8"), pill = Color.parseColor("#8E8A83"))

    @Suppress("UNUSED_PARAMETER")
    fun colors(c: Context): KeyboardColors = PALETTE
}
