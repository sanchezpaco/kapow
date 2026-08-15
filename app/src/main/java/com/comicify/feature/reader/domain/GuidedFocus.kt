package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

private const val MIN_VIEW_SIDE = 0.02f

object GuidedFocus {

    fun frame(panel: Rect, padding: Float): Rect =
        clamp(Rect(panel.left - padding, panel.top - padding, panel.right + padding, panel.bottom + padding))

    fun zoomed(view: Rect, drawn: Rect, image: Size, canvas: Size, focus: Offset, factor: Float): Rect {
        val pixelsPerImagePixel = drawn.width / (view.width * image.width) * factor
        val width = canvas.width / pixelsPerImagePixel / image.width
        val height = canvas.height / pixelsPerImagePixel / image.height
        return clamp(Rect(focus.x - width / 2f, focus.y - height / 2f, focus.x + width / 2f, focus.y + height / 2f))
    }

    fun panned(view: Rect, delta: Offset): Rect = clamp(view.translate(delta))

    fun fit(view: Rect, image: Size, canvas: Size): Rect {
        val viewAspect = (view.width * image.width) / (view.height * image.height)
        val canvasAspect = canvas.width / canvas.height
        val width = if (canvasAspect > viewAspect) canvas.height * viewAspect else canvas.width
        val height = if (canvasAspect > viewAspect) canvas.height else canvas.width / viewAspect
        val left = (canvas.width - width) / 2f
        val top = (canvas.height - height) / 2f
        return Rect(left, top, left + width, top + height)
    }

    fun toPage(view: Rect, drawn: Rect, position: Offset): Offset = Offset(
        view.left + (position.x - drawn.left) / drawn.width * view.width,
        view.top + (position.y - drawn.top) / drawn.height * view.height,
    )

    fun toPageDelta(view: Rect, drawn: Rect, delta: Offset): Offset =
        Offset(-delta.x / drawn.width * view.width, -delta.y / drawn.height * view.height)

    private fun clamp(rect: Rect): Rect {
        val width = rect.width.coerceIn(MIN_VIEW_SIDE, 1f)
        val height = rect.height.coerceIn(MIN_VIEW_SIDE, 1f)
        val left = rect.left.coerceIn(0f, 1f - width)
        val top = rect.top.coerceIn(0f, 1f - height)
        return Rect(left, top, left + width, top + height)
    }
}
