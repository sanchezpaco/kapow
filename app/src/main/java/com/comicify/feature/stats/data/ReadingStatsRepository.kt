package com.comicify.feature.stats.data

import com.comicify.feature.reader.domain.ReaderViewMode
import com.comicify.feature.stats.domain.ReadingSession
import com.comicify.feature.stats.domain.SeriesReading
import kotlinx.coroutines.flow.Flow

interface ReadingStatsRepository {
    val readings: Flow<List<SeriesReading>>
    fun secondsPerPage(documentUri: String, series: String): Flow<Int>
    suspend fun openMode(comicId: Long): ReaderViewMode
    suspend fun record(session: ReadingSession)
    suspend fun deleteAll()
}
