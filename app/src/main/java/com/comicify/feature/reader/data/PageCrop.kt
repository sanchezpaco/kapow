package com.comicify.feature.reader.data

import android.graphics.Bitmap
import com.comicify.feature.reader.domain.FullPage
import com.comicify.feature.reader.domain.MarginCrop
import kotlin.math.roundToInt

internal fun Bitmap.contentCropped(): Bitmap {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    val content = MarginCrop.detect(pixels, width, height)
    if (content == FullPage) return this
    return Bitmap.createBitmap(
        this,
        (content.left * width).roundToInt(),
        (content.top * height).roundToInt(),
        (content.width * width).roundToInt(),
        (content.height * height).roundToInt(),
    )
}
