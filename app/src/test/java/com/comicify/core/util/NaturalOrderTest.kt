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
}
