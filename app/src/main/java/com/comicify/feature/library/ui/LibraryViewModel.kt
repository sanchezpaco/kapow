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
import com.comicify.feature.library.domain.LibrarySelection
import com.comicify.feature.library.domain.LibrarySort
import com.comicify.feature.library.domain.ListMembership
import com.comicify.feature.library.domain.ReadingList
import com.comicify.feature.library.domain.ReadingListMembership
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
    private val selection = MutableStateFlow<Set<Long>>(emptySet())
    private var foregroundScan: Job? = null
    private var removed: List<RemovedEntry> = emptyList()
    private var wasRead: Map<Long, Boolean> = emptyMap()
    private var wasFavorite: Map<Long, Boolean> = emptyMap()

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

    val state: StateFlow<LibraryUiState> = combine(shelf, selection) { shelf, selected ->
        shelf.copy(selection = LibrarySelection.reconcile(selected, shelf.comics))
    }
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
        onClearSelection()
        openedSeries.value = series
    }

    fun onOpenList(list: ReadingList?) {
        onClearSelection()
        openedListId.value = list?.id
    }

    fun onToggleSelection(comic: LibraryComic) {
        selection.value = LibrarySelection.toggle(selection.value, comic.id)
    }

    fun onSelectAll(comics: List<LibraryComic>) {
        selection.value = comics.mapTo(mutableSetOf()) { it.id }
    }

    fun onClearSelection() {
        selection.value = emptySet()
    }

    fun onCreateList(name: String, comics: List<LibraryComic>) {
        viewModelScope.launch {
            val listId = repository.createList(name.trim())
            comics.forEach { repository.addToList(listId, it.id) }
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

    fun onToggleInList(list: ReadingList, comics: List<LibraryComic>) {
        val comicIds = comics.map { it.id }
        viewModelScope.launch {
            if (ReadingListMembership.of(list, comicIds) == ListMembership.ALL) {
                comicIds.forEach { repository.removeFromList(list.id, it) }
            } else {
                ReadingListMembership.missing(list, comicIds).forEach { repository.addToList(list.id, it) }
            }
        }
    }

    fun onRemoveFromList(list: ReadingList, comics: List<LibraryComic>) {
        viewModelScope.launch {
            removed = comics.mapNotNull { comic ->
                repository.removeFromList(list.id, comic.id)
                    ?.let { ordering -> RemovedEntry(listId = list.id, comicId = comic.id, ordering = ordering) }
            }
        }
    }

    fun onUndoRemoveFromList() {
        val entries = removed
        removed = emptyList()
        viewModelScope.launch { entries.forEach { repository.addToList(it.listId, it.comicId, it.ordering) } }
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
        onClearSelection()
        viewModelScope.launch { repository.setGrouped(!state.value.grouped) }
    }

    fun onUnshelve(comic: LibraryComic) {
        viewModelScope.launch { repository.unshelve(comic.id) }
    }

    fun onReshelve(comic: LibraryComic) {
        viewModelScope.launch { repository.reshelve(comic.id) }
    }

    fun onSetRead(comics: List<LibraryComic>, read: Boolean) {
        wasRead = comics.associate { it.id to it.completed }
        viewModelScope.launch { comics.forEach { repository.setRead(it.id, read) } }
    }

    fun onUndoSetRead() {
        val previous = wasRead
        wasRead = emptyMap()
        viewModelScope.launch { previous.forEach { (id, read) -> repository.setRead(id, read) } }
    }

    fun onSetFavorite(comics: List<LibraryComic>, favorite: Boolean) {
        wasFavorite = comics.associate { it.id to it.favorite }
        viewModelScope.launch { comics.forEach { repository.setFavorite(it.id, favorite) } }
    }

    fun onUndoSetFavorite() {
        val previous = wasFavorite
        wasFavorite = emptyMap()
        viewModelScope.launch { previous.forEach { (id, favorite) -> repository.setFavorite(id, favorite) } }
    }

    fun onDeleteComics(comics: List<LibraryComic>) {
        viewModelScope.launch { comics.forEach { repository.deleteComic(it.id) } }
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
