package com.comicify.feature.reader.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailStripTest {

    @Test
    fun singlePageStepIsThePageItself() {
        assertEquals(0, ThumbnailStrip.stepIndexForPage(0, pagesPerStep = 1))
        assertEquals(7, ThumbnailStrip.stepIndexForPage(7, pagesPerStep = 1))
    }

    @Test
    fun spreadStepPairsConsecutivePages() {
        assertEquals(0, ThumbnailStrip.stepIndexForPage(0, pagesPerStep = 2))
        assertEquals(0, ThumbnailStrip.stepIndexForPage(1, pagesPerStep = 2))
        assertEquals(1, ThumbnailStrip.stepIndexForPage(2, pagesPerStep = 2))
        assertEquals(3, ThumbnailStrip.stepIndexForPage(7, pagesPerStep = 2))
    }

    @Test
    fun negativePageClampsToFirstStep() {
        assertEquals(0, ThumbnailStrip.stepIndexForPage(-5, pagesPerStep = 2))
    }

    @Test
    fun centerOffsetPlacesItemInMiddleOfViewport() {
        assertEquals(-76, ThumbnailStrip.centerScrollOffsetPx(viewportWidthPx = 200, itemWidthPx = 48))
    }

    @Test
    fun centerOffsetIsZeroWhenItemFillsViewport() {
        assertEquals(0, ThumbnailStrip.centerScrollOffsetPx(viewportWidthPx = 48, itemWidthPx = 48))
    }

    @Test
    fun centerOffsetNeverPushesItemPastStart() {
        assertEquals(0, ThumbnailStrip.centerScrollOffsetPx(viewportWidthPx = 20, itemWidthPx = 48))
    }
}
