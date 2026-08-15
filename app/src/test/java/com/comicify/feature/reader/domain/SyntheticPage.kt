package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Rect
import kotlin.math.roundToInt

const val WHITE = 0xFFFFFFFF.toInt()
const val BLACK = 0xFF000000.toInt()
const val RED = 0xFFC03030.toInt()
const val BLUE = 0xFF3040C0.toInt()

class SyntheticPage(val width: Int, val height: Int, background: Int = WHITE) {
    val pixels = IntArray(width * height) { background }

    fun fill(box: Box, color: Int) = apply {
        for (y in box.top until box.bottom) for (x in box.left until box.right) pixels[y * width + x] = color
    }

    fun fillWavyPair(left: Box, right: Box, color: Int, amplitude: Int) = apply {
        for (y in left.top until left.bottom) {
            val wave = (amplitude * kotlin.math.sin(y / 9.0)).roundToInt()
            for (x in left.left until left.right + wave) pixels[y * width + x] = color
            for (x in right.left + wave + 2 until right.right) pixels[y * width + x] = color
        }
    }

    fun bubble(box: Box) = apply {
        fill(box, BLACK)
        fill(Box(box.left + 2, box.top + 2, box.right - 2, box.bottom - 2), WHITE)
        for (y in box.top + 6 until box.bottom - 6 step 6) fill(Box(box.left + 6, y, box.right - 6, y + 2), BLACK)
    }

    fun detect(pool: Int = 1): List<Rect> = PanelDetection.detect(pixels, width, height, pool)

    fun box(rect: Rect) = Box(
        (rect.left * width).roundToInt(),
        (rect.top * height).roundToInt(),
        (rect.right * width).roundToInt(),
        (rect.bottom * height).roundToInt(),
    )
}
