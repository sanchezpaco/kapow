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

    private fun SyntheticPage.negativeBubble(box: Box, body: Int = BLACK, lettering: Int = WHITE) = apply {
        fill(box, body)
        for (y in box.top + 7 until box.bottom - 7 step 6) {
            for (x in box.left + 7 until box.right - 7 - WORD_WIDTH step WORD_WIDTH + WORD_GAP) fill(Box(x, y, x + WORD_WIDTH, y + 3), lettering)
        }
    }

    private fun SyntheticPage.scannedTextBubble(box: Box) = apply {
        fill(box, BLACK)
        fill(Box(box.left + 2, box.top + 2, box.right - 2, box.bottom - 2), WHITE)
        for (y in box.top + 7 until box.bottom - 9 step 8) {
            fill(Box(box.left + 7, y, box.right - 7, y + 5), SCAN_GREY)
            fill(Box(box.left + 7, y + 1, box.right - 7, y + 3), BLACK)
        }
    }

    @Test
    fun scannedLetteringWithGreyFringeIsStillDetected() {
        val bubble = Box(100, 100, 220, 170)
        val page = SyntheticPage(400, 600).fill(panel, RED).scannedTextBubble(bubble)
        val found = bubblesOf(page)
        assertEquals(1, found.size)
        assertRoughly(bubble, page.box(found[0].box))
    }

    @Test
    fun textBubbleOverArtIsDetectedWithItsBox() {
        val bubble = Box(100, 100, 220, 170)
        val page = SyntheticPage(400, 600).fill(panel, RED).textBubble(bubble)
        val found = bubblesOf(page)
        assertEquals(1, found.size)
        assertRoughly(bubble, page.box(found[0].box))
        assertTrue(found[0].outlines.single().size >= 4)
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
    fun blackBubbleWithLightLetteringIsDetected() {
        val bubble = Box(110, 100, 210, 170)
        val page = SyntheticPage(400, 600).fill(panel, RED).negativeBubble(bubble)
        val found = bubblesOf(page)
        assertEquals(1, found.size)
        assertRoughly(bubble, page.box(found[0].box))
    }

    @Test
    fun solidBlackRegionWithoutLetteringIsNotABubble() {
        val page = SyntheticPage(400, 600).fill(panel, RED).fill(Box(110, 100, 210, 170), BLACK)
        assertTrue(bubblesOf(page).isEmpty())
    }

    @Test
    fun raggedRimAroundABubbleIsPartOfItsOutline() {
        val bubble = Box(200, 200, 360, 300)
        val page = SyntheticPage(800, 1000).fill(Box(20, 20, 780, 980), SKY).textBubble(bubble)
        for (y in bubble.top - 8 until bubble.bottom + 8 step 4) for (x in bubble.left - 8 until bubble.right + 8 step 4) {
            if (!bubble.inflate(2, page.width, page.height).contains(x, y)) page.fill(Box(x, y, x + 3, y + 3), BLACK)
        }
        val found = bubblesOf(page)
        assertEquals(1, found.size)
        val box = page.box(found[0].box)
        assertTrue("$box should include the rim around $bubble", box.left <= bubble.left - 6 && box.right >= bubble.right + 6)
    }

    @Test
    fun blackBubbleLeavesThePanelBorderLineItTouchesOutOfItsOutline() {
        val bubble = Box(110, 120, 210, 190)
        val borderLine = Box(20, 116, 380, 120)
        val page = SyntheticPage(400, 600).fill(panel, RED).negativeBubble(bubble).fill(borderLine, BLACK)
        val found = bubblesOf(page)
        assertEquals(1, found.size)
        val box = page.box(found[0].box)
        assertTrue("$box should stop at the bubble, not include the border line", box.top >= bubble.top - OUTLINE_MARGIN)
    }

    @Test
    fun darkShapeWhoseHolesAreMostlyNotLetteringIsNotABubble() {
        val mouth = Box(60, 100, 170, 220)
        val teeth = Box(60, 100, 170, 140)
        val tongue = Box(80, 140, 150, 151)
        val page = SyntheticPage(400, 600).fill(panel, RED).fill(mouth, BLACK).negativeBubble(teeth).fill(tongue, RED)
        assertTrue(bubblesOf(page).isEmpty())
    }

    private fun SyntheticPage.largeBubble(outer: Box, textBlock: Box, ink: Int = BLACK) = apply {
        fill(outer, ink)
        fill(Box(outer.left + 2, outer.top + 2, outer.right - 2, outer.bottom - 2), WHITE)
        for (y in textBlock.top + 7 until textBlock.bottom - 7 step 6) fill(Box(textBlock.left + 7, y, textBlock.right - 7, y + 3), ink)
    }

    @Test
    fun largeBubbleWithSmallCenteredTextGrowsToItsOutline() {
        val outer = Box(120, 150, 300, 430)
        val textBlock = Box(150, 185, 270, 395)
        val page = SyntheticPage(800, 1000).fill(Box(20, 20, 780, 980), RED).largeBubble(outer, textBlock)
        val found = bubblesOf(page)
        assertEquals(1, found.size)
        val box = page.box(found[0].box)
        assertTrue("$box should grow past the text block toward the outline", box.width >= 150 && box.height >= 230)
    }

    @Test
    fun touchingBubblesStaySeparate() {
        val page = SyntheticPage(400, 600).fill(panel, RED)
            .textBubble(Box(100, 100, 220, 170)).textBubble(Box(218, 120, 330, 190))
        assertEquals(2, bubblesOf(page).size)
    }

    @Test
    fun rectangularBubbleOutlineHasFourCorners() {
        val page = SyntheticPage(400, 600).fill(panel, RED).textBubble(Box(100, 100, 220, 170))
        assertEquals(4, bubblesOf(page).single().outlines.single().size)
    }

    private fun assertRoughly(expected: Box, actual: Box, tolerance: Int = 6) {
        assertTrue("expected $expected, got $actual", kotlin.math.abs(expected.left - actual.left) <= tolerance &&
            kotlin.math.abs(expected.top - actual.top) <= tolerance &&
            kotlin.math.abs(expected.right - actual.right) <= tolerance &&
            kotlin.math.abs(expected.bottom - actual.bottom) <= tolerance)
    }
}

private const val OUTLINE_MARGIN = 2
private const val SKY = 0xFF80A0FF.toInt()
private const val SCAN_GREY = 0xFF8C8C8C.toInt()
private const val WORD_WIDTH = 14
private const val WORD_GAP = 3
