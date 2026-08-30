package com.comicify.feature.library.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicify.feature.library.data.LibraryRepository
import com.comicify.feature.library.domain.LibraryCatalog
import com.comicify.feature.library.domain.LibraryComic
import com.comicify.feature.library.domain.LibraryFilter
import com.comicify.feature.library.domain.LibraryScanError
import com.comicify.feature.library.domain.LibrarySort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
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
    val comicFinished = MutableSharedFlow<Unit>()
    private val scanError = MutableStateFlow<LibraryScanError?>(null)
    private val filter = MutableStateFlow(LibraryFilter.ALL)
    private val sort = MutableStateFlow(LibrarySort.TITLE)
    private val query = MutableStateFlow("")
    private val openedSeries = MutableStateFlow<String?>(null)

    val state: StateFlow<LibraryUiState> =
        combine(
            repository.library,
            repository.folderUri,
            combine(scanning, scanError) { isScanning, error -> isScanning to error },
            combine(filter, sort, query, repository.grouped, openedSeries, ::ViewState),
        ) { comics, folder, scanState, view ->
            val (isScanning, error) = scanState
            val (selectedFilter, selectedSort, searchQuery, isGrouped, series) = view
            val filtered = LibraryCatalog.sorted(
                LibraryCatalog.search(LibraryCatalog.filtered(comics, selectedFilter), searchQuery),
                selectedSort,
            )
            LibraryUiState(
                loading = false,
                scanning = isScanning,
                scanError = error,
                folderUri = folder,
                filter = selectedFilter,
                sort = selectedSort,
                query = searchQuery,
                openedSeries = series,
                grouped = isGrouped,
                comics = filtered,
                allComics = comics,
                entries = LibraryCatalog.grouped(filtered),
                continueReading = if (selectedFilter == LibraryFilter.ALL && searchQuery.isBlank()) LibraryCatalog.continueReading(comics) else emptyList(),
                totalCount = comics.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        viewModelScope.launch {
            repository.seedSampleIfNeeded()
            repository.generateMissingCovers()
        }
    }

    fun onFolderPicked(treeUri: Uri) = runScan { repository.setFolder(treeUri) }

    fun onRefresh() = runScan { repository.refresh() }

    fun saveProgress(comicId: Long, pageIndex: Int, pageCount: Int) {
        viewModelScope.launch {
            if (repository.saveProgress(comicId, pageIndex, pageCount)) comicFinished.emit(Unit)
        }
    }

    fun onFilterSelected(selected: LibraryFilter) {
        filter.value = selected
    }

    fun onSortSelected(selected: LibrarySort) {
        sort.value = selected
    }

    fun onOpenSeries(series: String?) {
        openedSeries.value = series
    }

    fun onQueryChanged(text: String) {
        query.value = text
    }

    fun onToggleGrouped() {
        viewModelScope.launch { repository.setGrouped(!state.value.grouped) }
    }

    fun onUnshelve(comic: LibraryComic) {
        viewModelScope.launch { repository.unshelve(comic.id) }
    }

    fun onReshelve(comic: LibraryComic) {
        viewModelScope.launch { repository.reshelve(comic.id) }
    }

    fun onToggleRead(comic: LibraryComic) {
        viewModelScope.launch { repository.setRead(comic.id, !comic.completed) }
    }

    fun onSetSeriesRead(comics: List<LibraryComic>, read: Boolean) {
        viewModelScope.launch { comics.forEach { repository.setRead(it.id, read) } }
    }

    fun onSetSeriesFavorite(comics: List<LibraryComic>, favorite: Boolean) {
        viewModelScope.launch { comics.forEach { repository.setFavorite(it.id, favorite) } }
    }

    fun onDeleteSeries(comics: List<LibraryComic>) {
        viewModelScope.launch { comics.forEach { repository.deleteComic(it.id) } }
    }

    fun onToggleFavorite(comic: LibraryComic) {
        viewModelScope.launch { repository.setFavorite(comic.id, !comic.favorite) }
    }

    fun onDeleteComic(comic: LibraryComic) {
        viewModelScope.launch { repository.deleteComic(comic.id) }
    }

    private fun runScan(scan: suspend () -> Unit) {
        viewModelScope.launch {
            scanning.value = true
            scanError.value = null
            try {
                scan()
                repository.generateMissingCovers()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: SecurityException) {
                Log.e(LIBRARY_TAG, "Folder access lost", error)
                scanError.value = LibraryScanError.AccessLost
            } catch (error: Exception) {
                Log.e(LIBRARY_TAG, "Folder scan failed", error)
                scanError.value = LibraryScanError.ReadFailure
            } finally {
                scanning.value = false
            }
        }
    }
}

private data class ViewState(
    val filter: LibraryFilter,
    val sort: LibrarySort,
    val query: String,
    val grouped: Boolean,
    val openedSeries: String?,
)
