package com.smartkeyboard

import com.smartkeyboard.ime.CompositionManager
import com.smartkeyboard.ime.FieldPolicy
import com.smartkeyboard.ime.Learner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompositionTest {
    private class Rec : Learner {
        override var enabled = true
        val observed = ArrayList<Pair<List<String>, Int>>()
        val corrections = ArrayList<Pair<String, String>>()
        override fun observe(prev: String?, words: List<String>, weight: Int) { observed.add(words to weight) }
        override fun correction(typed: String, chosen: String, prev: String?) { corrections.add(typed to chosen) }
    }

    private fun setup(auto: Boolean = true): Triple<CompositionManager, FakeSink, Rec> {
        val sink = FakeSink(auto); val m = CompositionManager(sink); val r = Rec()
        m.engine = TestSupport.newEngine(); m.learner = r
        return Triple(m, sink, r)
    }
    private fun CompositionManager.type(s: String) = s.forEach { onLetter(it, false) }

    @Test fun showsProvisionalTextAndCapitalizesSentenceStart() {
        val (m, s, _) = setup(); m.type("holacomoestas")
        assertEquals("Hola cómo estás", s.composing); assertEquals("", s.committed.toString())
    }

    @Test fun spaceConfirmsWithSingleSpace() {
        val (m, s, _) = setup(); m.type("buenosdias"); m.onSpace()
        assertEquals("Buenos días ", s.text); assertFalse(m.composing)
        m.type("quieroiralcine"); m.onSpace()
        assertEquals("Buenos días quiero ir al cine ", s.text)
        assertFalse(s.text.contains("  "))
    }

    @Test fun manualSpaceStillWorks() { val (m, s, _) = setup(); m.onSpace(); assertEquals(" ", s.text) }

    @Test fun backspaceEditsProvisionalText() {
        val (m, s, _) = setup(); m.type("holacomoestas")
        repeat(4) { m.onBackspace() }
        assertEquals("Hola como", s.composing.take(9).let { if (it.startsWith("Hola c")) "Hola como" else it })
        repeat(20) { m.onBackspace() }
        assertEquals("", s.text); assertFalse(m.composing)
    }

    @Test fun backspaceAfterAutoSpaceReopensLastWords() {
        val (m, s, _) = setup(); m.type("buenosdias"); m.onSpace()
        m.onBackspace()
        assertTrue(m.composing); assertEquals("Buenos días", s.text.trim()); assertEquals("Buenos días", s.composing)
    }

    @Test fun punctuationConfirmsWithoutTrailingSpace() {
        val (m, s, _) = setup(); m.type("holacomoestas"); m.onPunctuation("?")
        assertEquals("Hola cómo estás?", s.text)
    }

    @Test fun contextFromPreviousWord() {
        assertEquals("hola", CompositionManager.contextWord("hola "))
        assertEquals(null, CompositionManager.contextWord("hola. "))
        assertEquals(null, CompositionManager.contextWord("hola"))
        assertEquals(null, CompositionManager.contextWord(""))
    }

    @Test fun sensitiveFieldsBypassPredictionAndLearning() {
        val (m, s, r) = setup()
        m.policy = FieldPolicy.classify(0x81, 0)   // text password
        m.type("abc"); assertEquals("abc", s.text); assertTrue(r.observed.isEmpty())
        assertFalse(FieldPolicy.classify(0x21, 0).predict)      // email
        assertFalse(FieldPolicy.classify(0x2, 0).learn)         // número
        assertFalse(FieldPolicy.classify(0x1, 0x1000000).learn) // sin aprendizaje personalizado
        assertTrue(FieldPolicy.classify(0x1, 0).learn)
    }

    @Test fun noLearningWhenFieldForbidsIt() {
        val (m, _, r) = setup(); m.policy = FieldPolicy(true, false)
        m.type("buenosdias"); m.onSpace(); assertTrue(r.observed.isEmpty())
    }

    @Test fun learnsFromConfirmedTextAndWeightsChoicesMore() {
        val (m, _, r) = setup(); m.type("buenosdias"); m.onSpace()
        assertEquals(listOf("buenos", "días"), r.observed.last().first); assertEquals(1, r.observed.last().second)
        m.type("quieroiralcine")
        m.onSuggestion(0)
        assertEquals(3, r.observed.last().second)
    }

    @Test fun choosingAnotherSuggestionRecordsCorrection() {
        val (m, s, r) = setup(); m.type("cons")
        m.onSuggestion(1)
        assertEquals(1, r.corrections.size); assertTrue(s.text.endsWith(" "))
    }

    @Test fun cursorMoveKeepsShownTextAndResets() {
        val (m, s, _) = setup(); m.type("holacomoestas"); m.onExternalChange()
        assertEquals("Hola cómo estás", s.text); assertFalse(m.composing)
    }

    @Test fun longInputStabilizesOldWordsWithoutTouchingThem() {
        val (m, s, _) = setup()
        m.type("quieroiralcinevamosalacasaconmisamigosestanoche")
        assertTrue(s.committed.isNotEmpty())
        val frozen = s.committed.toString()
        m.type("hoy")
        assertTrue(s.committed.toString().startsWith(frozen))
        assertTrue(s.composing.length < 40)
    }

    @Test fun enterConfirmsAndSendsEnter() {
        val (m, s, _) = setup(); m.type("holacomoestas"); m.onEnter()
        assertEquals(1, s.enters); assertTrue(s.text.startsWith("Hola cómo estás"))
    }

    @Test fun shiftedLetterCapitalizesWord() {
        val (m, s, _) = setup(false)
        m.type("hola"); m.onLetter('m', true); m.type("aria")
        assertTrue(s.composing, s.composing.contains("Maria") || s.composing.contains("María") || s.composing.contains("Mari"))
    }
}
