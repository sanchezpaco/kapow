package com.comicify.feature.library.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicify.core.storage.OpenDefaults
import com.comicify.core.storage.ReaderPreferencesRepository
import com.comicify.feature.library.data.LibraryRepository
import com.comicify.feature.library.domain.ComicSettings
import com.comicify.feature.library.domain.LibraryComic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ComicSettingsViewModel @Inject constructor(
    application: Application,
    private val repository: LibraryRepository,
) : ViewModel() {

    private val documentUris = MutableStateFlow<List<String>>(emptyList())

    val defaults: StateFlow<OpenDefaults> = ReaderPreferencesRepository(application).openDefaults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OpenDefaults())

    val settings: StateFlow<ComicSettings> = documentUris
        .flatMapLatest { uris -> uris.firstOrNull()?.let(repository::settings) ?: flowOf(ComicSettings.Default) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ComicSettings.Default)

    fun show(comics: List<LibraryComic>) {
        documentUris.value = comics.map { it.documentUri }
    }

    fun onSettingsChanged(settings: ComicSettings) {
        viewModelScope.launch { documentUris.value.forEach { repository.saveSettings(it, settings) } }
    }

    fun onClearDetections() {
        viewModelScope.launch { documentUris.value.forEach { repository.clearDetections(it) } }
    }
}
