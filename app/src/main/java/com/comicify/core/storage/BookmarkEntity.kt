package com.comicify.core.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "bookmark",
    primaryKeys = ["comicId", "pageIndex"],
    foreignKeys = [
        ForeignKey(
            entity = ComicEntity::class,
            parentColumns = ["id"],
            childColumns = ["comicId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("comicId")],
)
data class BookmarkEntity(
    val comicId: Long,
    val pageIndex: Int,
    val createdAt: Long,
)
