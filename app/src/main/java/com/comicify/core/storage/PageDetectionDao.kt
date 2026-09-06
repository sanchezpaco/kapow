package com.comicify.core.storage

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PageDetectionDao {

    @Query("SELECT * FROM page_detections WHERE documentUri = :documentUri AND pageIndex = :pageIndex AND modelVersion = :modelVersion")
    suspend fun find(documentUri: String, pageIndex: Int, modelVersion: String): PageDetectionEntity?

    @Upsert
    suspend fun upsert(detection: PageDetectionEntity)

    @Query("UPDATE OR REPLACE page_detections SET documentUri = :documentUri WHERE documentUri = :previousUri")
    suspend fun relink(previousUri: String, documentUri: String)

    @Query("DELETE FROM page_detections WHERE documentUri = :documentUri")
    suspend fun deleteAll(documentUri: String)
}
