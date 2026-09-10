package com.comicify.feature.library.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingListTest {

    private fun comic(id: Long, series: String = "Series", completed: Boolean = false) = LibraryComic(
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
        completed = completed,
        favorite = false,
        lastReadAt = null,
        shelved = true,
    )

    private fun list(vararg comicIds: Long) =
        ReadingList(id = 1, name = "Spider-Verse", createdAt = 0, comicIds = comicIds.toList())

    @Test
    fun moveUpSwapsWithThePreviousComic() {
        assertEquals(listOf(1L, 3L, 2L, 4L), ReadingListOrder.moveUp(listOf(1L, 2L, 3L, 4L), 3L))
    }

    @Test
    fun moveDownSwapsWithTheNextComic() {
        assertEquals(listOf(2L, 1L, 3L), ReadingListOrder.moveDown(listOf(1L, 2L, 3L), 1L))
    }

    @Test
    fun movesAtTheEndsAreRefused() {
        assertEquals(listOf(1L, 2L), ReadingListOrder.moveUp(listOf(1L, 2L), 1L))
        assertEquals(listOf(1L, 2L), ReadingListOrder.moveDown(listOf(1L, 2L), 2L))
        assertFalse(ReadingListOrder.canMoveUp(listOf(1L, 2L), 1L))
        assertFalse(ReadingListOrder.canMoveDown(listOf(1L, 2L), 2L))
        assertTrue(ReadingListOrder.canMoveUp(listOf(1L, 2L), 2L))
        assertTrue(ReadingListOrder.canMoveDown(listOf(1L, 2L), 1L))
    }

    @Test
    fun movingAnUnknownComicLeavesTheOrderAlone() {
        assertEquals(listOf(1L, 2L), ReadingListOrder.moveUp(listOf(1L, 2L), 9L))
        assertFalse(ReadingListOrder.canMoveDown(listOf(1L, 2L), 9L))
    }

    @Test
    fun removeKeepsTheOrderOfTheRest() {
        assertEquals(listOf(1L, 3L), ReadingListOrder.remove(listOf(1L, 2L, 3L), 2L))
    }

    @Test
    fun holdsAllOnlyWhenEveryComicIsAlreadyInTheList() {
        assertTrue(ReadingListMembership.holdsAll(list(1, 2, 3), listOf(1L, 3L)))
        assertFalse(ReadingListMembership.holdsAll(list(1, 2), listOf(1L, 9L)))
        assertFalse(ReadingListMembership.holdsAll(list(1, 2), emptyList()))
    }

    @Test
    fun missingKeepsTheGivenOrderOfTheComicsToAdd() {
        assertEquals(listOf(9L, 4L), ReadingListMembership.missing(list(1, 2), listOf(9L, 1L, 4L, 2L)))
        assertEquals(emptyList<Long>(), ReadingListMembership.missing(list(1, 2), listOf(2L, 1L)))
    }

    @Test
    fun listKeepsItsHandOrderAndDropsPrunedComics() {
        val comics = listOf(comic(1), comic(2), comic(3))
        assertEquals(listOf(3L, 1L), LibraryCatalog.inList(comics, list(3, 9, 1)).map { it.id })
    }

    @Test
    fun listProgressCountsOnlyItsMembers() {
        val comics = listOf(comic(1, completed = true), comic(2), comic(3, completed = true))
        val members = LibraryCatalog.inList(comics, list(3, 2))
        assertEquals(2, members.size)
        assertEquals(1, members.count { it.completed })
    }

    @Test
    fun nextInListFollowsTheHandOrderAcrossSeries() {
        val comics = listOf(comic(1, series = "Amazing"), comic(2, series = "Venom"), comic(3, series = "Amazing"))
        assertEquals(2L, LibraryCatalog.nextInList(comics, list(3, 2, 1), 3L)?.id)
        assertEquals(1L, LibraryCatalog.nextInList(comics, list(3, 2, 1), 2L)?.id)
    }

    @Test
    fun nextInListStopsAtTheEndAndIgnoresOutsiders() {
        val comics = listOf(comic(1), comic(2))
        assertNull(LibraryCatalog.nextInList(comics, list(1, 2), 2L))
        assertNull(LibraryCatalog.nextInList(comics, list(1), 2L))
    }
}
