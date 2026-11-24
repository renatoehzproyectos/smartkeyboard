package com.smartkeyboard

import com.smartkeyboard.ime.PreviewSplit
import com.smartkeyboard.ui.PreviewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewModelTest {
    private fun model() = PreviewModel { 10f }.also { it.innerW = 200f }
    private fun PreviewModel.run(sec: Float) { repeat((sec * 60).toInt()) { step(1f / 60f) } }
    private fun PreviewModel.text() = live.map { it.ch }.joinToString("")

    @Test fun splitsTypedAndPredictedParts() {
        assertEquals("got" to "as", PreviewSplit.split("gotas", "got"))
        assertEquals("n" to "o", PreviewSplit.split("no", "n"))
        assertEquals("N" to "o", PreviewSplit.split("No", "n"))
        assertEquals("árbo" to "l", PreviewSplit.split("árbol", "arbo"))
        assertEquals("es g" to "rande", PreviewSplit.split("es grande", "esg"))
        assertEquals("hola" to "", PreviewSplit.split("hola", "hola"))
        assertEquals("hace" to "", PreviewSplit.split("hace", "nace"))       // corrección: no es continuación
        assertEquals("comienza" to "", PreviewSplit.split("comienza", "coomi"))
    }

    @Test fun predictedLettersAreDimAndTypedOnesAreFull() {
        val m = model(); m.update("gotas", 0, 3, 0); m.run(1f)
        assertEquals("gotas", m.text())
        assertTrue(m.live.take(3).all { it.emph > 0.97f })
        assertTrue(m.live.drop(3).all { it.emph < 0.33f })
    }

    @Test fun caretSitsBetweenTypedAndPredictedPart() {
        val m = model(); m.update("gotas", 0, 3, 0); m.run(1f)
        assertEquals(30f, m.caretX, 0.5f)            // got|as  (3 letras de 10 px)
        m.update("gotas", 0, 5, 10); m.run(1f)
        assertEquals(50f, m.caretX, 0.5f)            // al terminar de escribir, al final
    }

    @Test fun typingTheNextPredictedLetterKeepsTheGlyphAndFirmsItUp() {
        val m = model(); m.update("gotas", 0, 3, 0); m.run(1f)
        val a = m.live[3]; val o = m.live[2]
        m.update("gotas", 0, 4, 20)                  // tecleas «a»: ya no es predicción
        assertTrue(m.live[3] === a && m.live[2] === o)
        assertTrue(m.dying.isEmpty())
        m.step(0.016f); assertTrue(a.emph > 0.30f && a.emph < 1f)   // se afirma poco a poco
        m.run(1f); assertTrue(a.emph > 0.97f)
    }

    @Test fun confirmingTurnsThePredictionIntoNormalText() {
        val m = model(); m.update("gotas", 0, 3, 0); m.run(1f)
        m.update("gotas ", 6, 6, 30); m.run(1f)      // confirmada: todo es texto normal
        assertTrue(m.live.all { kotlin.math.abs(it.emph - PreviewModel.EMPH_COMMITTED) < 0.03f })
        assertTrue(m.dying.isEmpty())
    }

    @Test fun rewrittenPredictionFadesOutOnlyTheOldTail() {
        val m = model(); m.update("gotas", 0, 2, 0); m.run(1f)
        val g = m.live[0]
        m.update("golpe", 0, 3, 40)
        assertEquals("golpe", m.text()); assertTrue(m.live[0] === g)
        assertEquals(3, m.dying.size)
        m.run(1f); assertTrue(m.dying.isEmpty())
    }

    @Test fun currentWordIsCenteredInTheStrip() {
        val m = model()
        m.update("hola gotas", 5, 8, 0); m.run(2f)   // palabra en curso: «gotas» (x=50..100)
        val center = (m.runL + m.runR) / 2f - m.scroll
        assertEquals(100f, center, 1f)               // innerW/2
        m.update("hola gotas más", 5, 8, 50); m.run(2f)
        assertEquals(100f, (m.runL + m.runR) / 2f - m.scroll, 1f)
    }

    @Test fun withoutAWordInProgressTheCaretIsCentered() {
        val m = model(); m.update("hola ", 5, 5, 0); m.run(2f)
        assertEquals(100f, m.caretX - m.scroll, 1f)
        val e = model(); e.update("", 0, 0, 0); e.run(2f)
        assertEquals(100f, e.caretX - e.scroll, 1f); assertTrue(e.isSettled())
    }

    @Test fun leftShiftedWindowKeepsGlyphsInsteadOfRebuildingThem() {
        val m = model(); m.update("abcdef", 6, 6, 0); m.run(1f)
        val c = m.live[2]
        m.update("cdefg", 5, 5, 10)
        assertEquals("cdefg", m.text()); assertTrue(m.live[0] === c)
        assertEquals(2, m.dying.size)
    }

    @Test fun emptyTextHasNoGlyphsAndSettles() {
        val m = model(); m.update("", 0, 0, 0); m.run(1f)
        assertTrue(m.live.isEmpty()); assertFalse(m.dying.isNotEmpty()); assertTrue(m.isSettled())
    }
}
