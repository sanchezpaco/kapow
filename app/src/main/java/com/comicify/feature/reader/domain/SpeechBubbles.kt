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
private const val SEED_RADIUS = 3
private const val MAX_GROWTH_PASSES = 64
private const val MIN_GROWTH_SHARE = 0.01f
private const val MAX_GROWTH_FACTOR = 4
private const val GUTTER_MIN_RUN_FRACTION = 0.3f
private const val GUTTER_MAX_THICKNESS_FRACTION = 0.012f

data class SpeechBubble(val box: Rect, val outline: List<Offset>)

object SpeechBubbles {

    fun detect(pixels: IntArray, sourceWidth: Int, sourceHeight: Int, pool: Int): List<SpeechBubble> =
        detect(PixelClasses.classify(pixels, sourceWidth, sourceHeight, pool))

    fun detect(classes: PixelClasses): List<SpeechBubble> {
        val cream = BooleanArray(classes.solidPaper.size) { classes.solidPaper[it] && !classes.solidWhite[it] }
        return (silhouettes(classes.solidWhite, classes.width, classes.height) + silhouettes(cream, classes.width, classes.height))
            .let { mergeTouching(it) }
            .map { it.toBubble(classes.width, classes.height) }
    }

    private fun silhouettes(tone: BooleanArray, width: Int, height: Int): List<Silhouette> {
        val pageArea = width * height
        val minSide = (min(width, height) * MIN_BUBBLE_SIDE_FRACTION).toInt()
        val paper = withoutGutters(tone, width, height)
        val cores = paper.opened(width, height, SEED_RADIUS)
        val seeds = cores.segment(width, height, (pageArea * MIN_BUBBLE_AREA_FRACTION).toInt())
        val isTextLike = textLikeHole(min(width, height))
        return seeds.components
            .filter { !it.touchesBorder && it.pixels <= pageArea * MAX_BUBBLE_AREA_FRACTION }
            .mapNotNull { Blob.grown(it, cores, paper, width, height) }
            .filter { !it.touchesBorder(width, height) && it.fill >= MIN_BUBBLE_FILL && it.pixels <= pageArea * MAX_BUBBLE_AREA_FRACTION }
            .filter { min(it.box.width, it.box.height) >= minSide && aspect(it.box) <= MAX_BUBBLE_ASPECT }
            .filter { hasTextInside(it, isTextLike) }
            .map { it.silhouette(width, height) }
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

    private fun hasTextInside(blob: Blob, isTextLike: (Box) -> Boolean): Boolean {
        val holes = blob.interiorHoles()
        val holeCount = holes.count { it }
        val inkShare = holeCount / (blob.pixels + holeCount).toFloat()
        if (inkShare < MIN_INK_SHARE || inkShare > MAX_INK_SHARE) return false
        val lines = holes.segment(blob.box.width, blob.box.height, 1).components
        val lineInk = lines.filter { isTextLike(it.box) }.sumOf { it.pixels }
        return lineInk >= holeCount * MIN_LINE_INK_SHARE
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

private class Blob(val box: Box, val cells: BooleanArray) {
    val pixels: Int = cells.count { it }
    val fill: Float get() = pixels / box.area.toFloat()

    fun touchesBorder(width: Int, height: Int) = box.left == 0 || box.top == 0 || box.right == width || box.bottom == height

    fun contains(x: Int, y: Int) = box.contains(x, y) && cells[(y - box.top) * box.width + (x - box.left)]

    fun interiorHoles(): BooleanArray {
        val open = BooleanArray(cells.size) { !cells[it] }
        val reached = BooleanArray(cells.size)
        val w = box.width
        val h = box.height
        floodFrom(open, reached, w, h) { it < w || it >= w * (h - 1) || it % w == 0 || it % w == w - 1 }
        return BooleanArray(cells.size) { open[it] && !reached[it] }
    }

    fun silhouette(width: Int, height: Int): Silhouette {
        val spans = HashMap<Int, IntRange>()
        for (y in 0 until box.height) {
            var left = -1
            var right = -1
            for (x in 0 until box.width) {
                if (!cells[y * box.width + x]) continue
                if (left < 0) left = x
                right = x
            }
            if (left < 0) continue
            for (row in max(0, box.top + y - OUTLINE_MARGIN)..min(height - 1, box.top + y + OUTLINE_MARGIN)) {
                spans[row] = spans[row].widened(max(0, box.left + left - OUTLINE_MARGIN), min(width - 1, box.left + right + OUTLINE_MARGIN))
            }
        }
        return Silhouette(spans)
    }

    companion object {
        fun grown(seed: Component, cores: BooleanArray, paper: BooleanArray, width: Int, height: Int): Blob? {
            var blob = grownWithin(seed.box, paper, width, height) { x, y -> cores[y * width + x] }
            repeat(MAX_GROWTH_PASSES) {
                val larger = grownWithin(blob.box.inflate(1, width, height), paper, width, height) { x, y -> blob.contains(x, y) }
                if (larger.pixels - blob.pixels < blob.pixels * MIN_GROWTH_SHARE) return larger
                if (larger.box.area > seed.box.area * MAX_GROWTH_FACTOR) return null
                blob = larger
            }
            return null
        }

        private fun grownWithin(box: Box, paper: BooleanArray, width: Int, height: Int, isSeed: (Int, Int) -> Boolean): Blob {
            val w = box.width
            val h = box.height
            val open = BooleanArray(w * h) { paper[(box.top + it / w) * width + box.left + it % w] }
            val reached = BooleanArray(w * h)
            floodFrom(open, reached, w, h) { isSeed(box.left + it % w, box.top + it / w) }
            return Blob(box, reached)
        }
    }
}

private inline fun floodFrom(open: BooleanArray, reached: BooleanArray, w: Int, h: Int, isSeed: (Int) -> Boolean) {
    val stack = IntArray(w * h)
    var top = 0
    for (i in 0 until w * h) {
        if (open[i] && !reached[i] && isSeed(i)) { reached[i] = true; stack[top++] = i }
    }
    while (top > 0) {
        val p = stack[--top]
        val x = p % w
        if (x > 0 && open[p - 1] && !reached[p - 1]) { reached[p - 1] = true; stack[top++] = p - 1 }
        if (x < w - 1 && open[p + 1] && !reached[p + 1]) { reached[p + 1] = true; stack[top++] = p + 1 }
        if (p >= w && open[p - w] && !reached[p - w]) { reached[p - w] = true; stack[top++] = p - w }
        if (p < w * (h - 1) && open[p + w] && !reached[p + w]) { reached[p + w] = true; stack[top++] = p + w }
    }
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
