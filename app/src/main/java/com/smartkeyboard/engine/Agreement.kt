package com.smartkeyboard.engine

enum class Gender { M, F }

/** Rasgos gramaticales: género y número (true = plural). null = se desconoce / no aplica. */
class Feat(val gender: Gender?, val plural: Boolean?)

/**
 * Concordancia de género y número. Idea: «una manta» (no «mantas»), «mi novio es muy romántico» (no «romántica»).
 * Es heurística y barata (sin etiquetador gramatical): determinantes y pronombres por tabla, sustantivos por terminación
 * y excepciones, y los candidatos «variables» (adjetivos/participios) se reconocen por tener su pareja o/a y su plural en el léxico.
 */
object SpanishMorph {
    private val M = Gender.M; private val F = Gender.F
    private fun f(g: Gender?, p: Boolean?) = Feat(g, p)

    val DET: Map<String, Feat> = buildMap {
        for ((s, p) in listOf("el" to "los", "un" to "unos", "este" to "estos", "ese" to "esos", "aquel" to "aquellos", "algún" to "algunos",
            "ningún" to "ningunos", "mucho" to "muchos", "poco" to "pocos", "todo" to "todos", "otro" to "otros", "tanto" to "tantos",
            "varios" to "varios", "cierto" to "ciertos", "mismo" to "mismos", "propio" to "propios", "nuestro" to "nuestros", "vuestro" to "vuestros")) {
            put(s, f(M, false)); put(p, f(M, true))
        }
        for ((s, p) in listOf("la" to "las", "una" to "unas", "esta" to "estas", "esa" to "esas", "aquella" to "aquellas", "alguna" to "algunas",
            "ninguna" to "ningunas", "mucha" to "muchas", "poca" to "pocas", "toda" to "todas", "otra" to "otras", "tanta" to "tantas",
            "varias" to "varias", "cierta" to "ciertas", "misma" to "mismas", "propia" to "propias", "nuestra" to "nuestras", "vuestra" to "vuestras")) {
            put(s, f(F, false)); put(p, f(F, true))
        }
        put("varios", f(M, true)); put("varias", f(F, true))
        for (w in listOf("mi", "tu", "su")) put(w, f(null, false))
        for (w in listOf("mis", "tus", "sus", "estos", "los")) if (w !in this) put(w, f(null, true))
    }

    /** Pronombres sujeto: no concuerdan con la palabra siguiente (un verbo), pero sí con un adjetivo posterior. */
    val PRON: Map<String, Feat> = mapOf(
        "él" to f(M, false), "ella" to f(F, false), "ellos" to f(M, true), "ellas" to f(F, true),
        "nosotros" to f(M, true), "nosotras" to f(F, true), "vosotros" to f(M, true), "vosotras" to f(F, true),
    )

    private val M_IN_A = setOf("día", "mapa", "problema", "tema", "sistema", "programa", "idioma", "clima", "poema", "planeta", "drama", "diploma",
        "panorama", "esquema", "teorema", "cometa", "pijama", "sofá", "aroma", "fantasma", "dilema", "lema", "síntoma", "trauma", "karma", "tranvía", "guía")
    private val F_IN_O = setOf("mano", "foto", "moto", "radio", "libido", "nao")
    /** Sustantivos cuyo género no se deduce de la terminación. */
    private val NOUN_G: Map<String, Gender> = buildMap {
        for (w in "hombre padre señor doctor profesor jefe presidente actor rey príncipe tío abuelo amor dolor color calor sol lápiz pie mes país coche cuerpo corazón árbol mar pan lugar".split(' ')) put(w, M)
        for (w in "mujer madre señora actriz reina princesa flor luz noche calle clase leche tarde nube sal voz paz cruz piel ciudad canción razón sangre gente fuente muerte parte carne imagen".split(' ')) put(w, F)
    }
    private val F_END = listOf("ción", "sión", "dad", "tad", "tud", "umbre", "itis")
    private val F_END_PL = listOf("ciones", "siones", "dades", "tades", "tudes", "umbres")
    private val M_END = listOf("aje", "ismo", "miento", "ambre")

    fun detFeat(w: String): Feat? = DET[w]

    /** Rasgos de un sustantivo/adjetivo por morfología; null si no se puede decir (verbos, partículas, palabras cortas). */
    fun nounFeat(w: String): Feat? {
        if (w.length < 4 || w in DET || w in PRON) return null
        NOUN_G[w]?.let { return f(it, false) }
        if (w.endsWith("s")) {
            val sg = if (w.endsWith("es") && NOUN_G[w.dropLast(2)] != null) w.dropLast(2) else w.dropLast(1)
            NOUN_G[sg]?.let { return f(it, true) }
            for (e in F_END_PL) if (w.endsWith(e)) return f(F, true)
            for (e in M_END) if (sg.endsWith(e) || w.endsWith(e + "s")) return f(M, true)
            if (w.endsWith("as")) return f(if (sg in M_IN_A) M else F, true)
            if (w.endsWith("os")) return f(if (sg in F_IN_O) F else M, true)
            return null
        }
        for (e in F_END) if (w.endsWith(e)) return f(F, false)
        for (e in M_END) if (w.endsWith(e)) return f(M, false)
        if (w.endsWith("a")) return f(if (w in M_IN_A) M else F, false)
        if (w.endsWith("o")) return f(if (w in F_IN_O) F else M, false)
        return null
    }

    /** Controlador de concordancia vigente a partir de las últimas palabras de la frase (la más reciente con rasgos). */
    fun contextFrom(words: List<String>): Feat? {
        for (w in words.asReversed()) {
            val l = w.lowercase()
            PRON[l]?.let { return it }
            DET[l]?.takeIf { it.gender != null }?.let { return it }
            nounFeat(l)?.let { return it }
        }
        return null
    }
}

/** Puntúa cuánto concuerda un candidato con su contexto (determinante anterior, sustantivo anterior o discurso previo). */
class AgreementModel(private val lexicons: () -> List<Lexicon>) {
    @Volatile var discourse: Feat? = null
    private val feats = HashMap<Int, Feat?>()
    private val variable = HashMap<Int, Boolean>()

    var detGender = 1.4f; var detNumber = 1.8f
    var ctxGender = 1.3f; var ctxNumber = 1.0f

    fun invalidate() { feats.clear(); variable.clear() }

    private fun has(w: String) = lexicons().any { it.contains(w) }

    /** ¿Palabra con variación de género y número (adjetivo/participio/sustantivo regular) y no una forma verbal? */
    private fun isVariable(w: String): Boolean {
        val sg = when { w.endsWith("os") || w.endsWith("as") -> w.dropLast(1); else -> w }
        if (sg.length < 4) return false
        val stem = when { sg.endsWith("o") || sg.endsWith("a") -> sg.dropLast(1); else -> return false }
        if (!(has(stem + "o") && has(stem + "a"))) return false
        if (has(stem + "an")) return false                       // habla/hablan…: forma verbal
        return has(sg + "s") || w.endsWith("s")
    }

    private fun candFeat(c: Word): Feat? {
        if (c.gid < 0) return SpanishMorph.nounFeat(c.lower)
        return feats.getOrPut(c.gid) { SpanishMorph.nounFeat(c.lower) }
    }

    private fun isVar(c: Word): Boolean =
        if (c.gid < 0) isVariable(c.lower) else variable.getOrPut(c.gid) { isVariable(c.lower) }

    fun bonus(prev: Word, cand: Word): Float {
        val cf = candFeat(cand) ?: return 0f
        val pl = prev.lower
        var ctx: Feat?; var wg: Float; var wn: Float
        var reward: Float   // cuánto premia concordar (la discrepancia siempre castiga)
        val strict: Boolean
        val det = SpanishMorph.detFeat(pl)
        if (det != null) { ctx = det; wg = detGender; wn = detNumber; strict = false; reward = 1f }
        else {
            ctx = SpanishMorph.PRON[pl] ?: SpanishMorph.nounFeat(pl)?.takeIf { prev.gid != Word.BOS }
            strict = true; wg = ctxGender; wn = ctxNumber
            reward = 0f      // justo tras sujeto/sustantivo suele venir un verbo: solo se castiga lo discrepante
            if (ctx == null) { ctx = discourse; wg = ctxGender * 0.9f; wn = ctxNumber * 0.9f; reward = 0.6f }  // «es muy ___»: predicado
        }
        if (ctx == null) return 0f
        if (strict && !isVar(cand)) return 0f
        var s = 0f
        if (ctx.gender != null && cf.gender != null) s += if (ctx.gender == cf.gender) wg * reward else -wg
        if (ctx.plural != null && cf.plural != null) s += if (ctx.plural == cf.plural) wn * reward else -wn
        return s
    }
}
