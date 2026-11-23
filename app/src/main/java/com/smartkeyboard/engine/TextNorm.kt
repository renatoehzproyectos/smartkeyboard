package com.smartkeyboard.engine

/** Normalización de letras: minúsculas, sin acentos (la ñ se conserva). */
object TextNorm {
    fun key(c: Char): Char = when (val l = c.lowercaseChar()) {
        'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó' -> 'o'; 'ú', 'ü' -> 'u'
        else -> l
    }

    fun key(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(key(c))
        return sb.toString()
    }

    fun isTypable(c: Char): Boolean {
        val k = key(c)
        return k in 'a'..'z' || k == 'ñ'
    }

    private val NEIGHBORS = mapOf(
        'q' to "wa", 'w' to "qes", 'e' to "wrd", 'r' to "etf", 't' to "ryg", 'y' to "tuh", 'u' to "yij",
        'i' to "uok", 'o' to "ipl", 'p' to "oñ", 'a' to "qsz", 's' to "awdx", 'd' to "sefc", 'f' to "drgv",
        'g' to "fthb", 'h' to "gyjn", 'j' to "hukm", 'k' to "jil", 'l' to "koñ", 'ñ' to "lp", 'z' to "ax",
        'x' to "zsc", 'c' to "xdv", 'v' to "cfb", 'b' to "vgn", 'n' to "bhm", 'm' to "nj"
    )

    fun neighbors(c: Char): String = NEIGHBORS[c] ?: ""
}
