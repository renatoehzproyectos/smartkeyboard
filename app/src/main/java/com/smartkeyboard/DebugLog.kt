package com.smartkeyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build

/** Registro de depuración en memoria (búfer circular). Solo se llena si el usuario lo activa; nunca sale del dispositivo salvo que lo copie. */
object DebugLog {
    private const val MAX = 3000
    private val lines = ArrayDeque<String>()
    private var t0 = System.currentTimeMillis()
    @Volatile var enabled = false

    @Synchronized fun add(msg: String) {
        if (!enabled) return
        if (lines.size >= MAX) lines.removeFirst()
        lines.addLast("%7d  %s".format(System.currentTimeMillis() - t0, msg))
    }

    @Synchronized fun clear() { lines.clear(); t0 = System.currentTimeMillis() }
    @Synchronized fun size() = lines.size

    @Synchronized fun dump(): String = buildString {
        append("SmartKeyboard debug log\n")
        append("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        append("Eventos: ${lines.size} (ms desde el inicio del registro)\n")
        append("Los campos protegidos (contraseñas, etc.) aparecen enmascarados con •\n\n")
        lines.forEach { append(it).append('\n') }
    }

    /** Copia el registro al portapapeles; devuelve el número de eventos. */
    fun copy(c: Context): Int {
        val cm = c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("SmartKeyboard log", dump()))
        return size()
    }
}
