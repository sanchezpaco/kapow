package com.comicify.core.storage

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingStateDao {

    @Query("SELECT * FROM reading_states")
    fun observeAll(): Flow<List<ReadingStateEntity>>

    @Query("SELECT * FROM reading_states WHERE comicId = :comicId")
    suspend fun find(comicId: Long): ReadingStateEntity?

    @Upsert
    suspend fun upsert(state: ReadingStateEntity)

    @Query("DELETE FROM reading_states WHERE comicId = :comicId")
    suspend fun delete(comicId: Long)
}
