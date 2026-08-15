package com.comicify.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comicify.R
import com.comicify.core.input.PageTurnDirection
import com.comicify.core.window.HingeOcclusion
import com.comicify.core.window.ReadingPosture
import com.comicify.core.window.splitAtHinge
import com.comicify.feature.reader.data.PageLoader
import com.comicify.feature.reader.domain.PageTurn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private const val PAGE_TURN_CAMERA_DISTANCE_DP = 12f

@Composable
fun ReaderSurface(
    loader: PageLoader,
    posture: ReadingPosture,
    hinge: HingeOcclusion?,
    guided: Boolean,
    guidedFullScreen: Boolean,
    initialPage: Int,
    pageTurnRequests: Flow<PageTurnDirection>,
    onPageChanged: (Int) -> Unit,
    onTap: () -> Unit,
    onAmbient: (Color) -> Unit,
) {
    if (guided) {
        val spread = posture == ReadingPosture.UnfoldedSpread && !guidedFullScreen
        key(spread) { GuidedReader(loader, spread, initialPage, pageTurnRequests, onPageChanged, onTap, onAmbient) }
        return
    }
    key(posture) {
        when (posture) {
            ReadingPosture.UnfoldedSpread ->
                SpreadReader(loader, initialPage, pageTurnRequests, onPageChanged, onTap, onAmbient)
            ReadingPosture.Tabletop ->
                TabletopReader(loader, hinge, initialPage, pageTurnRequests, onPageChanged, onTap, onAmbient)
            else -> SinglePageReader(loader, initialPage, pageTurnRequests, onPageChanged, onTap, onAmbient)
        }
    }
}

private fun targetPage(current: Int, lastIndex: Int, direction: PageTurnDirection): Int = when (direction) {
    PageTurnDirection.Next -> (current + 1).coerceAtMost(lastIndex)
    PageTurnDirection.Previous -> (current - 1).coerceAtLeast(0)
}

@Composable
private fun SinglePageReader(
    loader: PageLoader,
    initialPage: Int,
    pageTurnRequests: Flow<PageTurnDirection>,
    onPageChanged: (Int) -> Unit,
    onTap: () -> Unit,
    onAmbient: (Color) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, lastPage(loader))) {
        loader.pageCount
    }
    var zoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
        loader.preload((pagerState.currentPage - 1)..(pagerState.currentPage + 2))
        runCatching { loader.load(pagerState.currentPage) }.getOrNull()?.let { onAmbient(it.ambient) }
    }

    LaunchedEffect(pagerState) {
        pageTurnRequests.collect { direction ->
            pagerState.animateScrollToPage(targetPage(pagerState.currentPage, lastPage(loader), direction))
        }
    }

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = !zoomed,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        ZoomablePage(
            loader = loader,
            index = page,
            onTap = onTap,
            onZoomedChange = { if (page == pagerState.currentPage) zoomed = it },
            modifier = Modifier.pageTurnDepth(pagerState, page),
        )
    }
}

private fun Modifier.pageTurnDepth(pagerState: PagerState, page: Int): Modifier =
    graphicsLayer {
        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
        val transform = PageTurn.transform(pageOffset)
        cameraDistance = PAGE_TURN_CAMERA_DISTANCE_DP * density
        rotationY = transform.rotationY
        scaleX = transform.scale
        scaleY = transform.scale
        alpha = transform.alpha
        translationX = transform.translationFraction * size.width
    }

@Composable
private fun SpreadReader(
    loader: PageLoader,
    initialPage: Int,
    pageTurnRequests: Flow<PageTurnDirection>,
    onPageChanged: (Int) -> Unit,
    onTap: () -> Unit,
    onAmbient: (Color) -> Unit,
) {
    val spreadCount = (loader.pageCount + 1) / 2
    val pagerState = rememberPagerState(initialPage = (initialPage / 2).coerceIn(0, spreadCount - 1)) {
        spreadCount
    }
    var zoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        val leftPage = pagerState.currentPage * 2
        onPageChanged(leftPage)
        loader.preload((leftPage - 1)..(leftPage + 3))
        runCatching { loader.load(leftPage) }.getOrNull()?.let { onAmbient(it.ambient) }
    }

    LaunchedEffect(pagerState) {
        pageTurnRequests.collect { direction ->
            pagerState.animateScrollToPage(targetPage(pagerState.currentPage, spreadCount - 1, direction))
        }
    }

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = !zoomed,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize(),
    ) { spread ->
        val leftPage = spread * 2
        val rightPage = leftPage + 1
        Row(modifier = Modifier.fillMaxSize()) {
            ZoomablePage(
                loader = loader,
                index = leftPage,
                onTap = onTap,
                onZoomedChange = { if (spread == pagerState.currentPage) zoomed = it },
                modifier = Modifier.weight(1f),
            )
            if (rightPage < loader.pageCount) {
                ZoomablePage(
                    loader = loader,
                    index = rightPage,
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
    hinge: HingeOcclusion?,
    initialPage: Int,
    pageTurnRequests: Flow<PageTurnDirection>,
    onPageChanged: (Int) -> Unit,
    onTap: () -> Unit,
    onAmbient: (Color) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, lastPage(loader))) {
        loader.pageCount
    }
    var zoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
        loader.preload((pagerState.currentPage - 1)..(pagerState.currentPage + 2))
        runCatching { loader.load(pagerState.currentPage) }.getOrNull()?.let { onAmbient(it.ambient) }
    }

    LaunchedEffect(pagerState) {
        pageTurnRequests.collect { direction ->
            pagerState.animateScrollToPage(targetPage(pagerState.currentPage, lastPage(loader), direction))
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val split = splitAtHinge(containerHeight = maxHeight, hinge = hinge)
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !zoomed,
                beyondViewportPageCount = 1,
                modifier = Modifier.height(split.pageHeight).fillMaxWidth(),
            ) { page ->
                ZoomablePage(
                    loader = loader,
                    index = page,
                    onTap = onTap,
                    onZoomedChange = { if (page == pagerState.currentPage) zoomed = it },
                )
            }
            if (split.hingeHeight > 0.dp) {
                Spacer(modifier = Modifier.height(split.hingeHeight).fillMaxWidth())
            }
            TabletopControls(
                pagerState = pagerState,
                pageCount = loader.pageCount,
                modifier = Modifier.height(split.controlsHeight).fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TabletopControls(
    pagerState: PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier.background(Color.Black.copy(alpha = 0.4f)).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProgressBar(
            progress = readingProgress(pagerState.currentPage, pageCount),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) }
            }) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = stringResource(R.string.reader_action_previous_page),
                    tint = Color.White,
                )
            }
            PageCounter(current = pagerState.currentPage, total = pageCount)
            IconButton(onClick = {
                scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(pageCount - 1)) }
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
