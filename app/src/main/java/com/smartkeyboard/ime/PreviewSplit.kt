package com.smartkeyboard.ime

import com.smartkeyboard.engine.TextNorm

/** Separa el texto provisional en lo que tecleaste y lo que el motor predice a continuación («got» + «as»). */
object PreviewSplit {
    /**
     * Si [shown] empieza por las letras tecleadas (sin contar espacios, mayúsculas ni tildes) devuelve (tecleado, predicho);
     * si no (corrección, p. ej. «nace» → «hace»), todo se considera tecleado.
     */
    fun split(shown: String, typed: String): Pair<String, String> {
        val tk = TextNorm.key(typed.lowercase())
        if (tk.isEmpty()) return shown to ""
        var i = 0; var used = 0
        while (i < shown.length && used < tk.length) {
            val c = shown[i]
            if (c == ' ') { i++; continue }
            if (TextNorm.key(c.lowercaseChar()) != tk[used]) return shown to ""
            used++; i++
        }
        if (used < tk.length) return shown to ""
        return shown.substring(0, i) to shown.substring(i)
    }
}
