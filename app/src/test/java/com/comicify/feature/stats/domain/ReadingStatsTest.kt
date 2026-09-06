package com.comicify.feature.stats.domain

import com.comicify.feature.reader.domain.ReaderViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val ZONE: ZoneId = ZoneId.of("UTC")
private val TODAY: LocalDate = LocalDate.of(2026, 9, 15)
private val NOW = TODAY.atTime(LocalTime.NOON).atZone(ZONE).toInstant().toEpochMilli()
private const val MINUTE = 60_000L

class ReadingStatsTest {

    private fun reading(
        daysAgo: Long,
        pages: Int,
        minutes: Long = 10,
        series: String = "Daredevil",
        comicId: Long = 1,
        finished: Boolean = false,
    ): SeriesReading {
        val startedAt = TODAY.minusDays(daysAgo).atTime(LocalTime.NOON).atZone(ZONE).toInstant().toEpochMilli()
        return SeriesReading(
            series = series,
            session = ReadingSession(
                comicId = comicId,
                startedAt = startedAt,
                endedAt = startedAt + minutes * MINUTE,
                pages = pages,
                mode = ReaderViewMode.Pages,
                finished = finished,
            ),
        )
    }

    private fun statsOf(readings: List<SeriesReading>) = readingStats(readings, ZONE, NOW)

    @Test
    fun addsUpThePagesOfTheLastThirtyDays() {
        val stats = statsOf(listOf(reading(daysAgo = 0, pages = 10), reading(daysAgo = 29, pages = 5), reading(daysAgo = 30, pages = 100)))
        assertEquals(15, stats.pages)
    }

    @Test
    fun addsUpTheTimeOfTheLastThirtyDays() {
        val stats = statsOf(listOf(reading(daysAgo = 1, pages = 10, minutes = 12), reading(daysAgo = 2, pages = 10, minutes = 8)))
        assertEquals(20 * MINUTE, stats.millis)
    }

    @Test
    fun alwaysDrawsThirtyBarsEndingToday() {
        val stats = statsOf(listOf(reading(daysAgo = 0, pages = 4)))
        assertEquals(30, stats.perDay.size)
        assertEquals(TODAY.minusDays(29), stats.perDay.first().date)
        assertEquals(TODAY, stats.perDay.last().date)
        assertEquals(4, stats.perDay.last().pages)
    }

    @Test
    fun countsTheDaysWithReading() {
        val stats = statsOf(
            listOf(
                reading(daysAgo = 0, pages = 4),
                reading(daysAgo = 0, pages = 6),
                reading(daysAgo = 3, pages = 4),
            ),
        )
        assertEquals(2, stats.daysRead)
        assertEquals(10, stats.perDay.last().pages)
    }

    @Test
    fun countsEachComicFinishedThisMonthOnce() {
        val stats = statsOf(
            listOf(
                reading(daysAgo = 1, pages = 20, comicId = 1, finished = true),
                reading(daysAgo = 2, pages = 20, comicId = 1, finished = true),
                reading(daysAgo = 3, pages = 20, comicId = 2, finished = true),
                reading(daysAgo = 20, pages = 20, comicId = 3, finished = true),
                reading(daysAgo = 4, pages = 20, comicId = 4, finished = false),
            ),
        )
        assertEquals(listOf(1L, 2L), stats.finishedThisMonth)
    }

    @Test
    fun ranksSeriesByTimeRead() {
        val stats = statsOf(
            listOf(
                reading(daysAgo = 1, pages = 20, minutes = 10, series = "Daredevil", comicId = 1),
                reading(daysAgo = 2, pages = 20, minutes = 30, series = "Hellboy", comicId = 2),
                reading(daysAgo = 3, pages = 20, minutes = 30, series = "Hellboy", comicId = 3),
            ),
        )
        assertEquals(listOf("Hellboy", "Daredevil"), stats.series.map { it.series })
        assertEquals(2, stats.series.first().issues)
        assertEquals(90, stats.series.first().secondsPerPage)
        assertEquals(60 * MINUTE, stats.series.first().millis)
    }

    @Test
    fun keepsAtMostFiveSeries() {
        val readings = (1..8).map { reading(daysAgo = it.toLong(), pages = 20, series = "Series $it", comicId = it.toLong()) }
        assertEquals(5, statsOf(readings).series.size)
    }

    @Test
    fun staysEmptyUntilThereAreThreeSessionsAndTwentyPages() {
        assertFalse(statsOf(listOf(reading(daysAgo = 0, pages = 40))).enoughToShow)
        assertFalse(
            statsOf(List(3) { reading(daysAgo = it.toLong(), pages = 5) }).enoughToShow,
        )
        assertTrue(
            statsOf(List(3) { reading(daysAgo = it.toLong(), pages = 10) }).enoughToShow,
        )
    }

    @Test
    fun averagesPagesOverTheWholeWindow() {
        val stats = statsOf(listOf(reading(daysAgo = 0, pages = 60), reading(daysAgo = 1, pages = 30)))
        assertEquals(3, stats.averagePagesPerDay)
    }
}
