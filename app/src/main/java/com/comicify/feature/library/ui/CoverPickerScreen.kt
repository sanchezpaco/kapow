package com.comicify.feature.library.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comicify.R
import com.comicify.core.ui.KapowSnackbarHost
import com.comicify.core.ui.SectionHeader
import com.comicify.feature.library.domain.FIRST_PAGE
import com.comicify.feature.library.domain.LibraryComic
import kotlinx.coroutines.launch

private val ScreenPadding = 20.dp
private val CellGap = 18.dp
private val HeaderGap = 14.dp
private val CurrentBadgeSize = 30.dp
private val CurrentBadgeIcon = 18.dp
private val CurrentBorder = 2.dp

@Composable
fun CoverPickerScreen(comic: LibraryComic, onBack: () -> Unit) {
    val viewModel: CoverPickerViewModel = hiltViewModel()
    DisposableEffect(comic.documentUri) {
        viewModel.open(comic.documentUri)
        onDispose { viewModel.close() }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val failedMessage = stringResource(R.string.cover_picker_failed)
    val warnOnFailure: (Boolean) -> Unit = { succeeded ->
        if (!succeeded) scope.launch { snackbarHost.showSnackbar(failedMessage) }
    }
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { KapowSnackbarHost(snackbarHost) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = gridMinCell()),
            modifier = Modifier.fillMaxSize().padding(padding).statusBarsPadding(),
            contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(CellGap),
            verticalArrangement = Arrangement.spacedBy(CellGap),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PickerHeader(
                    comic = comic,
                    onBack = onBack,
                    onReset = { viewModel.choose(comic.id, FIRST_PAGE, warnOnFailure) },
                )
            }
            if (state.unreadable) {
                item(span = { GridItemSpan(maxLineSpan) }) { UnreadableComic() }
            }
            items(state.pageCount) { index ->
                PageCell(
                    index = index,
                    current = index == comic.coverPage,
                    thumbnail = viewModel::thumbnail,
                    onClick = {
                        viewModel.choose(comic.id, index) { succeeded ->
                            warnOnFailure(succeeded)
                            if (succeeded) onBack()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PickerHeader(comic: LibraryComic, onBack: () -> Unit, onReset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(HeaderGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.library_back),
                onClick = onBack,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (comic.coverPage != FIRST_PAGE) {
                TextButton(onClick = onReset) {
                    Text(text = stringResource(R.string.cover_picker_reset), color = Accent)
                }
            }
        }
        SectionHeader(
            eyebrow = stringResource(R.string.cover_picker_eyebrow),
            title = stringResource(R.string.cover_picker_title),
        )
        Text(
            text = comic.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UnreadableComic() {
    Text(
        text = stringResource(R.string.reader_error_read_failure),
        style = MaterialTheme.typography.bodyMedium,
        color = InkDim,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = ScreenPadding),
    )
}

@Composable
private fun PageCell(
    index: Int,
    current: Boolean,
    thumbnail: suspend (Int) -> ImageBitmap?,
    onClick: () -> Unit,
) {
    var page by remember(index) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(index) { page = thumbnail(index) }
    val label = stringResource(R.string.cover_picker_page, index + 1)

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(CardShape)
                .background(Surface2)
                .border(CurrentBorder, if (current) Accent else Color.Transparent, CardShape)
                .clickable(onClickLabel = label, onClick = onClick),
        ) {
            page?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (current) {
                CurrentCoverBadge(modifier = Modifier.align(Alignment.BottomEnd).padding(9.dp))
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = InkFaint,
            maxLines = 1,
        )
    }
}

@Composable
private fun CurrentCoverBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(CurrentBadgeSize).clip(CircleShape).background(Accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = stringResource(R.string.cover_picker_current),
            tint = Color.White,
            modifier = Modifier.size(CurrentBadgeIcon),
        )
    }
}
