package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

const val BUBBLE_ENLARGE_SCALE = 1.3f
val BUBBLE_SCALE_RANGE = 1.1f..2f
private const val SEPARATION_PASSES = 8
private const val MIN_ENLARGE_SCALE = 1.15f

data class EnlargedBubble(val bubble: SpeechBubble, val scale: Float, val target: Rect) {
    fun map(point: Offset): Offset = Offset(
        target.left + (point.x - bubble.box.left) * scale,
        target.top + (point.y - bubble.box.top) * scale,
    )
}

object BubbleLayout {

    fun enlarge(bubbles: List<SpeechBubble>, scale: Float): List<EnlargedBubble> {
        if (bubbles.isEmpty()) return emptyList()
        val floor = min(MIN_ENLARGE_SCALE, scale)
        val spread = bubbles.map { grown(it.box, scale) }.toMutableList()
        repeat(SEPARATION_PASSES) { pushApart(spread, bubbles) }
        val scales = FloatArray(bubbles.size) { scale }
        for (cluster in clusters(spread)) {
            if (cluster.size < 2) continue
            val uniform = clusterScale(cluster, spread, scale).coerceIn(floor, scale)
            cluster.forEach { scales[it] = uniform }
        }
        val targets = bubbles.indices.map { grown(bubbles[it].box, scales[it]) }.toMutableList()
        repeat(SEPARATION_PASSES) { pushApart(targets, bubbles) }
        return bubbles.indices.map { EnlargedBubble(bubbles[it], scales[it], targets[it]) }
    }

    private fun clusters(targets: List<Rect>): Collection<List<Int>> {
        val parent = IntArray(targets.size) { it }
        fun root(i: Int): Int = if (parent[i] == i) i else root(parent[i]).also { parent[i] = it }
        for (i in targets.indices) for (j in i + 1 until targets.size) {
            if (targets[i].overlaps(targets[j])) parent[root(i)] = root(j)
        }
        return targets.indices.groupBy { root(it) }.values
    }

    private fun clusterScale(cluster: List<Int>, spread: List<Rect>, scale: Float): Float {
        var limit = scale
        for (i in cluster.indices) for (j in i + 1 until cluster.size) {
            val a = spread[cluster[i]]
            val b = spread[cluster[j]]
            if (a.overlaps(b)) limit = min(limit, separatingScale(a, b, scale))
        }
        return limit
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
