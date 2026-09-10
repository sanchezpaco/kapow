package com.comicify.feature.stats.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicify.feature.library.domain.LibraryCatalog
import com.comicify.feature.stats.data.ReadingStatsRepository
import com.comicify.feature.stats.domain.ReadingSession
import com.comicify.feature.stats.domain.SessionDraft
import com.comicify.feature.stats.domain.SessionRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReadingSessionViewModel @Inject constructor(
    private val repository: ReadingStatsRepository,
) : ViewModel() {

    private var readerComicId: Long? = null
    private var draft: SessionDraft? = null
    private var pendingPageIndex: Int? = null

    fun onReaderOpened(comicId: Long) {
        if (readerComicId == comicId) return
        finish()
        readerComicId = comicId
        val startedAt = System.currentTimeMillis()
        viewModelScope.launch {
            val mode = repository.openMode(comicId)
            if (readerComicId != comicId) return@launch
            draft = SessionRecorder.start(comicId, mode, startedAt, pendingPageIndex)
            pendingPageIndex = null
        }
    }

    fun onPageChanged(comicId: Long, pageIndex: Int, pageCount: Int, autoplayed: Boolean) {
        val current = draft
        if (current == null) {
            if (readerComicId == comicId) pendingPageIndex = pageIndex
            return
        }
        val now = System.currentTimeMillis()
        val turn = if (current.comicId == comicId) {
            SessionRecorder.onPage(
                draft = current,
                pageIndex = pageIndex,
                comicCompleted = LibraryCatalog.isCompleted(pageIndex, pageCount),
                now = now,
            )
        } else {
            SessionRecorder.moveTo(current, comicId, pageIndex, now)
        }
        draft = if (autoplayed) SessionRecorder.onAutoplay(turn.draft) else turn.draft
        turn.ended?.let(::write)
    }

    fun onReaderClosed(comicId: Long) {
        if (readerComicId == comicId) finish()
    }

    fun onAppStopped() {
        finish()
    }

    private fun finish() {
        readerComicId = null
        pendingPageIndex = null
        val current = draft ?: return
        draft = null
        SessionRecorder.end(current)?.let(::write)
    }

    private fun write(session: ReadingSession) {
        viewModelScope.launch { repository.record(session) }
    }
}
