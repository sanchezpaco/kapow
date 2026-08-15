package com.comicify.feature.reader.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.comicify.feature.reader.data.ComicSource
import com.comicify.feature.reader.data.ComicSourceFactory
import com.comicify.feature.reader.data.PageLoader
import com.comicify.feature.reader.data.ReaderPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReaderViewModel(
    application: Application,
    private val uri: Uri,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private val preferencesRepository = ReaderPreferencesRepository(application)

    private var source: ComicSource? = null
    var pageLoader: PageLoader? = null
        private set

    init {
        openComic()
        observeNightTint()
    }

    private fun openComic() {
        viewModelScope.launch {
            runCatching { ComicSourceFactory.open(getApplication(), uri) }
                .onSuccess { opened ->
                    source = opened
                    pageLoader = PageLoader(opened, viewModelScope)
                    _state.update { it.copy(loading = false, pageCount = opened.pageCount) }
                }
                .onFailure {
                    _state.update { it.copy(loading = false, error = true) }
                }
        }
    }

    fun onPageChanged(pageIndex: Int) {
        _state.update { it.copy(position = it.position.copy(pageIndex = pageIndex)) }
    }

    fun toggleChrome() {
        _state.update { it.copy(chromeVisible = !it.chromeVisible) }
    }

    fun toggleGuided() {
        _state.update { it.copy(guided = !it.guided) }
    }

    fun toggleGuidedFullScreen() {
        _state.update { it.copy(guidedFullScreen = !it.guidedFullScreen) }
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

    override fun onCleared() {
        source?.close()
    }

    companion object {
        fun factory(uri: Uri): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as Application
                ReaderViewModel(application, uri)
            }
        }

        private val APPLICATION_KEY = ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
    }
}
