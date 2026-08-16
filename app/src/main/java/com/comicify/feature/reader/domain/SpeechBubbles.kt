package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min

private const val MIN_BUBBLE_AREA_FRACTION = 0.0005f
private const val MAX_BUBBLE_AREA_FRACTION = 0.12f
private const val MIN_BUBBLE_FILL = 0.35f
private const val MIN_BUBBLE_SIDE_FRACTION = 0.012f
private const val MAX_BUBBLE_ASPECT = 6f
private const val MIN_INK_SHARE = 0.03f
private const val MAX_INK_SHARE = 0.5f
private const val MAX_LINE_HEIGHT_FRACTION = 0.018f
private const val MAX_TEXT_BLOCK_HEIGHT_FRACTION = 0.045f
private const val MIN_TEXT_BLOCK_ASPECT = 1.2f
private const val MIN_LINE_INK_SHARE = 0.75f
private const val OUTLINE_MARGIN = 2
private const val KEYLINE_RADIUS = 1
private const val GUTTER_MIN_RUN_FRACTION = 0.3f
private const val GUTTER_MAX_THICKNESS_FRACTION = 0.012f

data class SpeechBubble(val box: Rect, val outline: List<Offset>)

object SpeechBubbles {

    fun detect(pixels: IntArray, sourceWidth: Int, sourceHeight: Int, pool: Int): List<SpeechBubble> =
        detect(PixelClasses.classify(pixels, sourceWidth, sourceHeight, pool))

    fun detect(classes: PixelClasses): List<SpeechBubble> {
        val width = classes.width
        val height = classes.height
        val pageArea = width * height
        val minSide = (min(width, height) * MIN_BUBBLE_SIDE_FRACTION).toInt()
        val paper = withoutGutters(classes.solidPaper, width, height).opened(width, height, KEYLINE_RADIUS)
        val segmentation = paper.segment(width, height, (pageArea * MIN_BUBBLE_AREA_FRACTION).toInt())
        val isTextLike = textLikeHole(min(width, height))
        return segmentation.components
            .filter { !it.touchesBorder && it.fill >= MIN_BUBBLE_FILL && it.pixels <= pageArea * MAX_BUBBLE_AREA_FRACTION }
            .filter { min(it.box.width, it.box.height) >= minSide && aspect(it.box) <= MAX_BUBBLE_ASPECT }
            .filter { hasTextInside(it, segmentation.labels, width, isTextLike) }
            .map { silhouette(it, segmentation.labels, width, height) }
            .let { mergeTouching(it) }
            .map { it.toBubble(width, height) }
    }

    private fun withoutGutters(paper: BooleanArray, width: Int, height: Int): BooleanArray {
        val horizontalRuns = runLengths(paper, width, height, horizontal = true)
        val verticalRuns = runLengths(paper, width, height, horizontal = false)
        val maxThickness = (min(width, height) * GUTTER_MAX_THICKNESS_FRACTION).toInt()
        val minHorizontalRun = (width * GUTTER_MIN_RUN_FRACTION).toInt()
        val minVerticalRun = (height * GUTTER_MIN_RUN_FRACTION).toInt()
        return BooleanArray(paper.size) { i ->
            paper[i] &&
                !(horizontalRuns[i] >= minHorizontalRun && verticalRuns[i] <= maxThickness) &&
                !(verticalRuns[i] >= minVerticalRun && horizontalRuns[i] <= maxThickness)
        }
    }

    private fun runLengths(mask: BooleanArray, width: Int, height: Int, horizontal: Boolean): IntArray {
        val runs = IntArray(mask.size)
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        for (line in 0 until lines) {
            var start = 0
            while (start < length) {
                val at = { pos: Int -> if (horizontal) line * width + pos else pos * width + line }
                if (!mask[at(start)]) { start++; continue }
                var end = start
                while (end < length && mask[at(end)]) end++
                for (pos in start until end) runs[at(pos)] = end - start
                start = end
            }
        }
        return runs
    }

    private fun textLikeHole(pageSide: Int): (Box) -> Boolean {
        val maxLineHeight = (pageSide * MAX_LINE_HEIGHT_FRACTION).toInt()
        val maxBlockHeight = (pageSide * MAX_TEXT_BLOCK_HEIGHT_FRACTION).toInt()
        return { hole -> hole.height <= maxLineHeight || (hole.height <= maxBlockHeight && hole.width >= hole.height * MIN_TEXT_BLOCK_ASPECT) }
    }

    private fun hasTextInside(component: Component, labels: IntArray, width: Int, isTextLike: (Box) -> Boolean): Boolean {
        val box = component.box
        val holes = interiorHoles(component, labels, width)
        val holeCount = holes.count { it }
        val inkShare = holeCount / (component.pixels + holeCount).toFloat()
        if (inkShare < MIN_INK_SHARE || inkShare > MAX_INK_SHARE) return false
        val lines = holes.segment(box.width, box.height, 1).components
        val lineInk = lines.filter { isTextLike(it.box) }.sumOf { it.pixels }
        return lineInk >= holeCount * MIN_LINE_INK_SHARE
    }

    private fun interiorHoles(component: Component, labels: IntArray, width: Int): BooleanArray {
        val box = component.box
        val w = box.width
        val h = box.height
        val open = BooleanArray(w * h) { labels[(box.top + it / w) * width + box.left + it % w] != component.label }
        val reached = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var top = 0
        for (i in 0 until w * h) {
            val edge = i < w || i >= w * (h - 1) || i % w == 0 || i % w == w - 1
            if (edge && open[i] && !reached[i]) { reached[i] = true; stack[top++] = i }
        }
        while (top > 0) {
            val p = stack[--top]
            val x = p % w
            if (x > 0 && open[p - 1] && !reached[p - 1]) { reached[p - 1] = true; stack[top++] = p - 1 }
            if (x < w - 1 && open[p + 1] && !reached[p + 1]) { reached[p + 1] = true; stack[top++] = p + 1 }
            if (p >= w && open[p - w] && !reached[p - w]) { reached[p - w] = true; stack[top++] = p - w }
            if (p < w * (h - 1) && open[p + w] && !reached[p + w]) { reached[p + w] = true; stack[top++] = p + w }
        }
        return BooleanArray(w * h) { open[it] && !reached[it] }
    }

    private fun silhouette(component: Component, labels: IntArray, width: Int, height: Int): Silhouette {
        val box = component.box
        val spans = HashMap<Int, IntRange>()
        for (y in box.top until box.bottom) {
            var left = -1
            var right = -1
            for (x in box.left until box.right) {
                if (labels[y * width + x] != component.label) continue
                if (left < 0) left = x
                right = x
            }
            if (left < 0) continue
            for (row in max(0, y - OUTLINE_MARGIN)..min(height - 1, y + OUTLINE_MARGIN)) {
                spans[row] = spans[row].widened(max(0, left - OUTLINE_MARGIN), min(width - 1, right + OUTLINE_MARGIN))
            }
        }
        return Silhouette(spans)
    }

    private fun mergeTouching(silhouettes: List<Silhouette>): List<Silhouette> {
        val merged = ArrayList<Silhouette>()
        silhouettes.sortedByDescending { it.box.area }.forEach { candidate ->
            val touching = merged.indexOfFirst { it.touches(candidate) }
            if (touching < 0) merged.add(candidate) else merged[touching] = merged[touching].union(candidate)
        }
        return merged
    }

    private fun aspect(box: Box) = max(box.width, box.height) / min(box.width, box.height).toFloat()
}

private fun IntRange?.widened(left: Int, right: Int) =
    if (this == null) left..right else min(first, left)..max(last, right)

private class Silhouette(val spans: Map<Int, IntRange>) {
    val box: Box = Box(
        spans.values.minOf { it.first },
        spans.keys.min(),
        spans.values.maxOf { it.last } + 1,
        spans.keys.max() + 1,
    )

    fun touches(other: Silhouette): Boolean =
        spans.any { (row, span) -> other.spans[row]?.let { it.first <= span.last && span.first <= it.last } ?: false }

    fun union(other: Silhouette): Silhouette {
        val joined = HashMap(spans)
        other.spans.forEach { (row, span) -> joined[row] = joined[row].widened(span.first, span.last) }
        return Silhouette(joined)
    }

    fun toBubble(width: Int, height: Int): SpeechBubble {
        val rows = spans.keys.sorted()
        val leftEdge = rows.map { Offset(spans.getValue(it).first / width.toFloat(), it / height.toFloat()) }
        val rightEdge = rows.reversed().map { Offset((spans.getValue(it).last + 1) / width.toFloat(), (it + 1) / height.toFloat()) }
        return SpeechBubble(box.toRect(width, height), leftEdge + rightEdge)
    }
}
