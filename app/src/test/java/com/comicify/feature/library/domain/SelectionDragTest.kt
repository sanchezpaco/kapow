package com.comicify.feature.library.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionDragTest {

    private fun comic(id: Long) = LibraryComic(
        id = id,
        documentUri = "uri$id",
        displayName = "file$id",
        title = "Comic $id",
        series = "Series",
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

    private val shelf = (1L..6L).map(::comic)

    @Test
    fun draggingForwardFromAnUnselectedAnchorAddsTheWholeSpan() {
        assertEquals(setOf(2L, 3L, 4L), SelectionDrag.rangeFromAnchor(emptySet(), shelf, anchor = 1..1, current = 3..3))
    }

    @Test
    fun draggingBackwardsAddsTheSameSpan() {
        assertEquals(setOf(2L, 3L, 4L), SelectionDrag.rangeFromAnchor(emptySet(), shelf, anchor = 3..3, current = 1..1))
    }

    @Test
    fun theResultDependsOnTheEndsOnly_soRetreatingShrinksTheSpan() {
        val overshot = SelectionDrag.rangeFromAnchor(emptySet(), shelf, anchor = 0..0, current = 4..4)
        val retreated = SelectionDrag.rangeFromAnchor(emptySet(), shelf, anchor = 0..0, current = 1..1)
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), overshot)
        assertEquals(setOf(1L, 2L), retreated)
    }

    @Test
    fun anchoringOnAnAlreadySelectedCardDeselectsTheSpan() {
        val base = setOf(1L, 2L, 3L, 4L)
        assertEquals(setOf(4L), SelectionDrag.rangeFromAnchor(base, shelf, anchor = 0..0, current = 2..2))
    }

    @Test
    fun cardsOutsideTheSpanAreNeverDisturbed() {
        val base = setOf(6L)
        assertEquals(setOf(6L, 2L, 3L), SelectionDrag.rangeFromAnchor(base, shelf, anchor = 1..1, current = 2..2))
    }

    @Test
    fun anAnchorOnItsOwnTogglesJustThatCard() {
        assertEquals(setOf(3L), SelectionDrag.rangeFromAnchor(emptySet(), shelf, anchor = 2..2, current = 2..2))
        assertEquals(emptySet<Long>(), SelectionDrag.rangeFromAnchor(setOf(3L), shelf, anchor = 2..2, current = 2..2))
    }

    @Test
    fun aStackAnchorTakesEveryIssueItCovers() {
        assertEquals(setOf(2L, 3L, 4L), SelectionDrag.rangeFromAnchor(emptySet(), shelf, 1..2, 3..3))
    }

    @Test
    fun draggingThroughAStackContributesAllOfIt() {
        assertEquals(setOf(1L, 2L, 3L, 4L), SelectionDrag.rangeFromAnchor(emptySet(), shelf, 0..0, 1..3))
    }

    @Test
    fun aFullyTickedStackAnchorGivesTheWholeSpanBack() {
        val base = setOf(1L, 2L, 3L, 4L)
        assertEquals(setOf(4L), SelectionDrag.rangeFromAnchor(base, shelf, 0..1, 2..2))
    }

    @Test
    fun aPartlyTickedStackAnchorTakesTheRestInstead() {
        val base = setOf(2L)
        assertEquals(setOf(1L, 2L, 3L), SelectionDrag.rangeFromAnchor(base, shelf, 0..2, 0..2))
    }

    @Test
    fun anIndexOffTheShelfLeavesTheSelectionAlone() {
        val base = setOf(1L)
        assertEquals(base, SelectionDrag.rangeFromAnchor(base, shelf, anchor = 0..0, current = 9..9))
        assertEquals(base, SelectionDrag.rangeFromAnchor(base, shelf, anchor = -1..-1, current = 2..2))
        assertEquals(base, SelectionDrag.rangeFromAnchor(base, emptyList(), anchor = 0..0, current = 0..0))
    }
}
