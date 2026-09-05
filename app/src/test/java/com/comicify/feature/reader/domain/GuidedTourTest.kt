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
    fun adjacentBubblesOnASplashShareOneWindowAfterTheOpener() {
        val big = Rect(0.0f, 0.0f, 1.0f, 0.6f)
        val first = Rect(0.40f, 0.10f, 0.52f, 0.20f)
        val second = Rect(0.54f, 0.10f, 0.66f, 0.20f)
        val stops = GuidedTour.stops(listOf(big), listOf(first, second), ltr)
        assertEquals(stops.toString(), 2, stops.size)
        assertEquals(big, stops[0])
        assertTrue(stops.toString(), stops[1].contains(first.center) && stops[1].contains(second.center))
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
    fun panelStopGrowsOverItsOverhangingBalloon() {
        val left = Rect(0.02f, 0.02f, 0.48f, 0.50f)
        val right = Rect(0.52f, 0.02f, 0.98f, 0.50f)
        val overhanging = Rect(0.38f, 0.10f, 0.56f, 0.20f)
        val stops = GuidedTour.stops(listOf(left, right), listOf(overhanging), ltr)
        assertEquals(stops.toString(), 2, stops.size)
        assertEquals(Rect(0.02f, 0.02f, 0.57f, 0.50f), stops[0])
        assertTrue(stops.toString(), stops[1].left > overhanging.right && stops[1].width >= 0.7f * right.width)
    }

    @Test
    fun balloonHangingOverTheGutterStaysWithThePanelItMostlyCovers() {
        val left = Rect(0.02f, 0.02f, 0.48f, 0.50f)
        val right = Rect(0.60f, 0.02f, 0.98f, 0.50f)
        val hanging = Rect(0.40f, 0.10f, 0.58f, 0.20f)
        val stops = GuidedTour.stops(listOf(left, right), listOf(hanging), ltr)
        assertEquals(stops.toString(), 2, stops.size)
        assertTrue(stops.toString(), stops[0].left == left.left && stops[0].right >= hanging.right)
    }

    @Test
    fun neighbourShrinksPastABalloonItDoesNotOwnSoItIsReadOnce() {
        val left = Rect(0.02f, 0.02f, 0.48f, 0.50f)
        val right = Rect(0.52f, 0.02f, 0.98f, 0.50f)
        val shared = Rect(0.40f, 0.10f, 0.66f, 0.20f)
        val stops = GuidedTour.stops(listOf(left, right), listOf(shared), ltr)
        assertEquals(stops.toString(), 2, stops.size)
        assertTrue(stops.toString(), stops[0].right < shared.left && stops[0].width >= 0.7f * left.width)
        assertTrue(stops.toString(), stops[1].left <= shared.left && stops[1].right >= shared.right)
    }

    @Test
    fun neighbourGrowsOverABalloonItCannotShrinkPast() {
        val left = Rect(0.02f, 0.02f, 0.48f, 0.50f)
        val right = Rect(0.52f, 0.02f, 0.98f, 0.50f)
        val wide = Rect(0.20f, 0.10f, 0.66f, 0.20f)
        val stops = GuidedTour.stops(listOf(left, right), listOf(wide), ltr)
        assertEquals(stops.toString(), 2, stops.size)
        assertTrue(stops.toString(), stops.all { it.left <= wide.left && it.right >= wide.right })
    }

    @Test
    fun orphanCaptionAlreadyShownWholeByAPanelStopGetsNoWindow() {
        val photo = Rect(0.02f, 0.02f, 0.48f, 0.40f)
        val other = Rect(0.52f, 0.52f, 0.98f, 0.98f)
        val owned = Rect(0.30f, 0.30f, 0.55f, 0.38f)
        val orphan = Rect(0.50f, 0.20f, 0.60f, 0.26f)
        val stops = GuidedTour.stops(listOf(photo, other), listOf(owned, orphan), ltr)
        assertEquals(stops.toString(), 2, stops.size)
        assertTrue(stops.toString(), stops[0].contains(orphan.center))
    }

    @Test
    fun floatingCaptionOverhangingAColumnDoesNotBlockTheColumnCut() {
        val tallLeft = Rect(0.0f, 0.09f, 0.49f, 0.51f)
        val topRight = Rect(0.51f, 0.05f, 1.0f, 0.24f)
        val midRight = Rect(0.51f, 0.25f, 1.0f, 0.51f)
        val caption = Rect(0.30f, 0.01f, 0.55f, 0.08f)
        val stops = GuidedTour.stops(listOf(tallLeft, topRight, midRight), listOf(caption), ltr)
        assertEquals(stops.toString(), 4, stops.size)
        assertTrue(stops.toString(), stops[0].contains(caption.center))
        assertEquals(listOf(tallLeft, topRight, midRight), stops.drop(1))
    }

    @Test
    fun balloonBarelyTouchingAPanelIsAnOrphan() {
        val panel = topRight
        val touching = Rect(0.55f, 0.25f, 0.75f, 0.35f)
        val stops = GuidedTour.stops(listOf(panel), listOf(touching), ltr)
        assertEquals(stops.toString(), 2, stops.size)
        assertTrue(stops.toString(), stops[0].left > touching.right && stops[0].width >= 0.7f * panel.width)
        assertTrue(stops.toString(), stops[1].contains(touching.center))
    }

    @Test
    fun splashWhoseWindowsMergeIntoOneStillGetsThatWindow() {
        val splash = Rect(0f, 0f, 1f, 1f)
        val bubbles = listOf(Rect(0.05f, 0.05f, 0.25f, 0.12f), Rect(0.05f, 0.22f, 0.25f, 0.29f))
        val stops = GuidedTour.stops(listOf(splash), bubbles, ltr)
        assertEquals(stops.toString(), 2, stops.size)
        assertEquals(splash, stops[0])
        assertTrue(stops.toString(), stops[1].height < 0.5f && bubbles.all { stops[1].contains(it.center) })
    }

    @Test
    fun orderFollowsTheBalloonsNotTheirWindows() {
        val tallStrip = Rect(0.284f, 0.144f, 0.732f, 0.976f)
        val right = Rect(0.732f, 0.106f, 0.998f, 0.508f)
        val inStrip = Rect(0.395f, 0.262f, 0.599f, 0.317f)
        val highLeft = Rect(0.037f, 0.049f, 0.212f, 0.102f)
        val lowLeft = Rect(0.035f, 0.326f, 0.229f, 0.378f)
        val stops = GuidedTour.stops(listOf(tallStrip, right), listOf(inStrip, lowLeft, highLeft), ltr)
        assertEquals(stops.toString(), 4, stops.size)
        assertTrue(stops.toString(), stops[0].contains(highLeft.center) && stops[1].contains(lowLeft.center))
        assertEquals(listOf(tallStrip, right), stops.drop(2))
    }

    @Test
    fun slightPanelOvershootDoesNotBlockTheColumnCut() {
        val topRightWide = Rect(0.351f, 0.001f, 0.999f, 0.43f)
        val topLeftNarrow = Rect(0.034f, 0.0f, 0.33f, 0.432f)
        val tallRight = Rect(0.554f, 0.461f, 0.94f, 0.997f)
        val band = Rect(0.033f, 0.461f, 0.567f, 0.585f)
        val bottomLeftBig = Rect(0.0f, 0.601f, 0.53f, 0.999f)
        val stops = GuidedTour.stops(listOf(topLeftNarrow, topRightWide, bottomLeftBig, tallRight, band), emptyList(), rtl)
        assertEquals(listOf(topRightWide, topLeftNarrow, tallRight, band, bottomLeftBig), stops)
    }

    @Test
    fun containerRemainderWithoutTextIsNotAStop() {
        val page = Rect(0f, 0f, 1f, 1f)
        val left = Rect(0.09f, 0.07f, 0.50f, 0.47f)
        val right = Rect(0.52f, 0.07f, 0.98f, 0.47f)
        val bottom = Rect(0.09f, 0.48f, 0.98f, 0.94f)
        val bubbles = listOf(Rect(0.12f, 0.10f, 0.30f, 0.16f), Rect(0.60f, 0.50f, 0.80f, 0.56f))
        val stops = GuidedTour.stops(listOf(page, left, right, bottom), bubbles, ltr)
        assertEquals(listOf(left, right, bottom), stops)
    }

    @Test
    fun stopEdgeCuttingAForeignBalloonByMoreThanAHairMovesPastIt() {
        val left = Rect(0.02f, 0.02f, 0.48f, 0.50f)
        val right = Rect(0.52f, 0.02f, 0.98f, 0.50f)
        val overhanging = Rect(0.45f, 0.10f, 0.75f, 0.20f)
        val stops = GuidedTour.stops(listOf(left, right), listOf(overhanging), ltr)
        assertEquals(stops.toString(), 2, stops.size)
        assertTrue(stops.toString(), stops[0].right < overhanging.left)
        assertTrue(stops.toString(), stops[1].left <= overhanging.left)
    }

    @Test
    fun pageSizedBoxDoesNotBuryTheStripAPanelKeepsBesideItsInset() {
        val page = Rect(0f, 0f, 1f, 1f)
        val top = Rect(0.11f, 0.09f, 0.96f, 0.37f)
        val inset = Rect(0.10f, 0.06f, 0.33f, 0.34f)
        val tier = Rect(0.13f, 0.38f, 0.96f, 0.95f)
        val bubbles = listOf(Rect(0.15f, 0.08f, 0.23f, 0.12f), Rect(0.54f, 0.14f, 0.81f, 0.23f), Rect(0.41f, 0.40f, 0.74f, 0.44f))
        val stops = GuidedTour.stops(listOf(page, top, inset, tier), bubbles, ltr)
        assertEquals(stops.toString(), 3, stops.size)
        assertEquals(inset, stops[0])
        assertTrue(stops.toString(), stops[1].left >= inset.right && stops[1].bottom <= tier.top && stops[1].contains(bubbles[1].center))
        assertEquals(tier, stops[2])
    }

    @Test
    fun wordlessRemainderWideEnoughToReadStaysAStop() {
        val top = Rect(0.04f, 0.03f, 0.94f, 0.53f)
        val bottom = Rect(0.04f, 0.71f, 0.93f, 0.97f)
        val inset = Rect(0.54f, 0.75f, 0.94f, 0.96f)
        val bubble = Rect(0.30f, 0.10f, 0.45f, 0.20f)
        val stops = GuidedTour.stops(listOf(top, bottom, inset), listOf(bubble), ltr)
        assertEquals(stops.toString(), 3, stops.size)
        assertTrue(stops.toString(), stops[1].right <= inset.left && stops[1].width >= 0.4f)
        assertEquals(inset, stops[2])
    }

    @Test
    fun tallPanelOverlappingTheColumnBesideItIsReadAfterThatColumn() {
        val midLeft = Rect(0.02f, 0.35f, 0.46f, 0.71f)
        val bottomLeft = Rect(0.02f, 0.65f, 0.29f, 0.97f)
        val bottomMid = Rect(0.27f, 0.62f, 0.56f, 0.97f)
        val tallRight = Rect(0.39f, 0.32f, 1.00f, 0.99f)
        val topRow = listOf(Rect(0.02f, 0.02f, 0.28f, 0.36f), Rect(0.24f, 0.02f, 0.51f, 0.35f), Rect(0.48f, 0.02f, 0.73f, 0.34f), Rect(0.71f, 0.02f, 0.98f, 0.33f))
        val stops = GuidedTour.stops(listOf(tallRight, bottomMid, bottomLeft, midLeft) + topRow, emptyList(), ltr)
        assertEquals(topRow + listOf(midLeft, bottomLeft, bottomMid, tallRight), stops)
        val mirrored = GuidedTour.stops(listOf(tallRight, bottomMid, bottomLeft, midLeft), emptyList(), rtl)
        assertEquals(mirrored.toString(), tallRight, mirrored.first())
    }

    @Test
    fun tallColumnOvershootingABandAboveItStillLeadsThePage() {
        val band = Rect(0.00f, 0.00f, 1.00f, 0.16f)
        val column = Rect(0.04f, 0.04f, 0.19f, 0.94f)
        val row = listOf(Rect(0.19f, 0.10f, 0.43f, 0.41f), Rect(0.44f, 0.09f, 0.74f, 0.41f), Rect(0.74f, 0.07f, 0.93f, 0.41f))
        val tier = Rect(0.20f, 0.42f, 0.94f, 0.62f)
        val bottom = listOf(Rect(0.19f, 0.64f, 0.66f, 0.94f), Rect(0.67f, 0.69f, 0.94f, 0.93f))
        val stops = GuidedTour.stops(listOf(band, column) + row + listOf(tier) + bottom, emptyList(), ltr)
        assertEquals(listOf(column, band) + row + listOf(tier) + bottom, stops)
    }

    @Test
    fun tallerPanelSharingItsRowIsNotAColumn() {
        val topLeft = Rect(0.04f, 0.02f, 0.32f, 0.45f)
        val topMiddle = Rect(0.35f, 0.02f, 0.63f, 0.45f)
        val topRightTaller = Rect(0.64f, 0.00f, 1.00f, 0.52f)
        val tier = Rect(0.04f, 0.47f, 0.96f, 0.63f)
        val bottom = Rect(0.04f, 0.65f, 0.96f, 0.80f)
        val stops = GuidedTour.stops(listOf(tier, topRightTaller, bottom, topMiddle, topLeft), emptyList(), ltr)
        assertEquals(listOf(topLeft, topMiddle, topRightTaller, tier, bottom), stops)
    }

    @Test
    fun wordlessPageWhosePanelsCoverLessThanHalfIsShownWhole() {
        val fragments = listOf(Rect(0.0f, 0.0f, 0.5f, 0.55f), Rect(0.0f, 0.6f, 0.37f, 1.0f))
        assertEquals(listOf(Rect(0f, 0f, 1f, 1f)), GuidedTour.stops(fragments, emptyList(), ltr))
    }

    @Test
    fun tinyBoxSwallowedByTheGrownPanelBeforeItIsNotAStop() {
        val panel = Rect(0.06f, 0.64f, 0.30f, 0.79f)
        val captionBox = Rect(0.19f, 0.76f, 0.41f, 0.83f)
        val below = Rect(0.04f, 0.88f, 0.42f, 1.00f)
        val balloon = Rect(0.10f, 0.70f, 0.42f, 0.78f)
        val caption = Rect(0.20f, 0.77f, 0.40f, 0.82f)
        val stops = GuidedTour.stops(listOf(panel, captionBox, below), listOf(balloon, caption), ltr)
        assertEquals(stops.toString(), 2, stops.size)
        assertTrue(stops.toString(), stops[0].contains(caption.center) && stops[0].contains(balloon.center))
        assertEquals(below, stops[1])
    }

    @Test
    fun captionBoxOnAPaintedPageIsNotAFragmentThatKeepsItsPanels() {
        val painting = Rect(0.02f, 0.02f, 0.98f, 0.98f)
        val captionBox = Rect(0.30f, 0.04f, 0.62f, 0.12f)
        val fragment = Rect(0.34f, 0.52f, 0.60f, 0.96f)
        val caption = Rect(0.31f, 0.05f, 0.61f, 0.11f)
        val balloon = Rect(0.10f, 0.60f, 0.30f, 0.70f)
        val stops = GuidedTour.stops(listOf(painting, captionBox, fragment), listOf(caption, balloon), ltr)
        assertEquals(stops.toString(), Rect(0f, 0f, 1f, 1f), stops[0])
        assertFalse(stops.toString(), stops.contains(fragment))
        assertTrue(stops.toString(), stops.any { it.contains(caption.center) })
    }

    @Test
    fun twoBoxesAroundTheSamePanelAreOneStop() {
        val left = Rect(0.02f, 0.02f, 0.70f, 0.48f)
        val right = Rect(0.14f, 0.04f, 0.98f, 0.50f)
        val below = Rect(0.02f, 0.55f, 0.98f, 0.98f)
        val stops = GuidedTour.stops(listOf(left, right, below), emptyList(), ltr)
        assertEquals(listOf(Rect(0.02f, 0.02f, 0.98f, 0.50f), below), stops)
    }

    @Test
    fun paintedPageOpensWholeAndDropsItsWordlessFragments() {
        val painting = Rect(0.0f, 0.0f, 1.0f, 1.0f)
        val fragment = Rect(0.34f, 0.52f, 0.60f, 1.0f)
        val balloonTop = Rect(0.10f, 0.06f, 0.34f, 0.16f)
        val balloonBottom = Rect(0.62f, 0.72f, 0.90f, 0.84f)
        val stops = GuidedTour.stops(listOf(painting, fragment), listOf(balloonTop, balloonBottom), ltr)
        assertEquals(stops.toString(), 3, stops.size)
        assertEquals(painting, stops[0])
        assertTrue(stops.toString(), stops.none { it.contains(fragment.center) && !it.contains(balloonBottom.center) })
    }

    @Test
    fun paintedPageKeepsTheFragmentsThatHoldDialogue() {
        val painting = Rect(0.0f, 0.0f, 1.0f, 1.0f)
        val panel = Rect(0.62f, 0.50f, 0.99f, 0.96f)
        val inPanel = Rect(0.70f, 0.60f, 0.90f, 0.70f)
        val onPainting = Rect(0.08f, 0.06f, 0.30f, 0.18f)
        val stops = GuidedTour.stops(listOf(painting, panel), listOf(inPanel, onPainting), ltr)
        assertEquals(stops.toString(), painting, stops[0])
        assertTrue(stops.toString(), stops.any { it.contains(inPanel.center) && !it.contains(onPainting.center) })
    }

    @Test
    fun panelWhoseBalloonsTheNeighboursAlreadyShowIsNotAStop() {
        val left = Rect(0.00f, 0.38f, 0.50f, 0.95f)
        val middle = Rect(0.29f, 0.45f, 0.81f, 0.96f)
        val right = Rect(0.48f, 0.47f, 1.00f, 0.96f)
        val onTheLeft = Rect(0.35f, 0.50f, 0.45f, 0.56f)
        val onTheRight = Rect(0.55f, 0.50f, 0.70f, 0.56f)
        val stops = GuidedTour.stops(listOf(left, middle, right), listOf(onTheLeft, onTheRight), ltr)
        assertEquals(stops.toString(), 2, stops.size)
        assertTrue(stops.toString(), stops.none { it.contains(onTheLeft.center) && it.contains(onTheRight.center) })
    }

    @Test
    fun readingWindowsShrinkToThePanelScaleOfADensePage() {
        val splash = Rect(0.01f, 0.01f, 0.99f, 0.48f)
        val grid = (0..2).flatMap { column ->
            (0..1).map { row -> Rect(0.01f + column * 0.33f, 0.50f + row * 0.25f, 0.30f + column * 0.33f, 0.72f + row * 0.25f) }
        }
        val left = Rect(0.06f, 0.06f, 0.20f, 0.14f)
        val right = Rect(0.70f, 0.30f, 0.86f, 0.40f)
        val stops = GuidedTour.stops(listOf(splash) + grid, listOf(left, right), ltr)
        val windows = stops.filter { it.width < splash.width }.filter { it.contains(left.center) || it.contains(right.center) }
        assertEquals(stops.toString(), 2, windows.size)
        assertTrue(stops.toString(), windows.all { it.width < 0.42f })
    }
}
