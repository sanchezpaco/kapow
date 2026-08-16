package com.comicify.feature.library.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicify.feature.library.data.LibraryRepository
import com.comicify.feature.library.domain.LibraryCatalog
import com.comicify.feature.library.domain.LibraryComic
import com.comicify.feature.library.domain.LibraryFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val LIBRARY_TAG = "Library"

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
) : ViewModel() {

    private val scanning = MutableStateFlow(false)
    private val scanFailed = MutableStateFlow(false)
    private val filter = MutableStateFlow(LibraryFilter.ALL)

    val state: StateFlow<LibraryUiState> =
        combine(repository.library, repository.folderUri, scanning, scanFailed, filter) { comics, folder, isScanning, failed, selectedFilter ->
            LibraryUiState(
                loading = false,
                scanning = isScanning,
                scanFailed = failed,
                hasFolder = folder != null,
                filter = selectedFilter,
                comics = LibraryCatalog.filtered(comics, selectedFilter),
                continueReading = if (selectedFilter == LibraryFilter.ALL) LibraryCatalog.continueReading(comics) else emptyList(),
                totalCount = comics.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        viewModelScope.launch { repository.generateMissingCovers() }
    }

    fun onFolderPicked(treeUri: Uri) = runScan { repository.setFolder(treeUri) }

    fun onRefresh() = runScan { repository.refresh() }

    fun saveProgress(comicId: Long, pageIndex: Int, pageCount: Int) {
        viewModelScope.launch { repository.saveProgress(comicId, pageIndex, pageCount) }
    }

    fun onFilterSelected(selected: LibraryFilter) {
        filter.value = selected
    }

    fun onToggleRead(comic: LibraryComic) {
        viewModelScope.launch { repository.setRead(comic.id, !comic.completed) }
    }

    fun onToggleFavorite(comic: LibraryComic) {
        viewModelScope.launch { repository.setFavorite(comic.id, !comic.favorite) }
    }

    private fun runScan(scan: suspend () -> Unit) {
        viewModelScope.launch {
            scanning.value = true
            scanFailed.value = false
            try {
                scan()
                repository.generateMissingCovers()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e(LIBRARY_TAG, "Folder scan failed", error)
                scanFailed.value = true
            } finally {
                scanning.value = false
            }
        }
    }
}
