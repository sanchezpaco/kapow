package com.comicify.feature.reader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
        bubbles.forEach { item ->
            val r = item.fillRect
            drawImage(
                image = item.fill,
                dstOffset = IntOffset((area.left + r.left * area.width).roundToInt(), (area.top + r.top * area.height).roundToInt()),
                dstSize = IntSize((r.width * area.width).roundToInt(), (r.height * area.height).roundToInt()),
                filterQuality = FilterQuality.Low,
            )
        }
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

@Composable
fun BubbleLayer(page: ImageBitmap, bubbles: List<PaintedBubble>, cached: () -> Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = if (cached()) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .drawBehind { BubbleOverlay.run { drawBubbles(page, fittedImageRect(page, size), bubbles) } },
    )
}

fun fittedImageRect(image: ImageBitmap, viewport: Size): Rect {
    val scale = minOf(viewport.width / image.width, viewport.height / image.height)
    val fitted = Size(image.width * scale, image.height * scale)
    return Rect(Offset((viewport.width - fitted.width) / 2f, (viewport.height - fitted.height) / 2f), fitted)
}
