package com.comicify.feature.reader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import com.comicify.feature.reader.data.PageArt
import com.comicify.feature.reader.data.PageLoader

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

private fun centeredOn(tap: Offset, size: IntSize, scale: Float): Offset {
    val center = Offset(size.width / 2f, size.height / 2f)
    return (center - tap) * scale
}

private fun clampOffset(offset: Offset, size: IntSize, scale: Float): Offset {
    val maxX = (scale - 1f) * size.width / 2f
    val maxY = (scale - 1f) * size.height / 2f
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

@Composable
fun ZoomablePage(
    loader: PageLoader,
    index: Int,
    onTap: () -> Unit,
    onZoomedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var art by remember(index) { mutableStateOf<PageArt?>(null) }
    var scale by remember(index) { mutableFloatStateOf(1f) }
    var offset by remember(index) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(index) { art = runCatching { loader.load(index) }.getOrNull() }
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
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.count { it.pressed }
                                if (pressed >= 2 || scale > 1f) {
                                    val next = (scale * event.calculateZoom()).coerceIn(1f, MAX_SCALE)
                                    scale = next
                                    offset = if (next > 1f) clampOffset(offset + event.calculatePan(), size, next) else Offset.Zero
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
        }
    }
}
