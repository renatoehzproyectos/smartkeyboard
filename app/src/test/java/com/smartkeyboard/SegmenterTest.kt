package com.smartkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmenterTest {
    private fun check(input: String, expected: String) =
        assertEquals(input, expected.lowercase(), TestSupport.interpret(TestSupport.shared, input).lowercase())

    @Test fun construappl() = check("construappl", "Construir una aplicación")
    @Test fun holacomoestas() = check("holacomoestas", "Hola cómo estás")
    @Test fun buenosdias() = check("buenosdias", "Buenos días")
    @Test fun quieroiralcine() = check("quieroiralcine", "Quiero ir al cine")
    @Test fun construapplparaandr() = check("construapplparaandr", "Construir una aplicación para Android")

    @Test fun otherPhrases() {
        check("vamosalacasa", "vamos a la casa")
        check("mepuedesayudar", "me puedes ayudar")
        check("estoyenlacasa", "estoy en la casa")
    }

    @Test fun accentsAreRestored() { check("dias", "días"); check("cancion", "canción") }
    @Test fun enye() { check("españa", "España"); check("mañana", "mañana") }
    @Test fun properNoun() = check("madrid", "Madrid")

    @Test fun unknownWordsNeverFail() {
        val r = TestSupport.interpret(TestSupport.shared, "zqxkvw")
        assertTrue(r.isNotEmpty())
    }

    @Test fun ambiguousPrefixKeepsAlternatives() {
        val e = TestSupport.shared
        e.setContext(null); for (c in "constru") e.type(c)
        assertTrue(e.interpretations(3).size >= 2)
    }

    @Test fun typoDoubledLetterIsTolerated() = check("hollacomoestas", "hola cómo estás")

    @Test fun trieCompletionsComeFromPrefix() {
        val e = TestSupport.shared
        assertEquals("construcción", TestSupport.interpret(e, "construcc"))
    }
}
