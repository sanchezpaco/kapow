package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

const val BUBBLE_ENLARGE_SCALE = 1.3f
val BUBBLE_SCALE_RANGE = 1.1f..2f
private const val SEPARATION_PASSES = 16
private const val MIN_ENLARGE_SCALE = 1.15f
private val COVERAGE_STEPS = listOf(1f, 0.7f, 0.4f)
private const val RESIDUAL_PUSH_PASSES = 4

data class EnlargedBubble(val bubble: SpeechBubble, val scale: Float, val target: Rect) {
    fun map(point: Offset): Offset = Offset(
        target.left + (point.x - bubble.box.left) * scale,
        target.top + (point.y - bubble.box.top) * scale,
    )
}

object BubbleLayout {

    fun enlarge(bubbles: List<SpeechBubble>, scale: Float, panels: List<Rect> = emptyList()): List<EnlargedBubble> {
        if (bubbles.isEmpty()) return emptyList()
        val floor = min(MIN_ENLARGE_SCALE, scale)
        val placed = bubbles.map { Placed(it, homePanel(it.box, panels)) }
        val spread = placed.map { it.grown(scale) }.toMutableList()
        separate(spread, placed)
        val scales = FloatArray(bubbles.size) { scale }
        for (cluster in clusters(spread)) {
            if (cluster.size < 2) continue
            val uniform = clusterScale(cluster, spread, scale).coerceIn(floor, scale)
            cluster.forEach { scales[it] = uniform }
        }
        val targets = placed.indices.map { placed[it].grown(scales[it]) }.toMutableList()
        separate(targets, placed)
        shrinkResidualOverlaps(targets, placed)
        return bubbles.indices.map { EnlargedBubble(bubbles[it], targets[it].width / bubbles[it].box.width, targets[it]) }
    }

    private class Placed(bubble: SpeechBubble, panel: Rect) {
        val box: Rect = bubble.box
        val bounds: Rect = Rect(
            min(panel.left, box.left), min(panel.top, box.top), max(panel.right, box.right), max(panel.bottom, box.bottom),
        )

        fun grown(scale: Float): Rect {
            val fitting = minOf(scale, bounds.width / box.width, bounds.height / box.height).coerceAtLeast(1f)
            val width = box.width * fitting
            val height = box.height * fitting
            return Rect(box.center.x - width / 2f, box.center.y - height / 2f, box.center.x + width / 2f, box.center.y + height / 2f).clamped()
        }

        fun Rect.clamped(): Rect {
            val x = left.coerceIn(bounds.left, max(bounds.left, bounds.right - width))
            val y = top.coerceIn(bounds.top, max(bounds.top, bounds.bottom - height))
            return Rect(x, y, x + width, y + height)
        }
    }

    private fun homePanel(box: Rect, panels: List<Rect>): Rect =
        panels.maxByOrNull { it.intersectionArea(box) }?.takeIf { it.intersectionArea(box) > 0f } ?: FullPagePanel

    private fun Rect.intersectionArea(other: Rect): Float =
        max(0f, min(right, other.right) - max(left, other.left)) * max(0f, min(bottom, other.bottom) - max(top, other.top))

    private fun separate(targets: MutableList<Rect>, placed: List<Placed>) {
        for (coverage in COVERAGE_STEPS) {
            repeat(SEPARATION_PASSES) { pushApart(targets, placed, coverage) }
            if (overlappingPairs(targets).isEmpty()) return
        }
    }

    private fun shrinkResidualOverlaps(targets: MutableList<Rect>, placed: List<Placed>) {
        val coverage = COVERAGE_STEPS.last()
        repeat(SEPARATION_PASSES) {
            val overlapping = overlappingPairs(targets)
            if (overlapping.isEmpty()) return
            for ((i, j) in overlapping) {
                val factor = separatingFactor(targets[i], targets[j])
                targets[i] = targets[i].shrunk(factor, placed[i], coverage)
                targets[j] = targets[j].shrunk(factor, placed[j], coverage)
            }
            repeat(RESIDUAL_PUSH_PASSES) { pushApart(targets, placed, coverage) }
        }
    }

    private fun overlappingPairs(targets: List<Rect>): List<Pair<Int, Int>> =
        targets.indices.flatMap { i -> (i + 1 until targets.size).filter { j -> targets[i].overlaps(targets[j]) }.map { i to it } }

    private fun separatingFactor(a: Rect, b: Rect): Float {
        val gapX = abs(a.center.x - b.center.x) / ((a.width + b.width) / 2f)
        val gapY = abs(a.center.y - b.center.y) / ((a.height + b.height) / 2f)
        return max(gapX, gapY).coerceAtMost(1f)
    }

    private fun Rect.shrunk(factor: Float, placed: Placed, coverage: Float): Rect {
        val width = max(placed.box.width, this.width * factor)
        val height = max(placed.box.height, this.height * factor)
        return with(placed) {
            Rect(center.x - width / 2f, center.y - height / 2f, center.x + width / 2f, center.y + height / 2f).covering(box, coverage).clamped()
        }
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

    private fun pushApart(targets: MutableList<Rect>, placed: List<Placed>, coverage: Float) {
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
            targets[i] = with(placed[i]) { a.translate(shift).covering(box, coverage).clamped() }
            targets[j] = with(placed[j]) { b.translate(-shift).covering(box, coverage).clamped() }
        }
    }

    private fun separatingScale(a: Rect, b: Rect, scale: Float): Float = (separatingFactor(a, b) * scale).coerceAtLeast(1f)

    private fun Rect.covering(box: Rect, coverage: Float): Rect {
        val slackX = box.width * (1f - coverage)
        val slackY = box.height * (1f - coverage)
        val x = left.between(box.right - width - slackX, box.left + slackX)
        val y = top.between(box.bottom - height - slackY, box.top + slackY)
        return Rect(x, y, x + width, y + height)
    }

    private fun Float.between(a: Float, b: Float) = coerceIn(min(a, b), max(a, b))
}
