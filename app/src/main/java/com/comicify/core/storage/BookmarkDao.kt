package com.comicify.core.storage

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT pageIndex FROM bookmark WHERE comicId = :comicId ORDER BY pageIndex")
    fun observePages(comicId: Long): Flow<List<Int>>

    @Query("SELECT COUNT(*) FROM bookmark WHERE comicId = :comicId")
    fun observeCount(comicId: Long): Flow<Int>

    @Query("SELECT * FROM bookmark WHERE comicId = :comicId ORDER BY pageIndex")
    suspend fun find(comicId: Long): List<BookmarkEntity>

    @Upsert
    suspend fun upsert(bookmark: BookmarkEntity)

    @Upsert
    suspend fun upsertAll(bookmarks: List<BookmarkEntity>)

    @Query("DELETE FROM bookmark WHERE comicId = :comicId AND pageIndex = :pageIndex")
    suspend fun delete(comicId: Long, pageIndex: Int)

    @Query("DELETE FROM bookmark WHERE comicId = :comicId")
    suspend fun deleteAll(comicId: Long)
}
