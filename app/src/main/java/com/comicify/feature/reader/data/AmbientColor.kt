package com.comicify.feature.reader.data

import android.graphics.Bitmap
import androidx.palette.graphics.Palette

private const val NEUTRAL_FALLBACK = 0xFF1A1A22.toInt()

internal fun Bitmap.ambientColorInt(): Int {
    val palette = Palette.from(this).clearFilters().maximumColorCount(24).generate()
    val dominant = palette.getDominantColor(NEUTRAL_FALLBACK)
    return palette.getVibrantColor(palette.getLightMutedColor(dominant))
}
