package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MlBoxOutlineTest {

    private val panel = Box(20, 20, 380, 580)
    private val shade = 0xFFDCDCDC.toInt()

    private fun outlined(page: SyntheticPage, box: Box): SpeechBubble =
        SpeechBubbles.outlined(PixelClasses.classify(page.pixels, page.width, page.height, 1), listOf(box.toRect(page.width, page.height))).single()

    private fun SyntheticPage.ovalBubble(center: Offset, rx: Int, ry: Int) = apply {
        for (y in 0 until height) for (x in 0 until width) {
            val dx = (x - center.x) / rx
            val dy = (y - center.y) / ry
            val d = dx * dx + dy * dy
            if (d <= 1f) fill(Box(x, y, x + 1, y + 1), if (d >= 0.9f) BLACK else WHITE)
        }
    }

    private fun SyntheticPage.grainedBeige() = apply {
        for (y in 0 until height) for (x in 0 until width) {
            if (pixels[y * width + x] != WHITE) continue
            val grain = (x * 7 + y * 13) % 5 * 6
            fill(Box(x, y, x + 1, y + 1), 0xFF000000.toInt() or (232 - grain shl 16) or (226 - grain shl 8) or (212 - grain))
        }
    }

    @Test
    fun whiteOvalInsideBoxGetsItsOwnShapeNotTheBox() {
        val page = SyntheticPage(400, 600).fill(panel, RED).ovalBubble(Offset(160f, 135f), 60, 35)
        val bubble = outlined(page, Box(94, 94, 226, 176))
        assertTrue(bubble.contains(Offset(160f / 400, 135f / 600)))
        assertFalse(bubble.contains(Offset(100f / 400, 100f / 600)))
        assertEquals(1, bubble.outlines.size)
    }

    @Test
    fun grainyBeigeOvalOnAScanGetsItsOwnShapeNotTheBox() {
        val page = SyntheticPage(400, 600).fill(panel, RED).ovalBubble(Offset(160f, 135f), 60, 35).grainedBeige()
        val bubble = outlined(page, Box(94, 94, 226, 176))
        assertTrue(bubble.contains(Offset(160f / 400, 135f / 600)))
        assertFalse(bubble.contains(Offset(100f / 400, 100f / 600)))
        assertEquals(1, bubble.outlines.size)
    }

    @Test
    fun darkBubbleOnArtIsOutlinedFromItsDarkBody() {
        val body = Box(100, 100, 220, 170)
        val page = SyntheticPage(400, 600).fill(panel, RED).fill(body, BLACK)
        val bubble = outlined(page, Box(92, 92, 228, 178))
        assertTrue(bubble.contains(Offset(160f / 400, 135f / 600)))
        assertFalse(bubble.contains(Offset(94f / 400, 94f / 600)))
    }

    @Test
    fun boxWithoutASolidBodyFallsBackToTheBoxItself() {
        val page = SyntheticPage(400, 600).fill(panel, RED)
        val box = Box(100, 100, 220, 170)
        val bubble = outlined(page, box)
        assertEquals(box.toRect(400, 600), bubble.box)
        assertTrue(bubble.contains(Offset(101f / 400, 101f / 600)))
    }

    @Test
    fun bodyLeakingOutsideTheBoxIsClippedNearTheBox() {
        val page = SyntheticPage(400, 600).fill(panel, RED).fill(Box(60, 100, 300, 170), WHITE)
        val bubble = outlined(page, Box(100, 100, 220, 170))
        assertTrue(bubble.box.left * 400 >= 90f)
        assertTrue(bubble.box.right * 400 <= 230f)
    }

    @Test
    fun shadedBandEnclosedByTheRimBelongsToTheBubble() {
        val page = SyntheticPage(400, 600).fill(panel, RED)
            .fill(Box(96, 96, 224, 174), BLACK).fill(Box(100, 100, 220, 170), WHITE).fill(Box(100, 150, 220, 170), shade)
        val bubble = outlined(page, Box(92, 92, 228, 178))
        assertTrue(bubble.contains(Offset(160f / 400, 160f / 600)))
    }

    @Test
    fun lastTextRowOverTheShadingDoesNotWallOffTheBand() {
        val page = SyntheticPage(400, 600).fill(panel, RED)
            .fill(Box(96, 96, 224, 174), BLACK).fill(Box(100, 100, 220, 146), WHITE).fill(Box(100, 146, 220, 170), shade)
            .fill(Box(140, 146, 148, 154), WHITE).fill(Box(140, 154, 148, 162), BLACK)
        for (x in 104 until 220 step 12) page.fill(Box(x, 146, x + 6, 158), BLACK)
        val bubble = outlined(page, Box(92, 92, 228, 178))
        assertTrue(bubble.contains(Offset(160f / 400, 166f / 600)))
    }

    @Test
    fun shadingNotEnclosedByInkIsLeftOut() {
        val page = SyntheticPage(400, 600).fill(panel, RED).fill(Box(100, 100, 220, 150), WHITE).fill(Box(100, 150, 220, 178), shade)
        val bubble = outlined(page, Box(92, 92, 228, 178))
        assertTrue(bubble.contains(Offset(160f / 400, 120f / 600)))
        assertFalse(bubble.contains(Offset(160f / 400, 170f / 600)))
    }
}
