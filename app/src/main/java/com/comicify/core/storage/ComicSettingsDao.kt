package com.comicify.core.storage

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ComicSettingsDao {

    @Query("SELECT * FROM comic_settings WHERE documentUri = :documentUri")
    fun observe(documentUri: String): Flow<ComicSettingsEntity?>

    @Query("SELECT * FROM comic_settings WHERE documentUri = :documentUri")
    suspend fun find(documentUri: String): ComicSettingsEntity?

    @Upsert
    suspend fun upsert(settings: ComicSettingsEntity)

    @Query("DELETE FROM comic_settings WHERE documentUri = :documentUri")
    suspend fun delete(documentUri: String)
}
