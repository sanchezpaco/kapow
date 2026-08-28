package com.comicify.feature.library.domain

import com.comicify.core.util.naturalOrder

object LibraryCatalog {

    private const val CONTINUE_READING_LIMIT = 12
    private const val SECONDS_PER_PAGE = 45
    private const val SECONDS_PER_MINUTE = 60

    fun isCompleted(pageIndex: Int, pageCount: Int): Boolean =
        pageCount > 0 && pageIndex >= pageCount - 1

    fun progress(pageIndex: Int, pageCount: Int): Float =
        if (pageCount <= 0) 0f else ((pageIndex + 1).toFloat() / pageCount).coerceIn(0f, 1f)

    fun minutesLeft(pageIndex: Int, pageCount: Int): Int {
        val pagesLeft = (pageCount - pageIndex - 1).coerceAtLeast(0)
        return (pagesLeft * SECONDS_PER_PAGE + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
    }

    fun title(series: String, issueNumber: Int?): String =
        if (issueNumber != null) "$series #$issueNumber" else series

    fun sort(comics: List<LibraryComic>): List<LibraryComic> {
        val comparator = compareBy(naturalOrder) { comic: LibraryComic -> comic.series }
            .thenBy(nullsLast<Int>()) { it.issueNumber }
            .thenComparing(compareBy(naturalOrder) { comic: LibraryComic -> comic.title })
        return comics.sortedWith(comparator)
    }

    fun filtered(comics: List<LibraryComic>, filter: LibraryFilter): List<LibraryComic> =
        when (filter) {
            LibraryFilter.ALL -> comics
            LibraryFilter.UNREAD -> comics.filter { !it.completed }
            LibraryFilter.READ -> comics.filter { it.completed }
            LibraryFilter.FAVORITES -> comics.filter { it.favorite }
        }

    fun nextInSeries(comics: List<LibraryComic>, comicId: Long): LibraryComic? {
        val current = comics.firstOrNull { it.id == comicId } ?: return null
        val seriesKey = groupKey(current.series)
        val ordered = sort(comics.filter { groupKey(it.series) == seriesKey })
        return ordered.getOrNull(ordered.indexOfFirst { it.id == comicId } + 1)
    }

    fun continueReading(comics: List<LibraryComic>): List<LibraryComic> =
        comics.filter { it.lastReadAt != null && !it.completed && it.pageIndex > 0 && it.shelved }
            .sortedByDescending { it.lastReadAt }
            .take(CONTINUE_READING_LIMIT)

    fun grouped(comics: List<LibraryComic>): List<LibraryEntry> {
        val bySeries = LinkedHashMap<String, MutableList<LibraryComic>>()
        comics.forEach { comic ->
            bySeries.getOrPut(groupKey(comic.series)) { mutableListOf() }.add(comic)
        }
        return bySeries.values.map { members ->
            if (members.size >= 2) LibraryEntry.Group(members.first().series, members)
            else LibraryEntry.Single(members.first())
        }
    }

    private fun groupKey(series: String): String = series.trim().lowercase()
}
