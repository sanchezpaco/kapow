package com.comicify.feature.library.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicify.feature.library.data.LibraryRepository
import com.comicify.feature.library.domain.LibraryCatalog
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

    val state: StateFlow<LibraryUiState> =
        combine(repository.library, repository.folderUri, scanning, scanFailed) { comics, folder, isScanning, failed ->
            LibraryUiState(
                loading = false,
                scanning = isScanning,
                scanFailed = failed,
                hasFolder = folder != null,
                comics = comics,
                continueReading = LibraryCatalog.continueReading(comics),
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
