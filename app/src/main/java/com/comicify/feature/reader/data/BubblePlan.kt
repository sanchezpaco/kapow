package com.comicify.feature.reader.data

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.comicify.feature.reader.domain.CrescentFill
import com.comicify.feature.reader.domain.EnlargedBubble

data class PaintedBubble(val enlarged: EnlargedBubble, val fill: ImageBitmap, val fillRect: Rect)

object BubblePlan {

    fun of(page: ImageBitmap, bubbles: List<EnlargedBubble>): List<PaintedBubble> {
        val big = bubbles.filter { it.scale > 1f }
        if (big.isEmpty()) return emptyList()
        val pixels = IntArray(page.width * page.height)
        page.asAndroidBitmap().getPixels(pixels, 0, page.width, 0, 0, page.width, page.height)
        return big.map { item ->
            val fill = CrescentFill.of(pixels, page.width, page.height, item.bubble)
            val bitmap = Bitmap.createBitmap(fill.argb, fill.width, fill.height, Bitmap.Config.ARGB_8888)
            val rect = Rect(
                fill.left.toFloat() / page.width, fill.top.toFloat() / page.height,
                (fill.left + fill.width).toFloat() / page.width, (fill.top + fill.height).toFloat() / page.height,
            )
            PaintedBubble(item, bitmap.asImageBitmap(), rect)
        }
    }
}
