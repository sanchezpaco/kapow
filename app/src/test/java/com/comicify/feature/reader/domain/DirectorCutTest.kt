package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectorCutTest {

    private val wholePage = Rect(0f, 0f, 1f, 1f)
    private val topLeftPanel = Rect(0.05f, 0.05f, 0.45f, 0.45f)
    private val topRightPanel = Rect(0.55f, 0.05f, 0.95f, 0.45f)
    private val closeUp = Rect(0.1f, 0.1f, 0.25f, 0.25f)

    private fun cut(from: Rect, to: Rect, pageChanged: Boolean = false, forward: Boolean = true) =
        DirectorCut.between(from, to, pageChanged, forward)

    @Test
    fun neighbouringPanelsOfTheSameSizePanFasterTheCloserTheyAre() {
        val far = cut(topLeftPanel, topRightPanel)
        val near = cut(topLeftPanel, topLeftPanel.translate(0.05f, 0f))
        assertTrue(near.durationMillis < far.durationMillis)
        assertTrue(far.durationMillis < 520)
    }

    @Test
    fun onlyALongTravelArcs() {
        assertFalse(cut(topLeftPanel, topLeftPanel.translate(0.05f, 0f)).arcing)
        assertTrue(cut(topLeftPanel, topRightPanel).arcing)
    }

    @Test
    fun theArcApexIsWiderThanBothEndsAndStaysInsideThePage() {
        val arc = cut(topLeftPanel, topRightPanel)
        val apex = DirectorCut.apex(topLeftPanel, topRightPanel, arc.lift)
        assertTrue(apex.width > topLeftPanel.width)
        assertTrue(apex.width > topRightPanel.width)
        assertTrue(apex.left >= 0f && apex.right <= 1f && apex.top >= 0f && apex.bottom <= 1f)
    }

    @Test
    fun aMuchSmallerTargetPushesInAndAMuchLargerOnePullsBack() {
        assertEquals(560, cut(topLeftPanel, closeUp).durationMillis)
        assertEquals(340, cut(closeUp, topLeftPanel).durationMillis)
    }

    @Test
    fun arrivingForwardOnAWholePageStopRevealsFromBlack() {
        assertTrue(cut(closeUp, wholePage).fromBlack)
        assertFalse(cut(closeUp, wholePage, forward = false).fromBlack)
    }

    @Test
    fun aPageChangeJumpsInsteadOfTravellingAcrossTwoPages() {
        val turn = cut(topRightPanel, topLeftPanel, pageChanged = true)
        assertTrue(turn.jump)
        assertFalse(turn.arcing)
    }
}
