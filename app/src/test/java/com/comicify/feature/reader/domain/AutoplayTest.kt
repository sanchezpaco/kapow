package com.comicify.feature.reader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoplayTest {

    @Test
    fun aPaceInsideTheRangeIsKeptAsMeasured() {
        assertEquals(45, Autoplay.secondsFor(45))
        assertEquals(47, Autoplay.secondsFor(47))
    }

    @Test
    fun aPaceOutsideTheRangeIsClamped() {
        assertEquals(AUTOPLAY_SECONDS_RANGE.first, Autoplay.secondsFor(1))
        assertEquals(AUTOPLAY_SECONDS_RANGE.last, Autoplay.secondsFor(600))
    }

    @Test
    fun steppingStopsAtTheBounds() {
        assertEquals(AUTOPLAY_SECONDS_RANGE.first, Autoplay.stepped(AUTOPLAY_SECONDS_RANGE.first, -AUTOPLAY_SECONDS_STEP))
        assertEquals(AUTOPLAY_SECONDS_RANGE.last, Autoplay.stepped(AUTOPLAY_SECONDS_RANGE.last, AUTOPLAY_SECONDS_STEP))
    }

    @Test
    fun steppingMovesOneSecondAtATime() {
        assertEquals(31, Autoplay.stepped(30, AUTOPLAY_SECONDS_STEP))
        assertEquals(29, Autoplay.stepped(30, -AUTOPLAY_SECONDS_STEP))
    }

    @Test
    fun aPageLastsTheChosenSeconds() {
        assertEquals(20_000L, Autoplay.pageMillis(20))
    }

    @Test
    fun aPageOfStopsIsSharedBetweenThem() {
        assertEquals(10_000L, Autoplay.stopMillis(seconds = 40, stops = 4))
    }

    @Test
    fun manyStopsNeverMachineGun() {
        assertEquals(1_500L, Autoplay.stopMillis(seconds = 5, stops = 8))
    }

    @Test
    fun aPageWithoutStopsLastsTheWholeInterval() {
        assertEquals(30_000L, Autoplay.stopMillis(seconds = 30, stops = 0))
    }

    @Test
    fun theStripCoversOnePageHeightPerInterval() {
        val pageHeight = 2400f
        val seconds = 30
        val scrolled = Autoplay.scrollPixels(pageHeight, seconds, elapsedNanos = seconds * 1_000_000_000L)
        assertEquals(pageHeight, scrolled, 0.01f)
    }

    @Test
    fun theStripScrollsProportionallyToTheFrameLength() {
        val half = Autoplay.scrollPixels(pageHeight = 1000f, seconds = 10, elapsedNanos = 5_000_000_000L)
        assertEquals(500f, half, 0.01f)
    }

    @Test
    fun aHeldButtonWaitsBeforeItStartsRepeating() {
        val first = Autoplay.holdWaitMillis(0)
        assertTrue(first > Autoplay.holdWaitMillis(1))
    }

    @Test
    fun aHeldButtonSpeedsUpAsItGoes() {
        val waits = (1..8).map(Autoplay::holdWaitMillis)
        waits.zipWithNext { slower, faster -> assertTrue(faster < slower) }
    }

    @Test
    fun aHeldButtonNeverGoesFasterThanItsFloor() {
        val fastest = Autoplay.holdWaitMillis(50)
        assertEquals(fastest, Autoplay.holdWaitMillis(500))
        assertTrue(fastest > 0)
    }

    @Test
    fun aSlowerIntervalScrollsLess() {
        val frame = 16_000_000L
        val fast = Autoplay.scrollPixels(pageHeight = 1000f, seconds = 10, elapsedNanos = frame)
        val slow = Autoplay.scrollPixels(pageHeight = 1000f, seconds = 60, elapsedNanos = frame)
        assertTrue(slow < fast)
    }
}
