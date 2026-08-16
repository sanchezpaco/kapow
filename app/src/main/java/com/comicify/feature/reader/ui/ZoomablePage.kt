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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
import com.comicify.feature.reader.data.PageLoader
import com.comicify.feature.reader.domain.BUBBLE_ENLARGE_SCALE
import com.comicify.feature.reader.domain.BubbleLayout
import com.comicify.feature.reader.domain.EnlargedBubble
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
private val BubbleOutlineWidth = 1.dp
private val BubbleOutlineColor = Color.Black.copy(alpha = 0.35f)

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
    bubblesEnlarged: Boolean,
    onTap: () -> Unit,
    onZoomedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var art by remember(index) { mutableStateOf<PageArt?>(null) }
    var bubbles by remember(index) { mutableStateOf<List<EnlargedBubble>>(emptyList()) }
    var scale by remember(index) { mutableFloatStateOf(1f) }
    var offset by remember(index) { mutableStateOf(Offset.Zero) }
    var flingJob by remember(index) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val decay = remember { exponentialDecay<Offset>(frictionMultiplier = 2.0f) }

    LaunchedEffect(index) { art = runCatching { loader.load(index) }.getOrNull() }
    LaunchedEffect(index, bubblesEnlarged) {
        bubbles = if (!bubblesEnlarged) emptyList() else runCatching { loader.bubbles(index) }.getOrDefault(emptyList())
            .let { BubbleLayout.enlarge(it, BUBBLE_ENLARGE_SCALE) }
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
            Image(
                bitmap = page.image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(index) {
                        detectTapGestures(
                            onTap = { onTap() },
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
                    }
                    .drawWithContent {
                        drawContent()
                        drawEnlargedBubbles(page.image, bubbles)
                    },
            )
        }
    }
}

private fun ContentDrawScope.drawEnlargedBubbles(image: ImageBitmap, bubbles: List<EnlargedBubble>) {
    if (bubbles.isEmpty()) return
    val fitted = fittedImageRect(image, size)
    val outline = Stroke(BubbleOutlineWidth.toPx())
    bubbles.filter { it.scale > 1f }.forEach { item ->
        val shape = Path().apply {
            item.bubble.outline.forEachIndexed { i, point ->
                val at = fitted.toScreen(item.map(point))
                if (i == 0) moveTo(at.x, at.y) else lineTo(at.x, at.y)
            }
            close()
        }
        val src = item.bubble.box
        val dst = fitted.toScreen(item.target)
        clipPath(shape) {
            drawImage(
                image = image,
                srcOffset = IntOffset((src.left * image.width).toInt(), (src.top * image.height).toInt()),
                srcSize = IntSize((src.width * image.width).toInt(), (src.height * image.height).toInt()),
                dstOffset = IntOffset(dst.left.toInt(), dst.top.toInt()),
                dstSize = IntSize(dst.width.toInt(), dst.height.toInt()),
                filterQuality = FilterQuality.High,
            )
        }
        drawPath(shape, BubbleOutlineColor, style = outline)
    }
}

private fun fittedImageRect(image: ImageBitmap, viewport: Size): Rect {
    val scale = minOf(viewport.width / image.width, viewport.height / image.height)
    val fitted = Size(image.width * scale, image.height * scale)
    return Rect(Offset((viewport.width - fitted.width) / 2f, (viewport.height - fitted.height) / 2f), fitted)
}

private fun Rect.toScreen(point: Offset) = Offset(left + point.x * width, top + point.y * height)

private fun Rect.toScreen(rect: Rect) = Rect(toScreen(rect.topLeft), toScreen(rect.bottomRight))
