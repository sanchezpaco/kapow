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

    private fun outlined(page: SyntheticPage, box: Box): SpeechBubble = outlined(page, listOf(box)).single()

    private fun outlined(page: SyntheticPage, boxes: List<Box>): List<SpeechBubble> =
        SpeechBubbles.outlined(PixelClasses.classify(page.pixels, page.width, page.height, 1), boxes.map { it.toRect(page.width, page.height) })

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
    fun letteringInsideABubbleIsExtractedAsTextBoxesOnlyWhenAsked() {
        val page = SyntheticPage(400, 600).fill(panel, RED).fill(Box(100, 100, 220, 170), WHITE)
            .fill(Box(120, 125, 150, 133), BLACK).fill(Box(160, 125, 195, 133), BLACK)
        val classes = PixelClasses.classify(page.pixels, page.width, page.height, 1)
        val boxes = listOf(Box(94, 94, 226, 176).toRect(page.width, page.height))
        assertTrue(SpeechBubbles.outlined(classes, boxes).single().text.isEmpty())
        val text = SpeechBubbles.outlined(classes, boxes, extractText = true).single().text
        assertEquals(2, text.size)
        assertTrue(text.all { it.top >= 100f / 600 && it.bottom <= 170f / 600 && it.left >= 100f / 400 && it.right <= 220f / 400 })
    }

    @Test
    fun oneBalloonSplitIntoOverlappingBoxesMergesIntoOneUnit() {
        val page = SyntheticPage(400, 600).fill(panel, RED).fill(Box(96, 166, 304, 234), BLACK).fill(Box(100, 170, 300, 230), WHITE)
        val bubbles = outlined(page, listOf(Box(96, 166, 224, 234), Box(176, 166, 304, 234)))
        assertEquals(1, bubbles.size)
        val merged = bubbles.single()
        assertTrue(merged.contains(Offset(120f / 400, 200f / 600)))
        assertTrue(merged.contains(Offset(280f / 400, 200f / 600)))
    }

    @Test
    fun separateBubblesAreNotMerged() {
        val page = SyntheticPage(400, 600).fill(panel, RED)
            .ovalBubble(Offset(110f, 200f), 40, 30).ovalBubble(Offset(300f, 200f), 40, 30)
        val bubbles = outlined(page, listOf(Box(70, 170, 150, 230), Box(260, 170, 340, 230)))
        assertEquals(2, bubbles.size)
    }

    @Test
    fun shadingNotEnclosedByInkIsLeftOut() {
        val page = SyntheticPage(400, 600).fill(panel, RED).fill(Box(100, 100, 220, 150), WHITE).fill(Box(100, 150, 220, 178), shade)
        val bubble = outlined(page, Box(92, 92, 228, 178))
        assertTrue(bubble.contains(Offset(160f / 400, 120f / 600)))
        assertFalse(bubble.contains(Offset(160f / 400, 170f / 600)))
    }
}
