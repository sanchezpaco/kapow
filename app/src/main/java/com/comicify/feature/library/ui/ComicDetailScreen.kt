package com.comicify.feature.library.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicify.R
import com.comicify.domain.model.ReadingDirection
import com.comicify.feature.library.domain.ComicSettings
import com.comicify.feature.library.domain.LibraryCatalog
import com.comicify.feature.library.domain.LibraryComic

private val DetailCoverWidth = 150.dp
private val IssueCoverWidth = 96.dp
private val PageThumbMinWidth = 96.dp

@Composable
fun ComicDetailScreen(
    comic: LibraryComic,
    onBack: () -> Unit,
    onOpenComic: (LibraryComic) -> Unit,
    onShowComic: (LibraryComic) -> Unit,
) {
    val viewModel: ComicDetailViewModel = hiltViewModel()
    LaunchedEffect(comic.id) { viewModel.show(comic) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state.comic ?: comic
    BackHandler(onBack = onBack)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = PageThumbMinWidth),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            DetailToolbar(
                comic = current,
                onBack = onBack,
                onToggleFavorite = { viewModel.onToggleFavorite(current) },
                onToggleRead = { viewModel.onToggleRead(current) },
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            DetailHeader(comic = current, pageCount = state.pageCount, onOpenComic = onOpenComic)
        }
        if (state.series.size > 1) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SeriesIssues(
                    series = state.series,
                    current = current,
                    nextUnread = state.nextUnread,
                    onShowComic = onShowComic,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            ComicSettingsSection(
                settings = state.settings,
                onSettingsChanged = viewModel::onSettingsChanged,
                onClearDetections = viewModel::onClearDetections,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        if (state.pageCount > 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeaderPadded(
                    eyebrow = stringResource(R.string.detail_pages_eyebrow),
                    title = stringResource(R.string.detail_pages, state.pageCount),
                )
            }
            items(count = state.pageCount, key = { "page-$it" }) { index ->
                PageThumb(
                    index = index,
                    current = index == current.pageIndex,
                    load = { viewModel.thumb(index) },
                    onClick = { onOpenComic(current.copy(pageIndex = index)) },
                )
            }
        }
    }
}

@Composable
private fun DetailToolbar(
    comic: LibraryComic,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleRead: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
        GhostAction(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.library_back), onClick = onBack)
        Spacer(modifier = Modifier.weight(1f))
        GhostAction(
            icon = if (comic.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = stringResource(if (comic.favorite) R.string.library_remove_favorite else R.string.library_add_favorite),
            onClick = onToggleFavorite,
            active = comic.favorite,
        )
        GhostAction(
            icon = Icons.Filled.Check,
            contentDescription = stringResource(if (comic.completed) R.string.library_mark_unread else R.string.library_mark_read),
            onClick = onToggleRead,
            active = comic.completed,
        )
    }
}

@Composable
private fun DetailHeader(comic: LibraryComic, pageCount: Int, onOpenComic: (LibraryComic) -> Unit) {
    val ambient = comic.ambientColor()
    val glow = lerp(Color.Black, ambient, HeroGlowMix)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(glow, HeroGround)))
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(modifier = Modifier.width(DetailCoverWidth).aspectRatio(2f / 3f).sharedCover(comic.id).clip(RoundedCornerShape(14.dp))) {
            CoverArt(comic = comic, showArtwork = true)
        }
        Column(modifier = Modifier.fillMaxHeight().weight(1f)) {
            if (comic.series != comic.title) {
                Text(
                    text = comic.series,
                    style = MaterialTheme.typography.labelMedium,
                    color = InkDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = comic.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (pageCount > 0) {
                Text(
                    text = stringResource(R.string.detail_pages, pageCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = InkFaint,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            DetailProgress(comic = comic, pageCount = pageCount)
            Spacer(modifier = Modifier.height(14.dp))
            PrimaryAction(icon = Icons.Filled.PlayArrow, label = stringResource(comic.callToActionRes()), onClick = { onOpenComic(comic.startingPoint()) })
        }
    }
}

private fun LibraryComic.callToActionRes(): Int = when {
    completed -> R.string.detail_read_again
    pageIndex > 0 -> R.string.detail_continue
    else -> R.string.detail_read
}

private fun LibraryComic.startingPoint(): LibraryComic = if (completed) copy(pageIndex = 0) else this

@Composable
private fun DetailProgress(comic: LibraryComic, pageCount: Int) {
    if (pageCount <= 0 || comic.pageIndex <= 0 || comic.completed) return
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

@Composable
private fun SeriesIssues(
    series: List<LibraryComic>,
    current: LibraryComic,
    nextUnread: LibraryComic?,
    onShowComic: (LibraryComic) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader(eyebrow = stringResource(R.string.detail_issues_eyebrow), title = stringResource(R.string.detail_issues, series.size))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items = series, key = { it.id }) { issue ->
                IssueCard(
                    issue = issue,
                    selected = issue.id == current.id,
                    nextUnread = issue.id == nextUnread?.id,
                    onClick = { onShowComic(issue) },
                )
            }
        }
    }
}

@Composable
private fun IssueCard(issue: LibraryComic, selected: Boolean, nextUnread: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.width(IssueCoverWidth).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .border(width = 2.dp, color = if (selected) Accent else Color.Transparent, shape = RoundedCornerShape(10.dp)),
        ) {
            CoverArt(comic = issue, showArtwork = true)
            if (issue.completed) CompletedBadge(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
        Text(
            text = issue.issueNumber?.let { "#$it" } ?: issue.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onBackground else InkDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (nextUnread) {
            Text(
                text = stringResource(R.string.detail_next_unread).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = AccentAmber,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ComicSettingsSection(
    settings: ComicSettings,
    onSettingsChanged: (ComicSettings) -> Unit,
    onClearDetections: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader(eyebrow = stringResource(R.string.detail_settings_eyebrow), title = stringResource(R.string.detail_settings))
        SettingRow(label = stringResource(R.string.detail_setting_direction)) {
            OptionChips(
                options = listOf(
                    null to R.string.detail_option_default,
                    ReadingDirection.LeftToRight to R.string.detail_option_ltr,
                    ReadingDirection.RightToLeft to R.string.detail_option_rtl,
                ),
                selected = settings.direction,
                onSelect = { onSettingsChanged(settings.copy(direction = it)) },
            )
        }
        SettingRow(label = stringResource(R.string.detail_setting_cover_alone)) {
            OptionChips(
                options = listOf(false to R.string.detail_option_off, true to R.string.detail_option_on),
                selected = settings.coverAlone,
                onSelect = { onSettingsChanged(settings.copy(coverAlone = it)) },
            )
        }
        SettingRow(label = stringResource(R.string.detail_setting_bubbles)) {
            TriStateChips(selected = settings.bubblesEnlarged, onSelect = { onSettingsChanged(settings.copy(bubblesEnlarged = it)) })
        }
        SettingRow(label = stringResource(R.string.detail_setting_guided)) {
            TriStateChips(selected = settings.guided, onSelect = { onSettingsChanged(settings.copy(guided = it)) })
        }
        ClearDetectionsRow(onClearDetections = onClearDetections)
    }
}

@Composable
private fun SettingRow(label: String, options: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        options()
    }
}

@Composable
private fun TriStateChips(selected: Boolean?, onSelect: (Boolean?) -> Unit) {
    OptionChips(
        options = listOf(null to R.string.detail_option_default, true to R.string.detail_option_on, false to R.string.detail_option_off),
        selected = selected,
        onSelect = onSelect,
    )
}

@Composable
private fun <T> OptionChips(options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, labelRes) ->
            val active = value == selected
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (active) Color.White else InkDim,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) Accent else Surface2)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ClearDetectionsRow(onClearDetections: () -> Unit) {
    var cleared by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClearDetections(); cleared = true }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.DeleteSweep,
            contentDescription = null,
            tint = if (cleared) Good else InkDim,
        )
        Text(
            text = stringResource(if (cleared) R.string.detail_detections_cleared else R.string.detail_clear_detections),
            style = MaterialTheme.typography.bodyMedium,
            color = if (cleared) Good else InkDim,
        )
    }
}

@Composable
private fun SectionHeaderPadded(eyebrow: String, title: String) {
    Box(modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)) {
        SectionHeader(eyebrow = eyebrow, title = title)
    }
}

@Composable
private fun PageThumb(index: Int, current: Boolean, load: suspend () -> ImageBitmap?, onClick: () -> Unit) {
    val thumb by produceState<ImageBitmap?>(initialValue = null, key1 = index) { value = load() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(8.dp))
            .background(Surface2)
            .border(width = 2.dp, color = if (current) Accent else Color.Transparent, shape = RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        thumb?.let {
            Image(bitmap = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
