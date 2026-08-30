package com.comicify.feature.reader.domain

private const val WIDE_PAGE_SHARE_TO_SUGGEST = 0.8f
private const val MIN_PAGES_AFTER_COVER = 2

object SplitSuggestion {

    fun shouldSuggest(aspects: List<Float>): Boolean {
        val afterCover = aspects.drop(1)
        if (afterCover.size < MIN_PAGES_AFTER_COVER) return false
        val wide = afterCover.count { it > WIDE_PAGE_ASPECT }
        return wide.toFloat() / afterCover.size >= WIDE_PAGE_SHARE_TO_SUGGEST
    }
}
