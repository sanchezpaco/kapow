package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Rect
import com.comicify.domain.model.ReadingDirection

private const val LARGE_PANEL = 0.45f
private const val CLUSTER_GAP = 0.07f
private const val MARGIN = 0.05f
private const val MIN_WINDOW_WIDTH = 0.42f
private const val MIN_WINDOW_HEIGHT = 0.30f
private const val EDGE_GAP = 0.01f
private const val WINDOW_MERGE_OVERLAP = 0.5f
private const val REDUNDANT_FRACTION = 0.85f
private const val REDUNDANT_SIZE_RATIO = 0.5f
private const val SPLIT_MIN_CLUSTERS = 2
private const val MIN_INSET_AREA = 0.04f
private const val CONTAINMENT = 0.8f
private const val MIN_REMAINDER_AREA = 0.08f
private const val REMAINDER_BURIED = 0.5f
private const val CUT_TOLERANCE = 0.01f
private const val GATE_OVERSIZE_AREA = 0.35f
private const val GATE_MAX_PANELS = 3
private const val GATE_MIN_COVERAGE = 0.8f

object GuidedTour {

    private val wholePage = Rect(0f, 0f, 1f, 1f)

    fun needsBubbles(panels: List<Rect>): Boolean {
        if (panels.isEmpty()) return true
        if (panels.size <= GATE_MAX_PANELS) return true
        if (panels.any { it.area >= GATE_OVERSIZE_AREA }) return true
        return panels.sumOf { it.area.toDouble() } < GATE_MIN_COVERAGE
    }

    fun stops(panels: List<Rect>, bubbles: List<Rect>, direction: ReadingDirection): List<Rect> {
        val base = withoutTinyInsets(panels.ifEmpty { listOf(wholePage) })
        val frames = base.mapNotNull { panel -> frameOf(panel, base) }
        val assignment = assign(bubbles, frames)
        val splash = frames.size == 1
        val stops = frames.flatMap { frame -> frameStops(frame, assignment.owned[frame].orEmpty(), splash, bubbles) } +
            mergeOverlapping(cluster(assignment.orphans).map { window(it, wholePage, bubbles) })
        return dropRedundant(readingOrder(stops, direction))
    }

    private fun withoutTinyInsets(panels: List<Rect>): List<Rect> =
        panels.filter { panel -> panel.area >= MIN_INSET_AREA || panels.none { contains(it, panel) } }

    private fun frameOf(panel: Rect, panels: List<Rect>): Rect? {
        val children = panels.filter { it !== panel && contains(panel, it) }
        if (children.isEmpty()) return panel
        val remainder = remainderOf(panel, children.reduce(Rect::expandToInclude)) ?: return null
        val buried = panels.any { it !== panel && it.overlapArea(remainder) >= REMAINDER_BURIED * remainder.area }
        return remainder.takeUnless { buried }
    }

    private fun remainderOf(panel: Rect, occupied: Rect): Rect? = listOf(
        Rect(panel.left, panel.top, panel.right, occupied.top),
        Rect(panel.left, occupied.bottom, panel.right, panel.bottom),
        Rect(panel.left, panel.top, occupied.left, panel.bottom),
        Rect(occupied.right, panel.top, panel.right, panel.bottom),
    ).filter { it.width > 0f && it.height > 0f }.maxByOrNull { it.area }?.takeIf { it.area >= MIN_REMAINDER_AREA }

    private fun frameStops(frame: Rect, owned: List<Rect>, establishing: Boolean, bubbles: List<Rect>): List<Rect> {
        val clusters = cluster(owned)
        if (clusters.size < SPLIT_MIN_CLUSTERS || frame.area < LARGE_PANEL) return listOf(frame)
        val windows = mergeOverlapping(clusters.map { window(it, frame.expandToInclude(it), bubbles) })
        if (windows.size < SPLIT_MIN_CLUSTERS) return listOf(frame)
        return if (establishing) listOf(frame) + windows else windows
    }

    private class Assignment(val owned: Map<Rect, List<Rect>>, val orphans: List<Rect>)

    private fun assign(bubbles: List<Rect>, frames: List<Rect>): Assignment {
        val owned = HashMap<Rect, MutableList<Rect>>()
        val orphans = ArrayList<Rect>()
        for (bubble in bubbles) {
            val host = hostOf(bubble, frames)
            if (host == null) orphans.add(bubble) else owned.getOrPut(host) { ArrayList() }.add(bubble)
        }
        return Assignment(owned, orphans)
    }

    private fun hostOf(bubble: Rect, frames: List<Rect>): Rect? {
        frames.filter { it.contains(bubble.center) }.minByOrNull { it.area }?.let { return it }
        val best = frames.maxByOrNull { it.overlapArea(bubble) } ?: return null
        return best.takeIf { it.overlapArea(bubble) > 0f }
    }

    private fun cluster(boxes: List<Rect>): List<Rect> =
        mergeWhile(boxes.map { listOf(it) }) { a, b -> a.any { x -> b.any { y -> gap(x, y) <= CLUSTER_GAP } } }
            .map { it.reduce(Rect::expandToInclude) }

    private fun mergeOverlapping(windows: List<Rect>): List<Rect> =
        mergeWhile(windows.map { listOf(it) }) { a, b ->
            val x = a.reduce(Rect::expandToInclude)
            val y = b.reduce(Rect::expandToInclude)
            x.overlapArea(y) >= WINDOW_MERGE_OVERLAP * minOf(x.area, y.area)
        }.map { it.reduce(Rect::expandToInclude) }

    private fun mergeWhile(initial: List<List<Rect>>, joined: (List<Rect>, List<Rect>) -> Boolean): List<List<Rect>> {
        val groups = initial.map { it.toMutableList() }.toMutableList()
        var merged = true
        while (merged) {
            merged = false
            outer@ for (i in groups.indices) {
                for (j in i + 1 until groups.size) {
                    if (joined(groups[i], groups[j])) {
                        groups[i].addAll(groups[j])
                        groups.removeAt(j)
                        merged = true
                        break@outer
                    }
                }
            }
        }
        return groups
    }

    private fun window(region: Rect, bounds: Rect, bubbles: List<Rect>): Rect {
        var window = sized(region, bounds)
        var kept = region
        repeat(bubbles.size + 1) {
            val crossing = bubbles.firstOrNull { window.crosses(it) } ?: return window
            val shrunk = shrunkPast(window, crossing, kept)
            if (shrunk != null) {
                window = shrunk
            } else {
                kept = kept.expandToInclude(crossing)
                window = sized(kept, bounds)
            }
        }
        return window
    }

    private fun sized(region: Rect, bounds: Rect): Rect {
        val width = maxOf(MIN_WINDOW_WIDTH, region.width + 2 * MARGIN).coerceAtMost(bounds.width)
        val height = maxOf(MIN_WINDOW_HEIGHT, region.height + 2 * MARGIN).coerceAtMost(bounds.height)
        val left = (region.center.x - width / 2f).coerceIn(bounds.left, bounds.right - width)
        val top = (region.center.y - height / 2f).coerceIn(bounds.top, bounds.bottom - height)
        return Rect(left, top, left + width, top + height)
    }

    private fun shrunkPast(window: Rect, bubble: Rect, kept: Rect): Rect? = listOf(
        window.copy(right = bubble.left - EDGE_GAP),
        window.copy(left = bubble.right + EDGE_GAP),
        window.copy(bottom = bubble.top - EDGE_GAP),
        window.copy(top = bubble.bottom + EDGE_GAP),
    ).filter { it.left <= kept.left && it.top <= kept.top && it.right >= kept.right && it.bottom >= kept.bottom }
        .maxByOrNull { it.area }

    private fun Rect.crosses(bubble: Rect): Boolean =
        overlapArea(bubble) > 0f && !(bubble.left >= left && bubble.top >= top && bubble.right <= right && bubble.bottom <= bottom)

    private fun dropRedundant(stops: List<Rect>): List<Rect> {
        val out = ArrayList<Rect>()
        for (stop in stops) {
            val previous = out.lastOrNull()
            if (previous != null && redundant(previous, stop)) {
                if (stop.area > previous.area) out[out.lastIndex] = stop
            } else {
                out.add(stop)
            }
        }
        return out
    }

    private fun redundant(a: Rect, b: Rect): Boolean {
        val smaller = minOf(a.area, b.area)
        return smaller >= REDUNDANT_SIZE_RATIO * maxOf(a.area, b.area) && a.overlapArea(b) >= REDUNDANT_FRACTION * smaller
    }

    private fun readingOrder(stops: List<Rect>, direction: ReadingDirection): List<Rect> {
        val containers = stops.filter { host -> stops.any { it !== host && contains(host, it) } }
        val ordered = cutOrder(stops - containers.toSet(), direction).toMutableList()
        for (container in containers.sortedBy { it.area }) {
            val firstContained = ordered.indexOfFirst { contains(container, it) }
            ordered.add(if (firstContained < 0) ordered.size else firstContained, container)
        }
        return ordered
    }

    private fun cutOrder(stops: List<Rect>, direction: ReadingDirection): List<Rect> {
        if (stops.size <= 1) return stops
        horizontalCut(stops)?.let { y ->
            val (top, bottom) = stops.partition { it.bottom <= y + CUT_TOLERANCE }
            return cutOrder(top, direction) + cutOrder(bottom, direction)
        }
        verticalCut(stops)?.let { x ->
            val (left, right) = stops.partition { it.right <= x + CUT_TOLERANCE }
            val (first, second) = if (direction == ReadingDirection.RightToLeft) right to left else left to right
            return cutOrder(first, direction) + cutOrder(second, direction)
        }
        return rowOrder(stops, direction)
    }

    private fun horizontalCut(stops: List<Rect>): Float? = stops.map { it.bottom }.sorted().firstOrNull { y ->
        stops.none { it.top < y - CUT_TOLERANCE && it.bottom > y + CUT_TOLERANCE } && stops.any { it.top >= y - CUT_TOLERANCE }
    }

    private fun verticalCut(stops: List<Rect>): Float? = stops.map { it.right }.sorted().firstOrNull { x ->
        stops.none { it.left < x - CUT_TOLERANCE && it.right > x + CUT_TOLERANCE } && stops.any { it.left >= x - CUT_TOLERANCE }
    }

    private fun rowOrder(stops: List<Rect>, direction: ReadingDirection): List<Rect> {
        val rows = ArrayList<MutableList<Rect>>()
        for (stop in stops.sortedBy { it.top }) {
            val row = rows.firstOrNull { sameRow(it, stop) }
            if (row != null) row.add(stop) else rows.add(mutableListOf(stop))
        }
        val rightToLeft = direction == ReadingDirection.RightToLeft
        return rows.sortedBy { it.top() }
            .flatMap { row -> row.sortedBy { if (rightToLeft) -it.center.x else it.center.x } }
    }

    private fun sameRow(row: List<Rect>, stop: Rect): Boolean {
        val rowTop = row.top()
        val rowBottom = row.bottom()
        val rowCenterY = (rowTop + rowBottom) / 2f
        return stop.center.y in rowTop..rowBottom && rowCenterY in stop.top..stop.bottom
    }

    private fun List<Rect>.top() = minOf { it.top }
    private fun List<Rect>.bottom() = maxOf { it.bottom }

    private fun contains(host: Rect, box: Rect) = box.area < host.area && host.overlapArea(box) >= CONTAINMENT * box.area

    private val Rect.area: Float get() = width * height

    private fun Rect.overlapArea(other: Rect): Float {
        val w = (minOf(right, other.right) - maxOf(left, other.left)).coerceAtLeast(0f)
        val h = (minOf(bottom, other.bottom) - maxOf(top, other.top)).coerceAtLeast(0f)
        return w * h
    }

    private fun gap(a: Rect, b: Rect): Float {
        val dx = (maxOf(a.left, b.left) - minOf(a.right, b.right)).coerceAtLeast(0f)
        val dy = (maxOf(a.top, b.top) - minOf(a.bottom, b.bottom)).coerceAtLeast(0f)
        return maxOf(dx, dy)
    }
}

private fun Rect.expandToInclude(other: Rect) = Rect(
    minOf(left, other.left),
    minOf(top, other.top),
    maxOf(right, other.right),
    maxOf(bottom, other.bottom),
)
