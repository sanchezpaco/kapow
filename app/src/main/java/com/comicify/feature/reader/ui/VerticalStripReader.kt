package com.comicify.feature.reader.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.comicify.R
import com.comicify.core.input.PageTurnDirection
import com.comicify.feature.reader.data.PageArt
import com.comicify.feature.reader.data.PageLoader
import com.comicify.feature.reader.data.PaintedBubble
import com.comicify.feature.reader.domain.PanSlop
import com.comicify.feature.reader.domain.StripChain
import com.comicify.feature.reader.domain.StripItem
import com.comicify.feature.reader.domain.StripLink
import kotlinx.coroutines.flow.Flow

private const val PORTRAIT_PAGE_ASPECT = 2f / 3f
private const val PRELOAD_BEHIND = 1
private const val PRELOAD_AHEAD = 2
private const val OPEN_NEXT_ISSUE_WITHIN = 3
private const val BOUNDARY_HEIGHT_FRACTION = 0.45f
private const val MAX_STRIP_SCALE = 4f
private const val DOUBLE_TAP_STRIP_SCALE = 2f

data class StripComic(val id: Long, val uri: Uri, val title: String)

private class ChainLink(val comic: StripComic?, val loader: PageLoader, val aspects: List<Float>)

@Composable
fun VerticalStripReader(
    loader: PageLoader,
    comic: StripComic?,
    bubbleScale: Float?,
    initialPage: Int,
    pageTurnRequests: Flow<PageTurnDirection>,
    pendingJump: Int?,
    onJumpApplied: () -> Unit,
    onActiveChanged: (StripComic?, PageLoader, Int, Int) -> Unit,
    nextInSeries: (StripComic) -> StripComic?,
    openIssue: suspend (StripComic) -> PageLoader?,
    onTap: () -> Unit,
    onScrolled: () -> Unit,
    onAmbient: (Color) -> Unit,
) {
    val chain = remember(loader) { mutableStateListOf<ChainLink>() }
    LaunchedEffect(loader) {
        if (chain.isEmpty()) chain.add(ChainLink(comic, loader, loader.measuredAspects()))
    }
    if (chain.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val items by remember {
        derivedStateOf { StripChain.items(chain.map { StripLink(it.comic?.title.orEmpty(), it.aspects) }) }
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialPage.coerceIn(0, chain.first().aspects.lastIndex.coerceAtLeast(0)),
    )
    val position by remember {
        derivedStateOf {
            val laidOut = listState.layoutInfo.visibleItemsInfo.isNotEmpty()
            val index = if (laidOut && !listState.canScrollForward) items.lastIndex else listState.firstVisibleItemIndex
            items.getOrNull(index)
        }
    }
    val activeLink = position?.link ?: 0
    val activePage = (position as? StripItem.Page)?.page ?: chain[activeLink].aspects.lastIndex

    val currentOnActiveChanged by rememberUpdatedState(onActiveChanged)
    LaunchedEffect(activeLink, activePage) {
        val active = chain[activeLink]
        currentOnActiveChanged(active.comic, active.loader, activePage, active.aspects.size)
        active.loader.preload(activePage, (activePage - PRELOAD_BEHIND)..(activePage + PRELOAD_AHEAD), bubbleScale, panels = false)
        runCatching { active.loader.load(activePage) }.getOrNull()?.let { onAmbient(it.ambient) }
    }

    LaunchedEffect(activeLink) {
        chain.take(activeLink).forEach { it.loader.release() }
    }

    LaunchedEffect(activeLink, activePage, chain.size) {
        if (activeLink != chain.lastIndex) return@LaunchedEffect
        val tail = chain.last()
        if (activePage < tail.aspects.size - OPEN_NEXT_ISSUE_WITHIN) return@LaunchedEffect
        val following = tail.comic?.let(nextInSeries) ?: return@LaunchedEffect
        val opened = openIssue(following) ?: return@LaunchedEffect
        chain.add(ChainLink(following, opened, opened.measuredAspects()))
    }

    LaunchedEffect(pendingJump) {
        val target = pendingJump ?: return@LaunchedEffect
        val index = StripChain.indexOfPage(items, activeLink, target)
        if (index >= 0) listState.scrollToItem(index)
        onJumpApplied()
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling -> if (scrolling) onScrolled() }
    }

    LaunchedEffect(listState) {
        pageTurnRequests.collect { turn ->
            val step = if (turn == PageTurnDirection.Next) 1 else -1
            listState.animateScrollToItem((listState.firstVisibleItemIndex + step).coerceIn(0, items.lastIndex))
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    val zoomed by remember { derivedStateOf { scale > 1f } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            panX = 0f
                        } else {
                            scale = DOUBLE_TAP_STRIP_SCALE
                            panX = clampPan((size.width / 2f - tap.x) * (DOUBLE_TAP_STRIP_SCALE - 1f), size.width, DOUBLE_TAP_STRIP_SCALE)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var panning = false
                    var slop = PanSlop()
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        val pan = event.calculatePan()
                        if (pressed >= 2) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, MAX_STRIP_SCALE)
                            panX = if (scale > 1f) clampPan(panX + pan.x, size.width, scale) else 0f
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (scale > 1f) {
                            if (!panning) {
                                slop = slop.plus(pan)
                                panning = slop.exceeds(viewConfiguration.touchSlop) && slop.isHorizontal()
                            }
                            if (panning) {
                                panX = clampPan(panX + pan.x, size.width, scale)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(if (zoomed) Modifier.clipToBounds().zoomLayer({ scale }, { panX }) else Modifier),
        ) {
            items(items = items, key = { it.key() }) { item ->
                when (item) {
                    is StripItem.Page -> StripPage(chain[item.link].loader, item, bubbleScale)
                    is StripItem.Boundary -> StripBoundary(item)
                }
            }
        }
    }
}

private fun Modifier.zoomLayer(scale: () -> Float, panX: () -> Float): Modifier = graphicsLayer {
    scaleX = scale()
    scaleY = scale()
    translationX = panX()
}

private fun clampPan(pan: Float, width: Int, scale: Float): Float {
    val limit = (scale - 1f) * width / 2f
    return pan.coerceIn(-limit, limit)
}

private suspend fun PageLoader.measuredAspects(): List<Float> =
    runCatching { aspects() }.getOrDefault(List(pageCount) { PORTRAIT_PAGE_ASPECT })

private fun StripItem.key(): Any = when (this) {
    is StripItem.Page -> "page-$link-$page"
    is StripItem.Boundary -> "boundary-$link"
}

@Composable
private fun StripPage(loader: PageLoader, item: StripItem.Page, bubbleScale: Float?) {
    val art by produceState<PageArt?>(initialValue = null, loader, item.page) {
        value = runCatching { loader.load(item.page) }.getOrNull()
    }
    val bubbles by produceState(initialValue = emptyList<PaintedBubble>(), loader, item.page, bubbleScale) {
        value = if (bubbleScale == null) emptyList()
        else runCatching { loader.overlay(item.page, bubbleScale) }.getOrDefault(emptyList())
    }
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(item.aspect)) {
        art?.let { page ->
            Image(
                bitmap = page.image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (bubbles.isNotEmpty()) BubbleLayer(page.image, bubbles, cached = { true })
        }
    }
}

@Composable
private fun StripBoundary(item: StripItem.Boundary) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxWidth * BOUNDARY_HEIGHT_FRACTION)
                .background(Color.Black)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.reader_strip_finished, item.finished),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.reader_strip_next, item.next),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}
