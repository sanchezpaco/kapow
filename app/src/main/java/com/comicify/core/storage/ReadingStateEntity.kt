package com.comicify.core.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_states")
data class ReadingStateEntity(
    @PrimaryKey val comicId: Long,
    val pageIndex: Int,
    val completed: Boolean,
    val updatedAt: Long,
)
