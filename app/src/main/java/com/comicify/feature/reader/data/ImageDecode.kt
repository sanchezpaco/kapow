package com.comicify.feature.reader.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory

internal val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

internal fun String.hasImageExtension(): Boolean =
    substringAfterLast('.', "").lowercase() in imageExtensions

internal fun decodeSampled(bytes: ByteArray, targetWidth: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = computeInSampleSize(bounds.outWidth, targetWidth)
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private fun computeInSampleSize(sourceWidth: Int, targetWidth: Int): Int {
    if (targetWidth <= 0 || sourceWidth <= targetWidth) return 1
    var sample = 1
    while (sourceWidth / (sample * 2) >= targetWidth) sample *= 2
    return sample
}
