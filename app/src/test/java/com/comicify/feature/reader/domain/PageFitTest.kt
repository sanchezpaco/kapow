package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

private val Screen = Size(1000f, 2000f)
private const val DENSE_PAGE_ASPECT = 0.5f
private const val WIDE_PAGE_ASPECT = 2f

class PageFitTest {

    private val delta = 0.01f

    @Test
    fun fitScreenLeavesTheContentAtTheContainerSize() {
        assertEquals(Screen, PageFit.content(Screen, DENSE_PAGE_ASPECT, fitWidth = false))
    }

    @Test
    fun fitWidthFillsTheWidthAndOverflowsDown() {
        val content = PageFit.content(Screen, DENSE_PAGE_ASPECT, fitWidth = true)
        assertEquals(Screen.width, content.width, delta)
        assertEquals(2000f, content.height, delta)
    }

    @Test
    fun fitWidthOnAWidePageStaysShorterThanTheScreen() {
        val content = PageFit.content(Screen, WIDE_PAGE_ASPECT, fitWidth = true)
        assertEquals(500f, content.height, delta)
        assertEquals(Offset.Zero, PageFit.panBounds(Screen, content, scale = 1f))
    }

    @Test
    fun anUnmeasuredAspectFallsBackToFitScreen() {
        assertEquals(Screen, PageFit.content(Screen, pageAspect = 0f, fitWidth = true))
    }

    @Test
    fun fitScreenPansOnlyOnceZoomed() {
        assertEquals(Offset.Zero, PageFit.panBounds(Screen, Screen, scale = 1f))
        assertEquals(Offset(250f, 500f), PageFit.panBounds(Screen, Screen, scale = 1.5f))
    }

    @Test
    fun fitWidthPansVerticallyAtTheBaseScale() {
        val content = PageFit.content(Screen, 0.4f, fitWidth = true)
        val bounds = PageFit.panBounds(Screen, content, scale = 1f)
        assertEquals(0f, bounds.x, delta)
        assertEquals(250f, bounds.y, delta)
    }

    @Test
    fun aPageLandsOnItsTopEdgeHorizontallyCentred() {
        val content = PageFit.content(Screen, 0.4f, fitWidth = true)
        val landing = PageFit.atTopEdge(PageFit.panBounds(Screen, content, scale = 1f))
        assertEquals(Offset(0f, 250f), landing)
    }

    @Test
    fun panningStopsAtThePageEdges() {
        val bounds = Offset(0f, 250f)
        val down = PageFit.clamp(Offset(80f, 900f), bounds)
        assertEquals(0f, down.x, delta)
        assertEquals(250f, down.y, delta)
        val up = PageFit.clamp(Offset(-80f, -900f), bounds)
        assertEquals(0f, up.x, delta)
        assertEquals(-250f, up.y, delta)
    }

    @Test
    fun doubleTapUnderFitScreenZoomsInAndBackOut() {
        assertEquals(
            PageZoomAction.ZoomIn,
            PageFit.doubleTap(fitWidth = false, framedToWidth = false, zoomed = false),
        )
        assertEquals(
            PageZoomAction.ZoomOut,
            PageFit.doubleTap(fitWidth = false, framedToWidth = false, zoomed = true),
        )
    }

    @Test
    fun doubleTapUnderFitWidthShowsTheOtherFraming() {
        assertEquals(
            PageZoomAction.ToFitScreen,
            PageFit.doubleTap(fitWidth = true, framedToWidth = true, zoomed = false),
        )
        assertEquals(
            PageZoomAction.ToFitWidth,
            PageFit.doubleTap(fitWidth = true, framedToWidth = false, zoomed = false),
        )
    }

    @Test
    fun doubleTapUnderFitWidthNeverZoomsIn() {
        assertEquals(
            PageZoomAction.ToFitScreen,
            PageFit.doubleTap(fitWidth = true, framedToWidth = true, zoomed = true),
        )
    }
}
