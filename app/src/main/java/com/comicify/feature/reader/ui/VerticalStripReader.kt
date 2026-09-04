package com.comicify.feature.reader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import com.comicify.core.input.PageTurnDirection
import com.comicify.feature.reader.data.PageArt
import com.comicify.feature.reader.data.PageLoader
import kotlinx.coroutines.flow.Flow

private const val PORTRAIT_PAGE_ASPECT = 2f / 3f
private const val PRELOAD_BEHIND = 1
private const val PRELOAD_AHEAD = 2

@Composable
fun VerticalStripReader(
    loader: PageLoader,
    initialPage: Int,
    pageTurnRequests: Flow<PageTurnDirection>,
    pendingJump: Int?,
    onJumpApplied: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onTap: () -> Unit,
    onAmbient: (Color) -> Unit,
) {
    val aspects by produceState<List<Float>?>(initialValue = null, loader) {
        value = runCatching { loader.aspects() }.getOrDefault(List(loader.pageCount) { PORTRAIT_PAGE_ASPECT })
    }
    val measured = aspects ?: run {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val lastPage = (measured.size - 1).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage.coerceIn(0, lastPage))
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    LaunchedEffect(currentPage) {
        onPageChanged(currentPage)
        loader.preload(currentPage, (currentPage - PRELOAD_BEHIND)..(currentPage + PRELOAD_AHEAD), null, panels = false)
        runCatching { loader.load(currentPage) }.getOrNull()?.let { onAmbient(it.ambient) }
    }

    LaunchedEffect(pendingJump) {
        val target = pendingJump ?: return@LaunchedEffect
        listState.scrollToItem(target.coerceIn(0, lastPage))
        onJumpApplied()
    }

    LaunchedEffect(listState) {
        pageTurnRequests.collect { turn ->
            val step = if (turn == PageTurnDirection.Next) 1 else -1
            listState.animateScrollToItem((listState.firstVisibleItemIndex + step).coerceIn(0, lastPage))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onTap() } },
    ) {
        itemsIndexed(items = measured, key = { index, _ -> index }) { index, aspect ->
            StripPage(loader = loader, index = index, aspect = aspect)
        }
    }
}

@Composable
private fun StripPage(loader: PageLoader, index: Int, aspect: Float) {
    val art by produceState<PageArt?>(initialValue = null, loader, index) {
        value = runCatching { loader.load(index) }.getOrNull()
    }
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(aspect)) {
        art?.let {
            Image(
                bitmap = it.image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
