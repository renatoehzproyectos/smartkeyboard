package com.smartkeyboard

import com.smartkeyboard.engine.EngineConfig
import com.smartkeyboard.engine.Gender
import com.smartkeyboard.engine.PredictionEngine
import com.smartkeyboard.engine.SpanishMorph
import com.smartkeyboard.ime.CompositionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AgreementTest {
    private fun asset(n: String) = File("src/main/assets/$n").inputStream()
    private val off by lazy { PredictionEngine.load(asset("es_words.txt"), asset("es_bigrams.txt"), EngineConfig(agreementWeight = 0f)) }

    private fun top(e: PredictionEngine, ctx: String, typed: String, n: Int = 4): List<String> {
        val words = ctx.split(' ')
        e.setDiscourse(words); e.setContext(words.last())
        for (c in typed) e.type(c)
        return e.interpretations(n).map { it.text }
    }

    @Test fun morphologyOfNounsAndDeterminers() {
        assertEquals(Gender.M, SpanishMorph.nounFeat("novio")!!.gender)
        assertEquals(true, SpanishMorph.nounFeat("mantas")!!.plural)
        assertEquals(Gender.F, SpanishMorph.nounFeat("mantas")!!.gender)
        assertEquals(Gender.M, SpanishMorph.nounFeat("problemas")!!.gender)      // excepción en -a
        assertEquals(Gender.F, SpanishMorph.nounFeat("mano")!!.gender)           // excepción en -o
        assertEquals(Gender.F, SpanishMorph.nounFeat("canciones")!!.gender)
        assertEquals(Gender.M, SpanishMorph.nounFeat("hombre")!!.gender)
        assertEquals(Gender.F, SpanishMorph.nounFeat("mujeres")!!.gender)
        assertEquals(null, SpanishMorph.nounFeat("la"))                           // determinantes y palabras cortas no cuentan
        assertEquals(Gender.F, SpanishMorph.detFeat("una")!!.gender)
        assertEquals(true, SpanishMorph.detFeat("los")!!.plural)
    }

    @Test fun discourseKeepsTheLastControllerAcrossWordsInBetween() {
        val f = SpanishMorph.contextFrom(listOf("mi", "novio", "es", "muy"))!!
        assertEquals(Gender.M, f.gender); assertEquals(false, f.plural)
        val g = SpanishMorph.contextFrom(listOf("las", "chicas", "son", "muy"))!!
        assertEquals(Gender.F, g.gender); assertEquals(true, g.plural)
    }

    @Test fun recentWordsStopAtTheStartOfTheSentence() {
        assertEquals(listOf("mi", "novio", "es", "muy"), CompositionManager.recentWords("Hola. Mi novio es muy "))
    }

    @Test fun determinerFixesGenderAndNumberOfTheNoun() {
        val e = TestSupport.newEngine()
        assertEquals("manta", top(e, "una", "mant")[0])                      // no «mantas»/«mantener»
        assertEquals("gatos", top(e, "los", "gat")[0])                       // antes: «gato»
        assertEquals("gatas", top(e, "las", "gat")[0])
        assertEquals("casa", top(e, "una", "cas")[0])
    }

    @Test fun adjectiveFollowsTheGenderOfTheSubject() {
        val e = TestSupport.newEngine()
        for (c in listOf("mi novio es muy", "el hombre es muy")) {
            val t = top(e, c, "roman"); assertTrue("$c -> $t", t.take(2).contains("romántico") && !t.take(3).contains("romántica"))
        }
        for (c in listOf("mi novia es muy", "la mujer es muy")) {
            val t = top(e, c, "roman"); assertTrue("$c -> $t", t.take(2).contains("romántica") && !t.take(3).contains("romántico"))
        }
    }

    @Test fun verbsAndOtherWordsAfterTheSubjectAreNotDisturbed() {
        val e = TestSupport.newEngine()
        for ((c, typed) in listOf("él" to "cant", "mi novio" to "cant", "ella" to "habl", "yo" to "quier")) {
            assertEquals("$c $typed", top(off, c, typed, 3), top(e, c, typed, 3))
        }
    }

    @Test fun agreementCanBeSwitchedOff() {
        assertFalse(top(off, "los", "gat")[0] == "gatos" && top(off, "las", "gat")[0] == "gatas")
        assertNotNull(off)
    }
}
