package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min

const val BUBBLE_ENLARGE_SCALE = 1.3f
val BUBBLE_SCALE_RANGE = 1.1f..2f
internal const val CONTAINED_SCALE_FLOOR = 1.15f
internal const val TEXT_BODY_SHARE = 0.9f
private const val SEPARATION_PASSES = 16
private const val FULL_COVERAGE = 1f
private val COVERAGE_STEPS = listOf(FULL_COVERAGE, 0.85f, 0.5f, 0f)
private const val OVERLAP_EPSILON = 1e-4f
private const val SHRINK_STEP = 0.9f
private const val PUSH_SHARE = 0.25f
private val ANCHOR_SHARES = listOf(0f, 0.5f, 1f)
private const val COLLISION_OUTLINE_VERTICES = 48
private const val INTRUSION_EPSILON = 1e-3f
private const val PAIR_COLLISION_COST = 2

data class EnlargedBubble(val bubble: SpeechBubble, val scale: Float, val target: Rect) {
    fun map(point: Offset): Offset = Offset(
        target.left + (point.x - bubble.box.left) * scale,
        target.top + (point.y - bubble.box.top) * scale,
    )
}

object BubbleLayout {

    fun enlarge(bubbles: List<SpeechBubble>, scale: Float): List<EnlargedBubble> {
        if (bubbles.isEmpty()) return emptyList()
        val placed = bubbles.map { Placed(it.box) }
        val targets = placed.map { it.grown(scale) }.toMutableList()
        val silhouettes = Silhouettes(bubbles)
        separate(targets, placed, silhouettes)
        shrinkOverlapping(targets, placed, silhouettes, floor = 1f, coverage = 0f)
        return bubbles.indices.map { EnlargedBubble(bubbles[it], targets[it].width / bubbles[it].box.width, targets[it]) }
    }

    private fun Rect.scaledAbout(anchor: Offset, scale: Float) = Rect(
        anchor.x + (left - anchor.x) * scale, anchor.y + (top - anchor.y) * scale,
        anchor.x + (right - anchor.x) * scale, anchor.y + (bottom - anchor.y) * scale,
    )

    private class Placed(val box: Rect) {
        val bounds: Rect = Rect(min(0f, box.left), min(0f, box.top), max(1f, box.right), max(1f, box.bottom))

        fun grown(scale: Float): Rect = box.scaledAbout(box.center, scale.coerceAtLeast(1f)).clamped()

        fun Rect.clamped(): Rect {
            val x = left.coerceIn(bounds.left, max(bounds.left, bounds.right - width))
            val y = top.coerceIn(bounds.top, max(bounds.top, bounds.bottom - height))
            return Rect(x, y, x + width, y + height)
        }
    }

    private data class Placement(val i: Int, val j: Int, val a: Rect, val b: Rect)

    private class Silhouettes(detected: List<SpeechBubble>) {
        private val bubbles = detected.map { bubble ->
            val step = bubble.outlines.sumOf { perimeter(it).toDouble() }.toFloat() / COLLISION_OUTLINE_VERTICES
            bubble.copy(outlines = bubble.outlines.map { sampled(it, step) })
        }
        private val extents = bubbles.map { bubble -> bubble.outlines.flatten().let { points -> Rect(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y }) } }
        private val original = bubbles.indices.map { i -> bubbles.indices.map { j -> if (i < j) mutualIntrusion(i, bubbles[i].box, j, bubbles[j].box) else 0f } }

        private val collisions = HashMap<Placement, Boolean>()

        fun collide(i: Int, j: Int, a: Rect, b: Rect): Boolean =
            a.collides(b) && collisions.getOrPut(Placement(i, j, a, b)) { mutualIntrusion(i, a, j, b) > original[min(i, j)][max(i, j)] + INTRUSION_EPSILON }

        private fun mutualIntrusion(i: Int, a: Rect, j: Int, b: Rect): Float = intrusion(j, b, i, a) + intrusion(i, a, j, b)

        private fun placedExtent(index: Int, at: Rect): Rect {
            val box = bubbles[index].box
            val scale = at.width / box.width
            val extent = extents[index]
            return Rect(at.left + (extent.left - box.left) * scale, at.top + (extent.top - box.top) * scale, at.left + (extent.right - box.left) * scale, at.top + (extent.bottom - box.top) * scale)
        }

        private fun edges(outline: List<Offset>) = outline.indices.map { outline[it] to outline[(it + 1) % outline.size] }

        private fun perimeter(outline: List<Offset>) = edges(outline).sumOf { (a, b) -> (b - a).getDistance().toDouble() }.toFloat()

        private fun sampled(outline: List<Offset>, step: Float): List<Offset> {
            if (step <= 0f) return outline
            val edges = edges(outline)
            val points = ArrayList<Offset>(COLLISION_OUTLINE_VERTICES)
            var next = 0f
            var walked = 0f
            edges.forEach { (a, b) ->
                val length = (b - a).getDistance()
                while (next < walked + length) {
                    val t = (next - walked) / length
                    points.add(Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
                    next += step
                }
                walked += length
            }
            return points
        }

        private fun intrusion(from: Int, at: Rect, into: Int, target: Rect): Float {
            val source = bubbles[from]
            val host = bubbles[into]
            val outScale = at.width / source.box.width
            val inScale = host.box.width / target.width / TEXT_BODY_SHARE
            val body = target.scaledAbout(target.center, TEXT_BODY_SHARE)
            if (!placedExtent(from, at).overlaps(body)) return 0f
            val hostExtent = extents[into]
            val hostScale = target.width / host.box.width
            val hostOutline by lazy { host.outlines.flatten().map { Offset(target.left + (it.x - host.box.left) * hostScale, target.top + (it.y - host.box.top) * hostScale) } }
            val size = min(target.width, target.height)
            var depth = 0f
            source.outlines.forEach { outline ->
                outline.forEach { point ->
                    val onPage = Offset(at.left + (point.x - source.box.left) * outScale, at.top + (point.y - source.box.top) * outScale)
                    val inHost = Offset(host.box.left + (onPage.x - body.left) * inScale, host.box.top + (onPage.y - body.top) * inScale)
                    val inside = body.contains(onPage) && hostExtent.contains(inHost) && host.contains(inHost)
                    if (inside) depth += hostOutline.minOf { (it - onPage).getDistance() } / size
                }
            }
            return depth
        }
    }

    private fun separate(targets: MutableList<Rect>, placed: List<Placed>, silhouettes: Silhouettes) {
        for (coverage in COVERAGE_STEPS) {
            repeat(SEPARATION_PASSES) {
                pushApart(targets, placed, silhouettes, coverage)
                if (coverage == FULL_COVERAGE) reanchorColliding(targets, placed, silhouettes)
            }
            if (overlappingPairs(targets, silhouettes).isEmpty()) return
            if (coverage == FULL_COVERAGE) shrinkOverlapping(targets, placed, silhouettes, CONTAINED_SCALE_FLOOR, coverage)
            if (overlappingPairs(targets, silhouettes).isEmpty()) return
        }
    }

    private fun shrinkOverlapping(targets: MutableList<Rect>, placed: List<Placed>, silhouettes: Silhouettes, floor: Float, coverage: Float) {
        repeat(SEPARATION_PASSES) {
            val overlapping = overlappingPairs(targets, silhouettes)
            if (overlapping.isEmpty()) return
            for ((i, j) in overlapping) {
                targets[i] = targets[i].reduced(placed[i], floor)
                targets[j] = targets[j].reduced(placed[j], floor)
            }
            pushApart(targets, placed, silhouettes, coverage)
            if (coverage == FULL_COVERAGE) reanchorColliding(targets, placed, silhouettes)
        }
    }

    private fun reanchorColliding(targets: MutableList<Rect>, placed: List<Placed>, silhouettes: Silhouettes) {
        for ((i, j) in overlappingPairs(targets, silhouettes)) reanchorPair(i, j, targets, placed, silhouettes)
    }

    private fun reanchorPair(i: Int, j: Int, targets: MutableList<Rect>, placed: List<Placed>, silhouettes: Silhouettes) {
        val others = targets.indices.filter { it != i && it != j }
        val positionsI = containedPositions(targets[i], placed[i])
        val positionsJ = containedPositions(targets[j], placed[j])
        val costI = positionsI.map { a -> others.count { silhouettes.collide(i, it, a, targets[it]) } }
        val costJ = positionsJ.map { b -> others.count { silhouettes.collide(j, it, b, targets[it]) } }
        var bestCost = pairCost(i, j, targets, silhouettes)
        var best: Pair<Rect, Rect>? = null
        positionsI.forEachIndexed { ai, a ->
            positionsJ.forEachIndexed { bi, b ->
                val cost = costI[ai] + costJ[bi] + if (silhouettes.collide(i, j, a, b)) PAIR_COLLISION_COST else 0
                if (cost < bestCost) {
                    bestCost = cost
                    best = a to b
                }
            }
        }
        best?.let { (a, b) ->
            targets[i] = a
            targets[j] = b
        }
    }

    private fun pairCost(i: Int, j: Int, targets: List<Rect>, silhouettes: Silhouettes): Int =
        collisions(i, targets, silhouettes) + collisions(j, targets, silhouettes)

    private fun containedPositions(current: Rect, placed: Placed): List<Rect> {
        val box = placed.box
        return ANCHOR_SHARES.flatMap { sx ->
            ANCHOR_SHARES.map { sy ->
                val x = box.left - (current.width - box.width) * sx
                val y = box.top - (current.height - box.height) * sy
                with(placed) { Rect(x, y, x + current.width, y + current.height).clamped() }
            }
        }
    }

    private fun collisions(k: Int, targets: List<Rect>, silhouettes: Silhouettes): Int =
        targets.indices.count { other -> other != k && silhouettes.collide(k, other, targets[k], targets[other]) }

    private fun Rect.reduced(placed: Placed, floor: Float): Rect {
        val current = width / placed.box.width
        val next = max(floor, current * SHRINK_STEP)
        return with(placed) { scaledAbout(center, next / current).clamped() }
    }

    private fun overlappingPairs(targets: List<Rect>, silhouettes: Silhouettes): List<Pair<Int, Int>> =
        targets.indices.flatMap { i -> (i + 1 until targets.size).filter { j -> silhouettes.collide(i, j, targets[i], targets[j]) }.map { i to it } }

    private fun Rect.collides(other: Rect): Boolean =
        right > other.left + OVERLAP_EPSILON && other.right > left + OVERLAP_EPSILON &&
            bottom > other.top + OVERLAP_EPSILON && other.bottom > top + OVERLAP_EPSILON

    private fun pushApart(targets: MutableList<Rect>, placed: List<Placed>, silhouettes: Silhouettes, coverage: Float) {
        for (i in targets.indices) for (j in i + 1 until targets.size) {
            val a = targets[i]
            val b = targets[j]
            if (!silhouettes.collide(i, j, a, b)) continue
            val overlapX = min(a.right, b.right) - max(a.left, b.left)
            val overlapY = min(a.bottom, b.bottom) - max(a.top, b.top)
            val shift = if (overlapX < overlapY) {
                Offset(if (a.center.x <= b.center.x) -overlapX * PUSH_SHARE else overlapX * PUSH_SHARE, 0f)
            } else {
                Offset(0f, if (a.center.y <= b.center.y) -overlapY * PUSH_SHARE else overlapY * PUSH_SHARE)
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
