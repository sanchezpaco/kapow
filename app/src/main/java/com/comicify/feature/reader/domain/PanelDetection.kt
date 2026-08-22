package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min

private const val OPENING_RADIUS = 2
private const val OUTLINE_MARGIN = 2
private const val MIN_PANEL_AREA_FRACTION = 0.012f
private const val SLIVER_SIDE_FRACTION = 0.07f
private const val SLIVER_ASPECT = 4f
private const val SLIVER_REACH_FRACTION = 0.03f
private const val MAX_PANEL_ASPECT = 4f
private const val MIN_ENCLOSED_AREA_FRACTION = 0.0005f
private const val MAX_ENCLOSED_AREA_FRACTION = 0.25f
private const val MIN_ENCLOSED_FILL = 0.45f
private const val FRAME_RING_DEPTH = 3
private const val MIN_FRAMED_RING = 0.75f
private const val MAX_PANELS = 16
private const val MIN_COVERAGE = 0.75f

val FullPagePanel = Rect(0f, 0f, 1f, 1f)

object PanelDetection {

    fun detect(pixels: IntArray, sourceWidth: Int, sourceHeight: Int, pool: Int): List<Rect> {
        val classes = PixelClasses.classify(pixels, sourceWidth, sourceHeight, pool)
        val width = classes.width
        val height = classes.height
        val enclosed = enclosedWhites(classes)
        val panels = segment(classes, enclosed)
        if (panels.isEmpty() || panels.size > MAX_PANELS) return fallback(classes)
        if (PanelLayout.coverage(panels, classes.art, width) < MIN_COVERAGE) return fallback(classes)
        return PanelLayout.readingOrder(panels).map { it.toRect(width, height) }
    }

    private fun fallback(classes: PixelClasses) =
        PanelBands.bands(classes).map { it.toRect(classes.width, classes.height) }

    private fun segment(classes: PixelClasses, enclosed: List<Box>): List<Box> {
        val width = classes.width
        val height = classes.height
        val pageArea = width * height
        val minPanelArea = (pageArea * MIN_PANEL_AREA_FRACTION).toInt()
        val art = classes.art
        val separator = BooleanArray(art.size) { classes.white[it] || classes.border[it] }
        val segmentation = art.opened(width, height, OPENING_RADIUS).segment(width, height, minPanelArea)
        val isPanelWorthy = { box: Box -> box.area >= minPanelArea && aspect(box) <= MAX_PANEL_ASPECT }
        val isFramed = { box: Box -> framedShare(box, art, width, height) >= MIN_FRAMED_RING }
        val isSliver = { box: Box -> min(box.width, box.height) < SLIVER_SIDE_FRACTION * min(width, height) && aspect(box) > SLIVER_ASPECT }
        return PanelBodies(segmentation, width, height, isFramed).merged()
            .flatMap { PanelFrames.split(it, separator, classes.ink, art, width) }
            .map { Panel(it, it) }
            .let { PanelLayout.attach(it, enclosed, isPanelWorthy) }
            .let { PanelLayout.absorbContained(it, isFramed) }
            .let { PanelLayout.absorbSlivers(it, isSliver, (SLIVER_REACH_FRACTION * min(width, height)).toInt()) }
            .map { it.frame }
            .let { PanelLayout.dropRedundantContainers(it) }
            .filter { it.area >= minPanelArea }
    }

    private fun enclosedWhites(classes: PixelClasses): List<Box> {
        val pageArea = classes.width * classes.height
        val minPixels = (pageArea * MIN_ENCLOSED_AREA_FRACTION).toInt()
        return classes.solidWhite.segment(classes.width, classes.height, minPixels).components
            .filter { !it.touchesBorder && it.fill >= MIN_ENCLOSED_FILL && it.pixels <= pageArea * MAX_ENCLOSED_AREA_FRACTION }
            .map { it.box.inflate(OUTLINE_MARGIN, classes.width, classes.height) }
    }

    private fun framedShare(box: Box, art: BooleanArray, width: Int, height: Int): Float {
        var positions = 0
        var separated = 0
        fun probe(xs: IntProgression, ys: IntProgression) {
            if (xs.first !in 0 until width || xs.last !in 0 until width || ys.first !in 0 until height || ys.last !in 0 until height) return
            positions++
            if (xs.any { x -> ys.any { y -> !art[y * width + x] } }) separated++
        }
        for (x in box.left until box.right) {
            probe(x..x, box.top - FRAME_RING_DEPTH until box.top)
            probe(x..x, box.bottom until box.bottom + FRAME_RING_DEPTH)
        }
        for (y in box.top until box.bottom) {
            probe(box.left - FRAME_RING_DEPTH until box.left, y..y)
            probe(box.right until box.right + FRAME_RING_DEPTH, y..y)
        }
        return if (positions == 0) 0f else separated / positions.toFloat()
    }

    private fun aspect(box: Box) = max(box.width, box.height) / min(box.width, box.height).toFloat()
}
