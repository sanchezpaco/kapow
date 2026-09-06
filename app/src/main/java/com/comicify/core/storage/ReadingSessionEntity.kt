package com.comicify.core.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_session",
    foreignKeys = [
        ForeignKey(
            entity = ComicEntity::class,
            parentColumns = ["id"],
            childColumns = ["comicId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("comicId"), Index("startedAt")],
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val comicId: Long,
    val startedAt: Long,
    val endedAt: Long,
    val pages: Int,
    val mode: String,
    val finished: Boolean,
)
