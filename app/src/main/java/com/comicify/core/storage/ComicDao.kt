package com.comicify.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ComicDao {

    @Query("SELECT * FROM comics")
    fun observeAll(): Flow<List<ComicEntity>>

    @Query("SELECT * FROM comics")
    suspend fun getAll(): List<ComicEntity>

    @Query("SELECT * FROM comics WHERE id = :id")
    suspend fun findById(id: Long): ComicEntity?

    @Query("SELECT * FROM comics WHERE documentUri = :documentUri")
    suspend fun findByDocumentUri(documentUri: String): ComicEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(comic: ComicEntity): Long

    @Query("UPDATE comics SET pageCount = :pageCount, coverPath = :coverPath, coverAmbient = :coverAmbient WHERE id = :id")
    suspend fun updateCover(id: Long, pageCount: Int?, coverPath: String?, coverAmbient: Int?)

    @Query(
        "UPDATE comics SET series = :series, issueNumber = :issueNumber, year = :year, storyTitle = :storyTitle, " +
            "publisher = :publisher, writer = :writer, penciller = :penciller, inker = :inker, colorist = :colorist, " +
            "summary = :summary, readsRightToLeft = :readsRightToLeft, metadataVersion = :metadataVersion WHERE id = :id",
    )
    suspend fun updateMetadata(
        id: Long,
        series: String,
        issueNumber: Int?,
        year: Int?,
        storyTitle: String?,
        publisher: String?,
        writer: String?,
        penciller: String?,
        inker: String?,
        colorist: String?,
        summary: String?,
        readsRightToLeft: Boolean?,
        metadataVersion: Int,
    )

    @Query("UPDATE comics SET contentHash = :contentHash WHERE id = :id")
    suspend fun setContentHash(id: Long, contentHash: String)

    @Query(
        "UPDATE comics SET documentUri = :documentUri, displayName = :displayName, series = :series, " +
            "issueNumber = :issueNumber, year = :year, metadataVersion = 0 WHERE id = :id",
    )
    suspend fun relink(
        id: Long,
        documentUri: String,
        displayName: String,
        series: String,
        issueNumber: Int?,
        year: Int?,
    )

    @Query("UPDATE comics SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM comics WHERE id = :id")
    suspend fun deleteById(id: Long)
}
