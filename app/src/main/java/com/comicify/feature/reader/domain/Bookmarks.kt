package com.comicify.feature.reader.domain

object Bookmarks {

    fun scrubberPages(pageCount: Int, bookmarks: Set<Int>, bookmarksOnly: Boolean): List<Int> {
        val pages = 0 until pageCount
        return if (bookmarksOnly) pages.filter { it in bookmarks } else pages.toList()
    }

    fun remappedPages(pages: List<Int>, pageInNewSource: (Int) -> Int): List<Int> =
        pages.map(pageInNewSource).distinct().sorted()
}
