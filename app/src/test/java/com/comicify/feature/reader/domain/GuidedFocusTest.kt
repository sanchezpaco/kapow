package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class GuidedFocusTest {

    private val delta = 0.0001f

    private fun assertRect(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, delta)
        assertEquals(expected.top, actual.top, delta)
        assertEquals(expected.right, actual.right, delta)
        assertEquals(expected.bottom, actual.bottom, delta)
    }

    @Test
    fun framePadsAndClampsToPage() {
        assertRect(Rect(0.1f, 0.1f, 0.5f, 0.5f), GuidedFocus.frame(Rect(0.2f, 0.2f, 0.4f, 0.4f), 0.1f))
        assertRect(Rect(0f, 0f, 0.6f, 0.6f), GuidedFocus.frame(Rect(0f, 0f, 0.4f, 0.4f), 0.1f))
    }

    @Test
    fun zoomFillsTheCanvasAroundFocusAndStaysInsidePage() {
        val page = Rect(0f, 0f, 1f, 1f)
        val image = Size(1000f, 1000f)
        val canvas = Size(500f, 1000f)
        val drawn = GuidedFocus.fit(page, image, canvas)
        val zoomed = GuidedFocus.zoomed(page, drawn, image, canvas, Offset(0.5f, 0.5f), 2f)
        assertRect(Rect(0.25f, 0f, 0.75f, 1f), zoomed)
        val corner = GuidedFocus.zoomed(page, drawn, image, canvas, Offset(0.95f, 0.05f), 4f)
        assertRect(Rect(0.75f, 0f, 1f, 0.5f), corner)
    }

    @Test
    fun panIsClampedToPage() {
        val view = Rect(0.25f, 0.25f, 0.75f, 0.75f)
        assertRect(Rect(0.35f, 0.25f, 0.85f, 0.75f), GuidedFocus.panned(view, Offset(0.1f, 0f)))
        assertRect(Rect(0.5f, 0.25f, 1f, 0.75f), GuidedFocus.panned(view, Offset(0.9f, 0f)))
    }

    @Test
    fun fitLetterboxesTheViewInsideTheCanvas() {
        val drawn = GuidedFocus.fit(Rect(0f, 0f, 1f, 1f), Size(1000f, 1000f), Size(400f, 200f))
        assertRect(Rect(100f, 0f, 300f, 200f), drawn)
    }

    @Test
    fun screenPositionsMapBackToPageCoordinates() {
        val view = Rect(0.25f, 0.25f, 0.75f, 0.75f)
        val drawn = Rect(0f, 0f, 200f, 200f)
        val page = GuidedFocus.toPage(view, drawn, Offset(100f, 50f))
        assertEquals(0.5f, page.x, delta)
        assertEquals(0.375f, page.y, delta)
        val pageDelta = GuidedFocus.toPageDelta(view, drawn, Offset(20f, 0f))
        assertEquals(-0.05f, pageDelta.x, delta)
        assertEquals(0f, pageDelta.y, delta)
    }
}
