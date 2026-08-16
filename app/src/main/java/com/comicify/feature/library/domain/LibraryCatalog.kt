package com.comicify.feature.library.domain

import com.comicify.core.util.naturalOrder

object LibraryCatalog {

    private const val CONTINUE_READING_LIMIT = 12

    fun isCompleted(pageIndex: Int, pageCount: Int): Boolean =
        pageCount > 0 && pageIndex >= pageCount - 1

    fun progress(pageIndex: Int, pageCount: Int): Float =
        if (pageCount <= 0) 0f else ((pageIndex + 1).toFloat() / pageCount).coerceIn(0f, 1f)

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

    fun continueReading(comics: List<LibraryComic>): List<LibraryComic> =
        comics.filter { it.lastReadAt != null && !it.completed && it.pageIndex > 0 }
            .sortedByDescending { it.lastReadAt }
            .take(CONTINUE_READING_LIMIT)
}
