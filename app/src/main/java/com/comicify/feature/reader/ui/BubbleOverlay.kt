package com.comicify.feature.reader.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.comicify.feature.reader.domain.EnlargedBubble
import com.comicify.feature.reader.domain.SpeechBubble
import kotlin.math.roundToInt

private const val PAPER_SAMPLES_PER_AXIS = 12
private const val OUTLINE_WIDTH_FRACTION = 0.0007f
private const val OUTLINE_ALPHA = 0.35f

data class PaintedBubble(val enlarged: EnlargedBubble, val paper: Color)

object BubbleOverlay {

    fun plan(page: ImageBitmap, bubbles: List<EnlargedBubble>): List<PaintedBubble> =
        bubbles.filter { it.scale > 1f }.map { PaintedBubble(it, paperColor(page, it.bubble)) }

    fun DrawScope.drawBubbles(page: ImageBitmap, area: Rect, bubbles: List<PaintedBubble>) {
        bubbles.forEach { item -> drawPath(path(item.enlarged.bubble.outlines, area) { it }, item.paper) }
        val outline = Stroke(width = minOf(area.width, area.height) * OUTLINE_WIDTH_FRACTION)
        val outlineColor = Color.Black.copy(alpha = OUTLINE_ALPHA)
        bubbles.forEach { item ->
            val shape = path(item.enlarged.bubble.outlines, area, item.enlarged::map)
            val src = item.enlarged.bubble.box
            val dst = item.enlarged.target
            clipPath(shape) {
                drawImage(
                    image = page,
                    srcOffset = IntOffset((src.left * page.width).toInt(), (src.top * page.height).toInt()),
                    srcSize = IntSize((src.width * page.width).toInt(), (src.height * page.height).toInt()),
                    dstOffset = IntOffset((area.left + dst.left * area.width).roundToInt(), (area.top + dst.top * area.height).roundToInt()),
                    dstSize = IntSize((dst.width * area.width).roundToInt(), (dst.height * area.height).roundToInt()),
                    filterQuality = FilterQuality.High,
                )
            }
            drawPath(shape, outlineColor, style = outline)
        }
    }

    private fun path(outlines: List<List<Offset>>, area: Rect, map: (Offset) -> Offset) = Path().apply {
        outlines.forEach { outline ->
            outline.forEachIndexed { i, point ->
                val at = map(point)
                val x = area.left + at.x * area.width
                val y = area.top + at.y * area.height
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }

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
