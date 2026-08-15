package com.comicify.feature.reader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelBandsTest {

    private fun bandsOf(page: SyntheticPage): List<Box> {
        val classes = PixelClasses.classify(page.pixels, page.width, page.height, 1)
        return PanelBands.bands(classes)
    }

    @Test
    fun stackedRowsSplitIntoOrderedFullWidthBands() {
        val top = Box(40, 40, 260, 180)
        val middle = Box(40, 240, 260, 380)
        val bottom = Box(40, 440, 260, 580)
        val page = SyntheticPage(300, 600)
            .fill(top, RED)
            .fill(middle, BLUE)
            .fill(bottom, RED)
        val bands = bandsOf(page)
        assertEquals(3, bands.size)
        assertFullWidth(bands, 300)
        assertVerticalSpan(40, 180, bands[0])
        assertVerticalSpan(240, 380, bands[1])
        assertVerticalSpan(440, 580, bands[2])
    }

    @Test
    fun solidBlockFillingThePageIsOneBand() {
        val page = SyntheticPage(300, 600).fill(Box(0, 0, 300, 600), RED)
        val bands = bandsOf(page)
        assertEquals(1, bands.size)
        assertEquals(Box(0, 0, 300, 600), bands[0])
    }

    @Test
    fun thinWhiteLineDoesNotSplitABand() {
        val page = SyntheticPage(300, 600)
            .fill(Box(30, 40, 270, 300), RED)
            .fill(Box(30, 303, 270, 560), RED)
        val bands = bandsOf(page)
        assertEquals(1, bands.size)
    }

    @Test
    fun tinySliverMergesIntoTheNeighbouringBand() {
        val page = SyntheticPage(300, 600)
            .fill(Box(30, 30, 270, 45), RED)
            .fill(Box(30, 90, 270, 560), RED)
        val bands = bandsOf(page)
        assertEquals(1, bands.size)
    }

    private fun assertFullWidth(bands: List<Box>, width: Int) {
        for (band in bands) {
            assertEquals(0, band.left)
            assertEquals(width, band.right)
        }
    }

    private fun assertVerticalSpan(top: Int, bottom: Int, actual: Box, tolerance: Int = 6) {
        val message = "expected span [$top, $bottom] but was [${actual.top}, ${actual.bottom}]"
        assertTrue(message, kotlin.math.abs(top - actual.top) <= tolerance)
        assertTrue(message, kotlin.math.abs(bottom - actual.bottom) <= tolerance)
    }
}
