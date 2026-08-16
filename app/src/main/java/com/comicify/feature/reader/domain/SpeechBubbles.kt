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
private const val MIN_GROWTH_SHARE = 0.01f
private const val MAX_MARGIN_FRACTION = 0.024f
private const val MAX_WORD_GAP_FRACTION = 0.008f
private const val MAX_LINE_GAP_FRACTION = 0.012f
private const val MIN_LINE_PIXELS = 4
private const val MIN_WORD_HEIGHT_FRACTION = 0.004f
private const val MIN_BLOCK_DENSITY = 0.25f
private const val MIN_INK_BOUNDED_SHARE = 0.6f
private const val MIN_WORD_INK_SHARE = 0.5f
private const val MIN_OVERLAP_TO_MERGE = 0.15f
private const val GUTTER_MIN_RUN_FRACTION = 0.3f
private const val GUTTER_MAX_THICKNESS_FRACTION = 0.012f

data class SpeechBubble(val box: Rect, val outline: List<Offset>)

object SpeechBubbles {

    fun detect(pixels: IntArray, sourceWidth: Int, sourceHeight: Int, pool: Int): List<SpeechBubble> =
        detect(PixelClasses.classify(pixels, sourceWidth, sourceHeight, pool))

    fun detect(classes: PixelClasses): List<SpeechBubble> {
        val cream = BooleanArray(classes.solidPaper.size) { classes.solidPaper[it] && !classes.solidWhite[it] }
        val white = mergeTouching(silhouettes(classes.solidWhite, classes.ink, classes.width, classes.height))
        val captions = mergeTouching(silhouettes(cream, classes.ink, classes.width, classes.height))
        return mergeOverlapping(white + captions).map { it.toBubble(classes.width, classes.height) }
    }

    private fun silhouettes(tone: BooleanArray, ink: BooleanArray, width: Int, height: Int): List<Silhouette> {
        val pageArea = width * height
        val pageSide = min(width, height)
        val minSide = (pageSide * MIN_BUBBLE_SIDE_FRACTION).toInt()
        val paper = withoutGutters(tone, width, height)
        val isTextLike = textLikeHole(pageSide)
        val minWordHeight = (pageSide * MIN_WORD_HEIGHT_FRACTION).toInt()
        val holes = enclosedHoles(paper, width, height).segment(width, height, MIN_LINE_PIXELS)
        val inkPerHole = inkPerLabel(holes, ink)
        val words = holes.components
            .filter { isTextLike(it.box) && it.box.height >= minWordHeight && it.box.width >= it.box.height }
            .filter { inkPerHole[it.label] >= it.pixels * MIN_WORD_INK_SHARE }
        val blocks = TextBlocks.cluster(words, (pageSide * MAX_WORD_GAP_FRACTION).toInt(), (pageSide * MAX_LINE_GAP_FRACTION).toInt())
            .filter { it.density >= MIN_BLOCK_DENSITY }
            .map { it.box }
        val maxMargin = (pageSide * MAX_MARGIN_FRACTION).toInt()
        return blocks
            .map { Blob.grown(it, maxMargin, paper, width, height) }
            .filter { !it.touchesBorder(width, height) && it.fill >= MIN_BUBBLE_FILL && it.pixels <= pageArea * MAX_BUBBLE_AREA_FRACTION }
            .filter { min(it.box.width, it.box.height) >= minSide && aspect(it.box) <= MAX_BUBBLE_ASPECT }
            .filter { hasTextInside(it, isTextLike) && it.inkBoundedShare(paper, width, height) >= MIN_INK_BOUNDED_SHARE }
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
        val step = if (horizontal) 1 else width
        for (line in 0 until lines) {
            val origin = if (horizontal) line * width else line
            var start = 0
            while (start < length) {
                if (!mask[origin + start * step]) { start++; continue }
                var end = start
                while (end < length && mask[origin + end * step]) end++
                for (pos in start until end) runs[origin + pos * step] = end - start
                start = end
            }
        }
        return runs
    }

    private fun inkPerLabel(holes: Segmentation, ink: BooleanArray): IntArray {
        val counts = IntArray((holes.components.maxOfOrNull { it.label } ?: -1) + 1)
        for (i in holes.labels.indices) {
            val label = holes.labels[i]
            if (label != NO_LABEL && label < counts.size && ink[i]) counts[label]++
        }
        return counts
    }

    private fun enclosedHoles(paper: BooleanArray, width: Int, height: Int): BooleanArray {
        val open = BooleanArray(paper.size) { !paper[it] }
        val reached = BooleanArray(paper.size)
        floodFrom(open, reached, width, height) { it < width || it >= width * (height - 1) || it % width == 0 || it % width == width - 1 }
        return BooleanArray(paper.size) { open[it] && !reached[it] }
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

    private fun mergeTouching(silhouettes: List<Silhouette>) = merge(silhouettes) { a, b -> a.touches(b) || a.overlaps(b) }

    private fun mergeOverlapping(silhouettes: List<Silhouette>) = merge(silhouettes) { a, b -> a.overlaps(b) }

    private fun merge(silhouettes: List<Silhouette>, joins: (Silhouette, Silhouette) -> Boolean): List<Silhouette> {
        val merged = ArrayList<Silhouette>()
        silhouettes.sortedByDescending { it.box.area }.forEach { candidate ->
            val partner = merged.indexOfFirst { joins(it, candidate) }
            if (partner < 0) merged.add(candidate) else merged[partner] = merged[partner].union(candidate)
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

    fun inkBoundedShare(paper: BooleanArray, width: Int, height: Int): Float {
        var closed = 0
        var open = 0
        fun probe(x: Int, y: Int) {
            if (x < 0 || y < 0 || x >= width || y >= height || !paper[y * width + x]) closed++ else open++
        }
        for (y in 0 until box.height) {
            val first = (0 until box.width).firstOrNull { cells[y * box.width + it] } ?: continue
            val last = (box.width - 1 downTo 0).first { cells[y * box.width + it] }
            probe(box.left + first - 1, box.top + y)
            probe(box.left + last + 1, box.top + y)
        }
        for (x in 0 until box.width) {
            val first = (0 until box.height).firstOrNull { cells[it * box.width + x] } ?: continue
            val last = (box.height - 1 downTo 0).first { cells[it * box.width + x] }
            probe(box.left + x, box.top + first - 1)
            probe(box.left + x, box.top + last + 1)
        }
        return if (closed + open == 0) 0f else closed / (closed + open).toFloat()
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
        fun grown(block: Box, maxMargin: Int, paper: BooleanArray, width: Int, height: Int): Blob {
            val box = block.inflate(maxMargin, width, height)
            val w = box.width
            val h = box.height
            val open = BooleanArray(w * h) {
                val x = box.left + it % w
                val y = box.top + it / w
                paper[y * width + x] && block.distanceSquaredTo(x, y) <= maxMargin * maxMargin
            }
            val reached = BooleanArray(w * h)
            val ring = IntArray(w * h) { block.chebyshevDistanceTo(box.left + it % w, box.top + it / w) }
            val stack = IntArray(w * h)
            var total = 0
            for (radius in 1..maxMargin) {
                val added = spread(open, reached, ring, stack, w, h, radius)
                if (radius > 1 && added < total * MIN_GROWTH_SHARE) break
                total += added
            }
            return Blob(box, reached).cropped()
        }

        private fun spread(open: BooleanArray, reached: BooleanArray, ring: IntArray, stack: IntArray, w: Int, h: Int, radius: Int): Int {
            var top = 0
            var added = 0
            for (i in open.indices) {
                if (!open[i] || reached[i] || ring[i] != radius) continue
                val x = i % w
                val adjacent = radius == 1 ||
                    (x > 0 && reached[i - 1]) || (x < w - 1 && reached[i + 1]) || (i >= w && reached[i - w]) || (i < w * (h - 1) && reached[i + w])
                if (adjacent) { reached[i] = true; stack[top++] = i; added++ }
            }
            while (top > 0) {
                val p = stack[--top]
                val x = p % w
                if (x > 0 && open[p - 1] && !reached[p - 1] && ring[p - 1] <= radius) { reached[p - 1] = true; stack[top++] = p - 1; added++ }
                if (x < w - 1 && open[p + 1] && !reached[p + 1] && ring[p + 1] <= radius) { reached[p + 1] = true; stack[top++] = p + 1; added++ }
                if (p >= w && open[p - w] && !reached[p - w] && ring[p - w] <= radius) { reached[p - w] = true; stack[top++] = p - w; added++ }
                if (p < w * (h - 1) && open[p + w] && !reached[p + w] && ring[p + w] <= radius) { reached[p + w] = true; stack[top++] = p + w; added++ }
            }
            return added
        }
    }

    private fun cropped(): Blob {
        var left = box.width; var top = box.height; var right = -1; var bottom = -1
        for (i in cells.indices) {
            if (!cells[i]) continue
            val x = i % box.width
            val y = i / box.width
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
        if (right < 0) return Blob(Box(box.left, box.top, box.left, box.top), BooleanArray(0))
        val tight = Box(box.left + left, box.top + top, box.left + right + 1, box.top + bottom + 1)
        val trimmed = BooleanArray(tight.area) { cells[(top + it / tight.width) * box.width + left + it % tight.width] }
        return Blob(tight, trimmed)
    }
}

private class TextBlock(val words: List<Box>) {
    val box: Box = words.reduce(Box::union)
    val density: Float get() = words.sumOf { it.area } / box.area.toFloat()
}

private object TextBlocks {
    fun cluster(words: List<Component>, wordGap: Int, lineGap: Int): List<TextBlock> {
        val parent = IntArray(words.size) { it }
        fun root(i: Int): Int = if (parent[i] == i) i else root(parent[i]).also { parent[i] = it }
        for (i in words.indices) for (j in i + 1 until words.size) {
            if (near(words[i].box, words[j].box, wordGap, lineGap)) parent[root(i)] = root(j)
        }
        return words.indices.groupBy { root(it) }.values.map { members -> TextBlock(members.map { words[it].box }) }
    }

    private fun near(a: Box, b: Box, wordGap: Int, lineGap: Int) =
        a.left <= b.right + wordGap && b.left <= a.right + wordGap && a.top <= b.bottom + lineGap && b.top <= a.bottom + lineGap
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

    fun overlaps(other: Silhouette): Boolean =
        box.intersectionArea(other.box) >= MIN_OVERLAP_TO_MERGE * min(box.area, other.box.area)

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
