package com.comicify.feature.reader.domain

private const val LINE_SEPARATOR_SHARE = 0.85f
private const val MAX_LINE_THICKNESS_FRACTION = 0.02f
private const val MAX_INK_LINE_THICKNESS_FRACTION = 0.025f
private const val MIN_INK_LINE_THICKNESS_FRACTION = 0.0025f
private const val MIN_INK_LINE_CONTRAST = 0.15f
private const val MIN_INK_LINE_SPAN_FRACTION = 0.6f
private const val MIN_INK_PART_FRACTION = 0.08f
private const val MIN_FLANK_ART_SHARE = 0.45f
private const val FLANK_DEPTH = 3
private const val MIN_SPLIT_MARGIN_FRACTION = 0.1f
private const val MIN_PART_ART_SHARE = 0.3f
private const val BOX_EDGES = 2
private const val MIN_FRAMED_EDGE_SHARE = 0.75f

object PanelFrames {

    fun split(box: Box, separator: BooleanArray, ink: BooleanArray, art: BooleanArray, width: Int): List<Box> {
        val cut = cut(box, separator, ink, art, width) ?: return listOf(box)
        return split(cut.first, separator, ink, art, width) + split(cut.second, separator, ink, art, width)
    }

    private fun cut(box: Box, separator: BooleanArray, ink: BooleanArray, art: BooleanArray, width: Int): Pair<Box, Box>? {
        val rows = Counts(box.height)
        val columns = Counts(box.width)
        for (row in 0 until box.height) {
            val base = (box.top + row) * width + box.left
            for (column in 0 until box.width) {
                val index = base + column
                val keyline = separator[index] || ink[index]
                if (separator[index]) { rows.separator[row]++; columns.separator[column]++ }
                if (keyline) { rows.keyline[row]++; columns.keyline[column]++ }
                if (art[index]) { rows.art[row]++; columns.art[column]++ }
                if (separator[index] && (column == 0 || column == box.width - 1)) rows.edges[row]++
                if (separator[index] && (row == 0 || row == box.height - 1)) columns.edges[column]++
            }
        }
        val height = art.size / width
        val horizontal = bestLine(rows, box.width, width, box.width >= MIN_INK_LINE_SPAN_FRACTION * width, (height * MIN_INK_PART_FRACTION).toInt())
        val vertical = bestLine(columns, box.height, width, box.height >= MIN_INK_LINE_SPAN_FRACTION * height, (width * MIN_INK_PART_FRACTION).toInt())
        return when {
            horizontal.score >= vertical.score && horizontal.index >= 0 -> horizontalCut(box, horizontal.index)
            vertical.index >= 0 -> verticalCut(box, vertical.index)
            else -> null
        }
    }

    private fun bestLine(counts: Counts, span: Int, pageWidth: Int, spansPage: Boolean, minInkPart: Int): Line {
        val gutter = LineShape(1, (pageWidth * MAX_LINE_THICKNESS_FRACTION).toInt().coerceAtLeast(1), 0f, 0, needsFramedPart = false)
        val gutterLine = bestLine(counts.separator, counts.art, counts.edges, span, gutter)
        if (!spansPage) return gutterLine
        val keyline = LineShape(
            (pageWidth * MIN_INK_LINE_THICKNESS_FRACTION).toInt().coerceAtLeast(1),
            (pageWidth * MAX_INK_LINE_THICKNESS_FRACTION).toInt().coerceAtLeast(1),
            MIN_INK_LINE_CONTRAST,
            minInkPart,
            needsFramedPart = true,
        )
        val keylineLine = bestLine(counts.keyline, counts.art, counts.edges, span, keyline)
        return if (gutterLine.score >= keylineLine.score) gutterLine else keylineLine
    }

    private fun bestLine(separators: IntArray, arts: IntArray, edges: IntArray, span: Int, shape: LineShape): Line {
        val count = separators.size
        val margin = maxOf((count * MIN_SPLIT_MARGIN_FRACTION).toInt(), shape.minPart).coerceAtLeast(1)
        if (count < 2 * margin) return Line(-1, 0f)
        val minShare = LINE_SEPARATOR_SHARE * span
        val artPrefix = prefixSums(arts)
        val edgePrefix = prefixSums(edges)
        var best = Line(-1, 0f)
        var row = margin
        while (row < count - margin) {
            if (separators[row] < minShare) { row++; continue }
            val start = row
            while (row < count - margin && separators[row] >= minShare) row++
            val end = row - 1
            val thickness = end - start + 1
            if (thickness !in shape.minThickness..shape.maxThickness || start == margin) continue
            if (contrast(separators, span, start, end) < shape.minContrast) continue
            val flankBefore = flankShare(arts, span, start - 1, -1)
            val flankAfter = flankShare(arts, span, end + 1, 1)
            if (flankBefore < MIN_FLANK_ART_SHARE || flankAfter < MIN_FLANK_ART_SHARE) continue
            if (!partsAreArt(artPrefix, start, count, span)) continue
            if (shape.needsFramedPart && !aPartIsFramed(edgePrefix, start, count)) continue
            val score = minOf(flankBefore, flankAfter)
            if (score > best.score) best = Line(start, score)
        }
        return best
    }

    private fun contrast(separators: IntArray, span: Int, start: Int, end: Int): Float {
        val line = (start..end).sumOf { separators[it] } / (span.toFloat() * (end - start + 1))
        return line - maxOf(flankShare(separators, span, start - 1, -1), flankShare(separators, span, end + 1, 1))
    }

    private fun flankShare(counts: IntArray, span: Int, start: Int, step: Int): Float {
        var sum = 0f
        var counted = 0
        var index = start
        repeat(FLANK_DEPTH) {
            if (index in counts.indices) { sum += counts[index] / span.toFloat(); counted++ }
            index += step
        }
        return if (counted == 0) 0f else sum / counted
    }

    private fun prefixSums(values: IntArray) = IntArray(values.size + 1).also { sums ->
        for (k in values.indices) sums[k + 1] = sums[k] + values[k]
    }

    private fun aPartIsFramed(edgePrefix: IntArray, cut: Int, count: Int): Boolean {
        val before = edgePrefix[cut] / (BOX_EDGES * cut.toFloat())
        val after = (edgePrefix[count] - edgePrefix[cut]) / (BOX_EDGES * (count - cut).toFloat())
        return before >= MIN_FRAMED_EDGE_SHARE || after >= MIN_FRAMED_EDGE_SHARE
    }

    private fun partsAreArt(artPrefix: IntArray, cut: Int, count: Int, span: Int): Boolean {
        val before = artPrefix[cut] / (span.toFloat() * cut)
        val after = (artPrefix[count] - artPrefix[cut]) / (span.toFloat() * (count - cut))
        return before >= MIN_PART_ART_SHARE && after >= MIN_PART_ART_SHARE
    }

    private fun horizontalCut(box: Box, row: Int) = Pair(
        Box(box.left, box.top, box.right, box.top + row),
        Box(box.left, box.top + row, box.right, box.bottom),
    )

    private fun verticalCut(box: Box, column: Int) = Pair(
        Box(box.left, box.top, box.left + column, box.bottom),
        Box(box.left + column, box.top, box.right, box.bottom),
    )

    private class Counts(size: Int) {
        val separator = IntArray(size)
        val keyline = IntArray(size)
        val art = IntArray(size)
        val edges = IntArray(size)
    }

    private class LineShape(val minThickness: Int, val maxThickness: Int, val minContrast: Float, val minPart: Int, val needsFramedPart: Boolean)

    private class Line(val index: Int, val score: Float)
}
