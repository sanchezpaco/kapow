package com.comicify.feature.stats.domain

import com.comicify.feature.reader.domain.ReaderViewMode
import kotlin.math.roundToInt

object ReadingPace {

    const val FALLBACK_SECONDS_PER_PAGE = 45

    private const val MIN_PAGES_PER_SESSION = 3
    private const val MIN_SESSIONS = 3
    private const val MIN_SERIES_PAGES = 20
    private const val SERIES_WINDOW = 10
    private const val LIBRARY_WINDOW = 30
    private const val MIN_SECONDS_PER_PAGE = 10.0
    private const val MAX_SECONDS_PER_PAGE = 300.0
    private const val MILLIS_PER_SECOND = 1000.0

    fun secondsPerPage(recentFirst: List<SeriesReading>, series: String, mode: ReaderViewMode): Int {
        val inMode = recentFirst.filter { it.session.mode == mode }
        val ofSeries = inMode.filter { sameSeries(it.series, series) }.map { it.session }
        median(ofSeries, SERIES_WINDOW, MIN_SERIES_PAGES)?.let { return it }
        median(inMode.map { it.session }, LIBRARY_WINDOW, minPages = 0)?.let { return it }
        return FALLBACK_SECONDS_PER_PAGE
    }

    fun secondsPerPage(sessions: List<ReadingSession>): Int =
        median(contributing(sessions).map { it.clampedSecondsPerPage() }) ?: FALLBACK_SECONDS_PER_PAGE

    private fun median(recentFirst: List<ReadingSession>, window: Int, minPages: Int): Int? {
        val sessions = contributing(recentFirst).take(window)
        if (sessions.size < MIN_SESSIONS) return null
        if (sessions.sumOf { it.pages } < minPages) return null
        return median(sessions.map { it.clampedSecondsPerPage() })
    }

    private fun contributing(sessions: List<ReadingSession>): List<ReadingSession> =
        sessions.filter { it.pages >= MIN_PAGES_PER_SESSION }

    private fun median(values: List<Double>): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        val value = if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
        return value.roundToInt()
    }

    private fun ReadingSession.clampedSecondsPerPage(): Double =
        (millis / MILLIS_PER_SECOND / pages).coerceIn(MIN_SECONDS_PER_PAGE, MAX_SECONDS_PER_PAGE)

    private fun sameSeries(left: String, right: String): Boolean = left.trim().equals(right.trim(), ignoreCase = true)
}
