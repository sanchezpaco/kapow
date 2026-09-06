package com.comicify.core.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.comicify.feature.reader.domain.ReadingType

@Entity(tableName = "comic_settings")
data class ComicSettingsEntity(
    @PrimaryKey val documentUri: String,
    val readingType: ReadingType?,
    val coverAlone: Boolean,
    val bubblesEnlarged: Boolean?,
    val guided: Boolean?,
    val bubbleScale: Float? = null,
    val splitWidePages: Boolean = false,
    val splitSuggested: Boolean = false,
    val verticalScroll: Boolean = false,
)
