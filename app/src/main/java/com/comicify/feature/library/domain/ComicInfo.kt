package com.comicify.feature.library.domain

data class ComicInfo(
    val series: String?,
    val number: Int?,
    val year: Int?,
    val storyTitle: String?,
    val publisher: String?,
    val writer: String?,
    val penciller: String?,
    val inker: String?,
    val colorist: String?,
    val summary: String?,
    val readsRightToLeft: Boolean?,
)
