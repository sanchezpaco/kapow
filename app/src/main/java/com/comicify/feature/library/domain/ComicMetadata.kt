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

data class EditedComicMetadata(val series: String, val issueNumber: Int?, val storyTitle: String?)

private val editedIssue = Regex("""^#?0*(\d{1,6})$""")

fun parseEditedIssueNumber(text: String): Int? =
    editedIssue.find(text.trim())?.groupValues?.get(1)?.toIntOrNull()

fun mergeComicMetadata(info: ComicInfo?, parsed: ParsedComicName, edited: EditedComicMetadata? = null): ComicMetadata =
    ComicMetadata(
        series = edited?.series ?: info?.series ?: parsed.series,
        issueNumber = if (edited != null) edited.issueNumber else info?.number ?: parsed.issueNumber,
        year = info?.year ?: parsed.year,
        storyTitle = if (edited != null) edited.storyTitle else info?.storyTitle,
        publisher = info?.publisher,
        writer = info?.writer,
        penciller = info?.penciller,
        inker = info?.inker,
        colorist = info?.colorist,
        summary = info?.summary,
        readsRightToLeft = info?.readsRightToLeft,
    )
