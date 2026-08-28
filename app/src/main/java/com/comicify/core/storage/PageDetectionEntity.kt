package com.comicify.core.storage

import androidx.room.Entity

@Entity(tableName = "page_detections", primaryKeys = ["documentUri", "pageIndex"])
data class PageDetectionEntity(
    val documentUri: String,
    val pageIndex: Int,
    val modelVersion: String,
    val panels: String?,
    val bubbles: String?,
)
