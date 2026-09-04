package com.comicify.feature.reader.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.comicify.feature.reader.domain.Cut
import com.comicify.feature.reader.domain.DirectorCut
import com.comicify.feature.reader.domain.FullPagePanel
import com.comicify.feature.reader.domain.GuidedFocus
import com.comicify.feature.reader.domain.PageOrder
import com.comicify.feature.reader.domain.PanSlop
import com.comicify.feature.reader.domain.TapZone
import com.comicify.feature.reader.domain.TapZones
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow

private const val LAST_PANEL = Int.MAX_VALUE / 2
private const val PANEL_PADDING = 0.02f
private const val DOUBLE_TAP_ZOOM = 2f
private const val MAX_ZOOM = 4f
private const val RETURN_ANIMATION_MILLIS = 520
private const val FULL_DIM = 1f
private const val ZOOM_ANIMATION_MILLIS = 260
private const val PANEL_EXIT_EPSILON = 0.001f
private const val OVERSCROLL_RESISTANCE = 0.32f
private const val MAX_OVERSCROLL_PX = 220f
private const val BOUNCE_VELOCITY_FRACTION = 0.3f
private const val MAX_BOUNCE_VELOCITY_PX = 2400f
private const val SPOTLIGHT_DIM = 0.72f
private const val SPOTLIGHT_FEATHER_FRACTION = 0.1f
// TODO(guided): auto-pan is disabled (no HUD toggle) until it is content-gated
//  so it only tours detail-dense stops and never zooms into near-empty panels.
private const val AUTO_PAN_ENABLED = false
private const val AUTO_PAN_ZOOM = 1.2f
private const val AUTO_PAN_MILLIS = 6000
private const val AUTO_PAN_MIN_AREA = 0.4f
private const val AUTO_PAN_MIN_SIDE = 0.45f

@Composable
fun GuidedReader(
    loader: PageLoader,
    spread: Boolean,
    direction: ReadingDirection,
    coverAlone: Boolean,
    initialPage: Int,
    pageTurnRequests: Flow<PageTurnDirection>,
    onPageChanged: (Int) -> Unit,
    onGuidedStop: (Int, Int) -> Unit,
    onTap: () -> Unit,
    onAmbient: (Color) -> Unit,
) {
    var page by remember { mutableIntStateOf(initialPage.coerceIn(0, (loader.pageCount - 1).coerceAtLeast(0))) }
    var panelIndex by remember { mutableIntStateOf(0) }
    var panels by remember { mutableStateOf(listOf(FullPagePanel)) }
    var arts by remember { mutableStateOf(emptyMap<Int, PageArt>()) }

    LaunchedEffect(page, direction) {
        onPageChanged(page)
        val spreadStart = spreadStart(page, coverAlone)
        arts = arts.filterKeys { it in spreadStart..spreadStart + 1 }
        val loaded = runCatching { loader.load(page) }.getOrNull()
        loaded?.let { arts = arts + (page to it); onAmbient(it.ambient) }
        val detected = runCatching { loader.stops(page, direction) }.getOrDefault(listOf(FullPagePanel))
        panels = detected
        panelIndex = if (panelIndex == LAST_PANEL) detected.lastIndex else panelIndex.coerceIn(0, detected.lastIndex)
        loader.preload(page, (page - 1)..(page + 2), bubbleScale = null, panels = true)
        if (spread) {
            val sibling = spreadStart * 2 + 1 - page
            if (sibling in 0 until loader.pageCount) runCatching { loader.load(sibling) }.getOrNull()?.let { arts = arts + (sibling to it) }
        }
    }

    var advancing by remember { mutableStateOf(true) }

    fun goNext() {
        advancing = true
        if (panelIndex < panels.lastIndex) {
            panelIndex++
        } else if (page < loader.pageCount - 1) {
            panelIndex = 0
            page++
        }
    }

    fun goPrevious() {
        advancing = false
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

    LaunchedEffect(panelIndex, panels.size) { onGuidedStop(panelIndex, panels.size) }

    val currentStop = panels.getOrElse(panelIndex) { FullPagePanel }
    val autoPan = AUTO_PAN_ENABLED && isLargeStop(currentStop)
    val panelView = GuidedFocus.frame(currentStop, PANEL_PADDING)
    val resetKey = Triple(page, panelIndex, panelView)

    if (!spread) {
        GuidedPanel(arts[page], page, advancing, panelView, autoPan, resetKey, direction, ::goPrevious, ::goNext, onTap, Modifier.fillMaxSize())
        return
    }
    val firstPage = spreadStart(page, coverAlone)
    val secondPage = firstPage + 1
    val screenLeftPage = PageOrder.leftPage(direction, firstPage, secondPage)
    val screenRightPage = PageOrder.rightPage(direction, firstPage, secondPage)
    Row(modifier = Modifier.fillMaxSize()) {
        SpreadHalf(screenLeftPage, page, advancing, arts[screenLeftPage], panelView, autoPan, resetKey, direction, ::goPrevious, ::goNext, onTap)
        SpreadHalf(screenRightPage, page, advancing, arts[screenRightPage], panelView, autoPan, resetKey, direction, ::goPrevious, ::goNext, onTap)
    }
}

private fun spreadStart(page: Int, coverAlone: Boolean) =
    PageOrder.spreadFirstPage(PageOrder.spreadIndex(page, coverAlone), coverAlone)

private fun isLargeStop(stop: Rect) =
    stop.width * stop.height >= AUTO_PAN_MIN_AREA && minOf(stop.width, stop.height) >= AUTO_PAN_MIN_SIDE

private fun panCrop(stop: Rect, atTop: Boolean): Rect {
    val width = stop.width / AUTO_PAN_ZOOM
    val height = stop.height / AUTO_PAN_ZOOM
    val left = stop.left + (stop.width - width) / 2f
    val top = if (atTop) stop.top else stop.bottom - height
    return Rect(left, top, left + width, top + height)
}

@Composable
private fun RowScope.SpreadHalf(
    index: Int,
    activePage: Int,
    advancing: Boolean,
    art: PageArt?,
    panelView: Rect,
    autoPan: Boolean,
    resetKey: Any,
    direction: ReadingDirection,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTap: () -> Unit,
) {
    val modifier = Modifier.weight(1f).fillMaxSize()
    if (index == activePage) {
        GuidedPanel(art, activePage, advancing, panelView, autoPan, resetKey, direction, onPrevious, onNext, onTap, modifier)
        return
    }
    val image = art?.image
    Box(
        modifier = modifier.pointerInput(index, activePage) {
            detectTapGestures { if (index < activePage) onPrevious() else onNext() }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) GuidedPage(image, FullPagePanel, Offset.Zero, SPOTLIGHT_DIM)
    }
}

@Composable
private fun GuidedPanel(
    art: PageArt?,
    page: Int,
    advancing: Boolean,
    panelView: Rect,
    autoPan: Boolean,
    resetKey: Any,
    direction: ReadingDirection,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val decay = remember { exponentialDecay<Rect>(frictionMultiplier = 2.0f) }
    val view = remember { Animatable(panelView, Rect.VectorConverter) }
    val overscroll = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val settleSpring = remember { spring<Offset>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow) }
    var zoomed by remember { mutableStateOf(false) }
    var flingJob by remember { mutableStateOf<Job?>(null) }
    var dragView by remember { mutableStateOf<Rect?>(null) }
    var dragOverscroll by remember { mutableStateOf(Offset.Zero) }
    val currentPanelView by rememberUpdatedState(panelView)
    val currentArt by rememberUpdatedState(art)
    val currentDirection by rememberUpdatedState(direction)
    val currentAutoPan by rememberUpdatedState(autoPan)
    val currentAdvancing by rememberUpdatedState(advancing)
    val dim = remember { Animatable(SPOTLIGHT_DIM) }
    var shownPage by remember { mutableIntStateOf(page) }

    LaunchedEffect(resetKey) {
        zoomed = false
        flingJob?.cancel()
        dragView = null
        view.updateBounds(null, null)
        overscroll.snapTo(Offset.Zero)
        if (currentAutoPan) {
            shownPage = page
            view.animateTo(panCrop(currentPanelView, atTop = true), tween(RETURN_ANIMATION_MILLIS, easing = FastOutSlowInEasing))
            view.animateTo(panCrop(currentPanelView, atTop = false), tween(AUTO_PAN_MILLIS, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        val cut = DirectorCut.between(view.value, currentPanelView, page != shownPage, currentAdvancing)
        shownPage = page
        dim.snapTo(if (cut.fromBlack) FULL_DIM else SPOTLIGHT_DIM)
        launch { dim.animateTo(SPOTLIGHT_DIM, tween(cut.durationMillis, easing = cut.easing)) }
        moveCamera(view, currentPanelView, cut)
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        when {
                            zoomed -> onTap()
                            else -> when (TapZones.FullWidth.at(currentDirection, offset.x / size.width)) {
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
                                view.animateTo(currentPanelView, tween(RETURN_ANIMATION_MILLIS, easing = FastOutSlowInEasing))
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
                    var slop = PanSlop()
                    var pastSlop = false
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        val image = currentArt?.image
                        if (!pastSlop) {
                            slop = slop.plus(event.calculatePan())
                            pastSlop = pressed >= 2 || slop.exceeds(viewConfiguration.touchSlop)
                        }
                        if (pastSlop && image != null && size != IntSize.Zero) {
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
                                    dragView = live
                                    dragOverscroll = Offset.Zero
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
                                dragView = live
                                dragOverscroll = rubberBand(
                                    Offset(
                                        -(virtual.x - left) * drawn.width / width,
                                        -(virtual.y - top) * drawn.height / height,
                                    ),
                                )
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    val image = currentArt?.image
                    val finalRect = live
                    val finalOverscroll = dragOverscroll
                    val atPanel = live.width >= currentPanelView.width - PANEL_EXIT_EPSILON &&
                        live.height >= currentPanelView.height - PANEL_EXIT_EPSILON
                    when {
                        atPanel && zoomed -> {
                            zoomed = false
                            flingJob = scope.launch {
                                view.snapTo(finalRect)
                                overscroll.snapTo(finalOverscroll)
                                dragView = null
                                view.updateBounds(null, null)
                                launch { overscroll.animateTo(Offset.Zero, settleSpring) }
                                view.animateTo(currentPanelView, tween(RETURN_ANIMATION_MILLIS, easing = FastOutSlowInEasing))
                            }
                        }

                        panned && image != null -> {
                            val drawn = GuidedFocus.fit(live, image.size(), size.toSize())
                            val velocity = tracker.calculateVelocity()
                            val pageVelocity = GuidedFocus.toPageDelta(live, drawn, Offset(velocity.x, velocity.y))
                            val pixelsPerPageX = drawn.width / live.width
                            val pixelsPerPageY = drawn.height / live.height
                            flingJob = scope.launch {
                                view.snapTo(finalRect)
                                overscroll.snapTo(finalOverscroll)
                                dragView = null
                                launch { overscroll.animateTo(Offset.Zero, settleSpring) }
                                fling(view, overscroll, decay, settleSpring, pageVelocity, pixelsPerPageX, pixelsPerPageY)
                            }
                        }

                        else -> flingJob = scope.launch {
                            view.snapTo(finalRect)
                            overscroll.snapTo(finalOverscroll)
                            dragView = null
                            overscroll.animateTo(Offset.Zero, settleSpring)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val image = art?.image
        if (image == null) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
            val displayView = dragView ?: view.value
            val displayOverscroll = if (dragView != null) dragOverscroll else overscroll.value
            GuidedPage(image, displayView, displayOverscroll, dim.value)
        }
    }
}

private suspend fun moveCamera(view: Animatable<Rect, *>, target: Rect, cut: Cut) {
    if (cut.jump) {
        view.snapTo(target)
        return
    }
    if (!cut.arcing) {
        view.animateTo(target, tween(cut.durationMillis, easing = cut.easing))
        return
    }
    val half = cut.durationMillis / 2
    view.animateTo(DirectorCut.apex(view.value, target, cut.lift), tween(half, easing = DirectorCut.ArcOut))
    view.animateTo(target, tween(half, easing = DirectorCut.ArcIn))
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
private fun GuidedPage(image: ImageBitmap, view: Rect, overscroll: Offset, dim: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val source = view
        val drawn = GuidedFocus.fit(source, image.size(), size).translate(overscroll.x, overscroll.y)
        val scale = drawn.width / (source.width * image.width)
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(
                (drawn.left - source.left * image.width * scale).roundToInt(),
                (drawn.top - source.top * image.height * scale).roundToInt(),
            ),
            dstSize = IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt()),
            filterQuality = FilterQuality.Low,
        )
        drawRect(color = Color.Black, alpha = dim)
        drawImage(
            image = image,
            srcOffset = IntOffset((source.left * image.width).roundToInt(), (source.top * image.height).roundToInt()),
            srcSize = IntSize((source.width * image.width).roundToInt(), (source.height * image.height).roundToInt()),
            dstOffset = IntOffset(drawn.left.roundToInt(), drawn.top.roundToInt()),
            dstSize = IntSize(drawn.width.roundToInt(), drawn.height.roundToInt()),
            filterQuality = FilterQuality.High,
        )
        featherFocus(drawn, dim)
    }
}

private fun DrawScope.featherFocus(focus: Rect, dim: Float) {
    val feather = minOf(focus.width, focus.height) * SPOTLIGHT_FEATHER_FRACTION
    if (feather <= 0f) return
    val edge = Color.Black.copy(alpha = dim)
    drawRect(
        Brush.verticalGradient(listOf(edge, Color.Transparent), startY = focus.top, endY = focus.top + feather),
        topLeft = Offset(focus.left, focus.top), size = Size(focus.width, feather),
    )
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, edge), startY = focus.bottom - feather, endY = focus.bottom),
        topLeft = Offset(focus.left, focus.bottom - feather), size = Size(focus.width, feather),
    )
    drawRect(
        Brush.horizontalGradient(listOf(edge, Color.Transparent), startX = focus.left, endX = focus.left + feather),
        topLeft = Offset(focus.left, focus.top), size = Size(feather, focus.height),
    )
    drawRect(
        Brush.horizontalGradient(listOf(Color.Transparent, edge), startX = focus.right - feather, endX = focus.right),
        topLeft = Offset(focus.right - feather, focus.top), size = Size(feather, focus.height),
    )
}
