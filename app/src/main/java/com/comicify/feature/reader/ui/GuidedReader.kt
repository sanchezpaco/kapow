package com.comicify.feature.reader.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.comicify.core.input.PageTurnDirection
import com.comicify.domain.model.ReadingDirection
import com.comicify.feature.reader.data.PageArt
import com.comicify.feature.reader.data.PageLoader
import com.comicify.feature.reader.domain.FullPagePanel
import com.comicify.feature.reader.domain.GuidedFocus
import com.comicify.feature.reader.domain.PageOrder
import com.comicify.feature.reader.domain.TapZone
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow

private const val LAST_PANEL = Int.MAX_VALUE / 2
private const val PANEL_PADDING = 0.02f
private const val PREVIOUS_ZONE = 0.28f
private const val NEXT_ZONE = 0.72f
private const val DOUBLE_TAP_ZOOM = 2f
private const val MAX_ZOOM = 4f
private const val FOCUS_ANIMATION_MILLIS = 520
private const val ZOOM_ANIMATION_MILLIS = 260
private const val PANEL_EXIT_EPSILON = 0.001f
private const val OVERSCROLL_RESISTANCE = 0.32f
private const val MAX_OVERSCROLL_PX = 220f
private const val BOUNCE_VELOCITY_FRACTION = 0.55f
private const val MAX_BOUNCE_VELOCITY_PX = 2400f

@Composable
fun GuidedReader(
    loader: PageLoader,
    spread: Boolean,
    direction: ReadingDirection,
    initialPage: Int,
    pageTurnRequests: Flow<PageTurnDirection>,
    onPageChanged: (Int) -> Unit,
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
        panels = detected
        panelIndex = if (panelIndex == LAST_PANEL) detected.lastIndex else panelIndex.coerceIn(0, detected.lastIndex)
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

    LaunchedEffect(pageTurnRequests) {
        pageTurnRequests.collect { direction ->
            when (direction) {
                PageTurnDirection.Next -> goNext()
                PageTurnDirection.Previous -> goPrevious()
            }
        }
    }

    val panelView = GuidedFocus.frame(panels.getOrElse(panelIndex) { FullPagePanel }, PANEL_PADDING)
    val resetKey = page to panelIndex

    if (!spread) {
        GuidedPanel(arts[page], panelView, resetKey, direction, ::goPrevious, ::goNext, onTap, Modifier.fillMaxSize())
        return
    }
    val firstPage = spreadStart(page)
    val secondPage = firstPage + 1
    val screenLeftPage = PageOrder.leftPage(direction, firstPage, secondPage)
    val screenRightPage = PageOrder.rightPage(direction, firstPage, secondPage)
    Row(modifier = Modifier.fillMaxSize()) {
        SpreadHalf(screenLeftPage, page, arts[screenLeftPage], panelView, resetKey, direction, ::goPrevious, ::goNext, onTap)
        SpreadHalf(screenRightPage, page, arts[screenRightPage], panelView, resetKey, direction, ::goPrevious, ::goNext, onTap)
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
    direction: ReadingDirection,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTap: () -> Unit,
) {
    val modifier = Modifier.weight(1f).fillMaxSize()
    if (index == activePage) {
        GuidedPanel(art, panelView, resetKey, direction, onPrevious, onNext, onTap, modifier)
        return
    }
    val image = art?.image
    Box(
        modifier = modifier.pointerInput(index, activePage) {
            detectTapGestures { if (index < activePage) onPrevious() else onNext() }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) GuidedPage(image, FullPagePanel, Offset.Zero)
    }
}

@Composable
private fun GuidedPanel(
    art: PageArt?,
    panelView: Rect,
    resetKey: Any,
    direction: ReadingDirection,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val decay = rememberSplineBasedDecay<Rect>()
    val view = remember { Animatable(panelView, Rect.VectorConverter) }
    val overscroll = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val settleSpring = remember { spring<Offset>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow) }
    var zoomed by remember { mutableStateOf(false) }
    var flingJob by remember { mutableStateOf<Job?>(null) }
    val currentPanelView by rememberUpdatedState(panelView)
    val currentArt by rememberUpdatedState(art)
    val currentDirection by rememberUpdatedState(direction)

    LaunchedEffect(resetKey) {
        zoomed = false
        flingJob?.cancel()
        view.updateBounds(null, null)
        overscroll.snapTo(Offset.Zero)
        view.animateTo(currentPanelView, tween(FOCUS_ANIMATION_MILLIS, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        when {
                            zoomed -> onTap()
                            else -> when (PageOrder.tapZone(currentDirection, offset.x / size.width, PREVIOUS_ZONE, NEXT_ZONE)) {
                                TapZone.Previous -> onPrevious()
                                TapZone.Next -> onNext()
                                TapZone.Center -> onTap()
                            }
                        }
                    },
                    onDoubleTap = { offset ->
                        val image = currentArt?.image ?: return@detectTapGestures
                        flingJob?.cancel()
                        if (zoomed) {
                            zoomed = false
                            flingJob = scope.launch {
                                view.updateBounds(null, null)
                                launch { overscroll.animateTo(Offset.Zero, settleSpring) }
                                view.animateTo(currentPanelView, tween(FOCUS_ANIMATION_MILLIS, easing = FastOutSlowInEasing))
                            }
                        } else {
                            zoomed = true
                            val target = zoomAround(view.value, image, size, offset)
                            flingJob = scope.launch {
                                view.updateBounds(null, null)
                                view.animateTo(target, tween(ZOOM_ANIMATION_MILLIS, easing = FastOutSlowInEasing))
                            }
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    flingJob?.cancel()
                    view.updateBounds(null, null)
                    val tracker = VelocityTracker()
                    var live = view.value
                    var virtual = Offset(live.left, live.top)
                    var panned = false
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        val image = currentArt?.image
                        if (image != null && size != IntSize.Zero) {
                            val drawn = GuidedFocus.fit(live, image.size(), size.toSize())
                            if (pressed >= 2) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    zoomed = true
                                    val maxWidth = currentPanelView.width
                                    val maxHeight = currentPanelView.height
                                    live = GuidedFocus.pinch(
                                        live, drawn, event.calculateCentroid(), zoomChange, panChange,
                                        maxWidth / MAX_ZOOM, maxWidth, maxHeight / MAX_ZOOM, maxHeight,
                                    )
                                    virtual = Offset(live.left, live.top)
                                    val pinched = live
                                    scope.launch { overscroll.snapTo(Offset.Zero); view.snapTo(pinched) }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } else if (zoomed && pressed == 1) {
                                val change = event.changes.first { it.pressed }
                                tracker.addPointerInputChange(change)
                                panned = true
                                virtual += GuidedFocus.toPageDelta(live, drawn, change.positionChange())
                                val width = live.width
                                val height = live.height
                                val left = virtual.x.coerceIn(0f, 1f - width)
                                val top = virtual.y.coerceIn(0f, 1f - height)
                                live = Rect(left, top, left + width, top + height)
                                val panned2 = live
                                val rubber = rubberBand(
                                    Offset(
                                        -(virtual.x - left) * drawn.width / width,
                                        -(virtual.y - top) * drawn.height / height,
                                    ),
                                )
                                scope.launch { view.snapTo(panned2); overscroll.snapTo(rubber) }
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    val image = currentArt?.image
                    val atPanel = live.width >= currentPanelView.width - PANEL_EXIT_EPSILON &&
                        live.height >= currentPanelView.height - PANEL_EXIT_EPSILON
                    when {
                        atPanel && zoomed -> {
                            zoomed = false
                            flingJob = scope.launch {
                                view.updateBounds(null, null)
                                launch { overscroll.animateTo(Offset.Zero, settleSpring) }
                                view.animateTo(currentPanelView, tween(FOCUS_ANIMATION_MILLIS, easing = FastOutSlowInEasing))
                            }
                        }

                        panned && image != null -> {
                            val drawn = GuidedFocus.fit(live, image.size(), size.toSize())
                            val velocity = tracker.calculateVelocity()
                            val pageVelocity = GuidedFocus.toPageDelta(live, drawn, Offset(velocity.x, velocity.y))
                            val pixelsPerPageX = drawn.width / live.width
                            val pixelsPerPageY = drawn.height / live.height
                            flingJob = scope.launch {
                                launch { overscroll.animateTo(Offset.Zero, settleSpring) }
                                fling(view, overscroll, decay, settleSpring, pageVelocity, pixelsPerPageX, pixelsPerPageY)
                            }
                        }

                        else -> flingJob = scope.launch { overscroll.animateTo(Offset.Zero, settleSpring) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val image = art?.image
        if (image == null) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
            GuidedPage(image, view.value, overscroll.value)
        }
    }
}

private suspend fun fling(
    view: Animatable<Rect, *>,
    overscroll: Animatable<Offset, *>,
    decay: DecayAnimationSpec<Rect>,
    settleSpring: AnimationSpec<Offset>,
    pageVelocity: Offset,
    pixelsPerPageX: Float,
    pixelsPerPageY: Float,
) {
    val width = view.value.width
    val height = view.value.height
    view.updateBounds(Rect(0f, 0f, width, height), Rect(1f - width, 1f - height, 1f, 1f))
    val result = view.animateDecay(Rect(pageVelocity.x, pageVelocity.y, pageVelocity.x, pageVelocity.y), decay)
    view.updateBounds(null, null)
    if (result.endReason == AnimationEndReason.BoundReached) {
        val leftover = view.velocity
        val bounce = clampMagnitude(
            Offset(-leftover.left * pixelsPerPageX, -leftover.top * pixelsPerPageY) * BOUNCE_VELOCITY_FRACTION,
            MAX_BOUNCE_VELOCITY_PX,
        )
        overscroll.animateTo(Offset.Zero, settleSpring, initialVelocity = bounce)
    }
}

private fun rubberBand(overflow: Offset): Offset {
    fun band(value: Float) = (value * OVERSCROLL_RESISTANCE).coerceIn(-MAX_OVERSCROLL_PX, MAX_OVERSCROLL_PX)
    return Offset(band(overflow.x), band(overflow.y))
}

private fun clampMagnitude(offset: Offset, max: Float): Offset {
    val length = offset.getDistance()
    return if (length > max && length > 0f) offset * (max / length) else offset
}

private fun zoomAround(view: Rect, image: ImageBitmap, canvas: IntSize, position: Offset): Rect {
    val drawn = GuidedFocus.fit(view, image.size(), canvas.toSize())
    return GuidedFocus.zoomed(view, drawn, image.size(), canvas.toSize(), GuidedFocus.toPage(view, drawn, position), DOUBLE_TAP_ZOOM)
}

private fun ImageBitmap.size() = Size(width.toFloat(), height.toFloat())

private fun IntSize.toSize() = Size(width.toFloat(), height.toFloat())

@Composable
private fun GuidedPage(image: ImageBitmap, view: Rect, overscroll: Offset) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val drawn = GuidedFocus.fit(view, image.size(), size)
        drawImage(
            image = image,
            srcOffset = IntOffset((view.left * image.width).roundToInt(), (view.top * image.height).roundToInt()),
            srcSize = IntSize((view.width * image.width).roundToInt(), (view.height * image.height).roundToInt()),
            dstOffset = IntOffset((drawn.left + overscroll.x).roundToInt(), (drawn.top + overscroll.y).roundToInt()),
            dstSize = IntSize(drawn.width.roundToInt(), drawn.height.roundToInt()),
            filterQuality = FilterQuality.High,
        )
    }
}
