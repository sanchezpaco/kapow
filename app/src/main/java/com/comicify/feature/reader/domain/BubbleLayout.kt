package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

const val BUBBLE_ENLARGE_SCALE = 1.3f
private const val SEPARATION_PASSES = 8

data class EnlargedBubble(val bubble: SpeechBubble, val scale: Float, val target: Rect) {
    fun map(point: Offset): Offset = Offset(
        target.left + (point.x - bubble.box.left) * scale,
        target.top + (point.y - bubble.box.top) * scale,
    )
}

object BubbleLayout {

    fun enlarge(bubbles: List<SpeechBubble>, scale: Float): List<EnlargedBubble> {
        val targets = bubbles.map { grown(it.box, scale) }.toMutableList()
        repeat(SEPARATION_PASSES) { pushApart(targets, bubbles) }
        val scales = FloatArray(bubbles.size) { scale }
        for (i in bubbles.indices) for (j in i + 1 until bubbles.size) {
            if (!targets[i].overlaps(targets[j])) continue
            val limit = separatingScale(targets[i], targets[j], scale)
            scales[i] = min(scales[i], limit)
            scales[j] = min(scales[j], limit)
        }
        return bubbles.indices.map { index ->
            val target = if (scales[index] < scale) shrunk(targets[index], scales[index] / scale).covering(bubbles[index].box) else targets[index]
            EnlargedBubble(bubbles[index], scales[index], target)
        }
    }

    private fun pushApart(targets: MutableList<Rect>, bubbles: List<SpeechBubble>) {
        for (i in targets.indices) for (j in i + 1 until targets.size) {
            val a = targets[i]
            val b = targets[j]
            if (!a.overlaps(b)) continue
            val overlapX = min(a.right, b.right) - max(a.left, b.left)
            val overlapY = min(a.bottom, b.bottom) - max(a.top, b.top)
            val shift = if (overlapX < overlapY) {
                Offset(if (a.center.x <= b.center.x) -overlapX / 2f else overlapX / 2f, 0f)
            } else {
                Offset(0f, if (a.center.y <= b.center.y) -overlapY / 2f else overlapY / 2f)
            }
            targets[i] = a.translate(shift).covering(bubbles[i].box).clampedToPage()
            targets[j] = b.translate(-shift).covering(bubbles[j].box).clampedToPage()
        }
    }

    private fun separatingScale(a: Rect, b: Rect, scale: Float): Float {
        val gapX = abs(a.center.x - b.center.x) / ((a.width + b.width) / 2f)
        val gapY = abs(a.center.y - b.center.y) / ((a.height + b.height) / 2f)
        return (max(gapX, gapY) * scale).coerceIn(1f, scale)
    }

    private fun shrunk(target: Rect, factor: Float): Rect {
        val width = target.width * factor
        val height = target.height * factor
        return Rect(Offset(target.center.x - width / 2f, target.center.y - height / 2f), Size(width, height))
    }

    private fun grown(box: Rect, scale: Float): Rect {
        val width = box.width * scale
        val height = box.height * scale
        return Rect(box.center.x - width / 2f, box.center.y - height / 2f, box.center.x + width / 2f, box.center.y + height / 2f).clampedToPage()
    }

    private fun Rect.covering(box: Rect): Rect {
        val x = min(box.left, max(left, box.right - width))
        val y = min(box.top, max(top, box.bottom - height))
        return Rect(x, y, x + width, y + height)
    }

    private fun Rect.clampedToPage(): Rect {
        val x = left.coerceIn(0f, max(0f, 1f - width))
        val y = top.coerceIn(0f, max(0f, 1f - height))
        return Rect(x, y, x + width, y + height)
    }
}
