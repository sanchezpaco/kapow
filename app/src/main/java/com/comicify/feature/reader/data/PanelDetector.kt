package com.comicify.feature.reader.data

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import com.comicify.feature.reader.domain.PanelDetection
import kotlin.math.max
import kotlin.math.roundToInt

private const val ANALYSIS_WIDTH = 1000

object PanelDetector {

    fun detect(bitmap: Bitmap): List<Rect> {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val pool = max(1, (bitmap.width / ANALYSIS_WIDTH.toFloat()).roundToInt())
        return PanelDetection.detect(pixels, bitmap.width, bitmap.height, pool)
    }
}
