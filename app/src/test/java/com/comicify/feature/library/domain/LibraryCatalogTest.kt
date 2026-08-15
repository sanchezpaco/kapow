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
        lastReadAt: Long? = null,
        pageCount: Int? = 20,
    ) = LibraryComic(
        id = id,
        documentUri = "uri$id",
        title = LibraryCatalog.title(series, issueNumber),
        series = series,
        issueNumber = issueNumber,
        coverPath = null,
        pageCount = pageCount,
        pageIndex = pageIndex,
        completed = completed,
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
}
