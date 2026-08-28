package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PAGE = 200
private const val TRANSPARENT = 0

class CrescentFillTest {

    private fun bubble(left: Float, top: Float, right: Float, bottom: Float): SpeechBubble {
        val box = Rect(left, top, right, bottom)
        return SpeechBubble(box, listOf(listOf(box.topLeft, box.topRight, box.bottomRight, box.bottomLeft)))
    }

    private fun pageWithBubble(bubble: SpeechBubble, background: Int) = SyntheticPage(PAGE, PAGE, background).bubble(
        Box((bubble.box.left * PAGE).toInt(), (bubble.box.top * PAGE).toInt(), (bubble.box.right * PAGE).toInt(), (bubble.box.bottom * PAGE).toInt()),
    )

    private fun BubbleFill.at(pageX: Int, pageY: Int) = argb[(pageY - top) * width + (pageX - left)]

    @Test
    fun fillsTheWholeSilhouetteWithTheSurroundingColour() {
        val bubble = bubble(0.3f, 0.3f, 0.6f, 0.5f)
        val page = pageWithBubble(bubble, BLUE)
        val fill = CrescentFill.of(page.pixels, PAGE, PAGE, bubble, listOf(bubble))
        val interior = listOf(65 to 65, 100 to 80, 115 to 95)
        interior.forEach { (x, y) -> assertEquals("$x,$y", BLUE, fill.at(x, y)) }
    }

    @Test
    fun coversTheRimWithAMarginAndNothingBeyondIt() {
        val bubble = bubble(0.3f, 0.3f, 0.6f, 0.5f)
        val fill = CrescentFill.of(pageWithBubble(bubble, RED).pixels, PAGE, PAGE, bubble, listOf(bubble))
        assertEquals(RED, fill.at(58, 80))
        assertEquals(TRANSPARENT, fill.at(fill.left, fill.top))
        assertTrue(fill.left < 60 && fill.top < 60 && fill.left + fill.width > 120 && fill.top + fill.height > 100)
    }

    @Test
    fun ignoresTheNeighbourBubblePaperAsASource() {
        val bubble = bubble(0.3f, 0.3f, 0.6f, 0.5f)
        val neighbour = bubble(0.6f, 0.3f, 0.9f, 0.5f)
        val page = pageWithBubble(bubble, BLUE)
        page.fill(page.box(neighbour.box), WHITE)
        val fill = CrescentFill.of(page.pixels, PAGE, PAGE, bubble, listOf(bubble, neighbour))
        assertEquals(BLUE, fill.at(118, 80))
    }

    @Test
    fun staysInsideThePageForABubbleOnTheEdge() {
        val bubble = bubble(0f, 0f, 0.2f, 0.1f)
        val fill = CrescentFill.of(pageWithBubble(bubble, RED).pixels, PAGE, PAGE, bubble, listOf(bubble))
        assertEquals(0, fill.left)
        assertEquals(0, fill.top)
        assertEquals(RED, fill.at(20, 10))
    }
}
