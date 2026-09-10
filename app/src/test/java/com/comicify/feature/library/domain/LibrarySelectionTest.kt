package com.comicify.feature.library.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySelectionTest {

    private fun comic(id: Long, series: String = "Series") = LibraryComic(
        id = id,
        documentUri = "uri$id",
        displayName = "file$id",
        title = LibraryCatalog.title(series, id.toInt()),
        series = series,
        issueNumber = id.toInt(),
        coverPath = null,
        coverAmbient = null,
        pageCount = 20,
        pageIndex = 0,
        completed = false,
        favorite = false,
        lastReadAt = null,
        shelved = true,
    )

    @Test
    fun toggleAddsAndRemovesOneComic() {
        assertEquals(setOf(1L), LibrarySelection.toggle(emptySet(), listOf(1L)))
        assertEquals(setOf(1L, 2L), LibrarySelection.toggle(setOf(1L), listOf(2L)))
        assertEquals(setOf(2L), LibrarySelection.toggle(setOf(1L, 2L), listOf(1L)))
    }

    @Test
    fun togglingAStackTakesEveryIssueUnlessItAlreadyHasThemAll() {
        assertEquals(setOf(1L, 2L, 3L), LibrarySelection.toggle(setOf(2L), listOf(1L, 2L, 3L)))
        assertEquals(emptySet<Long>(), LibrarySelection.toggle(setOf(1L, 2L, 3L), listOf(1L, 2L, 3L)))
    }

    @Test
    fun reconcileDropsComicsThatLeftTheShelf() {
        val shelf = listOf(comic(1), comic(3))
        assertEquals(setOf(1L, 3L), LibrarySelection.reconcile(setOf(1L, 2L, 3L), shelf))
        assertEquals(emptySet<Long>(), LibrarySelection.reconcile(setOf(2L), shelf))
        assertEquals(emptySet<Long>(), LibrarySelection.reconcile(setOf(1L), emptyList()))
    }

    @Test
    fun reconcileOfAnEmptySelectionStaysEmpty() {
        assertEquals(emptySet<Long>(), LibrarySelection.reconcile(emptySet(), listOf(comic(1))))
    }

    @Test
    fun comicsFollowTheShelfOrderNotTheSelectionOrder() {
        val shelf = listOf(comic(3), comic(1), comic(2))
        assertEquals(listOf(3L, 1L), LibrarySelection.comics(setOf(1L, 3L), shelf).map { it.id })
    }

    @Test
    fun comicsIgnoresIdsMissingFromTheShelf() {
        assertEquals(listOf(1L), LibrarySelection.comics(setOf(1L, 9L), listOf(comic(1))).map { it.id })
    }

    @Test
    fun aGroupedShelfOffersTheIssuesInsideItsStacksToo() {
        val comics = listOf(comic(1, "Venom"), comic(2, "Hulk"), comic(3, "Hulk"))
        val entries = LibraryCatalog.grouped(comics)
        assertEquals(listOf(1L, 2L, 3L), LibraryCatalog.selectable(entries, comics, grouped = true).map { it.id })
    }

    @Test
    fun completeSeriesCountsOnlyStacksWithEveryIssueTicked() {
        val comics = listOf(comic(1, "Venom"), comic(2, "Hulk"), comic(3, "Hulk"), comic(4, "Thor"), comic(5, "Thor"))
        val entries = LibraryCatalog.grouped(comics)
        assertEquals(0, LibraryCatalog.completeSeries(entries, setOf(1L, 2L)))
        assertEquals(1, LibraryCatalog.completeSeries(entries, setOf(2L, 3L)))
        assertEquals(2, LibraryCatalog.completeSeries(entries, setOf(2L, 3L, 4L, 5L)))
    }

    @Test
    fun wholeSeriesOnlyMatchesWhenTheSelectionIsExactlyOneStack() {
        val comics = listOf(comic(1, "Venom"), comic(2, "Hulk"), comic(3, "Hulk"))
        val entries = LibraryCatalog.grouped(comics)
        assertEquals("Hulk", LibraryCatalog.wholeSeries(entries, setOf(2L, 3L))?.series)
        assertNull(LibraryCatalog.wholeSeries(entries, setOf(2L)))
        assertNull(LibraryCatalog.wholeSeries(entries, setOf(1L, 2L, 3L)))
    }

    @Test
    fun ungroupedShelfOffersEveryComic_evenOnesWhoseSeriesHasSiblings() {
        val comics = listOf(comic(1, "Venom"), comic(2, "Hulk"), comic(3, "Hulk"))
        val entries = LibraryCatalog.grouped(comics)
        assertEquals(listOf(1L, 2L, 3L), LibraryCatalog.selectable(entries, comics, grouped = false).map { it.id })
    }

    @Test
    fun allSelectedNeedsEveryComicOnTheShelf() {
        val shelf = listOf(comic(1), comic(2))
        assertTrue(LibrarySelection.allSelected(setOf(1L, 2L), shelf))
        assertFalse(LibrarySelection.allSelected(setOf(1L), shelf))
        assertFalse(LibrarySelection.allSelected(emptySet(), emptyList()))
    }
}
