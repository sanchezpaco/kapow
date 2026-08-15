package com.comicify.feature.reader.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt

class MarginCropTest {

    private fun page(width: Int, height: Int, border: Int, art: Box, artColor: Int = RED): IntArray {
        val pixels = IntArray(width * height) { border }
        for (y in art.top until art.bottom) for (x in art.left until art.right) pixels[y * width + x] = artColor
        return pixels
    }

    private fun detect(width: Int, height: Int, pixels: IntArray) = MarginCrop.detect(pixels, width, height)

    @Test
    fun whiteBorderIsCroppedToTheArt() {
        val art = Box(40, 60, 360, 540)
        val crop = detect(400, 600, page(400, 600, WHITE, art))
        assertEquals(art, boxOf(crop, 400, 600))
    }

    @Test
    fun blackBorderIsCroppedToTheArt() {
        val art = Box(40, 60, 360, 540)
        val crop = detect(400, 600, page(400, 600, BLACK, art))
        assertEquals(art, boxOf(crop, 400, 600))
    }

    @Test
    fun asymmetricMarginsAreRecovered() {
        val art = Box(24, 90, 380, 500)
        val crop = detect(400, 600, page(400, 600, WHITE, art))
        assertEquals(art, boxOf(crop, 400, 600))
    }

    @Test
    fun fullBleedArtIsNotCropped() {
        val crop = detect(400, 600, page(400, 600, WHITE, Box(0, 0, 400, 600), BLUE))
        assertEquals(FullPage, crop)
    }

    @Test
    fun mismatchedCornersKeepTheFullPage() {
        val pixels = page(400, 600, WHITE, Box(40, 40, 360, 560))
        pixels[0] = BLACK
        assertEquals(FullPage, detect(400, 600, pixels))
    }

    @Test
    fun marginThinnerThanThresholdIsIgnored() {
        val crop = detect(400, 600, page(400, 600, WHITE, Box(1, 1, 399, 599)))
        assertEquals(FullPage, crop)
    }

    @Test
    fun oversizedBorderIsRejectedAsUnsafe() {
        val crop = detect(400, 600, page(400, 600, WHITE, Box(170, 260, 230, 340)))
        assertEquals(FullPage, crop)
    }

    @Test
    fun blankPageKeepsTheFullPage() {
        assertEquals(FullPage, detect(400, 600, IntArray(400 * 600) { WHITE }))
    }

    private fun boxOf(crop: androidx.compose.ui.geometry.Rect, width: Int, height: Int) = Box(
        (crop.left * width).roundToInt(),
        (crop.top * height).roundToInt(),
        (crop.right * width).roundToInt(),
        (crop.bottom * height).roundToInt(),
    )
}
