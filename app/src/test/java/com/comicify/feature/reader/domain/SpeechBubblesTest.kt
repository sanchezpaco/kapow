package com.comicify.feature.reader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechBubblesTest {

    private val panel = Box(20, 20, 380, 580)
    private val cream = 0xFFFFF79A.toInt()

    private fun bubblesOf(page: SyntheticPage) = SpeechBubbles.detect(page.pixels, page.width, page.height, 1)

    @Test
    fun textBubbleOverArtIsDetectedWithItsBox() {
        val bubble = Box(100, 100, 220, 170)
        val page = SyntheticPage(400, 600).fill(panel, RED).bubble(bubble)
        val found = bubblesOf(page)
        assertEquals(1, found.size)
        assertRoughly(bubble, page.box(found[0].box))
        assertTrue(found[0].outline.size >= 4)
    }

    @Test
    fun blankWhiteRegionIsNotABubble() {
        val page = SyntheticPage(400, 600).fill(panel, RED)
            .fill(Box(100, 100, 220, 170), BLACK).fill(Box(102, 102, 218, 168), WHITE)
        assertTrue(bubblesOf(page).isEmpty())
    }

    @Test
    fun whiteRegionAroundASolidDarkBlobIsNotABubble() {
        val page = SyntheticPage(400, 600).fill(panel, RED)
            .fill(Box(100, 100, 260, 260), BLACK).fill(Box(102, 102, 258, 258), WHITE).fill(Box(140, 140, 220, 220), BLACK)
        assertTrue(bubblesOf(page).isEmpty())
    }

    @Test
    fun creamCaptionIsDetected() {
        val caption = Box(100, 100, 220, 170)
        val page = SyntheticPage(400, 600).fill(panel, RED).bubble(caption)
        for (y in caption.top + 2 until caption.bottom - 2) for (x in caption.left + 2 until caption.right - 2) {
            if (page.pixels[y * page.width + x] == WHITE) page.pixels[y * page.width + x] = cream
        }
        assertEquals(1, bubblesOf(page).size)
    }

    @Test
    fun bubbleTouchingAWhiteGutterIsStillDetected() {
        val bubble = Box(100, 250, 220, 320)
        val page = SyntheticPage(400, 600)
            .fill(Box(20, 20, 380, 300), RED).fill(Box(20, 306, 380, 580), BLUE)
            .bubble(bubble)
        page.fill(Box(bubble.left + 2, 298, bubble.right - 2, 308), WHITE)
        val found = bubblesOf(page)
        assertEquals(1, found.size)
        assertRoughly(bubble, page.box(found[0].box), tolerance = 8)
    }

    @Test
    fun touchingBubblesMergeIntoOne() {
        val page = SyntheticPage(400, 600).fill(panel, RED)
            .bubble(Box(100, 100, 220, 170)).bubble(Box(218, 120, 330, 190))
        assertEquals(1, bubblesOf(page).size)
    }

    private fun assertRoughly(expected: Box, actual: Box, tolerance: Int = 6) {
        assertTrue("expected $expected, got $actual", kotlin.math.abs(expected.left - actual.left) <= tolerance &&
            kotlin.math.abs(expected.top - actual.top) <= tolerance &&
            kotlin.math.abs(expected.right - actual.right) <= tolerance &&
            kotlin.math.abs(expected.bottom - actual.bottom) <= tolerance)
    }
}
