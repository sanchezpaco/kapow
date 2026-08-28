package com.comicify.feature.reader.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import com.comicify.feature.reader.domain.EnlargedBubble
import com.comicify.feature.reader.domain.SpeechBubble

private const val PAPER_SAMPLES_PER_AXIS = 12

data class PaintedBubble(val enlarged: EnlargedBubble, val paper: Color)

object BubblePlan {

    fun of(page: ImageBitmap, bubbles: List<EnlargedBubble>): List<PaintedBubble> =
        bubbles.filter { it.scale > 1f }.map { PaintedBubble(it, paperColor(page, it.bubble)) }

    private fun paperColor(page: ImageBitmap, bubble: SpeechBubble): Color {
        val box = bubble.box
        val x = (box.left * page.width).toInt().coerceIn(0, page.width - 1)
        val y = (box.top * page.height).toInt().coerceIn(0, page.height - 1)
        val w = (box.width * page.width).toInt().coerceIn(1, page.width - x)
        val h = (box.height * page.height).toInt().coerceIn(1, page.height - y)
        val pixels = page.toPixelMap(x, y, w, h)
        val samples = bubble.interiorSamples(PAPER_SAMPLES_PER_AXIS).map {
            pixels[((it.x * page.width).toInt() - x).coerceIn(0, w - 1), ((it.y * page.height).toInt() - y).coerceIn(0, h - 1)]
        }
        if (samples.isEmpty()) return Color.White
        return samples.sortedBy { it.luminance() }[samples.size / 2]
    }
}
