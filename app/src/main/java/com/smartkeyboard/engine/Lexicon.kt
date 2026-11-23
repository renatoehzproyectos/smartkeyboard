package com.smartkeyboard.engine

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.ln

/**
 * Diccionario con Trie compacto (listas de hermanos en arrays paralelos).
 * Los ids se asignan por frecuencia descendente: así los K primeros ids insertados
 * por cada nodo son las K palabras más frecuentes de ese prefijo (sin recorrer el subárbol).
 * [base] desplaza los ids para poder usar varios léxicos (principal + vocabulario del usuario).
 */
class Lexicon(
    val surfaces: Array<String>,
    val weights: FloatArray,
    val base: Int = 0,
) {
    companion object {
        const val K = 32
        fun fromStream(input: InputStream, base: Int = 0): Lexicon {
            val s = ArrayList<String>(70000)
            val w = ArrayList<Float>(70000)
            BufferedReader(InputStreamReader(input, Charsets.UTF_8), 1 shl 16).useLines { lines ->
                for (line in lines) {
                    val t = line.indexOf('\t')
                    if (t <= 0) continue
                    s.add(line.substring(0, t))
                    w.add(line.substring(t + 1).trim().toFloatOrNull() ?: 1f)
                }
            }
            return fromEntries(s, w, base)
        }

        /** Entradas (superficie, peso); se ordenan por peso descendente. */
        fun fromEntries(surf: List<String>, wt: List<Float>, base: Int = 0, totalOverride: Float? = null): Lexicon {
            val order = surf.indices.sortedByDescending { wt[it] }
            val total = totalOverride ?: wt.sum().coerceAtLeast(1f)
            return Lexicon(
                Array(order.size) { surf[order[it]] },
                FloatArray(order.size) { ln(wt[order[it]].coerceAtLeast(0.5f) / total) },
                base
            )
        }
    }

    val size get() = surfaces.size

    private val keys = Array(surfaces.size) { TextNorm.key(surfaces[it]) }
    private var nodeCount = 1
    private val cap = keys.sumOf { it.length } + 2
    private val firstChild = IntArray(cap) { -1 }
    private val nextSib = IntArray(cap) { -1 }
    private val chr = CharArray(cap)
    private val termHead = IntArray(cap) { -1 }
    private val topCount = ByteArray(cap)
    private val subCount = IntArray(cap)
    private var topOff = IntArray(0)
    private var topIds = IntArray(0)
    private val nextSame = IntArray(surfaces.size) { -1 }

    init {
        for (id in keys.indices) insert(id)
        // Segunda pasada: reserva sólo min(K, palabras del subárbol) huecos por nodo.
        topOff = IntArray(nodeCount + 1)
        for (n in 0 until nodeCount) topOff[n + 1] = topOff[n] + minOf(K, subCount[n])
        topIds = IntArray(topOff[nodeCount]) { -1 }
        java.util.Arrays.fill(topCount, 0.toByte())
        for (id in keys.indices) {
            var node = 0
            for (ch in keys[id]) {
                node = child(node, ch)
                val tc = topCount[node].toInt()
                if (tc < K) { topIds[topOff[node] + tc] = id; topCount[node] = (tc + 1).toByte() }
            }
        }
    }

    private fun insert(id: Int) {
        var node = 0
        val k = keys[id]
        for (ch in k) {
            var c = child(node, ch)
            if (c < 0) {
                c = nodeCount++
                chr[c] = ch
                nextSib[c] = firstChild[node]
                firstChild[node] = c
            }
            node = c
            subCount[node]++
        }
        if (termHead[node] < 0) termHead[node] = id
        else { var t = termHead[node]; while (nextSame[t] >= 0) t = nextSame[t]; nextSame[t] = id }
    }

    val root get() = 0

    fun child(node: Int, ch: Char): Int {
        var c = firstChild[node]
        while (c >= 0) { if (chr[c] == ch) return c; c = nextSib[c] }
        return -1
    }

    fun walk(start: Int, s: CharSequence, from: Int, to: Int): Int {
        var node = start
        var i = from
        while (i < to && node >= 0) { node = child(node, s[i]); i++ }
        return node
    }

    inline fun forEachChild(node: Int, f: (Char, Int) -> Unit) {
        var c = firstChildOf(node)
        while (c >= 0) { f(charOf(c), c); c = nextSiblingOf(c) }
    }

    fun firstChildOf(n: Int) = firstChild[n]
    fun nextSiblingOf(n: Int) = nextSib[n]
    fun charOf(n: Int) = chr[n]
    fun hasChildren(node: Int) = firstChild[node] >= 0

    /** Palabras (ids locales) cuya clave es exactamente la del nodo. */
    fun terminals(node: Int): IntArray {
        var id = termHead[node]
        if (id < 0) return EMPTY
        val out = ArrayList<Int>(2)
        while (id >= 0) { out.add(id); id = nextSame[id] }
        return out.toIntArray()
    }

    /** Las K palabras más frecuentes bajo el nodo (ids locales, por frecuencia). */
    fun topWords(node: Int): IntArray {
        val n = topCount[node].toInt()
        return IntArray(n) { topIds[topOff[node] + it] }
    }

    fun keyLength(id: Int) = keys[id].length
    fun surface(id: Int) = surfaces[id]
    fun logP(id: Int) = weights[id]
    fun contains(surfaceLower: String): Boolean {
        val k = TextNorm.key(surfaceLower)
        val n = walk(0, k, 0, k.length)
        return n >= 0 && terminals(n).any { surfaces[it].lowercase() == surfaceLower.lowercase() }
    }

    /** Búsqueda exacta por superficie (insensible a mayúsculas); devuelve id global o -1. */
    fun idOf(surfaceLower: String): Int {
        val k = TextNorm.key(surfaceLower)
        val n = walk(0, k, 0, k.length)
        if (n < 0) return -1
        val low = surfaceLower.lowercase()
        for (id in terminals(n)) if (surfaces[id].lowercase() == low) return base + id
        return -1
    }
}

private val EMPTY = IntArray(0)
