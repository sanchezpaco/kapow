package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Rect
import com.comicify.domain.model.ReadingDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedTourTest {

    private val topLeft = Rect(0.02f, 0.02f, 0.30f, 0.30f)
    private val topRight = Rect(0.70f, 0.02f, 0.98f, 0.30f)
    private val bottomLeft = Rect(0.02f, 0.70f, 0.30f, 0.98f)
    private val bottomRight = Rect(0.70f, 0.70f, 0.98f, 0.98f)
    private val ltr = ReadingDirection.LeftToRight
    private val rtl = ReadingDirection.RightToLeft

    @Test
    fun cleanGridPassesThroughUntouchedInReadingOrder() {
        val grid = listOf(bottomRight, topRight, bottomLeft, topLeft)
        assertEquals(listOf(topLeft, topRight, bottomLeft, bottomRight), GuidedTour.stops(grid, emptyList(), ltr))
    }

    @Test
    fun rightToLeftReversesEachRow() {
        val grid = listOf(topLeft, topRight, bottomLeft, bottomRight)
        assertEquals(listOf(topRight, topLeft, bottomRight, bottomLeft), GuidedTour.stops(grid, emptyList(), rtl))
    }

    @Test
    fun splashWithNoPanelsToursItsBubblesOverTheArt() {
        val bubbleA = Rect(0.10f, 0.08f, 0.25f, 0.16f)
        val bubbleB = Rect(0.60f, 0.55f, 0.80f, 0.68f)
        val stops = GuidedTour.stops(emptyList(), listOf(bubbleB, bubbleA), ltr)
        assertEquals(stops.toString(), 3, stops.size)
        assertEquals(Rect(0f, 0f, 1f, 1f), stops[0])
        val windows = stops.drop(1)
        val stopOfA = windows.indexOfFirst { it.contains(bubbleA.center) }
        val stopOfB = windows.indexOfFirst { it.contains(bubbleB.center) }
        assertTrue(stops.toString(), stopOfA >= 0 && stopOfB >= 0)
        assertTrue(stops.toString(), stopOfA < stopOfB)
        assertTrue(windows.all { it.width >= 0.41f && it.height >= 0.29f })
    }

    @Test
    fun bubblesOutsideEveryPanelBecomeTheirOwnStops() {
        val panel = topRight
        val orphan = Rect(0.05f, 0.40f, 0.22f, 0.52f)
        val stops = GuidedTour.stops(listOf(panel), listOf(orphan), ltr)
        assertTrue(stops.any { it.contains(orphan.center) })
        assertTrue(stops.contains(panel))
    }

    @Test
    fun oversizedPanelWithSpreadBubblesSplitsIntoSubStopsAfterAnEstablishingStop() {
        val big = Rect(0.0f, 0.0f, 1.0f, 0.6f)
        val left = Rect(0.05f, 0.10f, 0.20f, 0.22f)
        val right = Rect(0.78f, 0.10f, 0.95f, 0.22f)
        val stops = GuidedTour.stops(listOf(big), listOf(left, right), ltr)
        assertEquals(listOf(big), stops.take(1))
        assertEquals(3, stops.size)
        assertTrue(stops[1].center.x < stops[2].center.x)
        assertTrue(stops.toString(), stops.drop(1).all { it.top >= big.top && it.bottom <= big.bottom && it.left >= big.left && it.right <= big.right })
    }

    @Test
    fun denseSplashIsShownWholeBeforeItsWindows() {
        val splash = Rect(0f, 0f, 1f, 1f)
        val bubbles = listOf(
            Rect(0.05f, 0.05f, 0.20f, 0.12f), Rect(0.70f, 0.05f, 0.90f, 0.12f),
            Rect(0.05f, 0.60f, 0.20f, 0.68f), Rect(0.70f, 0.85f, 0.90f, 0.95f),
        )
        val stops = GuidedTour.stops(listOf(splash), bubbles, ltr)
        assertEquals(listOf(splash), stops.take(1))
        assertEquals(5, stops.size)
    }

    @Test
    fun largePanelBesideOtherPanelsGetsNoEstablishingStop() {
        val strip = Rect(0f, 0f, 1f, 0.3f)
        val big = Rect(0f, 0.31f, 1f, 1f)
        val bubbles = listOf(Rect(0.05f, 0.35f, 0.20f, 0.42f), Rect(0.75f, 0.85f, 0.95f, 0.95f))
        val stops = GuidedTour.stops(listOf(strip, big), bubbles, ltr)
        assertEquals(listOf(strip), stops.take(1))
        assertFalse(stops.toString(), stops.contains(big))
        assertEquals(3, stops.size)
    }

    @Test
    fun tinyBoxInsideAPanelIsAbsorbed() {
        val panel = Rect(0.5f, 0.6f, 1f, 1f)
        val credits = Rect(0.55f, 0.95f, 0.95f, 1f)
        assertEquals(listOf(topLeft, panel), GuidedTour.stops(listOf(topLeft, panel, credits), emptyList(), ltr))
    }

    @Test
    fun windowEdgesNeverSliceANeighbouringCaption() {
        val splash = Rect(0f, 0f, 1f, 1f)
        val lone = Rect(0.03f, 0.05f, 0.20f, 0.12f)
        val stack = listOf(Rect(0.30f, 0.02f, 0.60f, 0.08f), Rect(0.30f, 0.12f, 0.60f, 0.18f), Rect(0.30f, 0.22f, 0.60f, 0.28f))
        val far = Rect(0.70f, 0.80f, 0.95f, 0.90f)
        val stops = GuidedTour.stops(listOf(splash), listOf(lone) + stack + listOf(far), ltr)
        val bubbles = listOf(lone) + stack + listOf(far)
        for (stop in stops) for (bubble in bubbles) {
            val overlaps = stop.overlaps(bubble)
            val contains = bubble.left >= stop.left && bubble.top >= stop.top && bubble.right <= stop.right && bubble.bottom <= stop.bottom
            assertTrue("stop $stop slices $bubble", !overlaps || contains)
        }
        assertTrue(stops.toString(), stops.any { it.right < stack[0].left && it.left <= lone.left })
    }

    @Test
    fun subStopWindowsThatMostlyOverlapMergeIntoOne() {
        val splash = Rect(0f, 0f, 1f, 1f)
        val bubbles = listOf(
            Rect(0.05f, 0.05f, 0.25f, 0.15f),
            Rect(0.30f, 0.05f, 0.50f, 0.15f),
            Rect(0.60f, 0.70f, 0.80f, 0.80f),
        )
        val stops = GuidedTour.stops(listOf(splash), bubbles, ltr)
        assertEquals(stops.toString(), 3, stops.size)
        assertTrue(stops[1].contains(bubbles[0].center) && stops[1].contains(bubbles[1].center))
    }

    @Test
    fun tallPanelSpanningTwoRowsIsFollowedByItsRightColumnTopDown() {
        val tall = Rect(0.00f, 0.00f, 0.45f, 0.72f)
        val topRightWide = Rect(0.45f, 0.02f, 0.97f, 0.25f)
        val midLeft = Rect(0.45f, 0.26f, 0.70f, 0.71f)
        val midRight = Rect(0.71f, 0.26f, 0.97f, 0.72f)
        val bottom = Rect(0.00f, 0.73f, 1.00f, 1.00f)
        val stops = GuidedTour.stops(listOf(tall, midLeft, midRight, topRightWide, bottom), emptyList(), ltr)
        assertEquals(listOf(tall, topRightWide, midLeft, midRight, bottom), stops)
    }

    @Test
    fun containerPanelContributesOnlyTheStripItsChildrenLeaveFree() {
        val top = Rect(0.00f, 0.00f, 1.00f, 0.31f)
        val container = Rect(0.00f, 0.31f, 1.00f, 1.00f)
        val faces = listOf(
            Rect(0.03f, 0.32f, 0.26f, 0.65f), Rect(0.27f, 0.32f, 0.49f, 0.63f),
            Rect(0.50f, 0.32f, 0.73f, 0.65f), Rect(0.74f, 0.32f, 0.97f, 0.65f),
        )
        val faceBubble = Rect(0.05f, 0.35f, 0.20f, 0.40f)
        val bottomBubble = Rect(0.05f, 0.68f, 0.25f, 0.75f)
        val stops = GuidedTour.stops(listOf(top, container) + faces, listOf(faceBubble, bottomBubble), ltr)
        assertEquals(stops.toString(), 6, stops.size)
        assertEquals(listOf(top) + faces, stops.take(5))
        assertFalse(stops.contains(container))
        assertTrue(stops.last().contains(bottomBubble.center))
        assertTrue(stops.last().top >= 0.64f)
    }

    @Test
    fun subStopWindowsStayInsideTheirPanel() {
        val above = Rect(0.02f, 0.35f, 0.99f, 0.52f)
        val big = Rect(0.03f, 0.52f, 1.00f, 0.99f)
        val bubbles = listOf(Rect(0.10f, 0.54f, 0.30f, 0.60f), Rect(0.70f, 0.85f, 0.90f, 0.92f))
        val stops = GuidedTour.stops(listOf(above, big), bubbles, ltr)
        assertTrue(stops.toString(), stops.drop(2).all { it.top >= big.top })
    }

    @Test
    fun adjacentBubblesShareOneStop() {
        val big = Rect(0.0f, 0.0f, 1.0f, 0.6f)
        val first = Rect(0.40f, 0.10f, 0.52f, 0.20f)
        val second = Rect(0.54f, 0.10f, 0.66f, 0.20f)
        val stops = GuidedTour.stops(listOf(big), listOf(first, second), ltr)
        assertEquals(listOf(big), stops)
    }

    @Test
    fun mangaPanelsAreNotOverSplitByTheirBubbles() {
        val panels = listOf(
            Rect(0.52f, 0.02f, 0.98f, 0.30f), Rect(0.02f, 0.02f, 0.48f, 0.30f),
            Rect(0.52f, 0.34f, 0.98f, 0.62f), Rect(0.02f, 0.34f, 0.48f, 0.62f),
        )
        val bubbles = listOf(
            Rect(0.60f, 0.05f, 0.75f, 0.12f), Rect(0.10f, 0.05f, 0.25f, 0.12f),
            Rect(0.60f, 0.40f, 0.75f, 0.47f), Rect(0.10f, 0.40f, 0.25f, 0.47f),
        )
        val stops = GuidedTour.stops(panels, bubbles, rtl)
        assertEquals(4, stops.size)
        assertTrue(stops.all { stop -> panels.any { it == stop } })
    }

    @Test
    fun spreadClustersInABigPanelStayDistinctStops() {
        val panel = Rect(0f, 0f, 1f, 1f)
        val bubbles = listOf(
            Rect(0.10f, 0.10f, 0.25f, 0.18f),
            Rect(0.45f, 0.10f, 0.60f, 0.18f),
            Rect(0.80f, 0.10f, 0.95f, 0.18f),
        )
        val stops = GuidedTour.stops(listOf(panel), bubbles, ltr)
        assertEquals(4, stops.size)
        for (i in 2 until stops.size) {
            assertTrue(kotlin.math.abs(stops[i].center.x - stops[i - 1].center.x) > 0.1f)
        }
    }

    @Test
    fun subStopWindowCoversBubblesBeyondTheDetectedPanel() {
        val panel = Rect(0f, 0f, 0.85f, 1f)
        val inside = Rect(0.05f, 0.10f, 0.20f, 0.20f)
        val beyond = Rect(0.88f, 0.10f, 0.98f, 0.20f)
        val stops = GuidedTour.stops(listOf(panel), listOf(inside, beyond), ltr)
        assertTrue(stops.any { it.contains(beyond.center) })
    }

    @Test
    fun needsBubblesSkipsDenseCoveringGrids() {
        val dense = listOf(
            Rect(0f, 0f, 0.5f, 0.33f), Rect(0.5f, 0f, 1f, 0.33f),
            Rect(0f, 0.33f, 0.5f, 0.66f), Rect(0.5f, 0.33f, 1f, 0.66f),
            Rect(0f, 0.66f, 0.5f, 1f), Rect(0.5f, 0.66f, 1f, 1f),
        )
        assertFalse(GuidedTour.needsBubbles(dense))
    }

    @Test
    fun needsBubblesTriggersOnSplashesAndSparsePages() {
        assertTrue(GuidedTour.needsBubbles(emptyList()))
        assertTrue(GuidedTour.needsBubbles(listOf(Rect(0f, 0f, 1f, 1f))))
        assertTrue(GuidedTour.needsBubbles(listOf(topLeft, topRight)))
    }
}
