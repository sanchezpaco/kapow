package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelDetectionTest {

    private val topLeft = Box(20, 20, 190, 290)
    private val topRight = Box(210, 20, 380, 290)
    private val bottomLeft = Box(20, 310, 190, 580)
    private val bottomRight = Box(210, 310, 380, 580)

    private fun grid() = SyntheticPage(400, 600)
        .fill(topLeft, RED).fill(topRight, BLUE).fill(bottomLeft, BLUE).fill(bottomRight, RED)

    @Test
    fun gridPanelsAreDetectedInZOrder() {
        val page = grid()
        val panels = page.detect().map(page::box)
        assertEquals(4, panels.size)
        assertRoughly(topLeft, panels[0])
        assertRoughly(topRight, panels[1])
        assertRoughly(bottomLeft, panels[2])
        assertRoughly(bottomRight, panels[3])
    }

    @Test
    fun bubbleOverhangingTheGutterBelongsToItsPanel() {
        val page = grid().bubble(Box(150, 100, 220, 160))
        val panels = page.detect().map(page::box)
        assertEquals(4, panels.size)
        assertTrue("panel should include the bubble", panels[0].right >= 220 && panels[0].top <= 100)
        assertRoughly(topRight, panels[1])
    }

    @Test
    fun blackGuttersSeparatePanels() {
        val page = SyntheticPage(400, 600, background = BLACK)
            .fill(topLeft, RED).fill(topRight, BLUE).fill(bottomLeft, BLUE).fill(bottomRight, RED)
        assertEquals(4, page.detect().size)
    }

    @Test
    fun hairlineGutterSurvivesPooling() {
        val page = SyntheticPage(800, 1200)
            .fill(Box(0, 0, 800, 1200), RED)
            .fill(Box(398, 0, 401, 1200), WHITE)
        assertEquals(2, page.detect(pool = 2).size)
    }

    @Test
    fun raggedSplitIsOnePanel() {
        val page = SyntheticPage(400, 600).fillWavyPair(Box(20, 20, 200, 580), Box(200, 20, 380, 580), RED, amplitude = 14)
        assertEquals(1, page.detect().size)
    }

    @Test
    fun tiltedGutterSeparatesPanels() {
        val page = SyntheticPage(400, 600)
        for (y in 20 until 580) {
            val split = 200 + (y - 300) / 4
            page.fill(Box(20, y, split - 6, y + 1), RED)
            page.fill(Box(split + 6, y, 380, y + 1), BLUE)
        }
        assertEquals(2, page.detect().size)
    }

    @Test
    fun blankPageFallsBackToFullPage() {
        assertEquals(listOf(FullPagePanel), SyntheticPage(400, 600).detect())
    }

    @Test
    fun singleBodyIsReturnedTrimmed() {
        val page = SyntheticPage(400, 600).fill(Box(40, 60, 360, 540), RED)
        val panels = page.detect().map(page::box)
        assertEquals(1, panels.size)
        assertRoughly(Box(40, 60, 360, 540), panels[0])
    }

    private fun assertRoughly(expected: Box, actual: Box, tolerance: Int = 6) {
        val message = "expected $expected but was $actual"
        assertTrue(message, kotlin.math.abs(expected.left - actual.left) <= tolerance)
        assertTrue(message, kotlin.math.abs(expected.top - actual.top) <= tolerance)
        assertTrue(message, kotlin.math.abs(expected.right - actual.right) <= tolerance)
        assertTrue(message, kotlin.math.abs(expected.bottom - actual.bottom) <= tolerance)
    }
}
