package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleLayoutTest {

    private fun bubble(left: Float, top: Float, right: Float, bottom: Float): SpeechBubble {
        val box = Rect(left, top, right, bottom)
        return SpeechBubble(box, listOf(box.topLeft, box.topRight, box.bottomRight, box.bottomLeft))
    }

    @Test
    fun loneBubbleGrowsAroundItsCentre() {
        val enlarged = BubbleLayout.enlarge(listOf(bubble(0.4f, 0.4f, 0.6f, 0.5f)), 1.5f).single()
        assertEquals(1.5f, enlarged.scale, 0.001f)
        assertEquals(Offset(0.5f, 0.45f), enlarged.target.center)
        assertEquals(0.3f, enlarged.target.width, 0.001f)
        assertEquals(Offset(0.35f, 0.375f), enlarged.map(Offset(0.4f, 0.4f)))
    }

    @Test
    fun bubbleAtThePageEdgeStaysInsideThePage() {
        val enlarged = BubbleLayout.enlarge(listOf(bubble(0f, 0f, 0.2f, 0.1f)), 1.5f).single()
        assertEquals(0f, enlarged.target.left, 0.001f)
        assertEquals(0f, enlarged.target.top, 0.001f)
        assertEquals(1.5f, enlarged.scale, 0.001f)
    }

    @Test
    fun neighboursArePushedApartInsteadOfShrunkWhenThereIsRoom() {
        val enlarged = BubbleLayout.enlarge(listOf(bubble(0.4f, 0.4f, 0.6f, 0.5f), bubble(0.4f, 0.51f, 0.6f, 0.61f)), 1.3f)
        assertTrue(enlarged.all { it.scale > 1.29f })
        assertFalse(enlarged[0].target.overlaps(enlarged[1].target))
        enlarged.forEach { assertCovers(it) }
    }

    @Test
    fun stackedCaptionsGrowUniformlyInsteadOfPinningTheMiddleToOne() {
        val stacked = listOf(
            bubble(0.3f, 0.40f, 0.7f, 0.46f),
            bubble(0.3f, 0.47f, 0.7f, 0.53f),
            bubble(0.3f, 0.54f, 0.7f, 0.60f),
        )
        val enlarged = BubbleLayout.enlarge(stacked, 1.3f)
        assertTrue("every bubble should grow visibly", enlarged.all { it.scale > 1.1f })
        assertEquals(enlarged[0].scale, enlarged[1].scale, 0.001f)
        assertEquals(enlarged[1].scale, enlarged[2].scale, 0.001f)
        enlarged.forEach { assertCovers(it) }
    }

    @Test
    fun overlappingOriginalsShrinkRatherThanCoverEachOther() {
        val enlarged = BubbleLayout.enlarge(listOf(bubble(0.4f, 0.4f, 0.6f, 0.5f), bubble(0.4f, 0.5f, 0.6f, 0.6f)), 1.3f)
        assertTrue(enlarged.all { it.scale >= 1f })
        enlarged.forEach { assertCovers(it) }
    }

    private fun assertCovers(enlarged: EnlargedBubble) {
        val box = enlarged.bubble.box
        val target = enlarged.target
        assertTrue("$target should cover $box", target.left <= box.left + EPSILON && target.top <= box.top + EPSILON &&
            target.right >= box.right - EPSILON && target.bottom >= box.bottom - EPSILON)
    }
}

private const val EPSILON = 0.001f
