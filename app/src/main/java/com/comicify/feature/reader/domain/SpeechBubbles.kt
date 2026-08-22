package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min

private const val MAX_BUBBLE_AREA_FRACTION = 0.12f
private const val MIN_BUBBLE_FILL = 0.35f
private const val MIN_BUBBLE_SIDE_FRACTION = 0.012f
private const val MAX_BUBBLE_ASPECT = 6f
private const val MAX_BUBBLE_SPAN_FRACTION = 0.5f
private const val MIN_INK_SHARE = 0.03f
private const val MAX_INK_SHARE = 0.5f
private const val MAX_LINE_HEIGHT_FRACTION = 0.018f
private const val MAX_TEXT_BLOCK_HEIGHT_FRACTION = 0.045f
private const val MIN_TEXT_BLOCK_ASPECT = 1.2f
private const val MIN_LINE_ASPECT = 1.5f
private const val MIN_BLOCK_ASPECT = 0.6f
private const val MIN_TALL_BLOCK_DENSITY = 0.4f
private const val MIN_LINE_INK_SHARE = 0.75f
private const val OUTLINE_MARGIN = 2
private const val OUTLINE_TOLERANCE = 0.75f
private const val MAX_WORD_GAP_FRACTION = 0.008f
private const val MAX_LINE_GAP_FRACTION = 0.012f
private const val TEXT_PAD_FACTOR = 1.2f
private const val MIN_TEXT_PAD_FRACTION = 0.004f
private const val LOCAL_LINE_HEIGHT_FACTOR = 1.4f
private const val LOCAL_BLOCK_HEIGHT_FACTOR = 3.5f
private const val NEGATIVE_BODY_RADIUS = 3
private const val MIN_LINE_PIXELS = 4
private const val MIN_WORD_HEIGHT_FRACTION = 0.004f
private const val MIN_BLOCK_DENSITY = 0.25f
private val INK_ON_PAPER = BlockRules(minWords = 2, maxDensity = Float.MAX_VALUE, minHoleInkShare = 0f, stripsBorderLines = false, narrowRelativeTo = NarrowRelativeTo.RINGS)
private val LIGHT_ON_DARK = BlockRules(minWords = 4, maxDensity = 1f, minHoleInkShare = 0.6f, stripsBorderLines = true, narrowRelativeTo = NarrowRelativeTo.AREA)
private const val OVERSIZED_WORD_FACTOR = 2
private const val MAX_OVERSIZED_WORD_SHARE = 0.5f
private const val MIN_MARGIN_PAPER_SHARE = 0.6f
private const val MAX_MARGIN_FRACTION = 0.024f
private const val MARGIN_BLOCK_FACTOR = 0.8f
private const val MAX_MARGIN_CAP_FRACTION = 0.08f
private const val MIN_GROWTH_SHARE = 0.01f
private const val MAX_SEED_EXTENSIONS = 5
private const val MIN_WORD_INK_SHARE = 0.5f
private const val MIN_OVERLAP_TO_MERGE = 0.15f
private const val GUTTER_MIN_RUN_FRACTION = 0.3f
private const val GUTTER_MAX_THICKNESS_FRACTION = 0.012f

data class SpeechBubble(val box: Rect, val outlines: List<List<Offset>>) {
    fun interiorSamples(perAxis: Int): List<Offset> =
        (0 until perAxis).flatMap { r ->
            (0 until perAxis).map { c ->
                Offset(box.left + box.width * (c + 0.5f) / perAxis, box.top + box.height * (r + 0.5f) / perAxis)
            }
        }.filter(::contains)

    fun contains(point: Offset): Boolean = outlines.any { contains(it, point) }

    private fun contains(outline: List<Offset>, point: Offset): Boolean {
        var inside = false
        var j = outline.lastIndex
        for (i in outline.indices) {
            val a = outline[i]
            val b = outline[j]
            if ((a.y > point.y) != (b.y > point.y) && point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }
}

object SpeechBubbles {

    fun detect(pixels: IntArray, sourceWidth: Int, sourceHeight: Int, pool: Int): List<SpeechBubble> =
        detect(PixelClasses.classify(pixels, sourceWidth, sourceHeight, pool))

    fun detect(classes: PixelClasses): List<SpeechBubble> {
        val cream = BooleanArray(classes.solidPaper.size) { classes.solidPaper[it] && !classes.solidWhite[it] }
        val darkBody = withSolidCore(classes.solidDark, classes.width, classes.height)
        val white = silhouettes(classes.solidWhite, classes.ink, INK_ON_PAPER, classes.width, classes.height)
        val captions = silhouettes(cream, classes.ink, INK_ON_PAPER, classes.width, classes.height)
        val negatives = silhouettes(darkBody, classes.light, LIGHT_ON_DARK, classes.width, classes.height)
        return mergeOverlapping(white + captions + negatives).map { it.toBubble(classes.width, classes.height) }
    }

    private fun silhouettes(tone: BooleanArray, ink: BooleanArray, rules: BlockRules, width: Int, height: Int): List<Blob> {
        val pageArea = width * height
        val pageSide = min(width, height)
        val minSide = (pageSide * MIN_BUBBLE_SIDE_FRACTION).toInt()
        val paper = withoutGutters(tone, width, height)
        val borderLines = if (rules.stripsBorderLines) thinLongBands(tone, width, height) else null
        val maxLineHeight = (pageSide * MAX_LINE_HEIGHT_FRACTION).toInt()
        val maxBlockHeight = (pageSide * MAX_TEXT_BLOCK_HEIGHT_FRACTION).toInt()
        val isTextLike = textLikeHole(maxLineHeight, maxBlockHeight)
        val minWordHeight = (pageSide * MIN_WORD_HEIGHT_FRACTION).toInt()
        val holes = enclosedHoles(paper, width, height).segment(width, height, MIN_LINE_PIXELS)
        val inkPerHole = inkPerLabel(holes, ink)
        val isLine = { box: Box -> isTextLike(box) && box.height >= minWordHeight && box.width >= box.height * MIN_LINE_ASPECT }
        val inkHoles = holes.components.filter { inkPerHole[it.label] >= it.pixels * MIN_WORD_INK_SHARE }
        val words = inkHoles.filter { it.box.height in minWordHeight..maxBlockHeight && it.box.width >= minWordHeight }
        val paperLabels = paper.segment(width, height, 1).labels
        val surroundings = words.map { paperLabels[cellAbove(it, holes.labels, width)] }
        val blocks = TextBlocks.cluster(words, surroundings, (pageSide * MAX_WORD_GAP_FRACTION).toInt(), (pageSide * MAX_LINE_GAP_FRACTION).toInt())
            .filter { rules.accept(it) && it.lines.any(isLine) }
        val pageMargin = (pageSide * MAX_MARGIN_FRACTION).toInt()
        val foreignInk = { block: TextBlock, box: Box -> inkHoles.any { it.box !in block.words && it.box.intersectionArea(box) > 0 } }
        val fallback = { block: TextBlock -> paddedText(block, paper, foreignInk, pageSide, width, height) }
        val cutWords = { blob: Blob -> blob.cut(words, holes.labels, width) }
        val isLettered = { blob: Blob -> blob.holeInkShare(ink, width) >= rules.minHoleInkShare }
        val grow = { seed: List<Box> -> container(seed, paper, isLettered, cutWords, rules.narrowRelativeTo, pageMargin, pageArea, pageSide, minSide, width, height) }
        val contained = blocks.map { block -> Containment(block, grow(block.words)) }
        val loose = contained.filter { it.container == null && it.lettered }.mapNotNull { fallback(it.block) }
        val whole = mergeSharingPaper(contained.filter { it.container != null }).flatMap { group ->
            val container = group.container!!
            if (cutWords(container).isEmpty()) return@flatMap listOf(container)
            val rejoined = rejoined(group.blocks.flatMap { it.words }, container, words, grow, cutWords)
            if (rejoined != null) listOf(rejoined) else group.blocks.mapNotNull(fallback)
        }
        return (whole + loose).map { blob -> (borderLines?.let { blob.without(it, width) } ?: blob).dilated(OUTLINE_MARGIN, width, height) }
    }

    private fun rejoined(
        seed: List<Box>, container: Blob, words: List<Component>, grow: (List<Box>) -> Candidate, cutWords: (Blob) -> List<Component>,
    ): Blob? {
        var extended = seed
        var current = container
        repeat(MAX_SEED_EXTENSIONS) {
            extended = (extended + words.filter { it.box.intersectionArea(current.box) > 0 }.map { it.box }).distinct()
            current = grow(extended).container ?: return null
            if (cutWords(current).isEmpty()) return current
        }
        return null
    }

    private class Candidate(val container: Blob?, val lettered: Boolean)

    private class Containment(val blocks: List<TextBlock>, val container: Blob?, val lettered: Boolean = true) {
        constructor(block: TextBlock, candidate: Candidate) : this(listOf(block), candidate.container, candidate.lettered)

        val block: TextBlock get() = blocks.single()

        fun union(other: Containment) = Containment(blocks + other.blocks, container!!.union(other.container!!))
    }

    private fun container(
        words: List<Box>, paper: BooleanArray, isLettered: (Blob) -> Boolean, cutWords: (Blob) -> List<Component>, narrowRelativeTo: NarrowRelativeTo,
        pageMargin: Int, pageArea: Int, pageSide: Int, minSide: Int, width: Int, height: Int,
    ): Candidate {
        val seed = words.reduce(Box::union)
        val isClean = { blob: Blob -> isCleanContainer(blob, seed, pageArea, pageSide, minSide, width, height) }
        val near = Blob.grown(words, blockReach(seed, pageMargin, pageSide), paper, narrowRelativeTo, width, height)
        if (!isLettered(near.blob)) return Candidate(null, lettered = false)
        if (isClean(near.blob) && !near.reachLimited) return Candidate(near.blob, lettered = true)
        val far = Blob.grown(words, maxReach(pageSide), paper, narrowRelativeTo, width, height).blob
        if (isClean(far) && cutWords(far).isEmpty()) return Candidate(far, lettered = true)
        return Candidate(near.blob.takeIf(isClean), lettered = true)
    }

    private fun paddedText(
        block: TextBlock, paper: BooleanArray, foreignInk: (TextBlock, Box) -> Boolean, pageSide: Int, width: Int, height: Int,
    ): Blob? {
        if (block.oversizedWordShare > MAX_OVERSIZED_WORD_SHARE) return null
        val pad = max((block.glyphHeight * TEXT_PAD_FACTOR).toInt(), (pageSide * MIN_TEXT_PAD_FRACTION).toInt())
        val padded = block.box.inflate(pad, width, height)
        if (foreignInk(block, padded) || marginPaperShare(block.box, padded, paper, width) < MIN_MARGIN_PAPER_SHARE) return null
        return Blob.filled(padded)
    }

    private fun cellAbove(hole: Component, labels: IntArray, width: Int): Int {
        val topRow = hole.box.top * width
        val x = (hole.box.left until hole.box.right).first { labels[topRow + it] == hole.label }
        return topRow - width + x
    }

    private fun marginPaperShare(inner: Box, outer: Box, paper: BooleanArray, width: Int): Float {
        var total = 0
        var onPaper = 0
        for (y in outer.top until outer.bottom) for (x in outer.left until outer.right) {
            if (inner.contains(x, y)) continue
            total++
            if (paper[y * width + x]) onPaper++
        }
        return if (total == 0) 0f else onPaper / total.toFloat()
    }

    private fun isCleanContainer(blob: Blob, block: Box, pageArea: Int, pageSide: Int, minSide: Int, width: Int, height: Int): Boolean {
        if (blob.touchesBorder(width, height) || !blob.box.covers(block)) return false
        if (blob.fill < MIN_BUBBLE_FILL || blob.pixels > pageArea * MAX_BUBBLE_AREA_FRACTION) return false
        if (min(blob.box.width, blob.box.height) < minSide || aspect(blob.box) > MAX_BUBBLE_ASPECT) return false
        if (max(blob.box.width, blob.box.height) > pageSide * MAX_BUBBLE_SPAN_FRACTION) return false
        return hasTextInside(blob, pageSide)
    }

    private fun blockReach(block: Box, pageMargin: Int, pageSide: Int): Int {
        val fromBlock = (min(block.width, block.height) * MARGIN_BLOCK_FACTOR).toInt()
        return fromBlock.coerceIn(pageMargin, maxReach(pageSide))
    }

    private fun maxReach(pageSide: Int) = (pageSide * MAX_MARGIN_CAP_FRACTION).toInt()

    private fun withSolidCore(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        val core = mask.opened(width, height, NEGATIVE_BODY_RADIUS)
        val seg = mask.segment(width, height, 1)
        val hasCore = BooleanArray((seg.components.maxOfOrNull { it.label } ?: -1) + 1)
        for (i in seg.labels.indices) {
            val label = seg.labels[i]
            if (label != NO_LABEL && core[i]) hasCore[label] = true
        }
        return BooleanArray(mask.size) { seg.labels[it].let { label -> label != NO_LABEL && hasCore[label] } }
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

    private fun thinLongBands(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        val horizontalRuns = runLengths(mask, width, height, horizontal = true)
        val verticalRuns = runLengths(mask, width, height, horizontal = false)
        val maxThickness = (min(width, height) * GUTTER_MAX_THICKNESS_FRACTION).toInt()
        val longRows = BooleanArray(mask.size) { horizontalRuns[it] >= (width * GUTTER_MIN_RUN_FRACTION).toInt() }
        val longColumns = BooleanArray(mask.size) { verticalRuns[it] >= (height * GUTTER_MIN_RUN_FRACTION).toInt() }
        val rowBands = runLengths(longRows, width, height, horizontal = false)
        val columnBands = runLengths(longColumns, width, height, horizontal = true)
        return BooleanArray(mask.size) { (longRows[it] && rowBands[it] <= maxThickness) || (longColumns[it] && columnBands[it] <= maxThickness) }
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

    private fun textLikeHole(maxLineHeight: Int, maxBlockHeight: Int): (Box) -> Boolean =
        { hole -> hole.height <= maxLineHeight || (hole.height <= maxBlockHeight && hole.width >= hole.height * MIN_TEXT_BLOCK_ASPECT) }

    private fun hasTextInside(blob: Blob, pageSide: Int): Boolean {
        val holes = blob.interiorHoles()
        val holeCount = holes.count { it }
        val inkShare = holeCount / (blob.pixels + holeCount).toFloat()
        if (inkShare < MIN_INK_SHARE || inkShare > MAX_INK_SHARE) return false
        val lines = holes.segment(blob.box.width, blob.box.height, 1).components
        val glyphHeight = lines.map { it.box.height }.sorted().let { if (it.isEmpty()) 0 else it[it.size / 2] }
        val maxLineHeight = max((pageSide * MAX_LINE_HEIGHT_FRACTION).toInt(), (glyphHeight * LOCAL_LINE_HEIGHT_FACTOR).toInt())
        val maxBlockHeight = max((pageSide * MAX_TEXT_BLOCK_HEIGHT_FRACTION).toInt(), (glyphHeight * LOCAL_BLOCK_HEIGHT_FACTOR).toInt())
        val isTextLike = textLikeHole(maxLineHeight, maxBlockHeight)
        val lineInk = lines.filter { isTextLike(it.box) }.sumOf { it.pixels }
        return lineInk >= holeCount * MIN_LINE_INK_SHARE
    }

    private fun mergeSharingPaper(contained: List<Containment>): List<Containment> {
        val merged = ArrayList<Containment>()
        contained.sortedByDescending { it.container!!.pixels }.forEach { candidate ->
            val partner = merged.indexOfFirst { it.container!!.sharesCells(candidate.container!!) }
            if (partner < 0) merged.add(candidate) else merged[partner] = merged[partner].union(candidate)
        }
        return merged
    }

    private fun mergeOverlapping(blobs: List<Blob>): List<Blob> {
        val merged = ArrayList<Blob>()
        blobs.sortedByDescending { it.box.area }.forEach { candidate ->
            val partner = merged.indexOfFirst { it.overlaps(candidate) }
            if (partner < 0) merged.add(candidate) else merged[partner] = merged[partner].union(candidate)
        }
        return merged
    }

    private fun aspect(box: Box) = max(box.width, box.height) / min(box.width, box.height).toFloat()
}

private class Grown(val blob: Blob, val reachLimited: Boolean)

private class Blob(val box: Box, val cells: BooleanArray) {
    val pixels: Int = cells.count { it }
    val fill: Float get() = pixels / box.area.toFloat()

    fun touchesBorder(width: Int, height: Int) = box.left == 0 || box.top == 0 || box.right == width || box.bottom == height

    fun contains(x: Int, y: Int) = box.contains(x, y) && cells[(y - box.top) * box.width + (x - box.left)]

    fun overlaps(other: Blob): Boolean =
        box.intersectionArea(other.box) >= MIN_OVERLAP_TO_MERGE * min(box.area, other.box.area)

    fun sharesCells(other: Blob): Boolean {
        val overlap = box.intersect(other.box)
        if (overlap.width <= 0 || overlap.height <= 0) return false
        for (y in overlap.top until overlap.bottom) for (x in overlap.left until overlap.right) {
            if (contains(x, y) && other.contains(x, y)) return true
        }
        return false
    }

    fun union(other: Blob): Blob {
        val joined = box.union(other.box)
        val cells = BooleanArray(joined.area) {
            val x = joined.left + it % joined.width
            val y = joined.top + it / joined.width
            contains(x, y) || other.contains(x, y)
        }
        return Blob(joined, cells)
    }

    fun without(mask: BooleanArray, width: Int): Blob {
        val kept = BooleanArray(cells.size) { cells[it] && !mask[(box.top + it / box.width) * width + box.left + it % box.width] }
        return Blob(box, kept).cropped()
    }

    fun holeInkShare(ink: BooleanArray, width: Int): Float {
        val holes = interiorHoles()
        var total = 0
        var inked = 0
        for (i in holes.indices) {
            if (!holes[i]) continue
            total++
            if (ink[(box.top + i / box.width) * width + box.left + i % box.width]) inked++
        }
        return if (total == 0) 0f else inked / total.toFloat()
    }

    fun interiorHoles(): BooleanArray {
        val open = BooleanArray(cells.size) { !cells[it] }
        val reached = BooleanArray(cells.size)
        val w = box.width
        val h = box.height
        floodFrom(open, reached, w, h) { it < w || it >= w * (h - 1) || it % w == 0 || it % w == w - 1 }
        return BooleanArray(cells.size) { open[it] && !reached[it] }
    }

    fun cut(holes: List<Component>, labels: IntArray, width: Int): List<Component> {
        val hull = Hull(this)
        return holes.filter { hole -> hole.box.intersectionArea(box) > 0 && cuts(hole, hull, labels, width) }
    }

    private fun cuts(hole: Component, hull: Hull, labels: IntArray, width: Int): Boolean {
        var inside = 0
        var outside = 0
        for (y in hole.box.top until hole.box.bottom) for (x in hole.box.left until hole.box.right) {
            if (labels[y * width + x] != hole.label) continue
            if (hull.contains(x, y)) inside++ else outside++
        }
        return inside > 0 && outside > 0
    }

    private class Hull(blob: Blob) {
        private val box = blob.box
        private val rowFirst = IntArray(box.height) { Int.MAX_VALUE }
        private val rowLast = IntArray(box.height) { -1 }
        private val columnFirst = IntArray(box.width) { Int.MAX_VALUE }
        private val columnLast = IntArray(box.width) { -1 }

        init {
            for (y in 0 until box.height) for (x in 0 until box.width) {
                if (!blob.cells[y * box.width + x]) continue
                rowFirst[y] = min(rowFirst[y], x); rowLast[y] = max(rowLast[y], x)
                columnFirst[x] = min(columnFirst[x], y); columnLast[x] = max(columnLast[x], y)
            }
        }

        fun contains(x: Int, y: Int): Boolean {
            if (!box.contains(x, y)) return false
            val cx = x - box.left
            val cy = y - box.top
            return cx in rowFirst[cy]..rowLast[cy] && cy in columnFirst[cx]..columnLast[cx]
        }
    }

    fun dilated(margin: Int, width: Int, height: Int): Blob {
        val grown = box.inflate(margin, width, height)
        val cells = BooleanArray(grown.area) { i ->
            val x = grown.left + i % grown.width
            val y = grown.top + i / grown.width
            (max(box.top, y - margin)..min(box.bottom - 1, y + margin)).any { row ->
                (max(box.left, x - margin)..min(box.right - 1, x + margin)).any { col -> contains(col, row) }
            }
        }
        return Blob(grown, cells)
    }

    fun toBubble(width: Int, height: Int): SpeechBubble =
        SpeechBubble(box.toRect(width, height), components().map { part ->
            part.simplified(part.contour()).map { Offset(it.first / width.toFloat(), it.second / height.toFloat()) }
        })

    private fun components(): List<Blob> {
        val labels = cells.segment(box.width, box.height, 1)
        if (labels.components.size == 1) return listOf(this)
        return labels.components.map { component ->
            Blob(box, BooleanArray(cells.size) { labels.labels[it] == component.label }).cropped()
        }
    }

    private fun simplified(ring: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        val keep = BooleanArray(ring.size)
        keep[0] = true
        val stack = ArrayDeque(listOf(0 to ring.size))
        while (stack.isNotEmpty()) {
            val (from, to) = stack.removeLast()
            val end = ring[to % ring.size]
            var farthest = -1
            var farthestDistance = OUTLINE_TOLERANCE
            for (i in from + 1 until to) {
                val d = distanceToSegment(ring[i], ring[from], end)
                if (d > farthestDistance) { farthestDistance = d; farthest = i }
            }
            if (farthest < 0) continue
            keep[farthest] = true
            stack.addLast(from to farthest)
            stack.addLast(farthest to to)
        }
        return ring.filterIndexed { i, _ -> keep[i] }
    }

    private fun distanceToSegment(p: Pair<Int, Int>, a: Pair<Int, Int>, b: Pair<Int, Int>): Float {
        val dx = (b.first - a.first).toFloat()
        val dy = (b.second - a.second).toFloat()
        val length2 = dx * dx + dy * dy
        val t = if (length2 == 0f) 0f else (((p.first - a.first) * dx + (p.second - a.second) * dy) / length2).coerceIn(0f, 1f)
        val px = a.first + t * dx - p.first
        val py = a.second + t * dy - p.second
        return kotlin.math.sqrt(px * px + py * py)
    }

    private fun contour(): List<Pair<Int, Int>> {
        val startIndex = cells.indexOfFirst { it }
        val start = Pair(startIndex % box.width, startIndex / box.width)
        val corners = arrayListOf(Pair(box.left + start.first, box.top + start.second))
        var x = start.first
        var y = start.second
        var direction = EAST
        while (true) {
            val (leftCell, rightCell) = cellsAhead(x, y, direction)
            val turn = when {
                inside(leftCell) -> (direction + 3) % 4
                inside(rightCell) -> direction
                else -> (direction + 1) % 4
            }
            if (turn != direction) corners.add(Pair(box.left + x, box.top + y))
            direction = turn
            x += DX[direction]
            y += DY[direction]
            if (x == start.first && y == start.second) return corners
        }
    }

    private fun cellsAhead(x: Int, y: Int, direction: Int): Pair<Pair<Int, Int>, Pair<Int, Int>> = when (direction) {
        EAST -> Pair(Pair(x, y - 1), Pair(x, y))
        SOUTH -> Pair(Pair(x, y), Pair(x - 1, y))
        WEST -> Pair(Pair(x - 1, y), Pair(x - 1, y - 1))
        else -> Pair(Pair(x - 1, y - 1), Pair(x, y - 1))
    }

    private fun inside(cell: Pair<Int, Int>): Boolean {
        val (x, y) = cell
        return x >= 0 && y >= 0 && x < box.width && y < box.height && cells[y * box.width + x]
    }

    companion object {
        private const val EAST = 0
        private const val SOUTH = 1
        private const val WEST = 2
        private val DX = intArrayOf(1, 0, -1, 0)
        private val DY = intArrayOf(0, 1, 0, -1)

        fun filled(box: Box) = Blob(box, BooleanArray(box.area) { true })

        fun grown(words: List<Box>, maxMargin: Int, paper: BooleanArray, narrowRelativeTo: NarrowRelativeTo, width: Int, height: Int): Grown {
            val block = words.reduce(Box::union)
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
            val seed = BooleanArray(w * h) { i -> words.any { it.chebyshevDistanceTo(box.left + i % w, box.top + i / w) <= 1 } }
            val stack = IntArray(w * h)
            var total = 0
            var limited = false
            for (radius in 1..maxMargin) {
                val admitted = { i: Int -> if (radius == 1) seed[i] else ring[i] == radius && touchesReached(reached, i, w, h) }
                val added = spread(open, reached, ring, stack, w, h, radius, admitted)
                if (radius > 1 && added < total * MIN_GROWTH_SHARE) break
                total += if (radius == 1 && narrowRelativeTo == NarrowRelativeTo.RINGS) added - interiorReached(reached, ring) else added
                limited = radius == maxMargin && added > 0
            }
            return Grown(Blob(box, reached).cropped(), limited)
        }

        private fun interiorReached(reached: BooleanArray, ring: IntArray) = reached.indices.count { reached[it] && ring[it] == 0 }

        private fun touchesReached(reached: BooleanArray, i: Int, w: Int, h: Int): Boolean {
            val x = i % w
            return (x > 0 && reached[i - 1]) || (x < w - 1 && reached[i + 1]) || (i >= w && reached[i - w]) || (i < w * (h - 1) && reached[i + w])
        }

        private fun spread(
            open: BooleanArray, reached: BooleanArray, ring: IntArray, stack: IntArray, w: Int, h: Int, radius: Int, admitted: (Int) -> Boolean,
        ): Int {
            var top = 0
            var added = 0
            for (i in open.indices) {
                if (!open[i] || reached[i] || !admitted(i)) continue
                reached[i] = true; stack[top++] = i; added++
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

private enum class NarrowRelativeTo { RINGS, AREA }

private class BlockRules(
    val minWords: Int, val maxDensity: Float, val minHoleInkShare: Float, val stripsBorderLines: Boolean, val narrowRelativeTo: NarrowRelativeTo,
) {
    fun accept(block: TextBlock) = block.words.size >= minWords && block.density in MIN_BLOCK_DENSITY..maxDensity && block.isTextShaped
}

private class TextBlock(val words: List<Box>) {
    val box: Box = words.reduce(Box::union)
    val density: Float get() = words.sumOf { it.area } / box.area.toFloat()
    val glyphHeight: Int = words.map { it.height }.sorted().let { it[it.size / 2] }
    val isTextShaped: Boolean get() = box.width >= box.height * MIN_BLOCK_ASPECT || density >= MIN_TALL_BLOCK_DENSITY
    val oversizedWordShare: Float get() =
        words.filter { it.height > glyphHeight * OVERSIZED_WORD_FACTOR }.sumOf { it.area } / words.sumOf { it.area }.toFloat()
    val lines: List<Box> get() = words.sortedBy { it.top }.fold(ArrayList()) { rows, word ->
        val row = rows.lastOrNull()
        if (row != null && word.top < row.bottom - word.height / 2) rows[rows.lastIndex] = row.union(word) else rows.add(word)
        rows
    }
}

private object TextBlocks {
    fun cluster(words: List<Component>, surroundings: List<Int>, wordGap: Int, lineGap: Int): List<TextBlock> {
        val parent = IntArray(words.size) { it }
        fun root(i: Int): Int = if (parent[i] == i) i else root(parent[i]).also { parent[i] = it }
        for (i in words.indices) for (j in i + 1 until words.size) {
            if (surroundings[i] == surroundings[j] && near(words[i].box, words[j].box, wordGap, lineGap)) parent[root(i)] = root(j)
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
