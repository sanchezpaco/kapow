package com.comicify.core.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "comics",
    indices = [Index(value = ["documentUri"], unique = true)],
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
)
