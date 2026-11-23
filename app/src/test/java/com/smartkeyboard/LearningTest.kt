package com.smartkeyboard

import com.smartkeyboard.learning.InMemoryStore
import com.smartkeyboard.learning.LearningEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningTest {
    private fun engine(store: InMemoryStore = InMemoryStore(), known: Set<String> = setOf("hola", "mundo")) =
        LearningEngine(store, { it in known }, { 100000 })

    @Test fun frequencyAndBigramsAreLearned() {
        val l = engine()
        l.observe(null, listOf("hola", "mundo"), 1)
        assertTrue(l.unigramBoost("hola") > 0f)
        assertTrue(l.bigramBoost("hola", "mundo") > 0f)
        assertEquals(0f, l.bigramBoost("mundo", "hola"), 0f)
    }

    @Test fun activeChoicesWeighMoreThanObservation() {
        val l = engine()
        l.observe(null, listOf("hola"), LearningEngine.PASSIVE)
        val p = l.unigramBoost("hola")
        l.observe(null, listOf("mundo"), LearningEngine.ACTIVE)
        assertTrue(l.unigramBoost("mundo") > p)
    }

    @Test fun boostIsCappedToAvoidOverfitting() {
        val l = engine()
        repeat(10_000) { l.observe(null, listOf("hola"), 3) }
        assertTrue(l.unigramBoost("hola") <= 3.0f)
    }

    @Test fun pausedLearningStoresNothing() {
        val s = InMemoryStore(); val l = engine(s)
        l.enabled = false
        l.observe(null, listOf("hola", "mundo"), 1)
        assertTrue(s.freq.isEmpty() && s.ngrams.isEmpty())
        assertEquals(0f, l.unigramBoost("hola"), 0f)
    }

    @Test fun unknownWordsEnterVocabularyAfterRepetition() {
        val l = engine()
        var published: Any? = null
        l.onVocabularyChanged = { published = it }
        l.observe(null, listOf("zorbalandia"), 1)
        assertEquals(0, l.vocabulary.size())
        l.observe(null, listOf("zorbalandia"), 1)
        assertEquals(1, l.vocabulary.size())
        assertNotNull(published)
    }

    @Test fun persistsAndReloads() {
        val s = InMemoryStore()
        engine(s).also { it.observe(null, listOf("hola", "mundo"), 1) }
        val l2 = engine(s); l2.load()
        assertTrue(l2.bigramBoost("hola", "mundo") > 0f)
    }

    @Test fun correctionsAreRecordedAndBoostChosenWords() {
        val s = InMemoryStore(); val l = engine(s)
        l.correction("hola mundo", "hola mundo cruel", null)
        assertEquals(1, l.corrections.total)
        assertTrue(l.unigramBoost("cruel") > 0f)
    }

    @Test fun clearAllRemovesEverything() {
        val s = InMemoryStore(); val l = engine(s)
        l.observe(null, listOf("hola", "mundo", "cruel"), 3)
        l.clearAll()
        assertFalse(l.unigramBoost("hola") > 0f)
        assertTrue(s.freq.isEmpty() && s.ngrams.isEmpty() && s.phrases.isEmpty())
    }
}
