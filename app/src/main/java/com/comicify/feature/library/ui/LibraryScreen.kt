package com.comicify.feature.library.ui

import android.net.Uri
import com.comicify.BuildConfig
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.comicify.R
import com.comicify.feature.library.domain.LibraryCatalog
import com.comicify.feature.library.domain.LibraryComic
import com.comicify.feature.library.domain.LibraryEntry
import com.comicify.feature.library.domain.LibraryFilter
import java.io.File
import kotlin.math.roundToInt

private val HeroCoverWidth = 132.dp

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
    onRefresh: () -> Unit,
    onOpenComic: (LibraryComic) -> Unit,
    onShowDetail: (LibraryComic) -> Unit,
    onOpenFile: (Uri) -> Unit,
    onFilterSelected: (LibraryFilter) -> Unit,
    onToggleGrouped: () -> Unit,
    onToggleRead: (LibraryComic) -> Unit,
    onToggleFavorite: (LibraryComic) -> Unit,
    onDeleteComic: (LibraryComic) -> Unit,
) {
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(onFolderPicked) }
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onOpenFile) }

    val pickFolder = { folderLauncher.launch(null) }
    val openFile = { fileLauncher.launch(openDocumentMimeTypes) }

    var openedSeries by remember { mutableStateOf<String?>(null) }
    val openGroup = openedSeries?.let { series ->
        state.entries.filterIsInstance<LibraryEntry.Group>().firstOrNull { it.series == series }
    }
    LaunchedEffect(openedSeries, openGroup) {
        if (openedSeries != null && openGroup == null) openedSeries = null
    }

    BackHandler(enabled = openGroup != null) { openedSeries = null }

    if (openGroup != null) {
        SeriesScreen(
            group = openGroup,
            onBack = { openedSeries = null },
            onOpenComic = onShowDetail,
            onToggleRead = onToggleRead,
            onToggleFavorite = onToggleFavorite,
            onDeleteComic = onDeleteComic,
        )
        return
    }

    if (state.totalCount == 0 && !state.scanning) {
        EmptyLibrary(scanFailed = state.scanFailed, onPickFolder = pickFolder, onOpenFile = openFile)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 158.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            LibraryHeader(
                comicCount = state.totalCount,
                scanning = state.scanning,
                scanFailed = state.scanFailed,
                grouped = state.grouped,
                onPickFolder = pickFolder,
                onRefresh = onRefresh,
                onOpenFile = openFile,
                onToggleGrouped = onToggleGrouped,
            )
        }
        if (state.continueReading.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ContinueReadingShelf(comics = state.continueReading, onOpenComic = onOpenComic)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader(eyebrow = stringResource(R.string.library_shelf_eyebrow), title = stringResource(R.string.library_all_comics))
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            FilterBar(selected = state.filter, onFilterSelected = onFilterSelected)
        }
        if (state.comics.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                FilterEmpty()
            }
        }
        if (state.grouped) {
            items(items = state.entries, key = { it.gridKey() }) { entry ->
                when (entry) {
                    is LibraryEntry.Single -> ComicCard(
                        comic = entry.comic,
                        onOpenComic = onShowDetail,
                        onToggleRead = onToggleRead,
                        onToggleFavorite = onToggleFavorite,
                        onDeleteComic = onDeleteComic,
                    )
                    is LibraryEntry.Group -> GroupCard(group = entry, onOpen = { openedSeries = it.series })
                }
            }
        } else {
            items(items = state.comics, key = { it.id }) { comic ->
                ComicCard(
                    comic = comic,
                    onOpenComic = onShowDetail,
                    onToggleRead = onToggleRead,
                    onToggleFavorite = onToggleFavorite,
                    onDeleteComic = onDeleteComic,
                )
            }
        }
    }
}

private fun LibraryEntry.gridKey(): String = when (this) {
    is LibraryEntry.Single -> "comic-${comic.id}"
    is LibraryEntry.Group -> "group-$series"
}

@Composable
private fun SeriesScreen(
    group: LibraryEntry.Group,
    onBack: () -> Unit,
    onOpenComic: (LibraryComic) -> Unit,
    onToggleRead: (LibraryComic) -> Unit,
    onToggleFavorite: (LibraryComic) -> Unit,
    onDeleteComic: (LibraryComic) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 158.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SeriesHeader(
                title = group.series,
                subtitle = stringResource(R.string.library_group_read, group.comics.count { it.completed }, group.comics.size),
                onBack = onBack,
            )
        }
        items(items = group.comics, key = { it.id }) { comic ->
            ComicCard(
                comic = comic,
                onOpenComic = onOpenComic,
                onToggleRead = onToggleRead,
                onToggleFavorite = onToggleFavorite,
                onDeleteComic = onDeleteComic,
            )
        }
    }
}

@Composable
private fun SeriesHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(44.dp)
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
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = InkFaint,
            )
        }
    }
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
    scanFailed: Boolean,
    grouped: Boolean,
    onPickFolder: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFile: () -> Unit,
    onToggleGrouped: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.library_count, comicCount),
                style = MaterialTheme.typography.labelMedium,
                color = InkFaint,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            if (BuildConfig.DEBUG) DebugBadge()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostAction(icon = Icons.Filled.CreateNewFolder, contentDescription = stringResource(R.string.library_pick_folder), onClick = onPickFolder)
            GhostAction(
                icon = if (grouped) Icons.Filled.GridView else Icons.Outlined.Folder,
                contentDescription = stringResource(if (grouped) R.string.library_ungroup else R.string.library_group),
                onClick = onToggleGrouped,
                active = grouped,
            )
            GhostAction(icon = Icons.Filled.Refresh, contentDescription = stringResource(R.string.library_refresh), onClick = onRefresh)
            GhostAction(icon = Icons.AutoMirrored.Filled.MenuBook, contentDescription = stringResource(R.string.library_action_open_file), onClick = onOpenFile)
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
        if (scanFailed) {
            Text(
                text = stringResource(R.string.library_scan_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
internal fun PrimaryAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Accent, Color(0xFFB3161B))))
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
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Accent.copy(alpha = 0.16f) else Surface2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) Accent else InkDim,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun ContinueReadingShelf(comics: List<LibraryComic>, onOpenComic: (LibraryComic) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader(eyebrow = stringResource(R.string.library_resume_eyebrow), title = stringResource(R.string.library_continue_reading))
        LibraryHero(comic = comics.first(), onOpenComic = onOpenComic)
        val rest = comics.drop(1)
        if (rest.isEmpty()) return@Column
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(items = rest, key = { it.id }) { comic ->
                ContinueReadingCard(comic = comic, onOpenComic = onOpenComic)
            }
        }
    }
}

@Composable
private fun LibraryHero(comic: LibraryComic, onOpenComic: (LibraryComic) -> Unit) {
    val ambient = comic.ambientColor()
    val glow = lerp(Color.Black, ambient, HeroGlowMix)
    val tint = lerp(Color.White, ambient, HeroTintMix)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(glow, HeroGround)))
            .clickable { onOpenComic(comic) }
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(modifier = Modifier.width(HeroCoverWidth).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp))) {
            CoverArt(comic = comic, showArtwork = true)
        }
        Column(modifier = Modifier.fillMaxHeight().weight(1f)) {
            Text(
                text = stringResource(R.string.library_hero_eyebrow).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                color = tint,
            )
            if (comic.series != comic.title) {
                Text(
                    text = comic.series,
                    style = MaterialTheme.typography.labelMedium,
                    color = InkDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Text(
                text = comic.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            HeroProgress(comic = comic)
            Spacer(modifier = Modifier.height(14.dp))
            ResumePill()
        }
    }
}

@Composable
private fun HeroProgress(comic: LibraryComic) {
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
            text = stringResource(R.string.library_time_left, LibraryCatalog.minutesLeft(comic.pageIndex, pageCount)),
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
private fun ContinueReadingCard(comic: LibraryComic, onOpenComic: (LibraryComic) -> Unit) {
    val pageCount = comic.pageCount
    Row(
        modifier = Modifier
            .width(340.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Surface2, Color(0xFF0D0E13))))
            .clickable { onOpenComic(comic) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.width(84.dp).height(126.dp).clip(RoundedCornerShape(10.dp))) {
            CoverArt(comic = comic, showArtwork = false)
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = comic.series,
                style = MaterialTheme.typography.labelSmall,
                color = InkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = comic.title,
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
        Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null, tint = Color(0xFFFF9E99), modifier = Modifier.size(15.dp))
        Text(
            text = stringResource(R.string.library_resume),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFFF9E99),
        )
    }
}

@Composable
internal fun SectionHeader(eyebrow: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = eyebrow.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
            color = Accent,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Box(modifier = Modifier.weight(1f).height(1.dp).background(CardLine))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComicCard(
    comic: LibraryComic,
    onOpenComic: (LibraryComic) -> Unit,
    onToggleRead: (LibraryComic) -> Unit,
    onToggleFavorite: (LibraryComic) -> Unit,
    onDeleteComic: (LibraryComic) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = { onOpenComic(comic) },
                onLongClick = { menuExpanded = true },
            ),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(14.dp)),
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
            if (comic.favorite) {
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
            text = comic.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        ComicCardMenu(
            expanded = menuExpanded,
            comic = comic,
            onDismiss = { menuExpanded = false },
            onToggleRead = onToggleRead,
            onToggleFavorite = onToggleFavorite,
            onDelete = {
                menuExpanded = false
                confirmDelete = true
            },
        )
    }
    if (confirmDelete) {
        DeleteConfirmDialog(
            comic = comic,
            onConfirm = {
                confirmDelete = false
                onDeleteComic(comic)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
internal fun DeleteConfirmDialog(
    comic: LibraryComic,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_delete_confirm_title)) },
        text = { Text(stringResource(R.string.library_delete_confirm_body, comic.title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.library_delete_confirm), color = Accent)
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
internal fun ComicCardMenu(
    expanded: Boolean,
    comic: LibraryComic,
    onDismiss: () -> Unit,
    onToggleRead: (LibraryComic) -> Unit,
    onToggleFavorite: (LibraryComic) -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        val readLabel = if (comic.completed) R.string.library_mark_unread else R.string.library_mark_read
        val favoriteLabel = if (comic.favorite) R.string.library_remove_favorite else R.string.library_add_favorite
        DropdownMenuItem(
            text = { Text(stringResource(readLabel)) },
            leadingIcon = { Icon(imageVector = Icons.Filled.Check, contentDescription = null) },
            onClick = {
                onToggleRead(comic)
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(favoriteLabel)) },
            leadingIcon = {
                Icon(
                    imageVector = if (comic.favorite) Icons.Outlined.StarBorder else Icons.Filled.Star,
                    contentDescription = null,
                )
            },
            onClick = {
                onToggleFavorite(comic)
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_delete), color = Accent) },
            leadingIcon = { Icon(imageVector = Icons.Outlined.Delete, contentDescription = null, tint = Accent) },
            onClick = onDelete,
        )
    }
}

@Composable
internal fun FavoriteBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(0xD2060608)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = stringResource(R.string.library_favorite),
            tint = AccentAmber,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun GroupCard(group: LibraryEntry.Group, onOpen: (LibraryEntry.Group) -> Unit) {
    val representative = group.comics.firstOrNull { !it.completed && it.pageIndex > 0 } ?: group.comics.first()
    val readCount = group.comics.count { it.completed }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onOpen(group) },
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, bottom = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface2),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp, end = 12.dp)
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                CoverArt(comic = representative, showArtwork = true)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f))),
                )
                CountBadge(count = group.comics.size, modifier = Modifier.align(Alignment.TopEnd).padding(9.dp))
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
    }
}

@Composable
private fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xD2060608))
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
private fun FilterEmpty() {
    Text(
        text = stringResource(R.string.library_filter_empty),
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
            text = comic.title,
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
    Box(modifier = modifier.size(34.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = CoverTrack,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = Accent,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Box(modifier = Modifier.size(27.dp).clip(CircleShape).background(Color(0xD2060608)), contentAlignment = Alignment.Center) {
            Text(
                text = "${(progress * 100).roundToInt()}",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
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
private fun EmptyLibrary(scanFailed: Boolean, onPickFolder: () -> Unit, onOpenFile: () -> Unit) {
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
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
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
        if (scanFailed) {
            Text(
                text = stringResource(R.string.library_scan_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
        PrimaryAction(icon = Icons.Filled.CreateNewFolder, label = stringResource(R.string.library_pick_folder), onClick = onPickFolder)
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
    "application/pdf",
    "application/octet-stream",
)
