package com.smartkeyboard

import com.smartkeyboard.ime.CompositionManager
import com.smartkeyboard.ime.FieldPolicy
import com.smartkeyboard.ime.Learner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualModeTest {
    private class Rec : Learner {
        override var enabled = true
        val observed = ArrayList<Triple<String?, List<String>, Int>>()
        override fun observe(prev: String?, words: List<String>, weight: Int) { observed.add(Triple(prev, words, weight)) }
        override fun correction(typed: String, chosen: String, prev: String?) {}
    }

    private fun setup(): Triple<CompositionManager, FakeSink, Rec> {
        val sink = FakeSink(false); val m = CompositionManager(sink); val r = Rec()
        m.engine = TestSupport.newEngine(); m.learner = r; m.setManual(true)
        return Triple(m, sink, r)
    }
    private fun CompositionManager.type(s: String) = s.forEach { onLetter(it, false) }

    @Test fun writesLiterallyWithoutComposing() {
        val (m, s, _) = setup(); m.type("holacomoestas")
        assertEquals("holacomoestas", s.committed.toString()); assertEquals("", s.composing); assertFalse(m.composing)
    }

    @Test fun learnsEachFinishedWordWithManualWeightAndContext() {
        val (m, s, r) = setup(); m.type("hola"); m.onSpace(); m.type("quetal"); m.onPunctuation(",")
        assertEquals("hola quetal,", s.text)
        assertEquals(listOf("hola", "quetal"), r.observed.map { it.second[0] })
        assertTrue(r.observed.all { it.third == com.smartkeyboard.ime.LearningEngineWeights.MANUAL })
        assertEquals("hola", r.observed[1].first)
    }

    @Test fun erasedWordIsNotLearned() {
        val (m, s, r) = setup(); m.type("xyz"); repeat(3) { m.onBackspace() }; m.onSpace()
        assertEquals(" ", s.text); assertTrue(r.observed.isEmpty())
    }

    @Test fun noLearningWhenFieldForbidsIt() {
        val (m, _, r) = setup(); m.policy = FieldPolicy(predict = false, learn = false)
        m.type("clave"); m.onSpace(); assertTrue(r.observed.isEmpty())
    }

    @Test fun leavingManualModeFlushesPendingWord() {
        val (m, _, r) = setup(); m.type("pendiente"); m.setManual(false)
        assertEquals(listOf("pendiente"), r.observed.map { it.second[0] })
    }

    @Test fun debugLogRecordsActionsAndMasksProtectedFields() {
        val (m, _, _) = setup(); val out = ArrayList<String>()
        m.debug = true; m.log = { out.add(it) }
        m.type("hola"); m.onSpace()
        assertTrue(out.any { it.startsWith("TECLA letra 'h'") })
        assertTrue(out.any { it.contains("MANUAL aprende 'hola'") })
        out.clear(); m.policy = FieldPolicy(predict = false, learn = false)
        m.type("clave"); m.onSpace()
        assertFalse(out.any { it.contains("clave") }); assertTrue(out.any { it.contains("•••••") })
    }

    @Test fun debugOffProducesNoLog() {
        val (m, _, _) = setup(); val out = ArrayList<String>(); m.log = { out.add(it) }
        m.type("hola"); assertTrue(out.isEmpty())
    }

    // ---- Correcciones surgidas del log real ----
    private fun predictive(): Triple<CompositionManager, FakeSink, Rec> {
        val sink = FakeSink(false); val m = CompositionManager(sink); val r = Rec()
        m.engine = TestSupport.newEngine(); m.learner = r
        return Triple(m, sink, r)
    }

    @Test fun exactWordBeatsItsCompletions() {
        val e = TestSupport.newEngine()
        for (w in listOf("mi", "fue", "era", "hay", "como")) assertEquals(w, TestSupport.interpret(e, w))
    }

    @Test fun typedWordIsAlwaysOfferedAmongTheThreeOptions() {
        val (m, _, _) = predictive(); var shown = emptyList<String>(); m.onSuggestions = { shown = it }
        m.type("camina"); assertTrue(shown.toString(), shown.any { it.equals("camina", true) })
        m.reset(); m.type("brillan"); assertTrue(shown.toString(), shown.any { it.equals("brillan", true) })
    }

    @Test fun enterDoesNotCompleteAPrefix() {
        val (m, s, _) = predictive(); m.type("tes"); m.onEnter()
        assertTrue(s.text, s.text.lowercase().startsWith("tes") && !s.text.lowercase().startsWith("testigos"))
    }

    @Test fun junkWordsDoNotEnterPersonalVocabulary() {
        val store = com.smartkeyboard.learning.InMemoryStore()
        val l = com.smartkeyboard.learning.LearningEngine(store, { false }, { 0 })
        l.observe(null, listOf("fhhg", "aaa", "testear"), 3)
        assertEquals(1, l.vocabulary.size())
    }
}
