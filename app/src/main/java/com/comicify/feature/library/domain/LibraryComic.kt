package com.comicify.feature.library.domain

data class LibraryComic(
    val id: Long,
    val documentUri: String,
    val title: String,
    val series: String,
    val issueNumber: Int?,
    val coverPath: String?,
    val coverAmbient: Int?,
    val pageCount: Int?,
    val pageIndex: Int,
    val completed: Boolean,
    val favorite: Boolean,
    val lastReadAt: Long?,
)
