package com.comicify.feature.reader.data

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import com.comicify.feature.reader.domain.Box
import com.comicify.feature.reader.domain.PanelDetection
import com.comicify.feature.reader.domain.PanelLayout
import com.comicify.feature.reader.domain.SpeechBubble
import com.comicify.feature.reader.domain.SpeechBubbles
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val ANALYSIS_SIDE = 1000
private const val ORDERING_GRID = 1000

class PanelDetector(private val model: MlPanelDetector) {

    fun detect(bitmap: Bitmap): List<Rect> {
        val panels = model.detect(bitmap)
        if (panels.isEmpty()) return heuristicPanels(bitmap)
        return PanelLayout.readingOrder(panels.map(::toBox)).map { it.toRect(ORDERING_GRID, ORDERING_GRID) }
    }

    fun bubbles(bitmap: Bitmap): List<SpeechBubble> =
        analyze(bitmap) { pixels, pool -> SpeechBubbles.detect(pixels, bitmap.width, bitmap.height, pool) }

    private fun heuristicPanels(bitmap: Bitmap): List<Rect> =
        analyze(bitmap) { pixels, pool -> PanelDetection.detect(pixels, bitmap.width, bitmap.height, pool) }

    private fun toBox(rect: Rect) = Box(
        (rect.left * ORDERING_GRID).roundToInt(),
        (rect.top * ORDERING_GRID).roundToInt(),
        (rect.right * ORDERING_GRID).roundToInt(),
        (rect.bottom * ORDERING_GRID).roundToInt(),
    )

    private fun <T> analyze(bitmap: Bitmap, detect: (IntArray, Int) -> T): T {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val pool = max(1, (min(bitmap.width, bitmap.height) / ANALYSIS_SIDE.toFloat()).roundToInt())
        return detect(pixels, pool)
    }
}
