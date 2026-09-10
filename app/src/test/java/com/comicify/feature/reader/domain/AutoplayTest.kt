package com.comicify.feature.reader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ONE_SECOND_NANOS = 1_000_000_000L

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
    fun theFallbackPaceFitsInTheRange() {
        assertTrue(45 in AUTOPLAY_SECONDS_RANGE)
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
    fun theFirstCoarseStepLandsOnARoundValueInTheDirectionOfTravel() {
        assertEquals(40, Autoplay.coarseStepped(37, direction = 1))
        assertEquals(35, Autoplay.coarseStepped(37, direction = -1))
    }

    @Test
    fun aCoarseStepFromARoundValueMovesAWholeStep() {
        assertEquals(45, Autoplay.coarseStepped(40, direction = 1))
        assertEquals(35, Autoplay.coarseStepped(40, direction = -1))
    }

    @Test
    fun coarseStepsKeepTheValueRound() {
        val climbed = generateSequence(37) { Autoplay.coarseStepped(it, direction = 1) }.take(4).toList()
        assertEquals(listOf(37, 40, 45, 50), climbed)
    }

    @Test
    fun aCoarseStepThatWouldOvershootClampsToTheBound() {
        assertEquals(AUTOPLAY_SECONDS_RANGE.last, Autoplay.coarseStepped(AUTOPLAY_SECONDS_RANGE.last - 1, direction = 1))
        assertEquals(AUTOPLAY_SECONDS_RANGE.first, Autoplay.coarseStepped(AUTOPLAY_SECONDS_RANGE.first + 1, direction = -1))
    }

    @Test
    fun theHoldOnlyCoarsensAfterItHasBeenRepeatingAWhile() {
        assertFalse(Autoplay.coarseAfter(0))
        assertFalse(Autoplay.coarseAfter(AUTOPLAY_COARSE_AFTER_MS - AUTOPLAY_REPEAT_INTERVAL_MS))
        assertTrue(Autoplay.coarseAfter(AUTOPLAY_COARSE_AFTER_MS))
    }

    @Test
    fun aPageLastsTheChosenSeconds() {
        assertEquals(20_000L, Autoplay.pageMillis(20))
    }

    @Test
    fun aSpreadLastsAsLongAsThePagesItShows() {
        assertEquals(40_000L, Autoplay.pageMillis(20, pagesOnScreen = 2))
    }

    @Test
    fun aSpreadShowsTwoPagesExceptAtItsEdges() {
        assertEquals(2, Autoplay.spreadPages(firstPage = 4, pageCount = 20, coverAlone = false))
        assertEquals(1, Autoplay.spreadPages(firstPage = 19, pageCount = 20, coverAlone = false))
        assertEquals(1, Autoplay.spreadPages(firstPage = 0, pageCount = 20, coverAlone = true))
        assertEquals(2, Autoplay.spreadPages(firstPage = 0, pageCount = 20, coverAlone = false))
    }

    @Test
    fun aPageOfStopsIsSharedBetweenThem() {
        assertEquals(10_000L, Autoplay.stopMillis(seconds = 40, stops = 4))
    }

    @Test
    fun manyStopsNeverMachineGun() {
        assertEquals(1_500L, Autoplay.stopMillis(seconds = 4, stops = 8))
    }

    @Test
    fun theShortestIntervalStillClearsTheFloorOnATwoStopPage() {
        assertEquals(1_500L, Autoplay.stopMillis(AUTOPLAY_SECONDS_RANGE.first, stops = 2))
    }

    @Test
    fun aPageWithoutStopsLastsTheWholeInterval() {
        assertEquals(30_000L, Autoplay.stopMillis(seconds = 30, stops = 0))
    }

    @Test
    fun aFreshDwellHasNotRunOut() {
        val dwell = AutoplayDwell(totalMillis = 10_000L)
        assertFalse(dwell.done)
        assertEquals(0f, dwell.progress, 0.001f)
    }

    @Test
    fun aTickAdvancesTheDwell() {
        val dwell = AutoplayDwell(totalMillis = 10_000L).ticked(5 * ONE_SECOND_NANOS, held = false)
        assertEquals(0.5f, dwell.progress, 0.001f)
        assertFalse(dwell.done)
    }

    @Test
    fun aHeldFingerFreezesTheDwellWithoutLosingIt() {
        val half = AutoplayDwell(totalMillis = 10_000L).ticked(5 * ONE_SECOND_NANOS, held = false)
        val held = half.ticked(3 * ONE_SECOND_NANOS, held = true).ticked(ONE_SECOND_NANOS, held = true)
        assertEquals(half, held)
        assertEquals(0.5f, held.progress, 0.001f)
    }

    @Test
    fun aReleasedFingerCarriesOnFromWhereItFroze() {
        val dwell = AutoplayDwell(totalMillis = 10_000L)
            .ticked(5 * ONE_SECOND_NANOS, held = false)
            .ticked(9 * ONE_SECOND_NANOS, held = true)
            .ticked(5 * ONE_SECOND_NANOS, held = false)
        assertTrue(dwell.done)
        assertEquals(1f, dwell.progress, 0.001f)
    }

    @Test
    fun theSwitchTurnsAutoplayOnAndOffFromEitherPlaybackState() {
        assertEquals(AutoplayState.Running, AutoplayState.Off.switched())
        assertEquals(AutoplayState.Off, AutoplayState.Running.switched())
        assertEquals(AutoplayState.Off, AutoplayState.Paused.switched())
    }

    @Test
    fun theSwitchAlwaysComesBackOnRunningNeverPaused() {
        assertTrue(AutoplayState.Off.switched().running)
    }

    @Test
    fun thePillMovesBetweenRunningAndPausedBothWays() {
        assertEquals(AutoplayState.Paused, AutoplayState.Running.transported())
        assertEquals(AutoplayState.Running, AutoplayState.Paused.transported())
    }

    @Test
    fun thePillDoesNothingWhileAutoplayIsOff() {
        assertEquals(AutoplayState.Off, AutoplayState.Off.transported())
    }

    @Test
    fun aPausedAutoplayIsStillOn() {
        assertTrue(AutoplayState.Paused.on)
        assertFalse(AutoplayState.Paused.running)
        assertFalse(AutoplayState.Off.on)
    }

    @Test
    fun theStripCoversOnePageHeightPerInterval() {
        val pageHeight = 2400f
        val seconds = 30
        val scrolled = Autoplay.scrollPixels(pageHeight, seconds, elapsedNanos = seconds * ONE_SECOND_NANOS)
        assertEquals(pageHeight, scrolled, 0.01f)
    }

    @Test
    fun theStripScrollsProportionallyToTheFrameLength() {
        val half = Autoplay.scrollPixels(pageHeight = 1000f, seconds = 10, elapsedNanos = 5 * ONE_SECOND_NANOS)
        assertEquals(500f, half, 0.01f)
    }

    @Test
    fun aSlowerIntervalScrollsLess() {
        val frame = 16_000_000L
        val fast = Autoplay.scrollPixels(pageHeight = 1000f, seconds = 10, elapsedNanos = frame)
        val slow = Autoplay.scrollPixels(pageHeight = 1000f, seconds = 60, elapsedNanos = frame)
        assertTrue(slow < fast)
    }
}
