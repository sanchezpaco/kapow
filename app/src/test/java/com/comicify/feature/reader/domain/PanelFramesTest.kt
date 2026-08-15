package com.comicify.feature.reader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelFramesTest {

    private val width = 200
    private val height = 300
    private val full = Box(0, 0, width, height)

    private fun masks(build: (BooleanArray, BooleanArray) -> Unit): Pair<BooleanArray, BooleanArray> {
        val art = BooleanArray(width * height) { true }
        val separator = BooleanArray(width * height)
        build(art, separator)
        return art to separator
    }

    private fun horizontalKeyline(art: BooleanArray, separator: BooleanArray, row: Int, from: Int, to: Int) {
        for (y in row until row + 2) for (x in from until to) {
            art[y * width + x] = false
            separator[y * width + x] = true
        }
    }

    @Test
    fun splitsBodyAcrossAContinuousKeyline() {
        val (art, separator) = masks { a, s -> horizontalKeyline(a, s, 150, 0, width) }
        val parts = PanelFrames.split(full, separator, art, width)
        assertEquals(2, parts.size)
        assertTrue(parts.any { it.top == 0 && it.bottom <= 152 })
        assertTrue(parts.any { it.bottom == height })
    }

    @Test
    fun toleratesASmallBleedGapAndStillSplits() {
        val (art, separator) = masks { a, s -> horizontalKeyline(a, s, 150, 0, (width * 0.88f).toInt()) }
        assertEquals(2, PanelFrames.split(full, separator, art, width).size)
    }

    @Test
    fun keepsBodyWhenBleedBreaksTheKeyline() {
        val (art, separator) = masks { a, s -> horizontalKeyline(a, s, 150, 0, (width * 0.7f).toInt()) }
        assertEquals(1, PanelFrames.split(full, separator, art, width).size)
    }

    @Test
    fun doesNotSplitOffAWhiteMargin() {
        val (art, separator) = masks { a, s ->
            horizontalKeyline(a, s, 40, 0, width)
            for (y in 0 until 40) for (x in 0 until width) {
                a[y * width + x] = false
                s[y * width + x] = true
            }
        }
        assertEquals(1, PanelFrames.split(full, separator, art, width).size)
    }

    @Test
    fun splitsAcrossAVerticalKeyline() {
        val (art, separator) = masks { a, s ->
            for (x in 100 until 102) for (y in 0 until height) {
                a[y * width + x] = false
                s[y * width + x] = true
            }
        }
        assertEquals(2, PanelFrames.split(full, separator, art, width).size)
    }
}
