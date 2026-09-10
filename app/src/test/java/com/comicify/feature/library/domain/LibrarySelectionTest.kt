package com.comicify.feature.library.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(setOf(1L), LibrarySelection.toggle(emptySet(), 1L))
        assertEquals(setOf(1L, 2L), LibrarySelection.toggle(setOf(1L), 2L))
        assertEquals(setOf(2L), LibrarySelection.toggle(setOf(1L, 2L), 1L))
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
    fun onlyLooseCoversAreSelectable_issuesInsideAStackAreNot() {
        val entries = LibraryCatalog.grouped(listOf(comic(1, "Venom"), comic(2, "Hulk"), comic(3, "Hulk")))
        assertEquals(listOf(1L), LibraryCatalog.selectable(entries).map { it.id })
    }

    @Test
    fun allSelectedNeedsEveryComicOnTheShelf() {
        val shelf = listOf(comic(1), comic(2))
        assertTrue(LibrarySelection.allSelected(setOf(1L, 2L), shelf))
        assertFalse(LibrarySelection.allSelected(setOf(1L), shelf))
        assertFalse(LibrarySelection.allSelected(emptySet(), emptyList()))
    }
}
