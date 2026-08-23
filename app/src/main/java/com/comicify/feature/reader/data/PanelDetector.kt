package com.comicify.feature.reader.data

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import com.comicify.feature.reader.domain.Box
import com.comicify.feature.reader.domain.PanelDetection
import com.comicify.feature.reader.domain.PanelLayout
import com.comicify.feature.reader.domain.PixelClasses
import com.comicify.feature.reader.domain.SpeechBubble
import com.comicify.feature.reader.domain.SpeechBubbles
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val ANALYSIS_SIDE = 1000
private const val ORDERING_GRID = 1000
private const val PANELS_MODEL = "models/panels.onnx"
private const val BUBBLES_MODEL = "models/bubbles.onnx"
private const val PANEL_CONFIDENCE = 0.35f
private const val BUBBLE_CONFIDENCE = 0.25f

class PanelDetector(private val panelModel: OnnxBoxDetector, private val bubbleModel: OnnxBoxDetector) {

    fun detect(bitmap: Bitmap): List<Rect> {
        val panels = panelModel.detect(bitmap)
        if (panels.isEmpty()) return heuristicPanels(bitmap)
        return PanelLayout.readingOrder(panels.map(::toBox)).map { it.toRect(ORDERING_GRID, ORDERING_GRID) }
    }

    fun bubbles(bitmap: Bitmap): List<SpeechBubble> {
        val boxes = bubbleModel.detect(bitmap)
        if (boxes.isEmpty()) return emptyList()
        return analyze(bitmap) { classes -> SpeechBubbles.outlined(classes, boxes) }
    }

    private fun heuristicPanels(bitmap: Bitmap): List<Rect> = analyze(bitmap, PanelDetection::detect)

    private fun toBox(rect: Rect) = Box(
        (rect.left * ORDERING_GRID).roundToInt(),
        (rect.top * ORDERING_GRID).roundToInt(),
        (rect.right * ORDERING_GRID).roundToInt(),
        (rect.bottom * ORDERING_GRID).roundToInt(),
    )

    private fun <T> analyze(bitmap: Bitmap, detect: (PixelClasses) -> T): T {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val pool = max(1, (min(bitmap.width, bitmap.height) / ANALYSIS_SIDE.toFloat()).roundToInt())
        return detect(PixelClasses.classify(pixels, bitmap.width, bitmap.height, pool))
    }

    companion object {
        fun forContext(context: Context) = PanelDetector(
            OnnxBoxDetector.shared(context, PANELS_MODEL, PANEL_CONFIDENCE),
            OnnxBoxDetector.shared(context, BUBBLES_MODEL, BUBBLE_CONFIDENCE),
        )
    }
}
