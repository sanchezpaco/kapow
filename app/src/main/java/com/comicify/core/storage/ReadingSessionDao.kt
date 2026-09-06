package com.comicify.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ReadingSessionRow(
    val comicId: Long,
    val series: String,
    val startedAt: Long,
    val endedAt: Long,
    val pages: Int,
    val mode: String,
    val finished: Boolean,
)

@Dao
interface ReadingSessionDao {

    @Query(
        "SELECT s.comicId, c.series AS series, s.startedAt, s.endedAt, s.pages, s.mode, s.finished " +
            "FROM reading_session s JOIN comics c ON c.id = s.comicId ORDER BY s.startedAt DESC",
    )
    fun observeRecentFirst(): Flow<List<ReadingSessionRow>>

    @Insert
    suspend fun insert(session: ReadingSessionEntity)

    @Query("DELETE FROM reading_session WHERE startedAt < :cutoff")
    suspend fun pruneBefore(cutoff: Long)

    @Query("DELETE FROM reading_session")
    suspend fun deleteAll()
}
