package com.smartkeyboard

import com.smartkeyboard.ui.PreviewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewModelTest {
    private fun model() = PreviewModel { 10f }
    private fun PreviewModel.run(sec: Float) { repeat((sec * 60).toInt()) { step(1f / 60f) } }
    private fun PreviewModel.text() = live.map { it.ch }.joinToString("")

    @Test fun appendingKeepsExistingGlyphsAndFadesNewOnesIn() {
        val m = model(); m.update("hola", 4, 0); m.run(1f)
        val first = m.live.toList()
        m.update("hola m", 1, 100)
        assertEquals("hola m", m.text())
        for (i in 0 until 4) assertTrue(m.live[i] === first[i])
        assertTrue(m.live[5].alpha < 0.05f)           // el nuevo aparece desde transparente
        m.run(1f); assertTrue(m.live[5].alpha > 0.97f)
        assertTrue(m.isSettled())
    }

    @Test fun rewrittenTailFadesOutOldLettersAndKeepsTheCommonPrefix() {
        val m = model(); m.update("gotas", 5, 0); m.run(1f)
        val g = m.live[0]
        m.update("gol", 3, 50)
        assertEquals("gol", m.text()); assertTrue(m.live[0] === g)
        assertEquals(3, m.dying.size)                 // «t»,«a»,«s» → los que sobran se desvanecen
        m.run(1f); assertTrue(m.dying.isEmpty())
    }

    @Test fun leftShiftedWindowKeepsGlyphsInsteadOfRebuildingThem() {
        val m = model(); m.update("abcdef", 0, 0); m.run(1f)
        val c = m.live[2]
        m.update("cdefg", 0, 10)
        assertEquals("cdefg", m.text()); assertTrue(m.live[0] === c)
        assertEquals(2, m.dying.size)
    }

    @Test fun caretGlidesToTheEndAndScrollKeepsItInView() {
        val m = model(); m.innerW = 100f
        m.update("x".repeat(30), 0, 0); m.run(2f)
        assertEquals(300f, m.contentW, 0.01f)
        assertEquals(300f, m.caretX, 0.5f)
        assertTrue(m.scroll > 200f)                   // 300 de texto en 100 de ancho: se desplaza
        assertTrue(m.caretX - m.scroll <= 100f)
    }

    @Test fun composingFlagFollowsTheConfirmedWordSmoothly() {
        val m = model(); m.update("hola", 4, 0); m.run(1f)
        assertTrue(m.live.all { it.comp > 0.97f })
        m.update("hola ", 0, 100)                     // confirmada: deja de ser provisional
        m.step(0.016f); assertTrue(m.live[0].comp in 0.5f..0.999f) // se atenúa poco a poco, no de golpe
        m.run(1f); assertTrue(m.live[0].comp < 0.03f)
    }

    @Test fun emptyTextHasNoGlyphsAndSettles() {
        val m = model(); m.update("", 0, 0); m.run(1f)
        assertTrue(m.live.isEmpty()); assertFalse(m.dying.isNotEmpty()); assertTrue(m.isSettled())
    }
}
