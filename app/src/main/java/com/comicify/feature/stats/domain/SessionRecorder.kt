package com.comicify.feature.stats.domain

import com.comicify.feature.reader.domain.ReaderViewMode
import kotlin.math.abs
import kotlin.math.min

const val SESSION_IDLE_TIMEOUT_MS = 10L * 60 * 1000
private const val MIN_SESSION_MS = 20L * 1000
private const val MAX_PAGES_PER_TURN = 3

data class SessionDraft(
    val comicId: Long,
    val mode: ReaderViewMode,
    val startedAt: Long,
    val lastActivityAt: Long,
    val lastPageIndex: Int?,
    val pages: Int,
    val finished: Boolean,
)

data class PageTurn(val draft: SessionDraft, val ended: ReadingSession?)

object SessionRecorder {

    fun start(comicId: Long, mode: ReaderViewMode, startedAt: Long, pageIndex: Int? = null): SessionDraft =
        SessionDraft(
            comicId = comicId,
            mode = mode,
            startedAt = startedAt,
            lastActivityAt = startedAt,
            lastPageIndex = pageIndex,
            pages = 0,
            finished = false,
        )

    fun onPage(draft: SessionDraft, pageIndex: Int, comicCompleted: Boolean, now: Long): PageTurn {
        if (now - draft.lastActivityAt >= SESSION_IDLE_TIMEOUT_MS) {
            return PageTurn(start(draft.comicId, draft.mode, now, pageIndex), end(draft))
        }
        val turned = draft.lastPageIndex?.let { min(abs(pageIndex - it), MAX_PAGES_PER_TURN) } ?: 0
        return PageTurn(
            draft.copy(
                lastPageIndex = pageIndex,
                pages = draft.pages + turned,
                lastActivityAt = if (turned > 0) now else draft.lastActivityAt,
                finished = draft.finished || comicCompleted,
            ),
            ended = null,
        )
    }

    fun moveTo(draft: SessionDraft, comicId: Long, pageIndex: Int, now: Long): PageTurn {
        val idle = now - draft.lastActivityAt >= SESSION_IDLE_TIMEOUT_MS
        val ended = if (idle) draft else draft.copy(lastActivityAt = now)
        return PageTurn(start(comicId, draft.mode, now, pageIndex), end(ended))
    }

    fun end(draft: SessionDraft): ReadingSession? {
        if (draft.pages < 1) return null
        if (draft.lastActivityAt - draft.startedAt < MIN_SESSION_MS) return null
        return ReadingSession(
            comicId = draft.comicId,
            startedAt = draft.startedAt,
            endedAt = draft.lastActivityAt,
            pages = draft.pages,
            mode = draft.mode,
            finished = draft.finished,
        )
    }
}
