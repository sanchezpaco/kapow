package com.comicify.feature.library.ui

import android.net.Uri
import com.comicify.BuildConfig
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.RemoveDone
import com.comicify.feature.library.domain.LibraryScanError
import com.comicify.feature.library.domain.LibrarySelection
import com.comicify.feature.library.domain.LibrarySort
import com.comicify.feature.library.domain.SelectionDrag
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.comicify.R
import com.comicify.core.ui.KapowSnackbarHost
import com.comicify.core.ui.SectionHeader
import com.comicify.feature.library.domain.LibraryCatalog
import com.comicify.feature.library.domain.LibraryComic
import com.comicify.feature.library.domain.LibraryEntry
import com.comicify.feature.library.domain.LibraryFilter
import com.comicify.feature.library.domain.ReadingList
import java.io.File

private const val COMPACT_WIDTH_DP = 600
private const val SWIPE_UNSHELVE_THRESHOLD = 0.4f
internal val TouchTargetSize = 48.dp
private val HeroCoverWidth = 132.dp
private val HeroCoverWidthCompact = 100.dp
private val ShelfCardWidth = 340.dp
private val ShelfCardWidthCompact = 296.dp
private val GridMinCell = 158.dp
private val GridMinCellCompact = 112.dp
private val UnshelveButtonSize = 40.dp
internal val CardShape = RoundedCornerShape(14.dp)
private val SectionGap = 22.dp
private val SearchFieldMaxWidth = 480.dp
private val SearchPresetGap = 8.dp
internal val MenuHeaderMaxWidth = 260.dp
private val ProgressRingSize = 26.dp
private val SelectionBorder = 2.dp
private val SelectableBorder = 1.dp
private const val SelectionScale = 0.92f
private const val InertAlpha = 0.4f
private const val SELECTION_CROSSFADE_MS = 150
private val DragSelectHotZone = 96.dp
private val DragSelectMinRate = 240.dp
private val DragSelectMaxRate = 900.dp
private const val DragSelectHapticNanos = 40_000_000L
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val ComicKeyPrefix = "comic-"
private const val FilterHeaderKey = "filter-header"
private const val SelectionRowKey = "selection-row"
private const val TabularNumbers = "tnum"
private val SelectionSquare = TouchTargetSize
private const val GhostToneAlpha = 0.15f
private val SelectionGap = 10.dp
private val SelectionPillCompact = 76.dp
private val SelectionPillWide = 184.dp
private val BadgeSize = 28.dp
private val BadgeGlyphSize = 16.dp
private val BadgeGround = Color(0xD2060608)

private val CoverGradients = listOf(
    listOf(Color(0xFF7A2222), Color(0xFF2A0E0E)),
    listOf(Color(0xFF15385A), Color(0xFF091725)),
    listOf(Color(0xFF0E4740), Color(0xFF06201D)),
    listOf(Color(0xFF6B2540), Color(0xFF2A0F1A)),
    listOf(Color(0xFF7A5410), Color(0xFF261A08)),
    listOf(Color(0xFF3A2352), Color(0xFF160A2E)),
    listOf(Color(0xFF1F3C6B), Color(0xFF0B1730)),
    listOf(Color(0xFF5A3A22), Color(0xFF221208)),
)

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onFolderPicked: (Uri) -> Unit,
    onOpenComic: (LibraryComic) -> Unit,
    onOpenSettings: (List<LibraryComic>) -> Unit,
    onOpenDetails: (LibraryComic) -> Unit,
    onChooseCover: (LibraryComic) -> Unit,
    onOpenStats: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenFile: (Uri) -> Unit,
    onFilterSelected: (LibraryFilter) -> Unit,
    onToggleGrouped: () -> Unit,
    onUnshelve: (LibraryComic) -> Unit,
    onReshelve: (LibraryComic) -> Unit,
    onSetRead: (List<LibraryComic>, Boolean) -> Unit,
    onUndoSetRead: () -> Unit,
    onSetFavorite: (List<LibraryComic>, Boolean) -> Unit,
    onUndoSetFavorite: () -> Unit,
    onDeleteComics: (List<LibraryComic>) -> Unit,
    onQueryChanged: (String) -> Unit,
    onPresetQuery: (String) -> Unit,
    onSortSelected: (LibrarySort) -> Unit,
    onOpenSeries: (String?) -> Unit,
    onToggleSelection: (LibraryComic) -> Unit,
    onSelectRange: (Set<Long>) -> Unit,
    onDragSelecting: (Boolean) -> Unit,
    onDragHintShown: () -> Unit,
    onClearSelection: () -> Unit,
    listActions: ReadingListActions,
) {
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val unshelvedMessage = stringResource(R.string.library_unshelved)
    val deletedMessage = stringResource(R.string.library_list_deleted)
    val undoLabel = stringResource(R.string.library_undo)
    val readMessage = stringResource(R.string.library_selection_marked_read)
    val unreadMessage = stringResource(R.string.library_selection_marked_unread)
    val favoritedMessage = stringResource(R.string.library_selection_favorited)
    val unfavoritedMessage = stringResource(R.string.library_selection_unfavorited)
    val removedMessage = removedFromListMessage()
    val unshelveWithUndo: (LibraryComic) -> Unit = { comic ->
        onUnshelve(comic)
        scope.launch { if (snackbarHost.undoable(unshelvedMessage, undoLabel)) onReshelve(comic) }
    }
    val setReadWithUndo: (List<LibraryComic>, Boolean) -> Unit = { comics, read ->
        onSetRead(comics, read)
        val message = if (read) readMessage else unreadMessage
        scope.launch { if (snackbarHost.undoable(message, undoLabel)) onUndoSetRead() }
    }
    val setFavoriteWithUndo: (List<LibraryComic>, Boolean) -> Unit = { comics, favorite ->
        onSetFavorite(comics, favorite)
        val message = if (favorite) favoritedMessage else unfavoritedMessage
        scope.launch { if (snackbarHost.undoable(message, undoLabel)) onUndoSetFavorite() }
    }
    val lists = ReadingListsUi(
        lists = state.lists,
        opened = state.openedList,
        actions = listActions.copy(
            delete = { list ->
                listActions.delete(list)
                scope.launch { if (snackbarHost.undoable(deletedMessage, undoLabel)) listActions.restore(list) }
            },
            remove = { list, comics ->
                listActions.remove(list, comics)
                val message = removedMessage(comics.size)
                scope.launch { if (snackbarHost.undoable(message, undoLabel)) listActions.undoRemove() }
            },
        ),
    )
    val shelf = state.visibleComics
    val selection = SelectionUi(
        selected = LibrarySelection.comics(state.selection, shelf),
        shelf = shelf,
        lists = lists,
        openSettings = onOpenSettings,
        openDetails = onOpenDetails,
        chooseCover = onChooseCover,
        setRead = setReadWithUndo,
        setFavorite = setFavoriteWithUndo,
        delete = onDeleteComics,
        selectRange = onSelectRange,
        dragging = onDragSelecting,
        clear = onClearSelection,
    )
    val dragHint = stringResource(R.string.library_selection_drag_hint)
    LaunchedEffect(state.dragHintPending, selection.selected.isEmpty()) {
        if (!state.dragHintPending || selection.selected.isEmpty()) return@LaunchedEffect
        onDragHintShown()
        scope.launch { snackbarHost.showSnackbar(dragHint, duration = SnackbarDuration.Short) }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { KapowSnackbarHost(snackbarHost) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LibraryContent(
                state = state,
                onFolderPicked = onFolderPicked,
                onOpenComic = onOpenComic,
                onOpenStats = onOpenStats,
                onOpenAppSettings = onOpenAppSettings,
                onOpenFile = onOpenFile,
                onFilterSelected = onFilterSelected,
                onToggleGrouped = onToggleGrouped,
                onUnshelve = unshelveWithUndo,
                onQueryChanged = onQueryChanged,
                onPresetQuery = onPresetQuery,
                onSortSelected = onSortSelected,
                onOpenSeries = onOpenSeries,
                onToggleSelection = onToggleSelection,
                selection = selection,
                lists = lists,
            )
        }
    }
}

@Composable
private fun removedFromListMessage(): (Int) -> String {
    val resources = LocalContext.current.resources
    return { count -> resources.getQuantityString(R.plurals.library_list_removed_count, count, count) }
}

private suspend fun SnackbarHostState.undoable(message: String, undoLabel: String): Boolean {
    currentSnackbarData?.dismiss()
    return showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryContent(
    state: LibraryUiState,
    onFolderPicked: (Uri) -> Unit,
    onOpenComic: (LibraryComic) -> Unit,
    onOpenStats: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenFile: (Uri) -> Unit,
    onFilterSelected: (LibraryFilter) -> Unit,
    onToggleGrouped: () -> Unit,
    onUnshelve: (LibraryComic) -> Unit,
    onQueryChanged: (String) -> Unit,
    onPresetQuery: (String) -> Unit,
    onSortSelected: (LibrarySort) -> Unit,
    onOpenSeries: (String?) -> Unit,
    onToggleSelection: (LibraryComic) -> Unit,
    selection: SelectionUi,
    lists: ReadingListsUi,
) {
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(onFolderPicked) }
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onOpenFile) }

    val pickFolder = { folderLauncher.launch(null) }
    val openFile = { fileLauncher.launch(openDocumentMimeTypes) }

    val openGroup = LibraryCatalog.openGroup(state.entries, state.openedSeries)
    LaunchedEffect(state.openedSeries, openGroup) {
        if (state.openedSeries != null && openGroup == null) onOpenSeries(null)
    }

    val selecting = state.selection.isNotEmpty()
    BackHandler(enabled = selecting) { selection.clear() }
    BackHandler(enabled = !selecting && openGroup != null) { onOpenSeries(null) }
    BackHandler(enabled = !selecting && openGroup == null && lists.opened != null) { lists.actions.open(null) }

    if (openGroup != null) {
        SeriesScreen(
            group = openGroup,
            selectedIds = state.selection,
            selection = selection,
            onBack = { onOpenSeries(null) },
            onOpenComic = onOpenComic,
            onToggleSelection = onToggleSelection,
        )
        return
    }

    if (state.totalCount == 0 && !state.scanning) {
        EmptyLibrary(scanError = state.scanError, onPickFolder = pickFolder, onOpenFile = openFile)
        return
    }

    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = gridMinCell()),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .dragToSelect(gridState = gridState, pinnedKey = FilterHeaderKey, selection = selection),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            LibraryHeader(
                comicCount = state.totalCount,
                scanning = state.scanning,
                scanError = state.scanError,
                grouped = state.grouped,
                query = state.query,
                onOpenStats = onOpenStats,
                onOpenAppSettings = onOpenAppSettings,
                onOpenFile = openFile,
                onToggleGrouped = onToggleGrouped,
                onQueryChanged = onQueryChanged,
                onPresetQuery = onPresetQuery,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
        stickyHeader(key = FilterHeaderKey) {
            FilterHeader(filter = state.filter, onFilterSelected = onFilterSelected, selection = selection)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                AnimatedVisibility(
                    visible = state.continueReadingVisible && state.continueReading.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    ContinueReadingShelf(
                        comics = state.continueReading,
                        heroSecondsPerPage = state.heroSecondsPerPage,
                        onOpenComic = onOpenComic,
                        onUnshelve = onUnshelve,
                        modifier = Modifier.padding(bottom = SectionGap).inertWhile(selecting),
                    )
                }
                ShelfTitle(state = state, lists = lists, onSortSelected = onSortSelected)
            }
        }
        if (state.comics.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (lists.opened?.comicIds?.isEmpty() == true) ListEmpty() else FilterEmpty(searching = state.query.isNotBlank())
            }
        }
        if (state.grouped) {
            items(items = state.entries, key = { it.gridKey() }) { entry ->
                when (entry) {
                    is LibraryEntry.Single -> ComicCard(
                        comic = entry.comic,
                        selecting = selecting,
                        selected = entry.comic.id in state.selection,
                        onOpenComic = onOpenComic,
                        onToggleSelection = onToggleSelection,
                    )
                    is LibraryEntry.Group -> GroupCard(
                        group = entry,
                        inert = selecting,
                        onOpen = { onOpenSeries(it.series) },
                        selection = selection,
                    )
                }
            }
        } else {
            items(items = state.comics, key = { it.gridKey() }) { comic ->
                ComicCard(
                    comic = comic,
                    selecting = selecting,
                    selected = comic.id in state.selection,
                    onOpenComic = onOpenComic,
                    onToggleSelection = onToggleSelection,
                )
            }
        }
    }
}

private fun Modifier.inertWhile(inert: Boolean): Modifier =
    if (!inert) this else alpha(InertAlpha).pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }

private fun LibraryEntry.gridKey(): String = when (this) {
    is LibraryEntry.Single -> comic.gridKey()
    is LibraryEntry.Group -> "group-$series"
}

private fun LibraryComic.gridKey(): String = "$ComicKeyPrefix$id"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesScreen(
    group: LibraryEntry.Group,
    selectedIds: Set<Long>,
    selection: SelectionUi,
    onBack: () -> Unit,
    onOpenComic: (LibraryComic) -> Unit,
    onToggleSelection: (LibraryComic) -> Unit,
) {
    val selecting = selectedIds.isNotEmpty()
    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = gridMinCell()),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .dragToSelect(gridState = gridState, pinnedKey = SelectionRowKey, selection = selection),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SeriesHeader(group = group, selecting = selecting, onBack = onBack, selection = selection)
        }
        if (selecting) {
            stickyHeader(key = SelectionRowKey) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(vertical = 10.dp),
                ) {
                    SelectionRow(selection = rememberHeldSelection(selection))
                }
            }
        }
        items(items = group.comics, key = { it.gridKey() }) { comic ->
            ComicCard(
                comic = comic,
                title = comic.issueNumber?.let { "#$it" } ?: comic.title,
                subtitle = comic.storyTitle,
                selecting = selecting,
                selected = comic.id in selectedIds,
                onOpenComic = onOpenComic,
                onToggleSelection = onToggleSelection,
            )
        }
    }
}

@Composable
private fun SeriesHeader(
    group: LibraryEntry.Group,
    selecting: Boolean,
    onBack: () -> Unit,
    selection: SelectionUi,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(TouchTargetSize)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface2)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.library_back),
                tint = InkDim,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.series,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = group.progressLine(),
                style = MaterialTheme.typography.labelMedium,
                color = InkFaint,
            )
        }
        if (selecting) return@Row
        Box {
            GhostAction(icon = Icons.Filled.MoreVert, contentDescription = stringResource(R.string.library_series_menu), onClick = { menuExpanded = true })
            SeriesMenu(expanded = menuExpanded, group = group, selection = selection, onDismiss = { menuExpanded = false })
        }
    }
}

@Composable
private fun LibraryEntry.Group.progressLine(): String {
    val read = stringResource(R.string.library_group_read, comics.count { it.completed }, comics.size)
    val publisher = comics.firstNotNullOfOrNull { it.publisher } ?: return read
    return stringResource(R.string.library_series_publisher_read, publisher, read)
}

@Composable
private fun DebugBadge() {
    Text(
        text = stringResource(R.string.library_debug_badge, BuildConfig.BUILD_LABEL),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Accent)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun LibraryHeader(
    comicCount: Int,
    scanning: Boolean,
    scanError: LibraryScanError?,
    grouped: Boolean,
    query: String,
    onOpenStats: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenFile: () -> Unit,
    onToggleGrouped: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onPresetQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searching by remember { mutableStateOf(query.isNotEmpty()) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = pluralStringResource(R.plurals.library_count, comicCount, comicCount),
                style = MaterialTheme.typography.labelMedium,
                color = InkFaint,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            if (BuildConfig.DEBUG) DebugBadge()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostAction(
                icon = Icons.Filled.Search,
                contentDescription = stringResource(R.string.library_search),
                onClick = { searching = !searching; if (!searching) onQueryChanged("") },
                active = searching,
            )
            GhostAction(
                icon = Icons.Filled.Layers,
                contentDescription = stringResource(if (grouped) R.string.library_ungroup else R.string.library_group),
                onClick = onToggleGrouped,
                active = grouped,
            )
            GhostAction(icon = Icons.Outlined.FileOpen, contentDescription = stringResource(R.string.library_action_open_file), onClick = onOpenFile)
            GhostAction(icon = Icons.Outlined.BarChart, contentDescription = stringResource(R.string.library_action_stats), onClick = onOpenStats)
            GhostAction(icon = Icons.Outlined.Settings, contentDescription = stringResource(R.string.library_app_settings), onClick = onOpenAppSettings)
        }
        if (searching) {
            SearchField(
                query = query,
                onQueryChanged = onQueryChanged,
                onPresetQuery = onPresetQuery,
                onClose = { searching = false; onQueryChanged("") },
            )
        }
        if (scanning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = Accent)
                Text(
                    text = stringResource(R.string.library_scanning),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkDim,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        if (scanError != null) {
            Text(
                text = stringResource(scanError.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    onPresetQuery: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    var text by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(modifier = Modifier.fillMaxWidth().widthIn(max = SearchFieldMaxWidth)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Surface2)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(imageVector = Icons.Filled.Search, contentDescription = null, tint = InkFaint, modifier = Modifier.size(18.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (text.text.isEmpty()) {
                    Text(text = stringResource(R.string.library_search_hint), style = MaterialTheme.typography.bodyLarge, color = InkFaint)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it; onQueryChanged(it.text) },
                    singleLine = true,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                    cursorBrush = SolidColor(Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.library_close_search),
                tint = InkDim,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onClose),
            )
        }
        AnimatedVisibility(
            visible = text.text.isBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            SearchPresets(onPresetQuery = { preset ->
                text = TextFieldValue(preset, TextRange(preset.length))
                onPresetQuery(preset)
            })
        }
    }
}

@Composable
private fun SearchPresets(onPresetQuery: (String) -> Unit) {
    val readingQuery = stringResource(R.string.library_preset_reading_query)
    val ratedQuery = stringResource(R.string.library_preset_rated_query)
    val recentQuery = stringResource(R.string.library_preset_recent_query)
    Column(
        modifier = Modifier.padding(top = SearchPresetGap),
        verticalArrangement = Arrangement.spacedBy(SearchPresetGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilterPill(R.string.library_preset_reading, selected = false) { onPresetQuery(readingQuery) }
            FilterPill(R.string.library_preset_rated, selected = false) { onPresetQuery(ratedQuery) }
            FilterPill(R.string.library_preset_recent, selected = false) { onPresetQuery(recentQuery) }
        }
        Text(
            text = stringResource(R.string.library_search_help),
            style = MaterialTheme.typography.bodySmall,
            color = InkFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun PrimaryAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Accent, AccentDeep)))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(text = label, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun GhostAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
    destructive: Boolean = false,
) {
    val tint = when {
        destructive -> Danger
        active -> Accent
        else -> InkDim
    }
    Box(
        modifier = Modifier
            .size(TouchTargetSize)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active || destructive) tint.copy(alpha = GhostToneAlpha) else Surface2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun ContinueReadingShelf(
    comics: List<LibraryComic>,
    heroSecondsPerPage: Int,
    onOpenComic: (LibraryComic) -> Unit,
    onUnshelve: (LibraryComic) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader(eyebrow = stringResource(R.string.library_resume_eyebrow), title = stringResource(R.string.library_continue_reading))
        val hero = comics.firstOrNull() ?: return@Column
        key(hero.id) {
            SwipeToUnshelve(comic = hero, onUnshelve = onUnshelve) {
                LibraryHero(comic = hero, secondsPerPage = heroSecondsPerPage, onOpenComic = onOpenComic, onUnshelve = onUnshelve)
            }
        }
        val rest = comics.drop(1)
        if (rest.isEmpty()) return@Column
        val rowState = rememberLazyListState()
        LaunchedEffect(hero.id) { rowState.scrollToItem(0) }
        LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items = rest, key = { it.id }) { comic ->
                ContinueReadingCard(comic = comic, onOpenComic = onOpenComic, onUnshelve = onUnshelve)
            }
        }
    }
}

@Composable
private fun SwipeToUnshelve(comic: LibraryComic, onUnshelve: (LibraryComic) -> Unit, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * SWIPE_UNSHELVE_THRESHOLD },
    )
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) onUnshelve(comic)
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeUnshelveBackground(dismissState.dismissDirection) },
        content = { content() },
    )
}

@Composable
private fun SwipeUnshelveBackground(direction: SwipeToDismissBoxValue) {
    val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(Accent.copy(alpha = 0.16f))
            .padding(horizontal = 28.dp),
        contentAlignment = alignment,
    ) {
        Icon(imageVector = Icons.Filled.Close, contentDescription = null, tint = Accent)
    }
}

@Composable
private fun LibraryHero(
    comic: LibraryComic,
    secondsPerPage: Int,
    onOpenComic: (LibraryComic) -> Unit,
    onUnshelve: (LibraryComic) -> Unit,
) {
    val ambient = comic.ambientColor()
    val glow = lerp(Ground, ambient, HeroGlowMix)
    val tint = lerp(OnGround, ambient, HeroTintMix)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(glow, HeroGround)))
            .clickable { onOpenComic(comic) },
    ) {
        val compact = isCompactWidth()
        Row(
            modifier = Modifier.height(IntrinsicSize.Min).padding(if (compact) 16.dp else 20.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 20.dp),
        ) {
            Box(modifier = Modifier.width(if (compact) HeroCoverWidthCompact else HeroCoverWidth).aspectRatio(2f / 3f).clip(CardShape)) {
                CoverArt(comic = comic, showArtwork = true)
            }
            Column(modifier = Modifier.fillMaxHeight().weight(1f).padding(end = UnshelveButtonSize)) {
                Text(
                    text = stringResource(R.string.library_hero_eyebrow).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    color = tint,
                )
                comic.heroSeriesLine()?.let { seriesLine ->
                    Text(
                        text = seriesLine,
                        style = MaterialTheme.typography.labelMedium,
                        color = InkDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                Text(
                    text = comic.storyTitle ?: comic.title,
                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(modifier = Modifier.weight(1f).heightIn(min = 14.dp))
                HeroProgress(comic = comic, secondsPerPage = secondsPerPage)
                Spacer(modifier = Modifier.height(14.dp))
                ResumePill()
            }
        }
        UnshelveButton(onClick = { onUnshelve(comic) }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

private fun LibraryComic.heroSeriesLine(): String? = when {
    storyTitle != null -> title
    !title.startsWith(series) -> series
    else -> null
}

@Composable
private fun UnshelveButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(UnshelveButtonSize)
            .clip(CircleShape)
            .background(CoverTrack)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.library_unshelve),
            tint = OnGround,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun HeroProgress(comic: LibraryComic, secondsPerPage: Int) {
    val pageCount = comic.pageCount
    if (pageCount == null || pageCount <= 0) return
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text(
            text = stringResource(R.string.library_progress, comic.pageIndex + 1, pageCount),
            style = MaterialTheme.typography.labelSmall,
            color = InkDim,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.library_time_left, LibraryCatalog.minutesLeft(comic.pageIndex, pageCount, secondsPerPage)),
            style = MaterialTheme.typography.labelSmall,
            color = InkFaint,
        )
    }
    ProgressBar(progress = LibraryCatalog.progress(comic.pageIndex, pageCount))
}

internal fun LibraryComic.ambientColor(): Color =
    coverAmbient?.let { Color(it) } ?: proceduralGradient().first()

private fun LibraryComic.proceduralGradient(): List<Color> {
    val key = series.ifBlank { title }
    return CoverGradients[(key.hashCode() and 0x7fffffff) % CoverGradients.size]
}

@Composable
private fun ContinueReadingCard(comic: LibraryComic, onOpenComic: (LibraryComic) -> Unit, onUnshelve: (LibraryComic) -> Unit) {
    val pageCount = comic.pageCount
    Box(
        modifier = Modifier
            .width(if (isCompactWidth()) ShelfCardWidthCompact else ShelfCardWidth)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Surface2, HeroGround)))
            .clickable { onOpenComic(comic) },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.width(84.dp).height(126.dp).clip(RoundedCornerShape(10.dp))) {
                CoverArt(comic = comic, showArtwork = false)
            }
            Column(modifier = Modifier.fillMaxWidth().padding(end = UnshelveButtonSize)) {
                Text(
                    text = if (comic.storyTitle != null) comic.title else comic.series,
                    style = MaterialTheme.typography.labelSmall,
                    color = InkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = comic.storyTitle ?: comic.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
                if (pageCount != null && pageCount > 0) {
                    Text(
                        text = stringResource(R.string.library_progress, comic.pageIndex + 1, pageCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = InkDim,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    ProgressBar(progress = LibraryCatalog.progress(comic.pageIndex, pageCount))
                    Spacer(modifier = Modifier.height(12.dp))
                }
                ResumePill()
            }
        }
        UnshelveButton(onClick = { onUnshelve(comic) }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

@Composable
internal fun ResumePill() {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Accent.copy(alpha = 0.16f))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, tint = AccentPale, modifier = Modifier.size(15.dp))
        Text(
            text = stringResource(R.string.library_resume),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = AccentPale,
        )
    }
}

@Composable
private fun ComicCard(
    comic: LibraryComic,
    selecting: Boolean,
    selected: Boolean,
    onOpenComic: (LibraryComic) -> Unit,
    onToggleSelection: (LibraryComic) -> Unit,
    title: String = comic.storyTitle ?: comic.title,
    subtitle: String? = null,
) {
    val scale by animateFloatAsState(targetValue = if (selected) SelectionScale else 1f, animationSpec = spring())
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected }
            .clickable { if (selecting) onToggleSelection(comic) else onOpenComic(comic) },
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .sharedCover(comic.id)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CardShape)
                .then(
                    when {
                        selected -> Modifier.border(SelectionBorder, Accent, CardShape)
                        selecting -> Modifier.border(SelectableBorder, CardLine, CardShape)
                        else -> Modifier
                    },
                ),
        ) {
            CoverArt(comic = comic, showArtwork = true)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f))),
            )
            val pageCount = comic.pageCount
            when {
                comic.completed -> CompletedBadge(modifier = Modifier.align(Alignment.BottomEnd).padding(9.dp))
                pageCount != null && pageCount > 0 && comic.pageIndex > 0 ->
                    ProgressRing(
                        progress = LibraryCatalog.progress(comic.pageIndex, pageCount),
                        modifier = Modifier.align(Alignment.TopEnd).padding(9.dp),
                    )
            }
            if (selected) {
                SelectionBadge(modifier = Modifier.align(Alignment.TopStart).padding(9.dp))
            } else if (comic.favorite) {
                FavoriteBadge(modifier = Modifier.align(Alignment.TopStart).padding(9.dp))
            }
            comic.issueNumber?.let {
                Text(
                    text = "#%02d".format(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = InkFaint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SelectionBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(BadgeSize)
            .clip(CircleShape)
            .background(Accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(BadgeGlyphSize),
        )
    }
}

private data class DragSelect(val anchor: Int, val base: Set<Long>, val position: Offset)

@Composable
private fun Modifier.dragToSelect(
    gridState: LazyGridState,
    pinnedKey: Any,
    selection: SelectionUi,
): Modifier {
    val shelf = rememberUpdatedState(selection.shelf)
    val selected = rememberUpdatedState(selection.selected)
    val selectRange = rememberUpdatedState(selection.selectRange)
    val dragging = rememberUpdatedState(selection.dragging)
    val haptics = LocalHapticFeedback.current
    val hotZone: Float
    val minRate: Float
    val maxRate: Float
    with(LocalDensity.current) {
        hotZone = DragSelectHotZone.toPx()
        minRate = DragSelectMinRate.toPx()
        maxRate = DragSelectMaxRate.toPx()
    }
    var drag by remember { mutableStateOf<DragSelect?>(null) }

    LaunchedEffect(drag != null) {
        val start = drag ?: return@LaunchedEffect
        dragging.value(true)
        var applied = start.anchor
        var appliedCount =
            SelectionDrag.rangeFromAnchor(start.base, shelf.value, start.anchor, start.anchor).size
        var lastTick = 0L
        var previousFrame = 0L
        try {
            while (isActive) {
                val current = drag ?: break
                val frame = withFrameNanos { it }
                val elapsed = if (previousFrame == 0L) 0f else (frame - previousFrame) / NANOS_PER_SECOND
                previousFrame = frame
                val rate = gridState.edgeScrollRate(current.position.y, pinnedKey, hotZone, minRate, maxRate)
                if (elapsed > 0f && gridState.canScroll(rate)) gridState.scrollBy(rate * elapsed)
                val index = gridState.selectableIndexAt(current.position, shelf.value)
                if (index < 0 || index == applied) continue
                applied = index
                val next = SelectionDrag.rangeFromAnchor(current.base, shelf.value, current.anchor, index)
                if (next.size != appliedCount && frame - lastTick > DragSelectHapticNanos) {
                    lastTick = frame
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                appliedCount = next.size
                selectRange.value(next)
            }
        } finally {
            dragging.value(false)
        }
    }

    return pointerInput(gridState) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val anchor = gridState.selectableIndexAt(down.position, shelf.value)
            if (anchor < 0) return@awaitEachGesture
            if (!awaitLongPress(down)) return@awaitEachGesture
            val base = selected.value.mapTo(mutableSetOf()) { it.id }
            selectRange.value(SelectionDrag.rangeFromAnchor(base, shelf.value, anchor, anchor))
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            drag = DragSelect(anchor = anchor, base = base, position = down.position)
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes
                    .firstOrNull { it.id == down.id } ?: break
                change.consume()
                if (!change.pressed) break
                drag = drag?.copy(position = change.position)
            }
            drag = null
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitLongPress(down: PointerInputChange): Boolean {
    val slop = viewConfiguration.touchSlop
    val cancelled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        var done = false
        while (!done) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
            done = change == null || !change.pressed ||
                (change.position - down.position).getDistance() > slop
        }
        true
    }
    return cancelled == null
}

private fun LazyGridState.selectableIndexAt(position: Offset, shelf: List<LibraryComic>): Int {
    val hit = layoutInfo.visibleItemsInfo.firstOrNull { item ->
        position.x >= item.offset.x && position.x <= item.offset.x + item.size.width &&
            position.y >= item.offset.y && position.y <= item.offset.y + item.size.height
    }
    val id = hit?.key?.comicKeyId() ?: return -1
    return shelf.indexOfFirst { it.id == id }
}

private fun Any.comicKeyId(): Long? {
    val key = this as? String ?: return null
    if (!key.startsWith(ComicKeyPrefix)) return null
    return key.removePrefix(ComicKeyPrefix).toLongOrNull()
}

private fun LazyGridState.canScroll(rate: Float): Boolean = when {
    rate < 0f -> canScrollBackward
    rate > 0f -> canScrollForward
    else -> false
}

private fun LazyGridState.edgeScrollRate(
    y: Float,
    pinnedKey: Any,
    hotZone: Float,
    minRate: Float,
    maxRate: Float,
): Float {
    val top = pinnedBottom(pinnedKey)
    val bottom = layoutInfo.viewportSize.height.toFloat()
    if (bottom <= top || hotZone <= 0f) return 0f
    val fromTop = y - top
    val fromBottom = bottom - y
    if (fromTop < hotZone) return -dragScrollRate(fromTop, hotZone, minRate, maxRate)
    if (fromBottom < hotZone) return dragScrollRate(fromBottom, hotZone, minRate, maxRate)
    return 0f
}

private fun LazyGridState.pinnedBottom(pinnedKey: Any): Float {
    val pinned = layoutInfo.visibleItemsInfo.firstOrNull { it.key == pinnedKey } ?: return 0f
    if (pinned.offset.y > 0) return 0f
    return (pinned.offset.y + pinned.size.height).toFloat()
}

private fun dragScrollRate(distance: Float, hotZone: Float, minRate: Float, maxRate: Float): Float {
    val ramp = ((hotZone - distance) / hotZone).coerceIn(0f, 1f)
    return minRate + ramp * (maxRate - minRate)
}

internal data class SelectionUi(
    val selected: List<LibraryComic>,
    val shelf: List<LibraryComic>,
    val lists: ReadingListsUi,
    val openSettings: (List<LibraryComic>) -> Unit,
    val openDetails: (LibraryComic) -> Unit,
    val chooseCover: (LibraryComic) -> Unit,
    val setRead: (List<LibraryComic>, Boolean) -> Unit,
    val setFavorite: (List<LibraryComic>, Boolean) -> Unit,
    val delete: (List<LibraryComic>) -> Unit,
    val selectRange: (Set<Long>) -> Unit,
    val dragging: (Boolean) -> Unit,
    val clear: () -> Unit,
)

private class SelectionAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun rememberHeldSelection(selection: SelectionUi): SelectionUi {
    val held = remember { mutableStateOf(selection.selected) }
    if (selection.selected.isNotEmpty()) held.value = selection.selected
    return selection.copy(selected = held.value)
}

@Composable
private fun SelectionRow(selection: SelectionUi) {
    var menuExpanded by remember { mutableStateOf(false) }
    var addingToList by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selected = selection.selected
    val openList = selection.lists.opened
    val allRead = selected.all { it.completed }
    val allFavorite = selected.all { it.favorite }
    val actions = buildList {
        add(
            SelectionAction(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                label = stringResource(R.string.library_list_add_to),
                onClick = { addingToList = true },
            ),
        )
        openList?.let { list ->
            add(
                SelectionAction(
                    icon = Icons.Outlined.RemoveCircleOutline,
                    label = stringResource(R.string.library_list_remove),
                    onClick = { selection.clear(); selection.lists.actions.remove(list, selected) },
                ),
            )
        }
        add(
            SelectionAction(
                icon = if (allRead) Icons.Outlined.RemoveDone else Icons.Outlined.DoneAll,
                label = stringResource(
                    if (allRead) R.string.library_selection_mark_unread else R.string.library_selection_mark_read,
                ),
                onClick = { selection.setRead(selected, !allRead) },
            ),
        )
        add(
            SelectionAction(
                icon = if (allFavorite) Icons.Outlined.StarBorder else Icons.Filled.Star,
                label = stringResource(
                    if (allFavorite) R.string.library_selection_remove_favorite
                    else R.string.library_selection_add_favorite,
                ),
                onClick = { selection.setFavorite(selected, !allFavorite) },
            ),
        )
        add(
            SelectionAction(
                icon = Icons.Outlined.Delete,
                label = stringResource(R.string.library_selection_delete),
                destructive = true,
                onClick = { confirmDelete = true },
            ),
        )
    }
    BoxWithConstraints {
        val inline = actions.take(inlineActionCount(maxWidth, actions.size, isCompactWidth()))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SelectionGap),
        ) {
            GhostAction(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.library_selection_close),
                onClick = selection.clear,
            )
            SelectionCountPill(count = selected.size)
            Spacer(modifier = Modifier.weight(1f))
            inline.forEach { action ->
                GhostAction(
                    icon = action.icon,
                    contentDescription = action.label,
                    onClick = action.onClick,
                    destructive = action.destructive,
                )
            }
            Box {
                GhostAction(
                    icon = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.library_selection_more_actions),
                    onClick = { menuExpanded = true },
                )
                SelectionMenu(
                    expanded = menuExpanded,
                    selection = selection,
                    overflow = actions.drop(inline.size),
                    onDismiss = { menuExpanded = false },
                )
            }
        }
    }
    if (addingToList) {
        AddToListDialog(
            name = null,
            comics = selected,
            lists = selection.lists,
            onDismiss = { addingToList = false; selection.clear() },
        )
    }
    if (confirmDelete) {
        DeleteConfirmDialog(
            comics = selected,
            onConfirm = {
                confirmDelete = false
                selection.clear()
                selection.delete(selected)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

private fun inlineActionCount(available: Dp, actions: Int, compact: Boolean): Int {
    val fixed = SelectionSquare + SelectionGap + selectionPillWidth(compact) + SelectionGap + SelectionSquare
    val slot = SelectionSquare + SelectionGap
    return (((available - fixed) + SelectionGap) / slot).toInt().coerceIn(0, actions)
}

private fun selectionPillWidth(compact: Boolean): Dp =
    if (compact) SelectionPillCompact else SelectionPillWide

@Composable
private fun SelectionCountPill(count: Int) {
    val spoken = pluralStringResource(R.plurals.library_selected_count, count, count)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Accent)
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .semantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = if (isCompactWidth()) "$count" else spoken,
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TabularNumbers),
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
        )
    }
}

@Composable
private fun SelectionMenu(
    expanded: Boolean,
    selection: SelectionUi,
    overflow: List<SelectionAction>,
    onDismiss: () -> Unit,
) {
    val selected = selection.selected
    val single = selected.singleOrNull()
    val allSelected = LibrarySelection.allSelected(selected.mapTo(mutableSetOf()) { it.id }, selection.shelf)
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuHeader(title = pluralStringResource(R.plurals.library_selected_count, selected.size, selected.size))
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (allSelected) R.string.library_select_none else R.string.library_select_all,
                    ),
                )
            },
            leadingIcon = { Icon(imageVector = Icons.Filled.Check, contentDescription = null) },
            onClick = {
                onDismiss()
                if (allSelected) selection.clear()
                else selection.selectRange(selection.shelf.mapTo(mutableSetOf()) { it.id })
            },
        )
        if (single != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.detail_info)) },
                leadingIcon = { Icon(imageVector = Icons.Outlined.Info, contentDescription = null) },
                onClick = { onDismiss(); selection.clear(); selection.openDetails(single) },
            )
            if (single.pageCount != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.library_choose_cover)) },
                    leadingIcon = { Icon(imageVector = Icons.Outlined.Image, contentDescription = null) },
                    onClick = { onDismiss(); selection.clear(); selection.chooseCover(single) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.detail_settings)) },
                leadingIcon = { Icon(imageVector = Icons.Outlined.Settings, contentDescription = null) },
                onClick = { onDismiss(); selection.clear(); selection.openSettings(selected) },
            )
            selection.lists.opened?.let { list ->
                ReadingListMoveItems(
                    list = list,
                    comic = single,
                    actions = selection.lists.actions,
                    onDismiss = onDismiss,
                )
            }
        }
        if (overflow.isEmpty()) return@DropdownMenu
        HorizontalDivider(color = CardLine)
        overflow.forEach { action ->
            DropdownMenuItem(
                text = { Text(text = action.label, color = if (action.destructive) Danger else Color.Unspecified) },
                leadingIcon = {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = null,
                        tint = if (action.destructive) Danger else LocalContentColor.current,
                    )
                },
                onClick = { onDismiss(); action.onClick() },
            )
        }
    }
}
@Composable
private fun DeleteConfirmDialog(
    comics: List<LibraryComic>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val single = comics.singleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = single?.let { stringResource(R.string.library_delete_confirm_title) }
                    ?: pluralStringResource(R.plurals.library_selection_delete_title, comics.size, comics.size),
            )
        },
        text = {
            Text(
                text = single?.let { stringResource(R.string.library_delete_confirm_body, it.title) }
                    ?: pluralStringResource(R.plurals.library_selection_delete_body, comics.size, comics.size),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.library_delete_confirm), color = Danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.library_delete_cancel))
            }
        },
    )
}

@Composable
internal fun FavoriteBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(BadgeSize)
            .clip(CircleShape)
            .background(BadgeGround),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = stringResource(R.string.library_favorite),
            tint = AccentAmber,
            modifier = Modifier.size(BadgeGlyphSize),
        )
    }
}

@Composable
internal fun MenuHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = InkDim,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = MenuHeaderMaxWidth).padding(horizontal = 16.dp, vertical = 10.dp),
    )
    HorizontalDivider(color = CardLine)
}

@Composable
private fun SeriesMenu(
    expanded: Boolean,
    group: LibraryEntry.Group,
    selection: SelectionUi,
    onDismiss: () -> Unit,
) {
    val allRead = group.comics.all { it.completed }
    val allFavorite = group.comics.all { it.favorite }
    var confirmDelete by remember { mutableStateOf(false) }
    var addingToList by remember { mutableStateOf(false) }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuHeader(title = group.series)
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_series_settings)) },
            leadingIcon = { Icon(imageVector = Icons.Outlined.Settings, contentDescription = null) },
            onClick = { onDismiss(); selection.openSettings(group.comics) },
        )
        SeriesAddToListMenuItem(onClick = { onDismiss(); addingToList = true })
        DropdownMenuItem(
            text = { Text(stringResource(if (allRead) R.string.library_mark_series_unread else R.string.library_mark_series_read)) },
            leadingIcon = { Icon(imageVector = if (allRead) Icons.Outlined.RemoveDone else Icons.Outlined.DoneAll, contentDescription = null) },
            onClick = { onDismiss(); selection.setRead(group.comics, !allRead) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(if (allFavorite) R.string.library_series_remove_favorite else R.string.library_series_add_favorite)) },
            leadingIcon = { Icon(imageVector = if (allFavorite) Icons.Outlined.StarBorder else Icons.Filled.Star, contentDescription = null) },
            onClick = { onDismiss(); selection.setFavorite(group.comics, !allFavorite) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_delete_series), color = Danger) },
            leadingIcon = { Icon(imageVector = Icons.Outlined.Delete, contentDescription = null, tint = Danger) },
            onClick = { onDismiss(); confirmDelete = true },
        )
    }
    if (addingToList) {
        AddToListDialog(
            name = group.series,
            comics = group.comics,
            lists = selection.lists,
            onDismiss = { addingToList = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.library_delete_series_confirm_title)) },
            text = { Text(stringResource(R.string.library_delete_series_confirm_body, group.comics.size, group.series)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; selection.delete(group.comics) }) {
                    Text(stringResource(R.string.library_delete_confirm), color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.library_delete_cancel)) }
            },
        )
    }
}

@Composable
private fun GroupCard(
    group: LibraryEntry.Group,
    inert: Boolean,
    onOpen: (LibraryEntry.Group) -> Unit,
    selection: SelectionUi,
) {
    val representative = group.comics.firstOrNull { !it.completed && it.pageIndex > 0 } ?: group.comics.first()
    val readCount = group.comics.count { it.completed }
    var menuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .inertWhile(inert)
            .combinedClickable(
                onClick = { onOpen(group) },
                onLongClick = { menuExpanded = true },
            ),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, bottom = 12.dp)
                    .clip(CardShape)
                    .background(Surface2),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp, end = 12.dp)
                    .clip(CardShape),
            ) {
                CoverArt(comic = representative, showArtwork = true)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f))),
                )
                CountBadge(count = group.comics.size, modifier = Modifier.align(Alignment.BottomStart).padding(9.dp))
                if (readCount == group.comics.size) {
                    CompletedBadge(modifier = Modifier.align(Alignment.BottomEnd).padding(9.dp))
                }
            }
        }
        Column {
            Text(
                text = group.series,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.library_group_read, readCount, group.comics.size),
                style = MaterialTheme.typography.labelSmall,
                color = InkFaint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        SeriesMenu(expanded = menuExpanded, group = group, selection = selection, onDismiss = { menuExpanded = false })
    }
}

@Composable
private fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(BadgeGround)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(imageVector = Icons.Filled.Layers, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun FilterHeader(
    filter: LibraryFilter,
    onFilterSelected: (LibraryFilter) -> Unit,
    selection: SelectionUi,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 10.dp),
    ) {
        Crossfade(
            targetState = selection.selected.isNotEmpty(),
            animationSpec = tween(SELECTION_CROSSFADE_MS),
        ) { selecting ->
            if (selecting) SelectionRow(selection = rememberHeldSelection(selection))
            else FilterBar(selected = filter, onFilterSelected = onFilterSelected)
        }
    }
}

@Composable
internal fun RecentSortToggle(sort: LibrarySort, onSortSelected: (LibrarySort) -> Unit) {
    val recent = sort == LibrarySort.RECENT
    GhostAction(
        icon = Icons.Filled.History,
        contentDescription = stringResource(R.string.library_sort_recent),
        onClick = { onSortSelected(if (recent) LibrarySort.TITLE else LibrarySort.RECENT) },
        active = recent,
    )
}

@Composable
private fun FilterBar(selected: LibraryFilter, onFilterSelected: (LibraryFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilterPill(R.string.library_filter_all, selected == LibraryFilter.ALL) { onFilterSelected(LibraryFilter.ALL) }
        FilterPill(R.string.library_filter_unread, selected == LibraryFilter.UNREAD) { onFilterSelected(LibraryFilter.UNREAD) }
        FilterPill(R.string.library_filter_read, selected == LibraryFilter.READ) { onFilterSelected(LibraryFilter.READ) }
        FilterPill(R.string.library_filter_favorites, selected == LibraryFilter.FAVORITES) { onFilterSelected(LibraryFilter.FAVORITES) }
    }
}

@Composable
private fun isCompactWidth(): Boolean = LocalConfiguration.current.screenWidthDp < COMPACT_WIDTH_DP

@Composable
internal fun gridMinCell() = if (isCompactWidth()) GridMinCellCompact else GridMinCell

@Composable
private fun FilterPill(labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) Color.White else InkDim,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) Accent else Surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
private fun FilterEmpty(searching: Boolean) {
    Text(
        text = stringResource(if (searching) R.string.library_search_empty else R.string.library_filter_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = InkFaint,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun CoverArt(comic: LibraryComic, showArtwork: Boolean) {
    val coverPath = comic.coverPath
    if (coverPath != null) {
        AsyncImage(
            model = File(coverPath),
            contentDescription = comic.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    ProceduralCover(comic = comic, showArtwork = showArtwork)
}

@Composable
private fun ProceduralCover(comic: LibraryComic, showArtwork: Boolean) {
    val key = comic.series.ifBlank { comic.title }
    val gradient = comic.proceduralGradient()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(gradient)),
    ) {
        if (!showArtwork) return@Box
        Halftone()
        Text(
            text = key.take(1).uppercase(),
            fontSize = 132.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-6).sp,
            color = Color.White.copy(alpha = 0.14f),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 2.dp),
        )
        Text(
            text = comic.series.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 2.sp,
            color = Color.White.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        )
        Text(
            text = comic.storyTitle ?: comic.series,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 28.dp),
        )
    }
}

@Composable
private fun Halftone() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val step = 8.dp.toPx()
        val radius = 1.dp.toPx()
        val dot = Color.White.copy(alpha = 0.07f)
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                drawCircle(color = dot, radius = radius, center = Offset(x, y))
                x += step
            }
            y += step
        }
    }
}

@Composable
internal fun ProgressRing(progress: Float, modifier: Modifier = Modifier) {
    val track = CoverTrack
    val fill = Accent
    Box(modifier = modifier.size(ProgressRingSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = fill,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
internal fun CompletedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Good),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = stringResource(R.string.library_completed),
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun ProgressBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(CoverTrack),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Accent, AccentAmber))),
        )
    }
}

@Composable
private fun EmptyLibrary(scanError: LibraryScanError?, onPickFolder: () -> Unit, onOpenFile: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(CoverGradients[0])),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.FileOpen,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(38.dp),
            )
        }
        Text(
            text = stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = stringResource(R.string.library_empty_folder_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = InkDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
        )
        if (scanError != null) {
            Text(
                text = stringResource(scanError.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
        PrimaryAction(icon = Icons.Outlined.CreateNewFolder, label = stringResource(R.string.library_pick_folder), onClick = onPickFolder)
        Spacer(modifier = Modifier.height(12.dp))
        GhostTextButton(label = stringResource(R.string.library_open_comic), onClick = onOpenFile)
    }
}

@Composable
private fun GhostTextButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = InkDim,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

private val openDocumentMimeTypes = arrayOf(
    "application/vnd.comicbook+zip",
    "application/vnd.comicbook-rar",
    "application/x-cbz",
    "application/x-cbr",
    "application/zip",
    "application/x-rar-compressed",
    "application/vnd.rar",
    "application/x-7z-compressed",
    "application/x-tar",
    "application/pdf",
    "application/octet-stream",
)

private fun LibraryScanError.messageRes(): Int = when (this) {
    LibraryScanError.AccessLost -> R.string.library_scan_access_lost
    LibraryScanError.ReadFailure -> R.string.library_scan_error
}
