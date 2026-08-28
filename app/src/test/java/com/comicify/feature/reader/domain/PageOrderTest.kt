package com.comicify.feature.reader.domain

import com.comicify.domain.model.ReadingDirection.LeftToRight
import com.comicify.domain.model.ReadingDirection.RightToLeft
import org.junit.Assert.assertEquals
import org.junit.Test

class PageOrderTest {

    @Test
    fun pagerIndexIsIdentityForLeftToRight() {
        assertEquals(0, PageOrder.pagerIndex(LeftToRight, 0, 10))
        assertEquals(3, PageOrder.pagerIndex(LeftToRight, 3, 10))
        assertEquals(9, PageOrder.pagerIndex(LeftToRight, 9, 10))
    }

    @Test
    fun pagerIndexIsReversedForRightToLeft() {
        assertEquals(9, PageOrder.pagerIndex(RightToLeft, 0, 10))
        assertEquals(6, PageOrder.pagerIndex(RightToLeft, 3, 10))
        assertEquals(0, PageOrder.pagerIndex(RightToLeft, 9, 10))
    }

    @Test
    fun pagerIndexToLogicalIndexIsAnInvolution() {
        for (direction in listOf(LeftToRight, RightToLeft)) {
            for (index in 0 until 10) {
                val pagerIndex = PageOrder.pagerIndex(direction, index, 10)
                assertEquals(index, PageOrder.logicalIndex(direction, pagerIndex, 10))
            }
        }
    }

    @Test
    fun pagerIndexClampsForDegenerateCounts() {
        assertEquals(0, PageOrder.pagerIndex(RightToLeft, 0, 0))
        assertEquals(0, PageOrder.pagerIndex(RightToLeft, 0, 1))
        assertEquals(0, PageOrder.pagerIndex(LeftToRight, 0, 1))
    }

    @Test
    fun leftAndRightPageMatchNaturalOrderForLeftToRight() {
        assertEquals(4, PageOrder.leftPage(LeftToRight, 4, 5))
        assertEquals(5, PageOrder.rightPage(LeftToRight, 4, 5))
    }

    @Test
    fun leftAndRightPageAreSwappedForRightToLeft() {
        assertEquals(5, PageOrder.leftPage(RightToLeft, 4, 5))
        assertEquals(4, PageOrder.rightPage(RightToLeft, 4, 5))
    }

    @Test
    fun stepAdvancesForwardForLeftToRight() {
        assertEquals(1, PageOrder.step(LeftToRight))
    }

    @Test
    fun stepAdvancesBackwardForRightToLeft() {
        assertEquals(-1, PageOrder.step(RightToLeft))
    }

    @Test
    fun tapZoneMatchesScreenSideForLeftToRight() {
        assertEquals(TapZone.Previous, PageOrder.tapZone(LeftToRight, 0.1f, 0.28f, 0.72f))
        assertEquals(TapZone.Center, PageOrder.tapZone(LeftToRight, 0.5f, 0.28f, 0.72f))
        assertEquals(TapZone.Next, PageOrder.tapZone(LeftToRight, 0.9f, 0.28f, 0.72f))
    }

    @Test
    fun tapZoneIsMirroredForRightToLeft() {
        assertEquals(TapZone.Next, PageOrder.tapZone(RightToLeft, 0.1f, 0.28f, 0.72f))
        assertEquals(TapZone.Center, PageOrder.tapZone(RightToLeft, 0.5f, 0.28f, 0.72f))
        assertEquals(TapZone.Previous, PageOrder.tapZone(RightToLeft, 0.9f, 0.28f, 0.72f))
    }

    @Test
    fun spreadsPairFromTheCoverByDefault() {
        assertEquals(3, PageOrder.spreadCount(pageCount = 5, coverAlone = false))
        assertEquals(1, PageOrder.spreadIndex(pageIndex = 3, coverAlone = false))
        assertEquals(2, PageOrder.spreadFirstPage(spreadIndex = 1, coverAlone = false))
    }

    @Test
    fun coverAloneShiftsPairingByOnePage() {
        assertEquals(3, PageOrder.spreadCount(pageCount = 5, coverAlone = true))
        assertEquals(4, PageOrder.spreadCount(pageCount = 6, coverAlone = true))
        assertEquals(0, PageOrder.spreadIndex(pageIndex = 0, coverAlone = true))
        assertEquals(1, PageOrder.spreadIndex(pageIndex = 1, coverAlone = true))
        assertEquals(1, PageOrder.spreadIndex(pageIndex = 2, coverAlone = true))
        assertEquals(-1, PageOrder.spreadFirstPage(spreadIndex = 0, coverAlone = true))
        assertEquals(1, PageOrder.spreadFirstPage(spreadIndex = 1, coverAlone = true))
    }

    @Test
    fun spreadHalvesOnlyExposeTheirOuterEdge() {
        assertEquals(TapZone.Previous, TapZones.LeftHalf.at(LeftToRight, 0.1f))
        assertEquals(TapZone.Center, TapZones.LeftHalf.at(LeftToRight, 0.9f))
        assertEquals(TapZone.Center, TapZones.RightHalf.at(LeftToRight, 0.1f))
        assertEquals(TapZone.Next, TapZones.RightHalf.at(LeftToRight, 0.9f))
        assertEquals(TapZone.Next, TapZones.LeftHalf.at(RightToLeft, 0.1f))
        assertEquals(TapZone.Previous, TapZones.RightHalf.at(RightToLeft, 0.9f))
    }
}
