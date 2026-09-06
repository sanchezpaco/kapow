package com.comicify.feature.stats.data

import android.content.Context
import com.comicify.core.storage.ComicDao
import com.comicify.core.storage.ComicSettingsDao
import com.comicify.core.storage.ComicSettingsEntity
import com.comicify.core.storage.ReadingSessionDao
import com.comicify.core.storage.ReadingSessionEntity
import com.comicify.core.storage.ReadingSessionRow
import com.comicify.core.storage.ReaderPreferencesRepository
import com.comicify.feature.reader.domain.ReaderViewMode
import com.comicify.feature.stats.domain.ReadingPace
import com.comicify.feature.stats.domain.ReadingSession
import com.comicify.feature.stats.domain.SeriesReading
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val RETENTION_DAYS = 400L
private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

@Singleton
class ReadingStatsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val sessionDao: ReadingSessionDao,
    private val comicDao: ComicDao,
    private val comicSettingsDao: ComicSettingsDao,
) : ReadingStatsRepository {

    private val preferences = ReaderPreferencesRepository(context)

    override val readings: Flow<List<SeriesReading>> =
        sessionDao.observeRecentFirst().map { rows -> rows.map(ReadingSessionRow::toSeriesReading) }

    override fun secondsPerPage(documentUri: String, series: String): Flow<Int> =
        combine(readings, openMode(documentUri)) { sessions, mode ->
            ReadingPace.secondsPerPage(sessions, series, mode)
        }

    override suspend fun openMode(comicId: Long): ReaderViewMode {
        val comic = comicDao.findById(comicId) ?: return ReaderViewMode.Pages
        return openMode(comicSettingsDao.find(comic.documentUri), preferences.guidedOnOpen.first())
    }

    override suspend fun record(session: ReadingSession) {
        sessionDao.insert(session.toEntity())
        sessionDao.pruneBefore(System.currentTimeMillis() - RETENTION_DAYS * MILLIS_PER_DAY)
    }

    override suspend fun deleteAll() {
        sessionDao.deleteAll()
    }

    private fun openMode(documentUri: String): Flow<ReaderViewMode> =
        combine(comicSettingsDao.observe(documentUri), preferences.guidedOnOpen, ::openMode)

    private fun openMode(settings: ComicSettingsEntity?, guidedOnOpen: Boolean): ReaderViewMode =
        ReaderViewMode.of(
            guided = settings?.guided ?: guidedOnOpen,
            verticalScroll = settings?.verticalScroll ?: false,
        )
}

private fun ReadingSessionRow.toSeriesReading(): SeriesReading =
    SeriesReading(
        series = series,
        session = ReadingSession(
            comicId = comicId,
            startedAt = startedAt,
            endedAt = endedAt,
            pages = pages,
            mode = ReaderViewMode.valueOf(mode),
            finished = finished,
        ),
    )

private fun ReadingSession.toEntity(): ReadingSessionEntity =
    ReadingSessionEntity(
        comicId = comicId,
        startedAt = startedAt,
        endedAt = endedAt,
        pages = pages,
        mode = mode.name,
        finished = finished,
    )
