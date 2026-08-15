package com.comicify.feature.reader.domain

private const val LINE_SEPARATOR_SHARE = 0.85f
private const val MAX_LINE_THICKNESS_FRACTION = 0.02f
private const val MIN_FLANK_ART_SHARE = 0.45f
private const val FLANK_DEPTH = 3
private const val MIN_SPLIT_MARGIN_FRACTION = 0.1f
private const val MIN_PART_ART_SHARE = 0.3f

object PanelFrames {

    fun split(box: Box, separator: BooleanArray, art: BooleanArray, width: Int): List<Box> {
        val cut = cut(box, separator, art, width) ?: return listOf(box)
        return split(cut.first, separator, art, width) + split(cut.second, separator, art, width)
    }

    private fun cut(box: Box, separator: BooleanArray, art: BooleanArray, width: Int): Pair<Box, Box>? {
        val rowSeparator = IntArray(box.height)
        val rowArt = IntArray(box.height)
        val columnSeparator = IntArray(box.width)
        val columnArt = IntArray(box.width)
        for (row in 0 until box.height) {
            val base = (box.top + row) * width + box.left
            var separators = 0
            var arts = 0
            for (column in 0 until box.width) {
                val index = base + column
                if (separator[index]) { separators++; columnSeparator[column]++ }
                if (art[index]) { arts++; columnArt[column]++ }
            }
            rowSeparator[row] = separators
            rowArt[row] = arts
        }
        val horizontal = bestLine(rowSeparator, rowArt, box.width, box.height)
        val vertical = bestLine(columnSeparator, columnArt, box.height, box.width)
        return when {
            horizontal.score >= vertical.score && horizontal.index >= 0 -> horizontalCut(box, horizontal.index)
            vertical.index >= 0 -> verticalCut(box, vertical.index)
            else -> null
        }
    }

    private fun bestLine(separators: IntArray, arts: IntArray, span: Int, count: Int): Line {
        val margin = (count * MIN_SPLIT_MARGIN_FRACTION).toInt().coerceAtLeast(1)
        if (count < 2 * margin) return Line(-1, 0f)
        val maxThickness = (count * MAX_LINE_THICKNESS_FRACTION).toInt().coerceAtLeast(1)
        val minShare = LINE_SEPARATOR_SHARE * span
        val artPrefix = IntArray(count + 1)
        for (k in 0 until count) artPrefix[k + 1] = artPrefix[k] + arts[k]
        var best = Line(-1, 0f)
        var row = margin
        while (row < count - margin) {
            if (separators[row] < minShare) { row++; continue }
            val start = row
            while (row < count - margin && separators[row] >= minShare) row++
            val end = row - 1
            if (end - start + 1 > maxThickness || start == margin) continue
            val flankBefore = flankArt(arts, span, start - 1, -1)
            val flankAfter = flankArt(arts, span, end + 1, 1)
            if (flankBefore < MIN_FLANK_ART_SHARE || flankAfter < MIN_FLANK_ART_SHARE) continue
            if (!partsAreArt(artPrefix, start, count, span)) continue
            val score = minOf(flankBefore, flankAfter)
            if (score > best.score) best = Line(start, score)
        }
        return best
    }

    private fun flankArt(arts: IntArray, span: Int, start: Int, step: Int): Float {
        var sum = 0f
        var counted = 0
        var index = start
        repeat(FLANK_DEPTH) {
            if (index in arts.indices) { sum += arts[index] / span.toFloat(); counted++ }
            index += step
        }
        return if (counted == 0) 0f else sum / counted
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

    private class Line(val index: Int, val score: Float)
}
