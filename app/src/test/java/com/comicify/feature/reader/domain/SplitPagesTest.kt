package com.comicify.feature.reader.domain

import com.comicify.domain.model.ReadingDirection
import org.junit.Assert.assertEquals
import org.junit.Test

private const val PORTRAIT = 0.7f
private const val LANDSCAPE = 1.5f
private const val SQUARE = 1f

class SplitPagesTest {

    @Test
    fun portraitPagesPassThrough() {
        val pages = SplitPages.of(listOf(PORTRAIT, PORTRAIT), ReadingDirection.LeftToRight)

        assertEquals(listOf(SplitPage(0, null), SplitPage(1, null)), pages)
    }

    @Test
    fun squarePagesAreNotWide() {
        val pages = SplitPages.of(listOf(SQUARE), ReadingDirection.LeftToRight)

        assertEquals(listOf(SplitPage(0, null)), pages)
    }

    @Test
    fun leftToRightShowsTheLeftHalfFirst() {
        val pages = SplitPages.of(listOf(LANDSCAPE), ReadingDirection.LeftToRight)

        assertEquals(listOf(SplitPage(0, PageSide.Left), SplitPage(0, PageSide.Right)), pages)
    }

    @Test
    fun rightToLeftShowsTheRightHalfFirst() {
        val pages = SplitPages.of(listOf(LANDSCAPE), ReadingDirection.RightToLeft)

        assertEquals(listOf(SplitPage(0, PageSide.Right), SplitPage(0, PageSide.Left)), pages)
    }

    @Test
    fun mixedPagesKeepTheirSourceIndex() {
        val pages = SplitPages.of(listOf(PORTRAIT, LANDSCAPE, PORTRAIT), ReadingDirection.LeftToRight)

        assertEquals(
            listOf(
                SplitPage(0, null),
                SplitPage(1, PageSide.Left),
                SplitPage(1, PageSide.Right),
                SplitPage(2, null),
            ),
            pages,
        )
    }

    @Test
    fun firstPageOfSourceFindsTheLeadingHalf() {
        val pages = SplitPages.of(listOf(PORTRAIT, LANDSCAPE, LANDSCAPE), ReadingDirection.RightToLeft)

        assertEquals(0, SplitPages.firstPageOfSource(pages, 0))
        assertEquals(1, SplitPages.firstPageOfSource(pages, 1))
        assertEquals(3, SplitPages.firstPageOfSource(pages, 2))
    }

    @Test
    fun firstPageOfSourceFallsBackToTheFirstPage() {
        val pages = SplitPages.of(listOf(PORTRAIT), ReadingDirection.LeftToRight)

        assertEquals(0, SplitPages.firstPageOfSource(pages, 7))
    }
}
