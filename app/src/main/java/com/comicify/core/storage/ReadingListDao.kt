package com.comicify.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingListDao {

    @Query("SELECT * FROM reading_list ORDER BY createdAt")
    fun observeLists(): Flow<List<ReadingListEntity>>

    @Query("SELECT * FROM reading_list_entry ORDER BY ordering")
    fun observeEntries(): Flow<List<ReadingListEntryEntity>>

    @Query("SELECT * FROM reading_list_entry WHERE listId = :listId ORDER BY ordering")
    suspend fun entriesOf(listId: Long): List<ReadingListEntryEntity>

    @Query("SELECT * FROM reading_list_entry WHERE listId = :listId AND comicId = :comicId")
    suspend fun findEntry(listId: Long, comicId: Long): ReadingListEntryEntity?

    @Query("SELECT MAX(ordering) FROM reading_list_entry WHERE listId = :listId")
    suspend fun maxOrdering(listId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: ReadingListEntity): Long

    @Query("UPDATE reading_list SET name = :name WHERE id = :id")
    suspend fun renameList(id: Long, name: String)

    @Query("DELETE FROM reading_list WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: ReadingListEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<ReadingListEntryEntity>)

    @Update
    suspend fun updateEntries(entries: List<ReadingListEntryEntity>)

    @Query("DELETE FROM reading_list_entry WHERE listId = :listId AND comicId = :comicId")
    suspend fun deleteEntry(listId: Long, comicId: Long)
}
