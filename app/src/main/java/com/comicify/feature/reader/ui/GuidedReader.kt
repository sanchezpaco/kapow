package com.comicify.feature.reader.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.comicify.feature.reader.data.PageArt
import com.comicify.feature.reader.data.PageLoader
import com.comicify.feature.reader.domain.FullPagePanel
import com.comicify.feature.reader.domain.GuidedFocus
import kotlin.math.roundToInt

private const val LAST_PANEL = Int.MAX_VALUE / 2
private const val PANEL_PADDING = 0.02f
private const val PREVIOUS_ZONE = 0.28f
private const val NEXT_ZONE = 0.72f
private const val DOUBLE_TAP_ZOOM = 2f
private const val FOCUS_ANIMATION_MILLIS = 520

private data class FocusTarget(val view: Rect, val animated: Boolean)

@Composable
fun GuidedReader(
    loader: PageLoader,
    spread: Boolean,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onGuidedStop: (Int, Int) -> Unit,
    onTap: () -> Unit,
    onAmbient: (Color) -> Unit,
) {
    var page by remember { mutableIntStateOf(initialPage.coerceIn(0, (loader.pageCount - 1).coerceAtLeast(0))) }
    var panelIndex by remember { mutableIntStateOf(0) }
    var panels by remember { mutableStateOf(listOf(FullPagePanel)) }
    var arts by remember { mutableStateOf(emptyMap<Int, PageArt>()) }

    LaunchedEffect(page) {
        onPageChanged(page)
        arts = arts.filterKeys { it in spreadStart(page)..spreadStart(page) + 1 }
        val loaded = runCatching { loader.load(page) }.getOrNull()
        loaded?.let { arts = arts + (page to it); onAmbient(it.ambient) }
        val detected = runCatching { loader.panels(page) }.getOrDefault(listOf(FullPagePanel))
        val stops = if (detected.size > 1) listOf(FullPagePanel) + detected else detected
        panels = stops
        panelIndex = if (panelIndex == LAST_PANEL) stops.lastIndex else panelIndex.coerceIn(0, stops.lastIndex)
        loader.preload((page - 1)..(page + 2))
        if (spread) {
            val sibling = spreadStart(page) + 1 - page % 2
            if (sibling in 0 until loader.pageCount) runCatching { loader.load(sibling) }.getOrNull()?.let { arts = arts + (sibling to it) }
        }
    }

    fun goNext() {
        if (panelIndex < panels.lastIndex) {
            panelIndex++
        } else if (page < loader.pageCount - 1) {
            panelIndex = 0
            page++
        }
    }

    fun goPrevious() {
        if (panelIndex > 0) {
            panelIndex--
        } else if (page > 0) {
            panelIndex = LAST_PANEL
            page--
        }
    }

    LaunchedEffect(panelIndex, panels.size) { onGuidedStop(panelIndex, panels.size) }

    val panelView = GuidedFocus.frame(panels.getOrElse(panelIndex) { FullPagePanel }, PANEL_PADDING)
    val resetKey = page to panelIndex

    if (!spread) {
        GuidedPanel(arts[page], panelView, resetKey, ::goPrevious, ::goNext, onTap, Modifier.fillMaxSize())
        return
    }
    val leftPage = spreadStart(page)
    Row(modifier = Modifier.fillMaxSize()) {
        SpreadHalf(leftPage, page, arts[leftPage], panelView, resetKey, ::goPrevious, ::goNext, onTap)
        SpreadHalf(leftPage + 1, page, arts[leftPage + 1], panelView, resetKey, ::goPrevious, ::goNext, onTap)
    }
}

private fun spreadStart(page: Int) = page - page % 2

@Composable
private fun RowScope.SpreadHalf(
    index: Int,
    activePage: Int,
    art: PageArt?,
    panelView: Rect,
    resetKey: Any,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTap: () -> Unit,
) {
    val modifier = Modifier.weight(1f).fillMaxSize()
    if (index == activePage) {
        GuidedPanel(art, panelView, resetKey, onPrevious, onNext, onTap, modifier)
        return
    }
    val image = art?.image
    Box(
        modifier = modifier.pointerInput(index, activePage) {
            detectTapGestures { if (index < activePage) onPrevious() else onNext() }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) GuidedPage(image, FocusTarget(FullPagePanel, animated = false))
    }
}

@Composable
private fun GuidedPanel(
    art: PageArt?,
    panelView: Rect,
    resetKey: Any,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier,
) {
    var zoom by remember(resetKey) { mutableStateOf<FocusTarget?>(null) }
    val currentPanelView by rememberUpdatedState(panelView)
    val currentArt by rememberUpdatedState(art)
    val target = zoom ?: FocusTarget(panelView, animated = true)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        when {
                            offset.x < size.width * PREVIOUS_ZONE -> onPrevious()
                            offset.x > size.width * NEXT_ZONE -> onNext()
                            else -> onTap()
                        }
                    },
                    onDoubleTap = { offset ->
                        val image = currentArt?.image ?: return@detectTapGestures
                        zoom = if (zoom != null) null else FocusTarget(zoomAround(currentPanelView, image, size, offset), animated = true)
                    },
                )
            }
            .pointerInput(zoom != null) {
                if (zoom == null) return@pointerInput
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val image = currentArt?.image ?: return@detectDragGestures
                    val current = zoom ?: return@detectDragGestures
                    zoom = FocusTarget(pan(current.view, image, size, dragAmount), animated = false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val image = art?.image
        if (image == null) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
            GuidedPage(image, target)
        }
    }
}

private fun zoomAround(view: Rect, image: ImageBitmap, canvas: IntSize, position: Offset): Rect {
    val drawn = GuidedFocus.fit(view, image.size(), canvas.toSize())
    return GuidedFocus.zoomed(view, drawn, image.size(), canvas.toSize(), GuidedFocus.toPage(view, drawn, position), DOUBLE_TAP_ZOOM)
}

private fun pan(view: Rect, image: ImageBitmap, canvas: IntSize, drag: Offset): Rect {
    val drawn = GuidedFocus.fit(view, image.size(), canvas.toSize())
    return GuidedFocus.panned(view, GuidedFocus.toPageDelta(view, drawn, drag))
}

private fun ImageBitmap.size() = Size(width.toFloat(), height.toFloat())

private fun IntSize.toSize() = Size(width.toFloat(), height.toFloat())

@Composable
private fun GuidedPage(image: ImageBitmap, target: FocusTarget) {
    val view = remember { Animatable(target.view, Rect.VectorConverter) }
    LaunchedEffect(target) {
        if (target.animated) {
            view.animateTo(target.view, tween(FOCUS_ANIMATION_MILLIS, easing = FastOutSlowInEasing))
        } else {
            view.snapTo(target.view)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val source = view.value
        val drawn = GuidedFocus.fit(source, image.size(), size)
        drawImage(
            image = image,
            srcOffset = IntOffset((source.left * image.width).roundToInt(), (source.top * image.height).roundToInt()),
            srcSize = IntSize((source.width * image.width).roundToInt(), (source.height * image.height).roundToInt()),
            dstOffset = IntOffset(drawn.left.roundToInt(), drawn.top.roundToInt()),
            dstSize = IntSize(drawn.width.roundToInt(), drawn.height.roundToInt()),
            filterQuality = FilterQuality.High,
        )
    }
}
