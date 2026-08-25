package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min

const val BUBBLE_ENLARGE_SCALE = 1.3f
val BUBBLE_SCALE_RANGE = 1.1f..2f
private const val SEPARATION_PASSES = 16
private val COVERAGE_STEPS = listOf(1f, 0.85f, 0.5f, 0f)
private const val OVERLAP_EPSILON = 1e-4f
private const val MAX_GROUP_SIDE = 0.3f
private const val SHRINK_STEP = 0.9f

data class EnlargedBubble(val bubble: SpeechBubble, val scale: Float, val target: Rect) {
    fun map(point: Offset): Offset = Offset(
        target.left + (point.x - bubble.box.left) * scale,
        target.top + (point.y - bubble.box.top) * scale,
    )
}

object BubbleLayout {

    fun enlarge(bubbles: List<SpeechBubble>, scale: Float): List<EnlargedBubble> {
        if (bubbles.isEmpty()) return emptyList()
        val boxes = bubbles.map { it.box }
        val anchors = groupAnchors(boxes, scale)
        val placed = boxes.indices.map { Placed(boxes[it], anchors[it]) }
        val targets = placed.map { it.grown(scale) }.toMutableList()
        separate(targets, placed)
        shrinkResidualOverlaps(targets, placed)
        return bubbles.indices.map { EnlargedBubble(bubbles[it], targets[it].width / bubbles[it].box.width, targets[it]) }
    }

    private fun groupAnchors(boxes: List<Rect>, scale: Float): List<Anchor> {
        val grown = boxes.map { it.scaledAbout(it.center, scale) }
        val parent = IntArray(boxes.size) { it }
        fun root(i: Int): Int = if (parent[i] == i) i else root(parent[i]).also { parent[i] = it }
        for (i in boxes.indices) for (j in i + 1 until boxes.size) {
            if (grown[i].collides(grown[j])) parent[root(i)] = root(j)
        }
        val anchors = boxes.indices.groupBy { root(it) }.mapValues { (_, members) ->
            val union = members.map { boxes[it] }.reduce { union, box -> Rect(min(union.left, box.left), min(union.top, box.top), max(union.right, box.right), max(union.bottom, box.bottom)) }
            if (members.size > 1 && max(union.width, union.height) <= MAX_GROUP_SIDE) Anchor(union.center, union.fittingScale()) else null
        }
        return boxes.indices.map { anchors[root(it)] ?: Anchor(boxes[it].center, Float.MAX_VALUE) }
    }

    private fun Rect.fittingScale(): Float = minOf(
        center.x / max(center.x - left, Float.MIN_VALUE),
        (1f - center.x) / max(right - center.x, Float.MIN_VALUE),
        center.y / max(center.y - top, Float.MIN_VALUE),
        (1f - center.y) / max(bottom - center.y, Float.MIN_VALUE),
    ).coerceAtLeast(1f)

    private class Anchor(val centre: Offset, val fit: Float)

    private fun Rect.scaledAbout(anchor: Offset, scale: Float) = Rect(
        anchor.x + (left - anchor.x) * scale, anchor.y + (top - anchor.y) * scale,
        anchor.x + (right - anchor.x) * scale, anchor.y + (bottom - anchor.y) * scale,
    )

    private class Placed(val box: Rect, private val anchor: Anchor) {
        val bounds: Rect = Rect(min(0f, box.left), min(0f, box.top), max(1f, box.right), max(1f, box.bottom))

        fun grown(scale: Float): Rect = box.scaledAbout(anchor.centre, min(scale, anchor.fit).coerceAtLeast(1f)).clamped()

        fun Rect.clamped(): Rect {
            val x = left.coerceIn(bounds.left, max(bounds.left, bounds.right - width))
            val y = top.coerceIn(bounds.top, max(bounds.top, bounds.bottom - height))
            return Rect(x, y, x + width, y + height)
        }
    }

    private fun separate(targets: MutableList<Rect>, placed: List<Placed>) {
        for (coverage in COVERAGE_STEPS) {
            repeat(SEPARATION_PASSES) { pushApart(targets, placed, coverage) }
            if (overlappingPairs(targets).isEmpty()) return
        }
    }

    private fun shrinkResidualOverlaps(targets: MutableList<Rect>, placed: List<Placed>) {
        repeat(SEPARATION_PASSES) {
            val overlapping = overlappingPairs(targets)
            if (overlapping.isEmpty()) return
            for ((i, j) in overlapping) {
                targets[i] = targets[i].reduced(placed[i])
                targets[j] = targets[j].reduced(placed[j])
            }
            pushApart(targets, placed, 0f)
        }
    }

    private fun Rect.reduced(placed: Placed): Rect {
        val current = width / placed.box.width
        val next = max(1f, current * SHRINK_STEP)
        return with(placed) { scaledAbout(center, next / current).clamped() }
    }

    private fun overlappingPairs(targets: List<Rect>): List<Pair<Int, Int>> =
        targets.indices.flatMap { i -> (i + 1 until targets.size).filter { j -> targets[i].collides(targets[j]) }.map { i to it } }

    private fun Rect.collides(other: Rect): Boolean =
        right > other.left + OVERLAP_EPSILON && other.right > left + OVERLAP_EPSILON &&
            bottom > other.top + OVERLAP_EPSILON && other.bottom > top + OVERLAP_EPSILON

    private fun pushApart(targets: MutableList<Rect>, placed: List<Placed>, coverage: Float) {
        for (i in targets.indices) for (j in i + 1 until targets.size) {
            val a = targets[i]
            val b = targets[j]
            if (!a.collides(b)) continue
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

    private fun Rect.covering(box: Rect, coverage: Float): Rect {
        val slackX = box.width * (1f - coverage)
        val slackY = box.height * (1f - coverage)
        val x = left.between(box.right - width - slackX, box.left + slackX)
        val y = top.between(box.bottom - height - slackY, box.top + slackY)
        return Rect(x, y, x + width, y + height)
    }

    private fun Float.between(a: Float, b: Float) = coerceIn(min(a, b), max(a, b))
}
