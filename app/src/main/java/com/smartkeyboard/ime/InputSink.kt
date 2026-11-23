package com.smartkeyboard.ime

/** Abstracción del campo de texto (InputConnection en producción; falso en pruebas). */
interface InputSink {
    fun showComposing(text: String)
    fun commit(text: String)
    fun finishComposing()
    fun deleteBefore(n: Int)
    fun textBefore(n: Int): String
    fun sendBackspace()
    fun sendEnter()
    fun autoCapitalize(): Boolean
}

/** Contrato del aprendizaje visto desde la composición. */
interface Learner {
    val enabled: Boolean
    fun observe(prev: String?, words: List<String>, weight: Int)
    fun correction(typed: String, chosen: String, prev: String?)
}

/** Qué se permite en el campo actual. */
class FieldPolicy(val predict: Boolean, val learn: Boolean) {
    companion object {
        private const val MASK_CLASS = 0xf; private const val MASK_VAR = 0xff0
        private const val CLASS_TEXT = 1
        private const val FLAG_NO_SUGGESTIONS = 0x80000
        private const val IME_NO_PERSONALIZED = 0x1000000
        private val BLOCKED_TEXT_VARIATIONS = setOf(0x10, 0x20, 0x80, 0x90, 0xd0, 0xe0, 0xb0) // URI, email, passwords, web email, web password, filter
        val FULL = FieldPolicy(true, true)
        val NONE = FieldPolicy(false, false)

        /** Contraseñas, códigos, números, email y URL: sin predicción ni aprendizaje. */
        fun classify(inputType: Int, imeOptions: Int): FieldPolicy {
            if (inputType and MASK_CLASS != CLASS_TEXT) return NONE
            if ((inputType and MASK_VAR) in BLOCKED_TEXT_VARIATIONS) return NONE
            if (inputType and FLAG_NO_SUGGESTIONS != 0) return NONE
            return FieldPolicy(predict = true, learn = imeOptions and IME_NO_PERSONALIZED == 0)
        }
    }
}
