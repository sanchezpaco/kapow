package com.comicify.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalOrderTest {

    @Test
    fun sortsNumericSegmentsNumerically() {
        val input = listOf("page10.jpg", "page2.jpg", "page1.jpg")
        val sorted = input.sortedWith(naturalOrder)
        assertEquals(listOf("page1.jpg", "page2.jpg", "page10.jpg"), sorted)
    }

    @Test
    fun handlesZeroPadding() {
        val input = listOf("p001.png", "p010.png", "p002.png")
        val sorted = input.sortedWith(naturalOrder)
        assertEquals(listOf("p001.png", "p002.png", "p010.png"), sorted)
    }

    @Test
    fun sortsLargeMixedLibraryWithoutViolatingContract() {
        val alphabet = listOf("a", "b", "0", "1", "2")
        val names = buildList {
            for (a in alphabet) for (b in alphabet) for (c in alphabet) add(a + b + c)
        }
        val sorted = names.shuffled().sortedWith(naturalOrder)
        assertEquals(names.size, sorted.size)
    }

    @Test
    fun comparatorIsAConsistentTotalOrder() {
        val alphabet = listOf("a", "b", "0", "1", "2")
        val names = buildList {
            for (a in alphabet) for (b in alphabet) for (c in alphabet) add(a + b + c)
        }
        for (x in names) {
            for (y in names) {
                assertEquals(
                    "antisymmetry for '$x' vs '$y'",
                    naturalOrder.compare(x, y).sign(),
                    -naturalOrder.compare(y, x).sign(),
                )
            }
        }
        for (x in names) {
            for (y in names) {
                if (naturalOrder.compare(x, y) > 0) continue
                for (z in names) {
                    if (naturalOrder.compare(y, z) <= 0) {
                        assert(naturalOrder.compare(x, z) <= 0) { "transitivity for '$x' <= '$y' <= '$z'" }
                    }
                }
            }
        }
    }

    private fun Int.sign() = when {
        this > 0 -> 1
        this < 0 -> -1
        else -> 0
    }
}
