package com.comicify.feature.stats.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val STATS_WINDOW_DAYS = 30
private const val MIN_SESSIONS = 3
private const val MIN_PAGES = 20
private const val MAX_SERIES_ROWS = 5

data class DayPages(val date: LocalDate, val pages: Int)

data class SeriesPace(val series: String, val issues: Int, val secondsPerPage: Int, val millis: Long)

data class ReadingStats(
    val enoughToShow: Boolean,
    val pages: Int,
    val millis: Long,
    val secondsPerPage: Int,
    val daysRead: Int,
    val perDay: List<DayPages>,
    val series: List<SeriesPace>,
    val finishedThisMonth: List<Long>,
) {
    val averagePagesPerDay: Int = pages / STATS_WINDOW_DAYS
}

fun readingStats(recentFirst: List<SeriesReading>, zone: ZoneId, now: Long): ReadingStats {
    val today = dayOf(now, zone)
    val windowStart = today.minusDays(STATS_WINDOW_DAYS - 1L)
    val window = recentFirst.filter { dayOf(it.session.startedAt, zone) >= windowStart }
    val pagesByDay = window.groupingBy { dayOf(it.session.startedAt, zone) }.fold(0) { pages, reading -> pages + reading.session.pages }
    val perDay = (0 until STATS_WINDOW_DAYS).map { offset ->
        val date = windowStart.plusDays(offset.toLong())
        DayPages(date, pagesByDay[date] ?: 0)
    }
    val monthStart = today.withDayOfMonth(1)
    return ReadingStats(
        enoughToShow = recentFirst.size >= MIN_SESSIONS && recentFirst.sumOf { it.session.pages } >= MIN_PAGES,
        pages = window.sumOf { it.session.pages },
        millis = window.sumOf { it.session.millis },
        secondsPerPage = ReadingPace.secondsPerPage(window.map { it.session }),
        daysRead = perDay.count { it.pages > 0 },
        perDay = perDay,
        series = seriesPaces(recentFirst),
        finishedThisMonth = recentFirst
            .filter { it.session.finished && dayOf(it.session.startedAt, zone) >= monthStart }
            .map { it.session.comicId }
            .distinct(),
    )
}

private fun seriesPaces(recentFirst: List<SeriesReading>): List<SeriesPace> =
    recentFirst.groupBy { it.series.trim().lowercase() }.values
        .map { readings ->
            SeriesPace(
                series = readings.first().series.trim(),
                issues = readings.map { it.session.comicId }.distinct().size,
                secondsPerPage = ReadingPace.secondsPerPage(readings.map { it.session }),
                millis = readings.sumOf { it.session.millis },
            )
        }
        .sortedByDescending { it.millis }
        .take(MAX_SERIES_ROWS)

private fun dayOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
