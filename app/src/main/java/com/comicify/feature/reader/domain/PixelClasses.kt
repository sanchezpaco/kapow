package com.comicify.feature.reader.domain

import kotlin.math.abs

private const val WHITE_LUMINANCE = 228
private const val WHITE_CHROMA = 22
private const val BORDER_RING_FRACTION = 0.015f
private const val BORDER_BIN_SHIFT = 5
private const val MIN_BORDER_SHARE = 0.35f
private const val BORDER_TOLERANCE = 20
private const val MAX_BORDER_CHROMA = 40
private const val BORDER_LINE_RADIUS = 2
private const val PAPER_LUMINANCE = 215
private const val PALE_LUMINANCE = 200
private const val INK_LUMINANCE = 100
private const val LIGHT_LUMINANCE = 155
private const val SOLID_DARK_LUMINANCE = 75
private const val SOLID_DARK_CHROMA = 50
private const val PAPER_MIN_RED = 235
private const val PAPER_MIN_GREEN = 225
private const val PAPER_MIN_BLUE = 130
private const val PAPER_MAX_RED_GREEN_GAP = 25

class PixelClasses(
    val width: Int,
    val height: Int,
    val white: BooleanArray,
    val solidWhite: BooleanArray,
    val solidPaper: BooleanArray,
    val solidPale: BooleanArray,
    val solidDark: BooleanArray,
    val ink: BooleanArray,
    val light: BooleanArray,
    val border: BooleanArray,
    val colors: IntArray,
) {

    val art: BooleanArray = BooleanArray(width * height) { !white[it] && !border[it] }

    companion object {
        fun classify(pixels: IntArray, sourceWidth: Int, sourceHeight: Int, pool: Int): PixelClasses {
            val width = sourceWidth / pool
            val height = sourceHeight / pool
            val white = BooleanArray(width * height)
            val solidWhite = BooleanArray(width * height) { true }
            val solidPaper = BooleanArray(width * height) { true }
            val solidPale = BooleanArray(width * height) { true }
            val solidDark = BooleanArray(width * height) { true }
            val ink = BooleanArray(width * height)
            val light = BooleanArray(width * height)
            val border = BooleanArray(width * height)
            val red = IntArray(width * height)
            val green = IntArray(width * height)
            val blue = IntArray(width * height)
            val borderColor = borderColor(pixels, sourceWidth, sourceHeight)
            for (y in 0 until height * pool) {
                val row = (y / pool) * width
                val rowOffset = y * sourceWidth
                for (x in 0 until width * pool) {
                    val color = pixels[rowOffset + x]
                    val cell = row + x / pool
                    val luminance = luminance(color)
                    val chroma = chroma(color)
                    val whitePixel = luminance >= WHITE_LUMINANCE && chroma <= WHITE_CHROMA
                    if (whitePixel) white[cell] = true else solidWhite[cell] = false
                    if (!whitePixel && !isCream(color)) solidPaper[cell] = false
                    if (luminance < PALE_LUMINANCE || chroma > WHITE_CHROMA) solidPale[cell] = false
                    if (luminance > SOLID_DARK_LUMINANCE || chroma > SOLID_DARK_CHROMA) solidDark[cell] = false
                    if (luminance <= INK_LUMINANCE) ink[cell] = true
                    if (luminance >= LIGHT_LUMINANCE) light[cell] = true
                    if (borderColor != null && near(color, borderColor)) border[cell] = true
                    red[cell] += red(color); green[cell] += green(color); blue[cell] += blue(color)
                }
            }
            val thickBorder = if (borderColor == null || isWhite(borderColor)) border else border.opened(width, height, BORDER_LINE_RADIUS)
            val cellPixels = pool * pool
            val colors = IntArray(width * height) { rgb(red[it] / cellPixels, green[it] / cellPixels, blue[it] / cellPixels) }
            return PixelClasses(width, height, white, solidWhite, solidPaper, solidPale, solidDark, ink, light, thickBorder, colors)
        }

        private fun isWhite(color: Int) = luminance(color) >= WHITE_LUMINANCE && chroma(color) <= WHITE_CHROMA

        private fun isCream(color: Int) =
            luminance(color) >= PAPER_LUMINANCE &&
                red(color) >= PAPER_MIN_RED && green(color) >= PAPER_MIN_GREEN && blue(color) >= PAPER_MIN_BLUE &&
                red(color) - green(color) <= PAPER_MAX_RED_GREEN_GAP && green(color) > blue(color)

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
            val color = rgb(dominant[1] / n, dominant[2] / n, dominant[3] / n)
            return color.takeIf { chroma(it) <= MAX_BORDER_CHROMA }
        }

        private fun quantize(color: Int) =
            (red(color) shr BORDER_BIN_SHIFT shl 16) or (green(color) shr BORDER_BIN_SHIFT shl 8) or (blue(color) shr BORDER_BIN_SHIFT)
    }
}
