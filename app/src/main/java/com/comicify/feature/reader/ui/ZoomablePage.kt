package com.comicify.feature.reader.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import com.comicify.feature.reader.ui.BubbleOverlay.drawBubbles
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.comicify.feature.reader.data.PageArt
import com.comicify.domain.model.ReadingDirection
import com.comicify.feature.reader.data.PageLoader
import com.comicify.feature.reader.domain.TapZone
import com.comicify.feature.reader.domain.TapZones
import com.comicify.feature.reader.data.PaintedBubble
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
private val BubbleSpinnerSize = 22.dp
private val BubbleSpinnerStroke = 2.dp
private val BubbleSpinnerMargin = 14.dp

private fun centeredOn(tap: Offset, size: IntSize, scale: Float): Offset {
    val center = Offset(size.width / 2f, size.height / 2f)
    return (center - tap) * scale
}

private fun panBounds(size: IntSize, scale: Float): Offset =
    Offset((scale - 1f) * size.width / 2f, (scale - 1f) * size.height / 2f)

private fun clampOffset(offset: Offset, size: IntSize, scale: Float): Offset {
    val max = panBounds(size, scale)
    return Offset(offset.x.coerceIn(-max.x, max.x), offset.y.coerceIn(-max.y, max.y))
}

@Composable
fun ZoomablePage(
    loader: PageLoader,
    index: Int,
    bubbleScale: Float?,
    direction: ReadingDirection,
    tapZones: TapZones,
    onTap: (TapZone) -> Unit,
    onZoomedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var art by remember(index) { mutableStateOf<PageArt?>(null) }
    var overlay by remember(index) { mutableStateOf<BubbleOverlayState>(BubbleOverlayState.None) }
    var scale by remember(index) { mutableFloatStateOf(1f) }
    var offset by remember(index) { mutableStateOf(Offset.Zero) }
    var flingJob by remember(index) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val decay = remember { exponentialDecay<Offset>(frictionMultiplier = 2.0f) }

    LaunchedEffect(index) { art = runCatching { loader.load(index) }.getOrNull() }
    LaunchedEffect(index, bubbleScale, art) {
        val page = art
        if (bubbleScale == null || page == null) { overlay = BubbleOverlayState.None; return@LaunchedEffect }
        overlay = BubbleOverlayState.Loading
        overlay = BubbleOverlayState.Ready(runCatching { loader.overlay(index, bubbleScale) }.getOrDefault(emptyList()))
    }
    LaunchedEffect(scale) { onZoomedChange(scale > 1.01f) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val page = art
        if (page == null) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(index) {
                        detectTapGestures(
                            onTap = { tap -> onTap(if (scale > 1f) TapZone.Center else tapZones.at(direction, tap.x / size.width)) },
                            onDoubleTap = { tap ->
                                flingJob?.cancel()
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = DOUBLE_TAP_SCALE
                                    offset = clampOffset(centeredOn(tap, size, DOUBLE_TAP_SCALE), size, DOUBLE_TAP_SCALE)
                                }
                            },
                        )
                    }
                    .pointerInput(index) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            flingJob?.cancel()
                            val tracker = VelocityTracker()
                            var panned = false
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                if (pressed >= 2 || scale > 1f) {
                                    val next = (scale * event.calculateZoom()).coerceIn(1f, MAX_SCALE)
                                    scale = next
                                    if (next > 1f) {
                                        if (pressed == 1) event.changes.firstOrNull { it.pressed }?.let(tracker::addPointerInputChange)
                                        offset = clampOffset(offset + event.calculatePan(), size, next)
                                        panned = true
                                    } else {
                                        offset = Offset.Zero
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })

                            if (panned && scale > 1f) {
                                val velocity = tracker.calculateVelocity()
                                val bounds = panBounds(size, scale)
                                flingJob = scope.launch {
                                    val fling = Animatable(offset, Offset.VectorConverter)
                                    fling.updateBounds(Offset(-bounds.x, -bounds.y), bounds)
                                    fling.animateDecay(Offset(velocity.x, velocity.y), decay) { offset = value }
                                }
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            ) {
                Image(
                    bitmap = page.image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                (overlay as? BubbleOverlayState.Ready)?.let { ready ->
                    BubbleLayer(page.image, ready.bubbles, cached = { scale <= 1f })
                }
            }
            if (overlay == BubbleOverlayState.Loading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = BubbleSpinnerStroke,
                    modifier = Modifier.align(Alignment.TopEnd).padding(BubbleSpinnerMargin).size(BubbleSpinnerSize),
                )
            }
        }
    }
}

@Composable
private fun BubbleLayer(page: ImageBitmap, bubbles: List<PaintedBubble>, cached: () -> Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = if (cached()) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .drawBehind { drawBubbles(page, fittedImageRect(page, size), bubbles) },
    )
}

private sealed interface BubbleOverlayState {
    data object None : BubbleOverlayState
    data object Loading : BubbleOverlayState
    data class Ready(val bubbles: List<PaintedBubble>) : BubbleOverlayState
}

private fun fittedImageRect(image: ImageBitmap, viewport: Size): Rect {
    val scale = minOf(viewport.width / image.width, viewport.height / image.height)
    val fitted = Size(image.width * scale, image.height * scale)
    return Rect(Offset((viewport.width - fitted.width) / 2f, (viewport.height - fitted.height) / 2f), fitted)
}
