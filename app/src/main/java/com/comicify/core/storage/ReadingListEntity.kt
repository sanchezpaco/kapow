package com.comicify.core.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_list")
data class ReadingListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)
