package com.comicify.feature.library.domain

const val FIRST_PAGE = 0

object ComicCover {

    fun fileName(comicId: Long, page: Int): String = "${comicId}_$page.jpg"

    fun belongsTo(fileName: String, comicId: Long): Boolean =
        fileName == "$comicId.jpg" || fileName.startsWith("${comicId}_")

    fun page(chosen: Int, pageCount: Int?): Int {
        if (pageCount == null || pageCount <= 0) return FIRST_PAGE
        return chosen.coerceIn(FIRST_PAGE, pageCount - 1)
    }
}
