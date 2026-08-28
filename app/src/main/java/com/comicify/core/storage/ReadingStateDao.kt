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

    @Query("UPDATE reading_states SET shelved = 0 WHERE comicId = :comicId")
    suspend fun unshelve(comicId: Long)

    @Query("UPDATE reading_states SET shelved = 1 WHERE comicId = :comicId")
    suspend fun reshelve(comicId: Long)

    @Upsert
    suspend fun upsert(state: ReadingStateEntity)

    @Query("DELETE FROM reading_states WHERE comicId = :comicId")
    suspend fun delete(comicId: Long)
}
