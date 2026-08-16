package com.comicify.feature.reader.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.comicify.core.storage.ReaderPreferencesRepository
import com.comicify.domain.model.ReadingDirection
import com.comicify.domain.model.ReadingPosition
import com.comicify.feature.reader.data.ComicSource
import com.comicify.feature.reader.data.ComicSourceException
import com.comicify.feature.reader.data.ComicSourceFactory
import com.comicify.feature.reader.data.PageLoader
import com.comicify.feature.reader.domain.BUBBLE_ENLARGE_SCALE
import com.comicify.feature.reader.domain.ComicOpenError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReaderViewModel(
    application: Application,
    private val uri: Uri,
    initialPage: Int,
    private val preferencesRepository: ReaderPreferencesRepository = ReaderPreferencesRepository(application),
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ReaderUiState(position = ReadingPosition(pageIndex = initialPage)))
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var source: ComicSource? = null
    var pageLoader: PageLoader? = null
        private set

    init {
        openComic()
        observeVolumeKeyPaging()
        observeNightTint()
        observeBubbleScale()
        observeReadingDirection()
    }

    private fun openComic() {
        viewModelScope.launch {
            runCatching { ComicSourceFactory.open(getApplication(), uri) }
                .onSuccess { opened ->
                    source = opened
                    pageLoader = PageLoader(opened, viewModelScope)
                    _state.update { it.copy(loading = false, pageCount = opened.pageCount) }
                }
                .onFailure { throwable ->
                    val error = (throwable as? ComicSourceException)?.error ?: ComicOpenError.ReadFailure
                    _state.update { it.copy(loading = false, error = error) }
                }
        }
    }

    fun onPageChanged(pageIndex: Int) {
        _state.update { it.copy(position = it.position.copy(pageIndex = pageIndex)) }
    }

    fun toggleChrome() {
        _state.update { it.copy(chromeVisible = !it.chromeVisible) }
    }

    fun requestJump(pageIndex: Int) {
        _state.update { it.copy(pendingJump = pageIndex) }
    }

    fun onJumpApplied() {
        _state.update { it.copy(pendingJump = null) }
    }

    fun toggleGuided() {
        _state.update { it.copy(guided = !it.guided) }
    }

    fun toggleBubblesEnlarged() {
        _state.update { it.copy(bubblesEnlarged = !it.bubblesEnlarged) }
    }

    fun setBubbleScale(scale: Float) {
        viewModelScope.launch { preferencesRepository.setBubbleScale(scale) }
    }

    private fun observeBubbleScale() {
        viewModelScope.launch {
            preferencesRepository.bubbleScale.collect { scale ->
                _state.update { it.copy(bubbleScale = scale ?: BUBBLE_ENLARGE_SCALE) }
            }
        }
    }

    fun toggleGuidedFullScreen() {
        _state.update { it.copy(guidedFullScreen = !it.guidedFullScreen) }
    }

    fun setVolumeKeyPaging(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setVolumeKeyPageTurnEnabled(enabled) }
    }

    private fun observeVolumeKeyPaging() {
        viewModelScope.launch {
            preferencesRepository.volumeKeyPageTurnEnabled.collect { enabled ->
                _state.update { it.copy(volumeKeyPagingEnabled = enabled) }
            }
        }
    }

    fun toggleNightTint() {
        viewModelScope.launch {
            preferencesRepository.setNightTintEnabled(!_state.value.nightTintEnabled)
        }
    }

    private fun observeNightTint() {
        viewModelScope.launch {
            preferencesRepository.nightTintEnabled.collect { enabled ->
                _state.update { it.copy(nightTintEnabled = enabled) }
            }
        }
    }

    fun toggleReadingDirection() {
        val next = when (_state.value.direction) {
            ReadingDirection.LeftToRight -> ReadingDirection.RightToLeft
            ReadingDirection.RightToLeft -> ReadingDirection.LeftToRight
        }
        viewModelScope.launch { preferencesRepository.setReadingDirection(next) }
    }

    private fun observeReadingDirection() {
        viewModelScope.launch {
            preferencesRepository.readingDirection.collect { direction ->
                _state.update { it.copy(direction = direction) }
            }
        }
    }

    override fun onCleared() {
        source?.close()
    }

    companion object {
        fun factory(application: Application, uri: Uri, initialPage: Int): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReaderViewModel(application, uri, initialPage) }
        }
    }
}
