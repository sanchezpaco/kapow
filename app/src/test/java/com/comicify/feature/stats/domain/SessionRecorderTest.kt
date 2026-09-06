package com.comicify.feature.stats.domain

import com.comicify.feature.reader.domain.ReaderViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val COMIC_ID = 7L
private const val MINUTE = 60_000L

class SessionRecorderTest {

    private fun start(at: Long = 0L, mode: ReaderViewMode = ReaderViewMode.Pages) =
        SessionRecorder.start(COMIC_ID, mode, at)

    private fun SessionDraft.page(index: Int, at: Long, pageCount: Int = 100): SessionDraft =
        SessionRecorder.onPage(this, index, comicCompleted = index >= pageCount - 1, now = at).draft

    @Test
    fun firstPageEventOnlyAnchorsThePosition() {
        val draft = start().page(4, at = 100)
        assertEquals(0, draft.pages)
        assertEquals(4, draft.lastPageIndex)
    }

    @Test
    fun countsOnePageForEachStep() {
        val draft = start().page(0, at = 100).page(1, at = MINUTE).page(2, at = 2 * MINUTE)
        assertEquals(2, draft.pages)
    }

    @Test
    fun countsBothPagesOfASpread() {
        val draft = start().page(0, at = 100).page(2, at = MINUTE)
        assertEquals(2, draft.pages)
    }

    @Test
    fun capsAScrubberJumpAtThreePages() {
        val draft = start().page(0, at = 100).page(40, at = MINUTE)
        assertEquals(3, draft.pages)
    }

    @Test
    fun countsPagesTurnedBackwards() {
        val draft = start().page(10, at = 100).page(8, at = MINUTE)
        assertEquals(2, draft.pages)
    }

    @Test
    fun endsAtTheLastActivityNotAtTheEndOfTheIdle() {
        val draft = start().page(0, at = 100).page(1, at = MINUTE).page(2, at = 2 * MINUTE)
        val session = SessionRecorder.end(draft)
        assertNotNull(session)
        assertEquals(2 * MINUTE, session!!.endedAt)
        assertEquals(0L, session.startedAt)
    }

    @Test
    fun discardsASessionWithoutAPageTurn() {
        assertNull(SessionRecorder.end(start().page(3, at = 5 * MINUTE)))
    }

    @Test
    fun discardsASessionShorterThanTwentySeconds() {
        val draft = start().page(0, at = 100).page(1, at = 5_000).page(2, at = 15_000)
        assertNull(SessionRecorder.end(draft))
    }

    @Test
    fun keepsASessionOfExactlyTwentySeconds() {
        val draft = start().page(0, at = 0).page(1, at = 10_000).page(2, at = 20_000)
        assertNotNull(SessionRecorder.end(draft))
    }

    @Test
    fun marksTheSessionThatReachedTheLastPage() {
        val draft = start().page(97, at = 100).page(98, at = MINUTE).page(99, at = 2 * MINUTE)
        assertTrue(SessionRecorder.end(draft)!!.finished)
    }

    @Test
    fun tenIdleMinutesCloseTheSessionAndOpenANewOne() {
        val reading = start().page(0, at = 0).page(1, at = MINUTE).page(2, at = 2 * MINUTE)
        val turn = SessionRecorder.onPage(reading, 3, comicCompleted = false, now = 2 * MINUTE + SESSION_IDLE_TIMEOUT_MS)
        val closed = turn.ended
        assertNotNull(closed)
        assertEquals(2 * MINUTE, closed!!.endedAt)
        assertEquals(2, closed.pages)
        assertEquals(0, turn.draft.pages)
        assertEquals(2 * MINUTE + SESSION_IDLE_TIMEOUT_MS, turn.draft.startedAt)
    }

    @Test
    fun keepsTheModeTheComicWasOpenedIn() {
        val draft = start(mode = ReaderViewMode.Guided).page(0, at = 0).page(1, at = MINUTE).page(2, at = 2 * MINUTE)
        assertEquals(ReaderViewMode.Guided, SessionRecorder.end(draft)!!.mode)
    }
}
