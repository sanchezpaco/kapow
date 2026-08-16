package com.comicify.feature.reader.data

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import com.comicify.feature.reader.domain.PanelDetection
import com.comicify.feature.reader.domain.SpeechBubble
import com.comicify.feature.reader.domain.SpeechBubbles
import kotlin.math.max
import kotlin.math.roundToInt

private const val ANALYSIS_WIDTH = 1000

object PanelDetector {

    fun detect(bitmap: Bitmap): List<Rect> =
        analyze(bitmap) { pixels, pool -> PanelDetection.detect(pixels, bitmap.width, bitmap.height, pool) }

    fun bubbles(bitmap: Bitmap): List<SpeechBubble> =
        analyze(bitmap) { pixels, pool -> SpeechBubbles.detect(pixels, bitmap.width, bitmap.height, pool) }

    private fun <T> analyze(bitmap: Bitmap, detect: (IntArray, Int) -> T): T {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val pool = max(1, (bitmap.width / ANALYSIS_WIDTH.toFloat()).roundToInt())
        return detect(pixels, pool)
    }
}
