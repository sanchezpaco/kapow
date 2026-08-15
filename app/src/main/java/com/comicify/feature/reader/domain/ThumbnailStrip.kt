package com.comicify.feature.reader.domain

object ThumbnailStrip {

    fun stepIndexForPage(pageIndex: Int, pagesPerStep: Int): Int {
        val page = pageIndex.coerceAtLeast(0)
        return if (pagesPerStep <= 1) page else page / pagesPerStep
    }

    fun centerScrollOffsetPx(viewportWidthPx: Int, itemWidthPx: Int): Int =
        -((viewportWidthPx - itemWidthPx) / 2).coerceAtLeast(0)
}
