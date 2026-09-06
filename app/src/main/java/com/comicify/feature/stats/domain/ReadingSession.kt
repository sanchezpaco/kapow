package com.comicify.feature.stats.domain

import com.comicify.feature.reader.domain.ReaderViewMode

data class ReadingSession(
    val comicId: Long,
    val startedAt: Long,
    val endedAt: Long,
    val pages: Int,
    val mode: ReaderViewMode,
    val finished: Boolean,
) {
    val millis: Long = endedAt - startedAt
}

data class SeriesReading(val series: String, val session: ReadingSession)
