package com.comicify.feature.library.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicify.feature.library.data.LibraryRepository
import com.comicify.feature.library.domain.LibraryCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
) : ViewModel() {

    private val scanning = MutableStateFlow(false)

    val state: StateFlow<LibraryUiState> =
        combine(repository.library, repository.folderUri, scanning) { comics, folder, isScanning ->
            LibraryUiState(
                loading = false,
                scanning = isScanning,
                hasFolder = folder != null,
                comics = comics,
                continueReading = LibraryCatalog.continueReading(comics),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        viewModelScope.launch { repository.generateMissingCovers() }
    }

    fun onFolderPicked(treeUri: Uri) {
        viewModelScope.launch {
            scanning.value = true
            repository.setFolder(treeUri)
            repository.generateMissingCovers()
            scanning.value = false
        }
    }

    fun onRefresh() {
        viewModelScope.launch {
            scanning.value = true
            repository.refresh()
            repository.generateMissingCovers()
            scanning.value = false
        }
    }

    fun saveProgress(comicId: Long, pageIndex: Int, pageCount: Int) {
        viewModelScope.launch { repository.saveProgress(comicId, pageIndex, pageCount) }
    }
}
