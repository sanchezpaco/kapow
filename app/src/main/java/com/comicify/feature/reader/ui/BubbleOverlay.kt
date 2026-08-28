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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.comicify.feature.reader.data.PaintedBubble
import kotlin.math.roundToInt

private const val OUTLINE_WIDTH_FRACTION = 0.0007f
private const val OUTLINE_ALPHA = 0.35f

object BubbleOverlay {

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
}
