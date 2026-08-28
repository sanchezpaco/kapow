package com.comicify.feature.library.ui

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicify.feature.library.data.ComicThumbnails
import com.comicify.feature.library.data.LibraryRepository
import com.comicify.feature.library.domain.ComicSettings
import com.comicify.feature.library.domain.LibraryCatalog
import com.comicify.feature.library.domain.LibraryComic
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ComicDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LibraryRepository,
) : ViewModel() {

    private val comicId = MutableStateFlow<Long?>(null)
    private val pageCount = MutableStateFlow(0)
    private var thumbnails: ComicThumbnails? = null

    private val comic = combine(repository.library, comicId) { comics, id -> comics.firstOrNull { it.id == id } }

    val state: StateFlow<ComicDetailUiState> =
        combine(
            repository.library,
            comic,
            comic.flatMapLatest { it?.let { repository.settings(it.documentUri) } ?: flowOf(ComicSettings.Default) },
            pageCount,
        ) { comics, current, settings, pages ->
            if (current == null) return@combine ComicDetailUiState()
            val series = LibraryCatalog.seriesOf(comics, current)
            ComicDetailUiState(
                comic = current,
                series = series,
                nextUnread = LibraryCatalog.nextUnread(series),
                settings = settings,
                pageCount = pages,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ComicDetailUiState())

    fun show(comic: LibraryComic) {
        if (comicId.value == comic.id) return
        comicId.value = comic.id
        pageCount.value = comic.pageCount ?: 0
        thumbnails?.close()
        thumbnails = ComicThumbnails(context, comic.documentUri.toUri()).also { loader ->
            viewModelScope.launch { pageCount.value = runCatching { loader.pageCount() }.getOrDefault(0) }
        }
    }

    suspend fun thumb(index: Int): ImageBitmap? = thumbnails?.let { runCatching { it.thumb(index) }.getOrNull() }

    fun onSettingsChanged(settings: ComicSettings) {
        val comic = state.value.comic ?: return
        viewModelScope.launch { repository.saveSettings(comic.documentUri, settings) }
    }

    fun onToggleRead(comic: LibraryComic) {
        viewModelScope.launch { repository.setRead(comic.id, !comic.completed) }
    }

    fun onToggleFavorite(comic: LibraryComic) {
        viewModelScope.launch { repository.setFavorite(comic.id, !comic.favorite) }
    }

    fun onClearDetections() {
        val comic = state.value.comic ?: return
        viewModelScope.launch { repository.clearDetections(comic.documentUri) }
    }

    override fun onCleared() {
        thumbnails?.close()
    }
}
