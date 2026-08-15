package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private const val CORNER_TOLERANCE = 24
private const val COLOR_TOLERANCE = 20
private const val BORDER_LINE_FRACTION = 0.99f
private const val MIN_MARGIN_FRACTION = 0.01f
private const val MIN_CONTENT_FRACTION = 0.5f

val FullPage = Rect(0f, 0f, 1f, 1f)

object MarginCrop {

    fun detect(pixels: IntArray, width: Int, height: Int): Rect {
        val border = uniformBorderColor(pixels, width, height) ?: return FullPage
        val minRowMatch = ceil(BORDER_LINE_FRACTION * width).toInt()
        val minColumnMatch = ceil(BORDER_LINE_FRACTION * height).toInt()

        var top = 0
        while (top < height && rowIsBorder(pixels, width, top, border, minRowMatch)) top++
        if (top >= height) return FullPage
        var bottom = height
        while (bottom > top && rowIsBorder(pixels, width, bottom - 1, border, minRowMatch)) bottom--
        var left = 0
        while (left < width && columnIsBorder(pixels, width, height, left, border, minColumnMatch)) left++
        var right = width
        while (right > left && columnIsBorder(pixels, width, height, right - 1, border, minColumnMatch)) right--

        if (!isMeaningful(left, top, right, bottom, width, height)) return FullPage
        return Rect(left / width.toFloat(), top / height.toFloat(), right / width.toFloat(), bottom / height.toFloat())
    }

    private fun uniformBorderColor(pixels: IntArray, width: Int, height: Int): Int? {
        val topLeft = pixels[0]
        val topRight = pixels[width - 1]
        val bottomLeft = pixels[(height - 1) * width]
        val bottomRight = pixels[height * width - 1]
        val reference = averageColor(topLeft, topRight, bottomLeft, bottomRight)
        val corners = intArrayOf(topLeft, topRight, bottomLeft, bottomRight)
        return if (corners.all { colorsClose(it, reference, CORNER_TOLERANCE) }) reference else null
    }

    private fun rowIsBorder(pixels: IntArray, width: Int, y: Int, border: Int, minMatch: Int): Boolean {
        val base = y * width
        var mismatchesAllowed = width - minMatch
        for (x in 0 until width) {
            if (!colorsClose(pixels[base + x], border, COLOR_TOLERANCE) && --mismatchesAllowed < 0) return false
        }
        return true
    }

    private fun columnIsBorder(pixels: IntArray, width: Int, height: Int, x: Int, border: Int, minMatch: Int): Boolean {
        var mismatchesAllowed = height - minMatch
        for (y in 0 until height) {
            if (!colorsClose(pixels[y * width + x], border, COLOR_TOLERANCE) && --mismatchesAllowed < 0) return false
        }
        return true
    }

    private fun isMeaningful(left: Int, top: Int, right: Int, bottom: Int, width: Int, height: Int): Boolean {
        val widestMargin = max(max(top, height - bottom), max(left, width - right))
        if (widestMargin < MIN_MARGIN_FRACTION * min(width, height)) return false
        if (right - left < MIN_CONTENT_FRACTION * width) return false
        return bottom - top >= MIN_CONTENT_FRACTION * height
    }

    private fun averageColor(vararg colors: Int): Int {
        val red = colors.sumOf { (it shr 16) and 0xFF } / colors.size
        val green = colors.sumOf { (it shr 8) and 0xFF } / colors.size
        val blue = colors.sumOf { it and 0xFF } / colors.size
        return (red shl 16) or (green shl 8) or blue
    }

    private fun colorsClose(a: Int, b: Int, tolerance: Int): Boolean =
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) <= tolerance &&
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) <= tolerance &&
            abs((a and 0xFF) - (b and 0xFF)) <= tolerance
}
