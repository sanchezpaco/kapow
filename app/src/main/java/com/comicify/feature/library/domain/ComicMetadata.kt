package com.comicify.feature.library.domain

data class ComicMetadata(
    val series: String,
    val issueNumber: Int?,
    val year: Int?,
    val storyTitle: String? = null,
    val publisher: String? = null,
    val writer: String? = null,
    val penciller: String? = null,
    val inker: String? = null,
    val colorist: String? = null,
    val summary: String? = null,
    val readsRightToLeft: Boolean? = null,
)

fun mergeComicMetadata(info: ComicInfo?, parsed: ParsedComicName): ComicMetadata =
    ComicMetadata(
        series = info?.series ?: parsed.series,
        issueNumber = info?.number ?: parsed.issueNumber,
        year = info?.year ?: parsed.year,
        storyTitle = info?.storyTitle,
        publisher = info?.publisher,
        writer = info?.writer,
        penciller = info?.penciller,
        inker = info?.inker,
        colorist = info?.colorist,
        summary = info?.summary,
        readsRightToLeft = info?.readsRightToLeft,
    )
