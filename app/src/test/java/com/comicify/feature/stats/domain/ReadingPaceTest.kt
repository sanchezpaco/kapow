package com.comicify.feature.stats.domain

import com.comicify.feature.reader.domain.ReaderViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingPaceTest {

    private var nextComicId = 1L

    private fun reading(
        series: String,
        secondsPerPage: Int,
        pages: Int = 20,
        mode: ReaderViewMode = ReaderViewMode.Pages,
        autoplayed: Boolean = false,
    ): SeriesReading {
        val id = nextComicId++
        return SeriesReading(
            series = series,
            session = ReadingSession(
                comicId = id,
                startedAt = 0,
                endedAt = pages * secondsPerPage * 1000L,
                pages = pages,
                mode = mode,
                finished = false,
                autoplayed = autoplayed,
            ),
        )
    }

    @Test
    fun autoplayedSessionsNeverDefineThePace() {
        val readings = listOf(
            reading("Daredevil", 10, autoplayed = true),
            reading("Daredevil", 10, autoplayed = true),
            reading("Daredevil", 10, autoplayed = true),
            reading("Daredevil", 10, autoplayed = true),
        )
        assertEquals(45, ReadingPace.secondsPerPage(readings, "Daredevil", ReaderViewMode.Pages))
    }

    @Test
    fun autoplayedSessionsDoNotDragTheMedianOfTheReadOnes() {
        val readings = listOf(
            reading("Daredevil", 30),
            reading("Daredevil", 30),
            reading("Daredevil", 30),
            reading("Daredevil", 10, autoplayed = true),
            reading("Daredevil", 10, autoplayed = true),
            reading("Daredevil", 10, autoplayed = true),
        )
        assertEquals(30, ReadingPace.secondsPerPage(readings, "Daredevil", ReaderViewMode.Pages))
    }

    @Test
    fun fallsBackToFortyFiveSecondsWithoutHistory() {
        assertEquals(45, ReadingPace.secondsPerPage(emptyList(), "Daredevil", ReaderViewMode.Pages))
    }

    @Test
    fun fallsBackWhenTheHistoryIsTooThin() {
        val readings = listOf(reading("Daredevil", 20), reading("Daredevil", 20))
        assertEquals(45, ReadingPace.secondsPerPage(readings, "Daredevil", ReaderViewMode.Pages))
    }

    @Test
    fun usesTheMedianOfTheSeries() {
        val readings = listOf(reading("Daredevil", 20), reading("Daredevil", 30), reading("Daredevil", 100))
        assertEquals(30, ReadingPace.secondsPerPage(readings, "Daredevil", ReaderViewMode.Pages))
    }

    @Test
    fun ignoresOtherSeriesOnceTheSeriesQualifies() {
        val readings = listOf(
            reading("Daredevil", 20),
            reading("Daredevil", 20),
            reading("Daredevil", 20),
            reading("Hellboy", 200),
        )
        assertEquals(20, ReadingPace.secondsPerPage(readings, "Daredevil", ReaderViewMode.Pages))
    }

    @Test
    fun fallsBackToTheWholeLibraryWhenTheSeriesIsTooThin() {
        val readings = listOf(
            reading("Daredevil", 20, pages = 5),
            reading("Hellboy", 30),
            reading("Hellboy", 30),
            reading("Hellboy", 30),
        )
        assertEquals(30, ReadingPace.secondsPerPage(readings, "Daredevil", ReaderViewMode.Pages))
    }

    @Test
    fun theSeriesNeedsTwentyPagesBeforeItsOwnPaceCounts() {
        val readings = listOf(
            reading("Daredevil", 20, pages = 4),
            reading("Daredevil", 20, pages = 4),
            reading("Daredevil", 20, pages = 4),
            reading("Hellboy", 60),
            reading("Hellboy", 60),
            reading("Hellboy", 60),
        )
        assertEquals(40, ReadingPace.secondsPerPage(readings, "Daredevil", ReaderViewMode.Pages))
    }

    @Test
    fun onlyCountsSessionsReadInTheSameMode() {
        val readings = listOf(
            reading("Daredevil", 20, mode = ReaderViewMode.Guided),
            reading("Daredevil", 20, mode = ReaderViewMode.Guided),
            reading("Daredevil", 20, mode = ReaderViewMode.Guided),
        )
        assertEquals(45, ReadingPace.secondsPerPage(readings, "Daredevil", ReaderViewMode.Pages))
        assertEquals(20, ReadingPace.secondsPerPage(readings, "Daredevil", ReaderViewMode.Guided))
    }

    @Test
    fun skipsSessionsOfFewerThanThreePages() {
        val readings = listOf(
            reading("Daredevil", 20, pages = 2),
            reading("Daredevil", 40),
            reading("Daredevil", 40),
            reading("Daredevil", 40),
        )
        assertEquals(40, ReadingPace.secondsPerPage(readings, "Daredevil", ReaderViewMode.Pages))
    }

    @Test
    fun clampsASessionLeftOpenOnAPage() {
        val readings = listOf(
            reading("Daredevil", 30),
            reading("Daredevil", 40),
            reading("Daredevil", 4_000),
        )
        assertEquals(40, ReadingPace.secondsPerPage(readings, "Daredevil", ReaderViewMode.Pages))
    }

    @Test
    fun clampsAnImpossiblyFastSession() {
        assertEquals(10, ReadingPace.secondsPerPage(listOf(reading("Daredevil", 1).session)))
    }

    @Test
    fun onlyLooksAtTheTenMostRecentSessionsOfTheSeries() {
        val recent = List(10) { reading("Daredevil", 20) }
        val old = List(10) { reading("Daredevil", 200) }
        assertEquals(20, ReadingPace.secondsPerPage(recent + old, "Daredevil", ReaderViewMode.Pages))
    }
}
