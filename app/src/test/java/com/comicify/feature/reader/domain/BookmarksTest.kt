package com.comicify.feature.reader.domain

import com.comicify.domain.model.ReadingDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarksTest {

    @Test
    fun unfilteredScrubberShowsEveryPage() {
        assertEquals(listOf(0, 1, 2, 3), Bookmarks.scrubberPages(4, bookmarks = setOf(2), bookmarksOnly = false))
    }

    @Test
    fun filteredScrubberShowsTheBookmarkedPagesInReadingOrder() {
        assertEquals(listOf(1, 5), Bookmarks.scrubberPages(8, bookmarks = setOf(5, 1), bookmarksOnly = true))
    }

    @Test
    fun filteredScrubberDropsBookmarksOutsideTheComic() {
        assertEquals(listOf(2), Bookmarks.scrubberPages(3, bookmarks = setOf(2, 9), bookmarksOnly = true))
    }

    @Test
    fun filteredScrubberIsEmptyWithoutBookmarks() {
        assertEquals(emptyList<Int>(), Bookmarks.scrubberPages(3, bookmarks = emptySet(), bookmarksOnly = true))
    }

    @Test
    fun splittingWidePagesMovesBookmarksToTheFirstHalf() {
        val split = SplitPages.of(listOf(0.7f, 1.5f, 0.7f), ReadingDirection.LeftToRight)
        val remapped = Bookmarks.remappedPages(listOf(1, 2)) { SplitPages.firstPageOfSource(split, it) }
        assertEquals(listOf(1, 3), remapped)
    }

    @Test
    fun unsplittingWidePagesMergesBothHalvesIntoOneBookmark() {
        val split = SplitPages.of(listOf(0.7f, 1.5f, 0.7f), ReadingDirection.LeftToRight)
        val remapped = Bookmarks.remappedPages(listOf(1, 2, 3)) { split[it].sourceIndex }
        assertEquals(listOf(1, 2), remapped)
    }

    @Test
    fun theSpreadBookmarksThePageTheCounterNames() {
        val spread = PageOrder.spreadIndex(pageIndex = 5, coverAlone = false)
        assertEquals(4, PageOrder.spreadFirstPage(spread, coverAlone = false).coerceAtLeast(0))
    }

    @Test
    fun theLoneCoverBookmarksTheCover() {
        val spread = PageOrder.spreadIndex(pageIndex = 0, coverAlone = true)
        assertEquals(0, PageOrder.spreadFirstPage(spread, coverAlone = true).coerceAtLeast(0))
    }

    @Test
    fun theSinglePageSurfaceBookmarksThePageOnScreen() {
        val pagerIndex = PageOrder.pagerIndex(ReadingDirection.RightToLeft, logicalIndex = 5, count = 20)
        assertEquals(5, PageOrder.logicalIndex(ReadingDirection.RightToLeft, pagerIndex, count = 20))
    }
}
