package com.comicify.feature.reader.domain

private const val GUTTER_ROW_ART_SHARE = 0.03f
private const val MIN_GUTTER_FRACTION = 0.015f
private const val MIN_BAND_HEIGHT_FRACTION = 0.08f
private const val MAX_BANDS = 6

object PanelBands {

    fun bands(classes: PixelClasses): List<Box> {
        val width = classes.width
        val height = classes.height
        val minGutter = (height * MIN_GUTTER_FRACTION).toInt().coerceAtLeast(1)
        val minBandHeight = (height * MIN_BAND_HEIGHT_FRACTION).toInt().coerceAtLeast(1)
        val runs = contentRuns(classes, width, height, minGutter)
        val bands = capBands(mergeShortBands(runs, minBandHeight))
        if (bands.size < 2) return listOf(Box(0, 0, width, height))
        return bands
    }

    private fun contentRuns(classes: PixelClasses, width: Int, height: Int, minGutter: Int): List<Box> {
        val content = contentRows(classes, width, height)
        val runs = mutableListOf<Box>()
        var y = 0
        while (y < height) {
            if (!content[y]) {
                y++
                continue
            }
            val top = y
            while (y < height && content[y]) y++
            val run = Box(0, top, width, y)
            val previous = runs.lastOrNull()
            if (previous != null && run.top - previous.bottom < minGutter) {
                runs[runs.lastIndex] = previous.union(run)
            } else {
                runs.add(run)
            }
        }
        return runs
    }

    private fun contentRows(classes: PixelClasses, width: Int, height: Int): BooleanArray {
        val art = classes.art
        val minArtPixels = width * GUTTER_ROW_ART_SHARE
        return BooleanArray(height) { y ->
            val base = y * width
            var count = 0
            for (x in 0 until width) if (art[base + x]) count++
            count >= minArtPixels
        }
    }

    private fun mergeShortBands(runs: List<Box>, minBandHeight: Int): List<Box> {
        val bands = runs.toMutableList()
        while (bands.size > 1) {
            val shortest = bands.indices.minByOrNull { bands[it].height }!!
            if (bands[shortest].height >= minBandHeight) break
            mergeAt(bands, shortest, nearestNeighbour(bands, shortest))
        }
        return bands
    }

    private fun capBands(runs: List<Box>): List<Box> {
        val bands = runs.toMutableList()
        while (bands.size > MAX_BANDS) {
            val narrowestGap = (0 until bands.size - 1).minByOrNull { bands[it + 1].top - bands[it].bottom }!!
            mergeAt(bands, narrowestGap, narrowestGap + 1)
        }
        return bands
    }

    private fun nearestNeighbour(bands: List<Box>, index: Int): Int {
        if (index == 0) return 1
        if (index == bands.lastIndex) return index - 1
        val gapAbove = bands[index].top - bands[index - 1].bottom
        val gapBelow = bands[index + 1].top - bands[index].bottom
        return if (gapAbove <= gapBelow) index - 1 else index + 1
    }

    private fun mergeAt(bands: MutableList<Box>, first: Int, second: Int) {
        val low = minOf(first, second)
        val high = maxOf(first, second)
        bands[low] = bands[low].union(bands[high])
        bands.removeAt(high)
    }
}
