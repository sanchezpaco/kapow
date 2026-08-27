package com.comicify.feature.reader.domain

internal fun luminance(color: Int) = (red(color) * 77 + green(color) * 150 + blue(color) * 29) shr 8

internal fun chroma(color: Int) = maxOf(red(color), green(color), blue(color)) - minOf(red(color), green(color), blue(color))

internal fun red(color: Int) = color shr 16 and 0xFF
internal fun green(color: Int) = color shr 8 and 0xFF
internal fun blue(color: Int) = color and 0xFF

internal fun rgb(red: Int, green: Int, blue: Int) = (red shl 16) or (green shl 8) or blue
