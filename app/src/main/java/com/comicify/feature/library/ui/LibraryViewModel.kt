package com.comicify.feature.library.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comicify.feature.library.data.LibraryRepository
import com.comicify.feature.library.domain.LibraryCatalog
import com.comicify.feature.library.domain.LibraryComic
import com.comicify.feature.library.domain.LibraryEntry
import com.comicify.feature.library.domain.LibraryFilter
import com.comicify.feature.library.domain.LibraryScanError
import com.comicify.feature.library.domain.LibrarySort
import com.comicify.feature.library.domain.ReadingList
import com.comicify.feature.library.domain.ReadingListOrder
import com.comicify.feature.stats.data.ReadingStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val LIBRARY_TAG = "Library"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val stats: ReadingStatsRepository,
) : ViewModel() {

    private val scanning = MutableStateFlow(false)
    val comicFinished = MutableSharedFlow<Unit>()
    private val scanError = MutableStateFlow<LibraryScanError?>(null)
    private val filter = MutableStateFlow(LibraryFilter.ALL)
    private val sort = MutableStateFlow(LibrarySort.TITLE)
    private val query = MutableStateFlow("")
    private val openedSeries = MutableStateFlow<String?>(null)
    private val openedListId = MutableStateFlow<Long?>(null)
    private var foregroundScan: Job? = null
    private var removed: RemovedEntry? = null

    private val listState: Flow<ListState> =
        combine(repository.readingLists, openedListId) { lists, openedId ->
            ListState(lists = lists, opened = lists.firstOrNull { it.id == openedId })
        }

    private val shelf: Flow<LibraryUiState> =
        combine(
            repository.library,
            repository.folderUri,
            combine(scanning, scanError) { isScanning, error -> isScanning to error },
            combine(filter, sort, query, repository.grouped, openedSeries, ::ViewState),
            listState,
        ) { comics, folder, scanState, view, lists ->
            val (isScanning, error) = scanState
            val (selectedFilter, selectedSort, searchQuery, isGrouped, series) = view
            val opened = lists.opened
            val shelved = opened?.let { LibraryCatalog.inList(comics, it) } ?: comics
            val narrowed = LibraryCatalog.search(LibraryCatalog.filtered(shelved, selectedFilter), searchQuery)
            val filtered = if (opened == null) LibraryCatalog.sorted(narrowed, selectedSort) else narrowed
            LibraryUiState(
                loading = false,
                scanning = isScanning,
                scanError = error,
                folderUri = folder,
                filter = selectedFilter,
                sort = selectedSort,
                query = searchQuery,
                openedSeries = series,
                lists = lists.lists,
                openedList = opened,
                grouped = isGrouped,
                comics = filtered,
                allComics = comics,
                entries = if (opened == null) LibraryCatalog.grouped(filtered) else filtered.map(LibraryEntry::Single),
                continueReading = LibraryCatalog.continueReading(comics),
                continueReadingVisible = selectedFilter == LibraryFilter.ALL && searchQuery.isBlank() && opened == null,
                totalCount = comics.size,
            )
        }

    val state: StateFlow<LibraryUiState> = shelf
        .flatMapLatest { shelf -> shelf.withHeroPace() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    private fun LibraryUiState.withHeroPace(): Flow<LibraryUiState> {
        val hero = continueReading.firstOrNull() ?: return flowOf(this)
        return stats.secondsPerPage(hero).map { copy(heroSecondsPerPage = it) }
    }

    init {
        viewModelScope.launch {
            repository.seedSampleIfNeeded()
            repository.fillMissingDetails()
        }
    }

    fun onFolderPicked(treeUri: Uri) = runScan { repository.setFolder(treeUri) }

    fun onRefresh() = runScan { repository.refresh() }

    fun onForeground() {
        if (scanning.value || foregroundScan?.isActive == true) return
        foregroundScan = viewModelScope.launch {
            if (repository.folderUri.first() == null) return@launch
            scanCatching { repository.refresh() }
        }
    }

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

    fun onOpenList(list: ReadingList?) {
        openedListId.value = list?.id
    }

    fun onCreateList(name: String, comic: LibraryComic?) {
        viewModelScope.launch {
            val listId = repository.createList(name.trim())
            comic?.let { repository.addToList(listId, it.id) }
        }
    }

    fun onRenameList(list: ReadingList, name: String) {
        viewModelScope.launch { repository.renameList(list.id, name.trim()) }
    }

    fun onDeleteList(list: ReadingList) {
        openedListId.value = null
        viewModelScope.launch { repository.deleteList(list.id) }
    }

    fun onRestoreList(list: ReadingList) {
        viewModelScope.launch { repository.restoreList(list) }
    }

    fun onToggleInList(list: ReadingList, comic: LibraryComic) {
        viewModelScope.launch {
            if (comic.id in list.comicIds) repository.removeFromList(list.id, comic.id)
            else repository.addToList(list.id, comic.id)
        }
    }

    fun onRemoveFromList(list: ReadingList, comic: LibraryComic) {
        viewModelScope.launch {
            removed = repository.removeFromList(list.id, comic.id)
                ?.let { ordering -> RemovedEntry(listId = list.id, comicId = comic.id, ordering = ordering) }
        }
    }

    fun onUndoRemoveFromList() {
        val entry = removed ?: return
        removed = null
        viewModelScope.launch { repository.addToList(entry.listId, entry.comicId, entry.ordering) }
    }

    fun onMoveInList(list: ReadingList, comic: LibraryComic, up: Boolean) {
        val reordered = if (up) ReadingListOrder.moveUp(list.comicIds, comic.id)
        else ReadingListOrder.moveDown(list.comicIds, comic.id)
        if (reordered == list.comicIds) return
        viewModelScope.launch { repository.reorderList(list.id, reordered) }
    }

    fun onQueryChanged(text: String) {
        query.value = text
    }

    fun onPresetQuery(text: String) {
        filter.value = LibraryFilter.ALL
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
        viewModelScope.launch { scanCatching(scan) }
    }

    private suspend fun scanCatching(scan: suspend () -> Unit) {
        scanning.value = true
        scanError.value = null
        try {
            scan()
            repository.fillMissingDetails()
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

private data class ViewState(
    val filter: LibraryFilter,
    val sort: LibrarySort,
    val query: String,
    val grouped: Boolean,
    val openedSeries: String?,
)

private data class ListState(val lists: List<ReadingList>, val opened: ReadingList?)

private data class RemovedEntry(val listId: Long, val comicId: Long, val ordering: Int)
