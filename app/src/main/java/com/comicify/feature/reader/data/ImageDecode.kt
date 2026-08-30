package com.comicify.feature.reader.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.InputStream

private const val UNKNOWN_ASPECT = 1f

internal val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

internal fun String.hasImageExtension(): Boolean =
    substringAfterLast('.', "").lowercase() in imageExtensions

internal fun decodeAspect(stream: InputStream): Float {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeStream(stream, null, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return UNKNOWN_ASPECT
    return bounds.outWidth.toFloat() / bounds.outHeight
}

internal fun decodeSampled(bytes: ByteArray, targetWidth: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = computeInSampleSize(bounds.outWidth, targetWidth)
        scaleDownTo(bounds.outWidth / inSampleSize, targetWidth)
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

private fun BitmapFactory.Options.scaleDownTo(sampledWidth: Int, targetWidth: Int) {
    if (targetWidth <= 0 || sampledWidth <= targetWidth) return
    inScaled = true
    inDensity = sampledWidth
    inTargetDensity = targetWidth
}

private fun computeInSampleSize(sourceWidth: Int, targetWidth: Int): Int {
    if (targetWidth <= 0 || sourceWidth <= targetWidth) return 1
    var sample = 1
    while (sourceWidth / (sample * 2) >= targetWidth) sample *= 2
    return sample
}
