package com.comicify.feature.reader.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import com.comicify.feature.reader.domain.EnlargedBubble
import com.comicify.feature.reader.domain.SpeechBubble

private const val PAPER_SAMPLES_PER_AXIS = 12
private const val OUTLINE_WIDTH_FRACTION = 0.0007f
private const val OUTLINE_ALPHA = 0.35f

object BubbleOverlay {

    fun render(page: ImageBitmap, bubbles: List<EnlargedBubble>): ImageBitmap {
        val source = page.asAndroidBitmap()
        val overlay = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(overlay)
        val enlarged = bubbles.filter { it.scale > 1f }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        enlarged.forEach { item ->
            fill.color = paperColor(page, item.bubble).toArgb()
            canvas.drawPath(path(item.bubble.outlines, page) { it }, fill)
        }
        val copy = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = minOf(page.width, page.height) * OUTLINE_WIDTH_FRACTION
            color = Color.Black.copy(alpha = OUTLINE_ALPHA).toArgb()
        }
        enlarged.forEach { item ->
            val shape = path(item.bubble.outlines, page, item::map)
            val src = item.bubble.box
            val dst = item.target
            canvas.save()
            canvas.clipPath(shape)
            canvas.drawBitmap(
                source,
                android.graphics.Rect(
                    (src.left * page.width).toInt(), (src.top * page.height).toInt(),
                    (src.right * page.width).toInt(), (src.bottom * page.height).toInt(),
                ),
                RectF(dst.left * page.width, dst.top * page.height, dst.right * page.width, dst.bottom * page.height),
                copy,
            )
            canvas.restore()
            canvas.drawPath(shape, outline)
        }
        return overlay.asImageBitmap()
    }

    private fun path(outlines: List<List<Offset>>, page: ImageBitmap, map: (Offset) -> Offset) = Path().apply {
        outlines.forEach { outline ->
            outline.forEachIndexed { i, point ->
                val at = map(point)
                if (i == 0) moveTo(at.x * page.width, at.y * page.height) else lineTo(at.x * page.width, at.y * page.height)
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
