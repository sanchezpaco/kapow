package com.comicify.feature.reader.domain

import com.comicify.domain.model.ReadingDirection

const val WIDE_PAGE_ASPECT = 1f

enum class PageSide { Left, Right }

data class SplitPage(val sourceIndex: Int, val side: PageSide?)

object SplitPages {

    fun of(aspects: List<Float>, direction: ReadingDirection): List<SplitPage> =
        aspects.flatMapIndexed { index, aspect ->
            if (aspect > WIDE_PAGE_ASPECT) halves(index, direction) else listOf(SplitPage(index, side = null))
        }

    fun firstPageOfSource(pages: List<SplitPage>, sourceIndex: Int): Int =
        pages.indexOfFirst { it.sourceIndex == sourceIndex }.coerceAtLeast(0)

    private fun halves(index: Int, direction: ReadingDirection): List<SplitPage> = when (direction) {
        ReadingDirection.LeftToRight -> listOf(SplitPage(index, PageSide.Left), SplitPage(index, PageSide.Right))
        ReadingDirection.RightToLeft -> listOf(SplitPage(index, PageSide.Right), SplitPage(index, PageSide.Left))
    }
}
