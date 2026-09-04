package com.comicify.feature.reader.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.comicify.R
import com.comicify.core.input.PageTurnDirection
import com.comicify.feature.reader.data.PageArt
import com.comicify.feature.reader.data.PageLoader
import com.comicify.feature.reader.domain.StripChain
import com.comicify.feature.reader.domain.StripItem
import com.comicify.feature.reader.domain.StripLink
import kotlinx.coroutines.flow.Flow

private const val PORTRAIT_PAGE_ASPECT = 2f / 3f
private const val PRELOAD_BEHIND = 1
private const val PRELOAD_AHEAD = 2
private const val OPEN_NEXT_ISSUE_WITHIN = 3
private const val BOUNDARY_HEIGHT_FRACTION = 0.45f

data class StripComic(val id: Long, val uri: Uri, val title: String)

private class ChainLink(val comic: StripComic?, val loader: PageLoader, val aspects: List<Float>)

@Composable
fun VerticalStripReader(
    loader: PageLoader,
    comic: StripComic?,
    initialPage: Int,
    pageTurnRequests: Flow<PageTurnDirection>,
    pendingJump: Int?,
    onJumpApplied: () -> Unit,
    onActiveChanged: (StripComic?, PageLoader, Int, Int) -> Unit,
    nextInSeries: (StripComic) -> StripComic?,
    openIssue: suspend (StripComic) -> PageLoader?,
    onTap: () -> Unit,
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
        active.loader.preload(activePage, (activePage - PRELOAD_BEHIND)..(activePage + PRELOAD_AHEAD), null, panels = false)
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
        pageTurnRequests.collect { turn ->
            val step = if (turn == PageTurnDirection.Next) 1 else -1
            listState.animateScrollToItem((listState.firstVisibleItemIndex + step).coerceIn(0, items.lastIndex))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onTap() } },
    ) {
        items(items = items, key = { it.key() }) { item ->
            when (item) {
                is StripItem.Page -> StripPage(chain[item.link].loader, item)
                is StripItem.Boundary -> StripBoundary(item)
            }
        }
    }
}

private suspend fun PageLoader.measuredAspects(): List<Float> =
    runCatching { aspects() }.getOrDefault(List(pageCount) { PORTRAIT_PAGE_ASPECT })

private fun StripItem.key(): Any = when (this) {
    is StripItem.Page -> "page-$link-$page"
    is StripItem.Boundary -> "boundary-$link"
}

@Composable
private fun StripPage(loader: PageLoader, item: StripItem.Page) {
    val art by produceState<PageArt?>(initialValue = null, loader, item.page) {
        value = runCatching { loader.load(item.page) }.getOrNull()
    }
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(item.aspect)) {
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
