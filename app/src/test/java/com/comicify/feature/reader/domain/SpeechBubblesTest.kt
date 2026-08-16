package com.comicify.feature.reader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechBubblesTest {

    private val panel = Box(20, 20, 380, 580)
    private val cream = 0xFFFFF79A.toInt()

    private fun bubblesOf(page: SyntheticPage) = SpeechBubbles.detect(page.pixels, page.width, page.height, 1)

    private fun SyntheticPage.textBubble(box: Box, ink: Int = BLACK, paper: Int = WHITE) = apply {
        fill(box, ink)
        fill(Box(box.left + 2, box.top + 2, box.right - 2, box.bottom - 2), paper)
        for (y in box.top + 7 until box.bottom - 7 step 6) fill(Box(box.left + 7, y, box.right - 7, y + 3), ink)
    }

    @Test
    fun textBubbleOverArtIsDetectedWithItsBox() {
        val bubble = Box(100, 100, 220, 170)
        val page = SyntheticPage(400, 600).fill(panel, RED).textBubble(bubble)
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
        val page = SyntheticPage(400, 600).fill(panel, RED).textBubble(caption, paper = cream)
        assertEquals(1, bubblesOf(page).size)
    }

    @Test
    fun bubbleTouchingAWhiteGutterIsStillDetected() {
        val bubble = Box(100, 250, 220, 320)
        val page = SyntheticPage(400, 600)
            .fill(Box(20, 20, 380, 300), RED).fill(Box(20, 306, 380, 580), BLUE)
            .textBubble(bubble)
        page.fill(Box(bubble.left + 2, 298, bubble.right - 2, 308), WHITE)
        val found = bubblesOf(page)
        assertEquals(1, found.size)
        val box = page.box(found[0].box)
        assertTrue("$box should stay inside the bubble", box.left >= bubble.left - 4 && box.right <= bubble.right + 4 && box.top >= bubble.top - 4)
        assertTrue("$box should cover the text", box.left <= bubble.left + 7 && box.right >= bubble.right - 7 && box.top <= bubble.top + 7 && box.bottom >= 295)
    }

    @Test
    fun touchingBubblesMergeIntoOne() {
        val page = SyntheticPage(400, 600).fill(panel, RED)
            .textBubble(Box(100, 100, 220, 170)).textBubble(Box(218, 120, 330, 190))
        assertEquals(1, bubblesOf(page).size)
    }

    private fun assertRoughly(expected: Box, actual: Box, tolerance: Int = 6) {
        assertTrue("expected $expected, got $actual", kotlin.math.abs(expected.left - actual.left) <= tolerance &&
            kotlin.math.abs(expected.top - actual.top) <= tolerance &&
            kotlin.math.abs(expected.right - actual.right) <= tolerance &&
            kotlin.math.abs(expected.bottom - actual.bottom) <= tolerance)
    }
}
