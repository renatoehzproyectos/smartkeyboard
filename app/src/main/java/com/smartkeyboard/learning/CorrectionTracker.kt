package com.smartkeyboard.learning

/** Registra correcciones y rechazos explícitos del usuario. */
class CorrectionTracker(private val store: UserStore, private val io: (() -> Unit) -> Unit) {
    @Volatile var total: Int = 0; private set
    fun init(n: Int) { total = n }
    fun record(typed: String, chosen: String) {
        if (typed.equals(chosen, ignoreCase = true)) return
        total++
        io { store.bumpCorrection(typed.lowercase(), chosen.lowercase(), 1) }
    }
    fun reset() { total = 0 }
}
