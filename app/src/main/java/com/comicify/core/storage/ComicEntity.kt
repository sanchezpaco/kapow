package com.comicify.core.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "comics",
    indices = [Index(value = ["documentUri"], unique = true), Index(value = ["contentHash"])],
)
data class ComicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentUri: String,
    val displayName: String,
    val series: String,
    val issueNumber: Int?,
    val year: Int?,
    val pageCount: Int?,
    val coverPath: String?,
    val addedAt: Long,
    val favorite: Boolean = false,
    val coverAmbient: Int? = null,
    val storyTitle: String? = null,
    val publisher: String? = null,
    val writer: String? = null,
    val penciller: String? = null,
    val inker: String? = null,
    val colorist: String? = null,
    val summary: String? = null,
    val readsRightToLeft: Boolean? = null,
    val metadataVersion: Int = 0,
    val contentHash: String? = null,
    val rating: Int = 0,
    val metadataEdited: Boolean = false,
    val coverPage: Int = 0,
)
