package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanSlopTest {

    private val touchSlop = 10f

    @Test
    fun aStillFingerNeverExceedsSlop() {
        assertFalse(PanSlop().exceeds(touchSlop))
    }

    @Test
    fun jitterWithinSlopIsNotAPan() {
        val slop = PanSlop()
            .plus(Offset(2f, 1f))
            .plus(Offset(1f, 2f))
            .plus(Offset(2f, 1f))
        assertFalse(slop.exceeds(touchSlop))
    }

    @Test
    fun travelBeyondSlopIsAPan() {
        val slop = PanSlop()
            .plus(Offset(6f, 0f))
            .plus(Offset(5f, 0f))
        assertTrue(slop.exceeds(touchSlop))
    }

    @Test
    fun slopIsMeasuredAsDistanceNotPerAxis() {
        val slop = PanSlop().plus(Offset(8f, 8f))
        assertTrue(slop.exceeds(touchSlop))
    }

    @Test
    fun jitterThatReturnsToTheStartIsNotAPan() {
        val slop = PanSlop()
            .plus(Offset(9f, 0f))
            .plus(Offset(-9f, 0f))
        assertFalse(slop.exceeds(touchSlop))
    }
}
