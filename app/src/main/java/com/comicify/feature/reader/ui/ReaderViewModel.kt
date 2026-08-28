package com.comicify.feature.reader.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.comicify.core.storage.ComicSettingsDao
import com.comicify.core.storage.ComicSettingsEntity
import com.comicify.core.storage.DatabaseEntryPoint
import com.comicify.core.storage.ReaderPreferencesRepository
import com.comicify.domain.model.ReadingDirection
import com.comicify.domain.model.ReadingPosition
import com.comicify.feature.reader.data.ComicSource
import com.comicify.feature.reader.data.ComicSourceException
import com.comicify.feature.reader.data.ComicSourceFactory
import com.comicify.feature.reader.data.PageDetectionStore
import com.comicify.feature.reader.data.PageLoader
import com.comicify.feature.reader.data.PanelDetector
import com.comicify.feature.reader.domain.BUBBLE_ENLARGE_SCALE
import com.comicify.feature.reader.domain.ComicOpenError
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val comicSettingsDao: ComicSettingsDao =
        EntryPointAccessors.fromApplication(application, DatabaseEntryPoint::class.java).comicSettingsDao()
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
            applyComicSettings(comicSettingsDao.find(uri.toString()))
            runCatching { ComicSourceFactory.open(getApplication(), uri, state.value.position.pageIndex) }
                .onSuccess { opened ->
                    source = opened
                    pageLoader = PageLoader(opened, viewModelScope, PanelDetector.forContext(getApplication()), detectionStore())
                    _state.update { it.copy(loading = false, pageCount = opened.pageCount) }
                }
                .onFailure { throwable ->
                    val error = (throwable as? ComicSourceException)?.error ?: ComicOpenError.ReadFailure
                    _state.update { it.copy(loading = false, error = error) }
                }
        }
    }

    private fun applyComicSettings(settings: ComicSettingsEntity?) {
        if (settings == null) return
        _state.update {
            it.copy(
                bubblesEnlarged = settings.bubblesEnlarged ?: it.bubblesEnlarged,
                guided = settings.guided ?: it.guided,
            )
        }
    }

    private fun detectionStore(): PageDetectionStore {
        val dao = EntryPointAccessors.fromApplication(getApplication(), DatabaseEntryPoint::class.java).pageDetectionDao()
        return PageDetectionStore(dao, uri.toString())
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
        viewModelScope.launch {
            val override = comicSettingsDao.find(uri.toString())?.takeIf { it.bubbleScale != null }
            if (override == null) {
                preferencesRepository.setBubbleScale(scale)
            } else {
                comicSettingsDao.upsert(override.copy(bubbleScale = scale))
            }
        }
    }

    private fun observeBubbleScale() {
        viewModelScope.launch {
            combine(preferencesRepository.bubbleScale, comicSettingsDao.observe(uri.toString())) { global, settings ->
                settings?.bubbleScale ?: global ?: BUBBLE_ENLARGE_SCALE
            }.collect { scale ->
                _state.update { it.copy(bubbleScale = scale) }
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
        viewModelScope.launch {
            val override = comicSettingsDao.find(uri.toString())?.takeIf { it.rightToLeft != null }
            if (override == null) {
                preferencesRepository.setReadingDirection(next)
            } else {
                comicSettingsDao.upsert(override.copy(rightToLeft = next == ReadingDirection.RightToLeft))
            }
        }
    }

    private fun observeReadingDirection() {
        viewModelScope.launch {
            combine(preferencesRepository.readingDirection, comicSettingsDao.observe(uri.toString())) { global, settings ->
                val direction = when (settings?.rightToLeft) {
                    null -> global
                    true -> ReadingDirection.RightToLeft
                    false -> ReadingDirection.LeftToRight
                }
                direction to (settings?.coverAlone ?: false)
            }.collect { (direction, coverAlone) ->
                _state.update { it.copy(direction = direction, coverAlone = coverAlone) }
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
