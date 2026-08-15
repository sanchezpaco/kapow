package com.comicify.feature.reader.domain

import kotlin.math.abs

private const val WHITE_LUMINANCE = 215
private const val WHITE_CHROMA = 22
private const val BORDER_RING_FRACTION = 0.015f
private const val BORDER_BIN_SHIFT = 5
private const val MIN_BORDER_SHARE = 0.35f
private const val BORDER_TOLERANCE = 20
private const val MAX_BORDER_CHROMA = 40
private const val BORDER_LINE_RADIUS = 2

class PixelClasses(val width: Int, val height: Int, val white: BooleanArray, val solidWhite: BooleanArray, val border: BooleanArray) {

    val art: BooleanArray = BooleanArray(width * height) { !white[it] && !border[it] }

    companion object {
        fun classify(pixels: IntArray, sourceWidth: Int, sourceHeight: Int, pool: Int): PixelClasses {
            val width = sourceWidth / pool
            val height = sourceHeight / pool
            val white = BooleanArray(width * height)
            val solidWhite = BooleanArray(width * height) { true }
            val border = BooleanArray(width * height)
            val borderColor = borderColor(pixels, sourceWidth, sourceHeight)
            for (y in 0 until height * pool) {
                val row = (y / pool) * width
                for (x in 0 until width * pool) {
                    val color = pixels[y * sourceWidth + x]
                    val cell = row + x / pool
                    if (isWhite(color)) white[cell] = true else solidWhite[cell] = false
                    if (borderColor != null && near(color, borderColor)) border[cell] = true
                }
            }
            val thickBorder = if (borderColor == null || isWhite(borderColor)) border else border.opened(width, height, BORDER_LINE_RADIUS)
            return PixelClasses(width, height, white, solidWhite, thickBorder)
        }

        private fun isWhite(color: Int) = luminance(color) >= WHITE_LUMINANCE && chroma(color) <= WHITE_CHROMA

        private fun luminance(color: Int) = (red(color) * 77 + green(color) * 150 + blue(color) * 29) shr 8

        private fun chroma(color: Int) = maxOf(red(color), green(color), blue(color)) - minOf(red(color), green(color), blue(color))

        private fun near(color: Int, reference: Int) =
            abs(red(color) - red(reference)) <= BORDER_TOLERANCE &&
                abs(green(color) - green(reference)) <= BORDER_TOLERANCE &&
                abs(blue(color) - blue(reference)) <= BORDER_TOLERANCE

        private fun borderColor(pixels: IntArray, width: Int, height: Int): Int? {
            val ring = (minOf(width, height) * BORDER_RING_FRACTION).toInt().coerceAtLeast(1)
            val counts = HashMap<Int, IntArray>()
            var total = 0
            for (y in 0 until height) {
                val edgeRow = y < ring || y >= height - ring
                for (x in 0 until width) {
                    if (!edgeRow && x >= ring && x < width - ring) continue
                    val color = pixels[y * width + x]
                    val bin = counts.getOrPut(quantize(color)) { IntArray(4) }
                    bin[0]++; bin[1] += red(color); bin[2] += green(color); bin[3] += blue(color)
                    total++
                }
            }
            val dominant = counts.values.maxByOrNull { it[0] } ?: return null
            if (dominant[0] < total * MIN_BORDER_SHARE) return null
            val n = dominant[0]
            val color = (dominant[1] / n shl 16) or (dominant[2] / n shl 8) or (dominant[3] / n)
            return color.takeIf { chroma(it) <= MAX_BORDER_CHROMA }
        }

        private fun quantize(color: Int) =
            (red(color) shr BORDER_BIN_SHIFT shl 16) or (green(color) shr BORDER_BIN_SHIFT shl 8) or (blue(color) shr BORDER_BIN_SHIFT)

        private fun red(color: Int) = color shr 16 and 0xFF
        private fun green(color: Int) = color shr 8 and 0xFF
        private fun blue(color: Int) = color and 0xFF
    }
}
