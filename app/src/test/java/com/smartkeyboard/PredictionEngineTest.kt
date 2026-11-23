package com.smartkeyboard

import com.smartkeyboard.engine.UserModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictionEngineTest {
    @Test fun incrementalEqualsFreshComputation() {
        val e = TestSupport.shared
        val inc = TestSupport.interpret(e, "quieroiralcine")
        // recalcular desde cero tras escribir y borrar letras
        e.setContext(null)
        for (c in "quieroiralcinexyz") e.type(c)
        repeat(3) { e.backspace() }
        assertEquals(inc, e.best()?.text)
    }

    @Test fun backspaceRestoresPreviousInterpretation() {
        val e = TestSupport.shared
        val before = TestSupport.interpret(e, "holacomo")
        e.type('e'); e.backspace()
        assertEquals(before, e.best()?.text)
    }

    @Test fun contextChangesTheResult() {
        val e = TestSupport.shared
        val a = TestSupport.interpret(e, "estas", null)
        val b = TestSupport.interpret(e, "estas", "cómo")
        assertTrue(a.isNotEmpty() && b.isNotEmpty())
        assertEquals("estás", b)
    }

    @Test fun reinterpretsEarlierWordsWhenNewLettersArrive() {
        val e = TestSupport.shared
        e.setContext(null)
        for (c in "constru") e.type(c)
        val early = e.best()!!.text
        for (c in "appl") e.type(c)
        val later = e.best()!!.text
        assertTrue(later.startsWith(early.take(6)))
        assertTrue(later.split(' ').size > early.split(' ').size)
    }

    @Test fun userModelCanFlipTheChoice() {
        val e = TestSupport.newEngine()
        val before = TestSupport.interpret(e, "cons")
        val target = e.interpretations(6).map { it.text }.first { it != before && !it.contains(' ') }
        e.setUserModel(object : UserModel {
            override fun unigramBoost(lower: String) = if (lower == target) 6f else 0f
            override fun bigramBoost(prevLower: String, lower: String) = 0f
        })
        e.setContext(null); for (c in "cons") e.type(c)
        assertEquals(target, e.best()?.text)
    }

    @Test fun latencyPerKeystrokeIsWithinTarget() {
        val e = TestSupport.shared
        val phrase = "quieroiralcinevamosalacasaconmisamigosestanoche"
        repeat(3) { e.setContext(null); for (c in phrase) e.type(c) } // calentamiento JIT
        val times = ArrayList<Long>()
        e.setContext(null)
        for (c in phrase) { val t = System.nanoTime(); e.type(c); times.add(System.nanoTime() - t) }
        val avgMs = times.average() / 1e6
        val p95 = times.sorted()[(times.size * 0.95).toInt()] / 1e6
        println("latency avg=%.1fms p95=%.1fms".format(avgMs, p95))
        assertTrue("media $avgMs ms", avgMs < 30.0)
    }
}
