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

    private var openComicId: Long? = null
    private var draft: SessionDraft? = null
    private var pendingPageIndex: Int? = null

    fun onReaderOpened(comicId: Long) {
        if (openComicId == comicId) return
        openComicId?.let(::finish)
        openComicId = comicId
        val startedAt = System.currentTimeMillis()
        viewModelScope.launch {
            val mode = repository.openMode(comicId)
            if (openComicId != comicId) return@launch
            draft = SessionRecorder.start(comicId, mode, startedAt, pendingPageIndex)
            pendingPageIndex = null
        }
    }

    fun onPageChanged(comicId: Long, pageIndex: Int, pageCount: Int) {
        val current = draft?.takeIf { it.comicId == comicId }
        if (current == null) {
            if (openComicId == comicId) pendingPageIndex = pageIndex
            return
        }
        val turn = SessionRecorder.onPage(
            draft = current,
            pageIndex = pageIndex,
            comicCompleted = LibraryCatalog.isCompleted(pageIndex, pageCount),
            now = System.currentTimeMillis(),
        )
        draft = turn.draft
        turn.ended?.let(::write)
    }

    fun onReaderClosed(comicId: Long) {
        if (openComicId == comicId) finish(comicId)
    }

    fun onAppStopped() {
        openComicId?.let(::finish)
    }

    private fun finish(comicId: Long) {
        openComicId = null
        pendingPageIndex = null
        val current = draft?.takeIf { it.comicId == comicId } ?: return
        draft = null
        SessionRecorder.end(current)?.let(::write)
    }

    private fun write(session: ReadingSession) {
        viewModelScope.launch { repository.record(session) }
    }
}
