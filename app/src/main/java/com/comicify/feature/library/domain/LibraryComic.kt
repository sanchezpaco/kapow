package com.comicify.feature.library.domain

data class LibraryComic(
    val id: Long,
    val documentUri: String,
    val displayName: String,
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
    val shelved: Boolean,
    val storyTitle: String? = null,
    val publisher: String? = null,
    val writer: String? = null,
    val penciller: String? = null,
    val inker: String? = null,
    val colorist: String? = null,
    val summary: String? = null,
    val year: Int? = null,
    val addedAt: Long = 0L,
) {
    val hasComicInfo: Boolean
        get() = listOfNotNull(storyTitle, publisher, writer, penciller, inker, colorist, summary).isNotEmpty()
}
