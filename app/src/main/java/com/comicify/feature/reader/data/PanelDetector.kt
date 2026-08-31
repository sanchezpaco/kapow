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
import kotlin.math.roundToInt

private const val ORDERING_GRID = 1000
private const val PANELS_MODEL = "models/panels.ort"
private const val BUBBLES_MODEL = "models/bubbles.ort"
const val DETECTIONS_VERSION = "panels-v1ort+bubbles-v5ort+outline-v3+linked-v1"
private const val PANEL_CONFIDENCE = 0.35f
private const val BUBBLE_CONFIDENCE = 0.25f

class PanelDetector(private val panelModel: OnnxBoxDetector, private val bubbleModel: OnnxBoxDetector) {

    fun detect(bitmap: Bitmap): List<Rect> {
        val detected = panelModel.detect(bitmap).map(::toBox)
        val heuristic = heuristicPanels(bitmap).map(::toBox)
        return PanelLayout.readingOrder(PanelLayout.complemented(detected, heuristic)).map { it.toRect(ORDERING_GRID, ORDERING_GRID) }
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
        val analysed = bitmap.atAnalysisSize()
        val pixels = IntArray(analysed.width * analysed.height)
        analysed.getPixels(pixels, 0, analysed.width, 0, 0, analysed.width, analysed.height)
        return detect(PixelClasses.classify(pixels, analysed.width, analysed.height, 1))
    }


    companion object {
        fun forContext(context: Context) = PanelDetector(
            OnnxBoxDetector.shared(context, PANELS_MODEL, PANEL_CONFIDENCE),
            OnnxBoxDetector.shared(context, BUBBLES_MODEL, BUBBLE_CONFIDENCE),
        )
    }
}
