package com.comicify.feature.reader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.comicify.R
import com.comicify.feature.reader.data.PageLoader
import com.comicify.feature.reader.domain.ThumbnailStrip
import kotlinx.coroutines.flow.first

private val ThumbWidth = 48.dp
private val ThumbHeight = 66.dp
private val ThumbSpacing = 6.dp
private val ThumbCorner = RoundedCornerShape(4.dp)

@Composable
fun ThumbnailScrubber(
    loader: PageLoader,
    visible: Boolean,
    currentPage: Int,
    pageCount: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val itemWidthPx = with(LocalDensity.current) { (ThumbWidth + ThumbSpacing).roundToPx() }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        val viewport = snapshotFlow { listState.layoutInfo.viewportSize.width }.first { it > 0 }
        listState.scrollToItem(
            index = currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
            scrollOffset = ThumbnailStrip.centerScrollOffsetPx(viewport, itemWidthPx),
        )
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth().height(ThumbHeight),
        horizontalArrangement = Arrangement.spacedBy(ThumbSpacing),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(count = pageCount, key = { it }) { index ->
            ThumbnailCell(
                loader = loader,
                index = index,
                visible = visible,
                selected = index == currentPage,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun ThumbnailCell(
    loader: PageLoader,
    index: Int,
    visible: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var thumb by remember(index) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(index, visible) {
        if (visible && thumb == null) thumb = runCatching { loader.loadThumb(index) }.getOrNull()
    }

    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val description = stringResource(R.string.reader_thumbnail_go_to_page, index + 1)

    Box(
        modifier = Modifier
            .width(ThumbWidth)
            .height(ThumbHeight)
            .clip(ThumbCorner)
            .background(Color.White.copy(alpha = 0.08f))
            .border(2.dp, borderColor, ThumbCorner)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        val bitmap = thumb
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = if (selected) 1f else 0.55f,
                modifier = Modifier.fillMaxSize().clip(ThumbCorner),
            )
        }
    }
}
