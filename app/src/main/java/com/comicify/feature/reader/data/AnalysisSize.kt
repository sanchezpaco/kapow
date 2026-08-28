package com.comicify.feature.reader.data

import android.graphics.Bitmap
import kotlin.math.min
import kotlin.math.roundToInt

internal const val ANALYSIS_SIDE = 1000

internal fun Bitmap.atAnalysisSize(): Bitmap {
    val scale = ANALYSIS_SIDE / min(width, height).toFloat()
    if (scale >= 1f) return this
    return Bitmap.createScaledBitmap(this, (width * scale).roundToInt(), (height * scale).roundToInt(), true)
}
