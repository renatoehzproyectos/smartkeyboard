package com.smartkeyboard.engine

/** Evalúa la relación entre palabras vecinas: coherencia (C) y preferencia personal (U). */
class ContextAnalyzer(private val lm: LanguageModel, @Volatile var user: UserModel = NoUserModel) {
    fun coherence(prev: Word, w: Word): Float = lm.contextLift(prev, w)

    fun personal(prev: Word, w: Word): Float {
        val u = user
        var s = u.unigramBoost(w.lower)
        if (prev.gid != Word.BOS) s += u.bigramBoost(prev.lower, w.lower)
        return s
    }
}
