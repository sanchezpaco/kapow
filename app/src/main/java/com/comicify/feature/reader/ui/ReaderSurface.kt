package com.comicify.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comicify.R
import com.comicify.core.window.ReadingPosture
import com.comicify.domain.model.ReadingDirection
import com.comicify.feature.reader.data.PageLoader
import com.comicify.feature.reader.domain.PageOrder
import kotlinx.coroutines.launch

@Composable
fun ReaderSurface(
    loader: PageLoader,
    posture: ReadingPosture,
    guided: Boolean,
    guidedFullScreen: Boolean,
    direction: ReadingDirection,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onTap: () -> Unit,
    onAmbient: (Color) -> Unit,
) {
    if (guided) {
        val spread = posture == ReadingPosture.UnfoldedSpread && !guidedFullScreen
        key(spread) { GuidedReader(loader, spread, direction, initialPage, onPageChanged, onTap, onAmbient) }
        return
    }
    key(posture, direction) {
        when (posture) {
            ReadingPosture.UnfoldedSpread -> SpreadReader(loader, direction, initialPage, onPageChanged, onTap, onAmbient)
            ReadingPosture.Tabletop -> TabletopReader(loader, direction, initialPage, onPageChanged, onTap, onAmbient)
            else -> SinglePageReader(loader, direction, initialPage, onPageChanged, onTap, onAmbient)
        }
    }
}

@Composable
private fun SinglePageReader(
    loader: PageLoader,
    direction: ReadingDirection,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onTap: () -> Unit,
    onAmbient: (Color) -> Unit,
) {
    val pageCount = loader.pageCount
    val pagerState = rememberPagerState(
        initialPage = PageOrder.pagerIndex(direction, initialPage.coerceIn(0, lastPage(loader)), pageCount),
    ) {
        pageCount
    }
    var zoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        val logicalPage = PageOrder.logicalIndex(direction, pagerState.currentPage, pageCount)
        onPageChanged(logicalPage)
        loader.preload((logicalPage - 1)..(logicalPage + 2))
        runCatching { loader.load(logicalPage) }.getOrNull()?.let { onAmbient(it.ambient) }
    }

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = !zoomed,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize(),
    ) { physicalPage ->
        ZoomablePage(
            loader = loader,
            index = PageOrder.logicalIndex(direction, physicalPage, pageCount),
            onTap = onTap,
            onZoomedChange = { if (physicalPage == pagerState.currentPage) zoomed = it },
        )
    }
}

@Composable
private fun SpreadReader(
    loader: PageLoader,
    direction: ReadingDirection,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onTap: () -> Unit,
    onAmbient: (Color) -> Unit,
) {
    val spreadCount = (loader.pageCount + 1) / 2
    val initialSpread = (initialPage / 2).coerceIn(0, spreadCount - 1)
    val pagerState = rememberPagerState(
        initialPage = PageOrder.pagerIndex(direction, initialSpread, spreadCount),
    ) {
        spreadCount
    }
    var zoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        val firstPage = PageOrder.logicalIndex(direction, pagerState.currentPage, spreadCount) * 2
        onPageChanged(firstPage)
        loader.preload((firstPage - 1)..(firstPage + 3))
        runCatching { loader.load(firstPage) }.getOrNull()?.let { onAmbient(it.ambient) }
    }

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = !zoomed,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize(),
    ) { spread ->
        val firstPage = PageOrder.logicalIndex(direction, spread, spreadCount) * 2
        val secondPage = firstPage + 1
        val screenLeftPage = PageOrder.leftPage(direction, firstPage, secondPage)
        val screenRightPage = PageOrder.rightPage(direction, firstPage, secondPage)
        Row(modifier = Modifier.fillMaxSize()) {
            if (screenLeftPage < loader.pageCount) {
                ZoomablePage(
                    loader = loader,
                    index = screenLeftPage,
                    onTap = onTap,
                    onZoomedChange = { if (spread == pagerState.currentPage) zoomed = it },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            if (screenRightPage < loader.pageCount) {
                ZoomablePage(
                    loader = loader,
                    index = screenRightPage,
                    onTap = onTap,
                    onZoomedChange = { if (spread == pagerState.currentPage) zoomed = it },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TabletopReader(
    loader: PageLoader,
    direction: ReadingDirection,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onTap: () -> Unit,
    onAmbient: (Color) -> Unit,
) {
    val pageCount = loader.pageCount
    val pagerState = rememberPagerState(
        initialPage = PageOrder.pagerIndex(direction, initialPage.coerceIn(0, lastPage(loader)), pageCount),
    ) {
        pageCount
    }
    var zoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        val logicalPage = PageOrder.logicalIndex(direction, pagerState.currentPage, pageCount)
        onPageChanged(logicalPage)
        loader.preload((logicalPage - 1)..(logicalPage + 2))
        runCatching { loader.load(logicalPage) }.getOrNull()?.let { onAmbient(it.ambient) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 1,
            modifier = Modifier.weight(0.62f).fillMaxWidth(),
        ) { physicalPage ->
            ZoomablePage(
                loader = loader,
                index = PageOrder.logicalIndex(direction, physicalPage, pageCount),
                onTap = onTap,
                onZoomedChange = { if (physicalPage == pagerState.currentPage) zoomed = it },
            )
        }
        TabletopControls(
            pagerState = pagerState,
            pageCount = pageCount,
            direction = direction,
            modifier = Modifier.weight(0.38f).fillMaxWidth(),
        )
    }
}

@Composable
private fun TabletopControls(
    pagerState: PagerState,
    pageCount: Int,
    direction: ReadingDirection,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val step = PageOrder.step(direction)
    val logicalPage = PageOrder.logicalIndex(direction, pagerState.currentPage, pageCount)
    Column(
        modifier = modifier.background(Color.Black.copy(alpha = 0.4f)).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProgressBar(
            progress = readingProgress(logicalPage, pageCount),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - step).coerceIn(0, pageCount - 1)) }
            }) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = stringResource(R.string.reader_action_previous_page),
                    tint = Color.White,
                )
            }
            PageCounter(current = logicalPage, total = pageCount)
            IconButton(onClick = {
                scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + step).coerceIn(0, pageCount - 1)) }
            }) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = stringResource(R.string.reader_action_next_page),
                    tint = Color.White,
                )
            }
        }
    }
}

private fun lastPage(loader: PageLoader): Int = (loader.pageCount - 1).coerceAtLeast(0)
