package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

enum class PageZoomAction { ZoomIn, ZoomOut, ToFitScreen, ToFitWidth }

object PageFit {

    fun content(container: Size, pageAspect: Float, fitWidth: Boolean): Size =
        if (!fitWidth || pageAspect <= 0f) container else Size(container.width, container.width / pageAspect)

    fun panBounds(container: Size, content: Size, scale: Float): Offset = Offset(
        ((content.width * scale - container.width) / 2f).coerceAtLeast(0f),
        ((content.height * scale - container.height) / 2f).coerceAtLeast(0f),
    )

    fun clamp(offset: Offset, bounds: Offset): Offset =
        Offset(offset.x.coerceIn(-bounds.x, bounds.x), offset.y.coerceIn(-bounds.y, bounds.y))

    fun atTopEdge(bounds: Offset): Offset = Offset(0f, bounds.y)

    fun doubleTap(fitWidth: Boolean, framedToWidth: Boolean, zoomed: Boolean): PageZoomAction = when {
        fitWidth && framedToWidth -> PageZoomAction.ToFitScreen
        fitWidth -> PageZoomAction.ToFitWidth
        zoomed -> PageZoomAction.ZoomOut
        else -> PageZoomAction.ZoomIn
    }
}
