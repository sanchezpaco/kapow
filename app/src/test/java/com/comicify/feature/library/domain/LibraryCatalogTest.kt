package com.comicify.feature.library.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCatalogTest {

    private fun comic(
        id: Long,
        series: String = "Series",
        issueNumber: Int? = null,
        pageIndex: Int = 0,
        completed: Boolean = false,
        favorite: Boolean = false,
        lastReadAt: Long? = null,
        pageCount: Int? = 20,
    ) = LibraryComic(
        id = id,
        documentUri = "uri$id",
        title = LibraryCatalog.title(series, issueNumber),
        series = series,
        issueNumber = issueNumber,
        coverPath = null,
        coverAmbient = null,
        pageCount = pageCount,
        pageIndex = pageIndex,
        completed = completed,
        favorite = favorite,
        lastReadAt = lastReadAt,
    )

    @Test
    fun markedCompletedOnLastPage() {
        assertTrue(LibraryCatalog.isCompleted(pageIndex = 19, pageCount = 20))
        assertFalse(LibraryCatalog.isCompleted(pageIndex = 10, pageCount = 20))
        assertFalse(LibraryCatalog.isCompleted(pageIndex = 0, pageCount = 0))
    }

    @Test
    fun progressFraction() {
        assertEquals(0.5f, LibraryCatalog.progress(pageIndex = 9, pageCount = 20), 0.0001f)
        assertEquals(1f, LibraryCatalog.progress(pageIndex = 19, pageCount = 20), 0.0001f)
        assertEquals(0f, LibraryCatalog.progress(pageIndex = 5, pageCount = 0), 0.0001f)
    }

    @Test
    fun buildsTitleWithIssue() {
        assertEquals("Batman #7", LibraryCatalog.title("Batman", 7))
        assertEquals("Watchmen", LibraryCatalog.title("Watchmen", null))
    }

    @Test
    fun sortsNaturallyBySeriesThenIssue() {
        val input = listOf(
            comic(1, series = "Saga", issueNumber = 10),
            comic(2, series = "Saga", issueNumber = 2),
            comic(3, series = "Batman", issueNumber = 1),
        )
        val sorted = LibraryCatalog.sort(input).map { it.id }
        assertEquals(listOf(3L, 2L, 1L), sorted)
    }

    @Test
    fun filtersByReadUnreadAndFavorite() {
        val input = listOf(
            comic(1, completed = false, favorite = false),
            comic(2, completed = true, favorite = false),
            comic(3, completed = false, favorite = true),
        )
        assertEquals(listOf(1L, 2L, 3L), LibraryCatalog.filtered(input, LibraryFilter.ALL).map { it.id })
        assertEquals(listOf(1L, 3L), LibraryCatalog.filtered(input, LibraryFilter.UNREAD).map { it.id })
        assertEquals(listOf(2L), LibraryCatalog.filtered(input, LibraryFilter.READ).map { it.id })
        assertEquals(listOf(3L), LibraryCatalog.filtered(input, LibraryFilter.FAVORITES).map { it.id })
    }

    @Test
    fun minutesLeftRoundsUpRemainingPages() {
        assertEquals(0, LibraryCatalog.minutesLeft(pageIndex = 19, pageCount = 20))
        assertEquals(1, LibraryCatalog.minutesLeft(pageIndex = 18, pageCount = 20))
        assertEquals(15, LibraryCatalog.minutesLeft(pageIndex = 0, pageCount = 20))
        assertEquals(0, LibraryCatalog.minutesLeft(pageIndex = 5, pageCount = 0))
    }

    @Test
    fun continueReadingKeepsRecentUnfinishedOnly() {
        val input = listOf(
            comic(1, pageIndex = 5, lastReadAt = 100),
            comic(2, pageIndex = 19, completed = true, lastReadAt = 300),
            comic(3, pageIndex = 8, lastReadAt = 200),
            comic(4, pageIndex = 0, lastReadAt = null),
        )
        val result = LibraryCatalog.continueReading(input).map { it.id }
        assertEquals(listOf(3L, 1L), result)
    }

    @Test
    fun groupsSeriesWithSeveralVolumesAndLeavesSinglesAlone() {
        val input = listOf(
            comic(1, series = "Batman", issueNumber = 1),
            comic(2, series = "Batman", issueNumber = 2),
            comic(3, series = "Watchmen", issueNumber = null),
        )
        val entries = LibraryCatalog.grouped(input)
        assertEquals(2, entries.size)
        val group = entries[0] as LibraryEntry.Group
        assertEquals("Batman", group.series)
        assertEquals(listOf(1L, 2L), group.comics.map { it.id })
        val single = entries[1] as LibraryEntry.Single
        assertEquals(3L, single.comic.id)
    }

    @Test
    fun nextInSeriesFollowsIssueOrderIgnoringInputOrder() {
        val input = listOf(
            comic(1, series = "Saga", issueNumber = 3),
            comic(2, series = "Saga", issueNumber = 1),
            comic(3, series = "Saga", issueNumber = 2),
            comic(4, series = "Batman", issueNumber = 1),
        )
        assertEquals(3L, LibraryCatalog.nextInSeries(input, comicId = 2)?.id)
        assertEquals(1L, LibraryCatalog.nextInSeries(input, comicId = 3)?.id)
    }

    @Test
    fun nextInSeriesReturnsNullOnLastIssueOrUnknownComic() {
        val input = listOf(
            comic(1, series = "Saga", issueNumber = 1),
            comic(2, series = "Saga", issueNumber = 2),
        )
        assertEquals(null, LibraryCatalog.nextInSeries(input, comicId = 2))
        assertEquals(null, LibraryCatalog.nextInSeries(input, comicId = 99))
    }

    @Test
    fun groupingIgnoresSeriesCaseAndSurroundingSpace() {
        val input = listOf(
            comic(1, series = "Spawn"),
            comic(2, series = " spawn "),
        )
        val entries = LibraryCatalog.grouped(input)
        assertEquals(1, entries.size)
        assertEquals(2, (entries[0] as LibraryEntry.Group).comics.size)
    }
}
