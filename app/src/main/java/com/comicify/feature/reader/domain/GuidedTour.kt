package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.comicify.domain.model.ReadingDirection

private const val LARGE_PANEL = 0.45f
private const val CLUSTER_GAP = 0.07f
private const val MARGIN = 0.05f
private const val MIN_WINDOW_WIDTH = 0.42f
private const val MIN_WINDOW_HEIGHT = 0.30f
private const val EDGE_GAP = 0.01f
private const val WINDOW_MERGE_OVERLAP = 0.5f
private const val MAX_WINDOW_HEIGHT = 0.45f
private const val MAX_WINDOW_WIDTH = 0.6f
private const val REDUNDANT_FRACTION = 0.85f
private const val REDUNDANT_SIZE_RATIO = 0.5f
private const val SPLIT_MIN_CLUSTERS = 2
private const val MIN_INSET_AREA = 0.04f
private const val SPARSE_PANEL_COVERAGE = 0.5f
private const val SPARSE_MAX_PANELS = 2
private const val CONTAINMENT = 0.8f
private const val MIN_REMAINDER_AREA = 0.08f
private const val REMAINDER_BURIED = 0.5f
private const val MIN_REMAINDER_SIDE = 0.15f
private const val BUBBLE_ATTACH_OVERLAP = 0.25f
private const val CAPTION_GUTTER_GAP = 0.03f
private const val CAPTION_COLUMN_SHARE = 0.8f
private const val BUBBLE_SLICE_OVERLAP = 0.10f
private const val CUT_TOLERANCE = 0.01f
private const val PANEL_CUT_INTRUSION = 0.05f
private const val BALLOON_CUT_INTRUSION = 0.3f
private const val FRAME_SHRINK_KEEP = 0.7f
private const val SPANNING_HEIGHT = 0.8f
private const val SPANNING_COMPANY = 0.6f
private const val SPANNED_COLUMN_MIN = 2
private const val DUPLICATE_PANEL_OVERLAP = 0.7f
private const val BALLOON_PANEL_AREA = 0.08f
private const val BALLOON_PANEL_COVER = 0.7f
private const val PAINTED_PAGE = 0.8f
private const val PAINTED_FRAGMENTS_OUTSIDE = 0.2f
private const val PAINTED_MAX_FRAGMENTS = 2
private const val DEAD_TAP_COVERAGE = 0.9f

object GuidedTour {

    private val wholePage = Rect(0f, 0f, 1f, 1f)

    private class Stop(val anchor: Rect, val view: Rect, val floating: Boolean = false, val opening: Boolean = false)

    fun stops(panels: List<Rect>, bubbles: List<Rect>, direction: ReadingDirection): List<Rect> {
        val detected = withoutDuplicates(panels)
        if (bubbles.isEmpty() && detected.size <= SPARSE_MAX_PANELS && detected.sumOf { it.area.toDouble() } < SPARSE_PANEL_COVERAGE) return listOf(wholePage)
        val painted = paintedPage(detected, bubbles)
        val establishing = if (painted != null && painted.size > 1) listOf(Stop(wholePage, wholePage, floating = true, opening = true)) else emptyList()
        val base = withoutTinyInsets(painted ?: detected.ifEmpty { listOf(wholePage) })
        val minimum = minimumWindow(base)
        val frames = base.mapNotNull { panel -> frameOf(panel, base, bubbles) }
        val assignment = assign(bubbles, frames)
        val splash = frames.size == 1 && establishing.isEmpty()
        val panelStops = frames.flatMap { frame -> frameStops(frame, assignment.owned[frame].orEmpty(), splash, bubbles, minimum) }
        val shown = if (splash) emptyList() else panelStops.filter { !it.floating }.map { it.view }
        val orphanClusters = cluster(assignment.orphans).filter { orphan -> shown.none { it.encloses(orphan) } }
        val stops = establishing + panelStops + mergeOverlapping(orphanClusters.map { windowStop(it, homeOf(it, frames), bubbles, minimum) })
        return withoutDeadTaps(dropRedundant(readingOrder(stops, direction)), bubbles).map { it.view }
    }

    private fun homeOf(cluster: Rect, frames: List<Rect>): Rect =
        frames.maxByOrNull { it.overlapArea(cluster) }?.takeIf { it.overlapArea(cluster) > 0f }?.expandToInclude(cluster) ?: wholePage

    private fun Rect.isBalloon(bubbles: List<Rect>) =
        area < BALLOON_PANEL_AREA && bubbles.sumOf { overlapArea(it).toDouble() } >= BALLOON_PANEL_COVER * area

    private fun withoutDuplicates(panels: List<Rect>): List<Rect> =
        mergeWhile(panels.map { listOf(it) }) { a, b -> a.any { x -> b.any { y -> duplicated(x, y) } } }
            .map { it.reduce(Rect::expandToInclude) }

    private fun duplicated(a: Rect, b: Rect) =
        a.overlapArea(b) >= DUPLICATE_PANEL_OVERLAP * minOf(a.area, b.area) && !contains(a, b) && !contains(b, a)

    private fun paintedPage(panels: List<Rect>, bubbles: List<Rect>): List<Rect>? {
        val painting = panels.takeIf { it.size > 1 }?.maxByOrNull { it.area } ?: return null
        val fragments = panels.filter { it !== painting }
        val outside = fragments.sumOf { (it.area - painting.overlapArea(it)).toDouble() }
        val covered = fragments.sumOf { it.area.toDouble() }
        if (painting.area < PAINTED_PAGE || outside >= PAINTED_FRAGMENTS_OUTSIDE || covered >= SPARSE_PANEL_COVERAGE) return null
        val scraps = fragments.filterNot { it.isBalloon(bubbles) }
            .let { rest -> rest.size <= PAINTED_MAX_FRAGMENTS && rest.none { fragment -> bubbles.any { fragment.contains(it.center) } } }
        return listOf(wholePage) + if (scraps) emptyList() else fragments
    }

    private fun minimumWindow(panels: List<Rect>): Size = Size(
        minOf(MIN_WINDOW_WIDTH, panels.median { it.width }),
        minOf(MIN_WINDOW_HEIGHT, panels.median { it.height }),
    )

    private fun List<Rect>.median(extent: (Rect) -> Float): Float = map(extent).sorted()[size / 2]

    private fun withoutTinyInsets(panels: List<Rect>): List<Rect> =
        panels.filter { panel -> panel.area >= MIN_INSET_AREA || panels.none { contains(it, panel) } }

    private fun frameOf(panel: Rect, panels: List<Rect>, bubbles: List<Rect>): Rect? {
        val children = panels.filter { it !== panel && contains(panel, it) }
        if (children.isEmpty()) return panel
        val remainder = remainderOf(panel, children.reduce(Rect::expandToInclude)) ?: return null
        val buried = panels.any { it !== panel && !contains(it, panel) && it.overlapArea(remainder) >= REMAINDER_BURIED * remainder.area }
        val wordlessSliver = remainder.minDimension < MIN_REMAINDER_SIDE && bubbles.none { remainder.contains(it.center) }
        return remainder.takeUnless { buried || wordlessSliver }
    }

    private fun remainderOf(panel: Rect, occupied: Rect): Rect? = listOf(
        Rect(panel.left, panel.top, panel.right, occupied.top),
        Rect(panel.left, occupied.bottom, panel.right, panel.bottom),
        Rect(panel.left, panel.top, occupied.left, panel.bottom),
        Rect(occupied.right, panel.top, panel.right, panel.bottom),
    ).filter { it.width > 0f && it.height > 0f }.maxByOrNull { it.area }?.takeIf { it.area >= MIN_REMAINDER_AREA }

    private fun frameStops(frame: Rect, owned: List<Rect>, establishing: Boolean, bubbles: List<Rect>, minimum: Size): List<Stop> {
        val whole = Stop(frame, fitted(frame, owned, bubbles), opening = establishing)
        val clusters = cluster(owned)
        val enough = clusters.size >= if (establishing) 1 else SPLIT_MIN_CLUSTERS
        if (!enough || frame.area < LARGE_PANEL) return listOf(whole)
        val windows = mergeOverlapping(clusters.map { windowStop(it, frame.expandToInclude(it), bubbles, minimum) })
        return if (establishing) listOf(whole) + windows else windows
    }

    private fun fitted(frame: Rect, owned: List<Rect>, bubbles: List<Rect>): Rect {
        val kept = owned.fold(Rect(frame.center, frame.center), Rect::expandToInclude)
        var view = owned.fold(frame, Rect::expandToInclude)
        repeat(bubbles.size) {
            view = bubbles.asSequence().filter { view.slices(it) }.mapNotNull { unsliced(view, it, kept, frame) }.firstOrNull()
                ?: return padded(frame, view)
        }
        return padded(frame, view)
    }

    private fun unsliced(view: Rect, bubble: Rect, kept: Rect, frame: Rect): Rect? {
        val shrunk = shrunkPast(view, bubble, kept)?.takeIf { it.area >= FRAME_SHRINK_KEEP * frame.area }
        return shrunk ?: view.expandToInclude(bubble).takeIf { view.attaches(bubble) }
    }

    private fun padded(frame: Rect, fitted: Rect): Rect {
        val padded = fitted.inflate(EDGE_GAP).intersect(wholePage)
        return Rect(
            if (fitted.left < frame.left) padded.left else fitted.left,
            if (fitted.top < frame.top) padded.top else fitted.top,
            if (fitted.right > frame.right) padded.right else fitted.right,
            if (fitted.bottom > frame.bottom) padded.bottom else fitted.bottom,
        )
    }

    private fun Rect.attaches(bubble: Rect) = overlapArea(bubble) >= BUBBLE_ATTACH_OVERLAP * bubble.area

    private fun Rect.slices(bubble: Rect) = overlapArea(bubble) >= BUBBLE_SLICE_OVERLAP * bubble.area && !encloses(bubble)

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
        if (best.attaches(bubble)) return best
        if (best.slices(bubble)) return null
        return frames.filter { it.hostsAcrossTheGutter(bubble) }.minByOrNull { it.gutterTo(bubble) }
    }

    private fun Rect.hostsAcrossTheGutter(bubble: Rect): Boolean {
        val column = (minOf(right, bubble.right) - maxOf(left, bubble.left)).coerceAtLeast(0f)
        return gutterTo(bubble) in 0f..CAPTION_GUTTER_GAP && column >= CAPTION_COLUMN_SHARE * bubble.width
    }

    private fun Rect.gutterTo(bubble: Rect) = maxOf(top - bubble.bottom, bubble.top - bottom)

    private fun cluster(boxes: List<Rect>): List<Rect> =
        mergeWhile(boxes.map { listOf(it) }) { a, b ->
            a.any { x -> b.any { y -> gap(x, y) <= CLUSTER_GAP } } && readableTogether(a + b)
        }.map { it.reduce(Rect::expandToInclude) }

    private fun readableTogether(group: List<Rect>) =
        group.reduce(Rect::expandToInclude).height <= MIN_WINDOW_HEIGHT

    private fun mergeOverlapping(windows: List<Stop>): List<Stop> =
        mergeWhile(windows.map { listOf(it) }) { a, b ->
            val x = a.bounds { it.view }
            val y = b.bounds { it.view }
            x.overlapArea(y) >= WINDOW_MERGE_OVERLAP * minOf(x.area, y.area) &&
                x.expandToInclude(y).height <= MAX_WINDOW_HEIGHT
        }.map { group -> Stop(group.bounds { it.anchor }, group.bounds { it.view }, floating = true) }

    private fun <T> List<T>.bounds(box: (T) -> Rect): Rect = map(box).reduce(Rect::expandToInclude)

    private fun <T> mergeWhile(initial: List<List<T>>, joined: (List<T>, List<T>) -> Boolean): List<List<T>> {
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

    private fun windowStop(cluster: Rect, bounds: Rect, bubbles: List<Rect>, minimum: Size) =
        Stop(cluster, window(cluster, bounds, bubbles, minimum), floating = true)

    private fun window(region: Rect, bounds: Rect, bubbles: List<Rect>, minimum: Size): Rect {
        var window = sized(region, bounds, minimum)
        var kept = region
        repeat(bubbles.size + 1) {
            val crossing = bubbles.firstOrNull { window.crosses(it) } ?: return window
            val shrunk = shrunkPast(window, crossing, kept)
            if (shrunk != null) {
                window = shrunk
            } else {
                val grown = sized(kept.expandToInclude(crossing), bounds, minimum)
                if (grown.height > MAX_WINDOW_HEIGHT || grown.width > maxOf(MAX_WINDOW_WIDTH, window.width)) return window
                kept = kept.expandToInclude(crossing)
                window = grown
            }
        }
        return window
    }

    private fun sized(region: Rect, bounds: Rect, minimum: Size): Rect {
        val width = maxOf(minimum.width, region.width + 2 * MARGIN).coerceAtMost(bounds.width)
        val height = maxOf(minimum.height, region.height + 2 * MARGIN).coerceAtMost(bounds.height)
        val left = (region.center.x - width / 2f).coerceAtMost(bounds.right - width).coerceAtLeast(bounds.left)
        val top = (region.center.y - height / 2f).coerceAtMost(bounds.bottom - height).coerceAtLeast(bounds.top)
        return Rect(left, top, left + width, top + height)
    }

    private fun shrunkPast(window: Rect, bubble: Rect, kept: Rect): Rect? = listOf(
        window.copy(right = bubble.left - EDGE_GAP),
        window.copy(left = bubble.right + EDGE_GAP),
        window.copy(bottom = bubble.top - EDGE_GAP),
        window.copy(top = bubble.bottom + EDGE_GAP),
    ).filter { it.left <= kept.left && it.top <= kept.top && it.right >= kept.right && it.bottom >= kept.bottom }
        .maxByOrNull { it.area }

    private fun Rect.crosses(bubble: Rect): Boolean = overlapArea(bubble) > 0f && !encloses(bubble)

    private fun Rect.encloses(bubble: Rect): Boolean =
        bubble.left >= left && bubble.top >= top && bubble.right <= right && bubble.bottom <= bottom

    private fun withoutDeadTaps(stops: List<Stop>, bubbles: List<Rect>): List<Stop> {
        val kept = stops.toMutableList()
        while (kept.size > 1) {
            val opener = kept.firstOrNull { it.opening }
            val dead = kept.firstOrNull { stop -> stop !== opener && deadTap(stop, kept.filter { it !== stop && it !== opener }, bubbles) }
            kept.remove(dead ?: return kept)
        }
        return kept
    }

    private fun deadTap(stop: Stop, others: List<Stop>, bubbles: List<Rect>): Boolean {
        val shown = bubbles.filter { stop.view.encloses(it) }
        return shown.isNotEmpty() && shown.all { balloon -> others.any { it.view.encloses(balloon) } } &&
            coveredFraction(stop.view, others.map { it.view }) >= DEAD_TAP_COVERAGE
    }

    private fun coveredFraction(target: Rect, covers: List<Rect>): Float {
        val parts = covers.map { it.intersect(target) }.filter { it.width > 0f && it.height > 0f }
        val edges = parts.flatMap { listOf(it.left, it.right) }.distinct().sorted()
        val slabs = edges.zipWithNext { left, right ->
            (right - left) * heightOfUnion(parts.filter { it.left <= left && it.right >= right })
        }
        return slabs.sum() / target.area
    }

    private fun heightOfUnion(parts: List<Rect>): Float {
        var bottom = Float.NEGATIVE_INFINITY
        var height = 0f
        for (part in parts.sortedBy { it.top }) {
            height += (part.bottom - maxOf(part.top, bottom)).coerceAtLeast(0f)
            bottom = maxOf(bottom, part.bottom)
        }
        return height
    }

    private fun dropRedundant(stops: List<Stop>): List<Stop> {
        val out = ArrayList<Stop>()
        for (stop in stops) {
            val previous = out.lastOrNull()
            if (previous != null && redundant(previous, stop)) {
                if (stop.view.area > previous.view.area) out[out.lastIndex] = stop
            } else {
                out.add(stop)
            }
        }
        return out
    }

    private fun redundant(previous: Stop, stop: Stop): Boolean {
        val a = previous.view
        val b = stop.view
        val smaller = minOf(a.area, b.area)
        val sameView = smaller >= REDUNDANT_SIZE_RATIO * maxOf(a.area, b.area) && a.overlapArea(b) >= REDUNDANT_FRACTION * smaller
        val panelInsidePanel = !previous.floating && !stop.floating && a.encloses(b)
        return sameView || panelInsidePanel
    }

    private fun readingOrder(stops: List<Stop>, direction: ReadingDirection): List<Stop> {
        val containers = stops.filter { host -> stops.any { it !== host && contains(host.anchor, it.anchor) } }
        val ordered = cutOrder(stops - containers.toSet(), direction).toMutableList()
        for (container in containers.sortedBy { it.anchor.area }) {
            val firstContained = ordered.indexOfFirst { contains(container.anchor, it.anchor) }
            ordered.add(if (firstContained < 0) ordered.size else firstContained, container)
        }
        return ordered
    }

    private fun cutOrder(stops: List<Stop>, direction: ReadingDirection): List<Stop> {
        if (stops.size <= 1) return stops
        horizontalCut(stops)?.let { y ->
            val (top, bottom) = stops.partition { it.endsAbove(y) }
            return cutOrder(top, direction) + cutOrder(bottom, direction)
        }
        verticalCut(stops)?.let { x ->
            val (left, right) = stops.partition { it.endsBefore(x) }
            val (first, second) = if (direction == ReadingDirection.RightToLeft) right to left else left to right
            return cutOrder(first, direction) + cutOrder(second, direction)
        }
        return rowOrder(stops, direction)
    }

    private fun horizontalCut(stops: List<Stop>): Float? = stops.map { it.anchor.bottom }.sorted().firstOrNull { y ->
        stops.none { it.straddlesRow(y) } && stops.any { !it.endsAbove(y) }
    }

    private fun verticalCut(stops: List<Stop>): Float? = stops.map { it.anchor.right }.sorted().firstOrNull { x ->
        stops.none { it.straddlesColumn(x) } && stops.any { !it.endsBefore(x) }
    }

    private fun Stop.straddlesRow(y: Float) = anchor.top < y - intrusion(anchor.height) && anchor.bottom > y + intrusion(anchor.height)
    private fun Stop.straddlesColumn(x: Float) = anchor.left < x - intrusion(anchor.width) && anchor.right > x + intrusion(anchor.width)
    private fun Stop.endsAbove(y: Float) = anchor.bottom <= y + intrusion(anchor.height)
    private fun Stop.endsBefore(x: Float) = anchor.right <= x + intrusion(anchor.width)
    private fun Stop.intrusion(extent: Float) =
        maxOf(CUT_TOLERANCE, (if (floating) BALLOON_CUT_INTRUSION else PANEL_CUT_INTRUSION) * extent)

    private fun rowOrder(stops: List<Stop>, direction: ReadingDirection): List<Stop> {
        besideSpanning(stops, direction)?.let { return it }
        val rows = ArrayList<MutableList<Stop>>()
        for (stop in stops.sortedBy { it.anchor.top }) {
            val row = rows.firstOrNull { sameRow(it, stop) }
            if (row != null) row.add(stop) else rows.add(mutableListOf(stop))
        }
        return rows.sortedBy { it.top() }.flatMap { row -> rowStops(row, direction) }
    }

    private fun rowStops(row: List<Stop>, direction: ReadingDirection): List<Stop> {
        besideSpanning(row, direction)?.let { return it }
        val rightToLeft = direction == ReadingDirection.RightToLeft
        return row.sortedBy { if (rightToLeft) -it.anchor.center.x else it.anchor.center.x }
    }

    private fun besideSpanning(group: List<Stop>, direction: ReadingDirection): List<Stop>? {
        val spanning = group.filter { it.anchor.height >= SPANNING_HEIGHT * (group.bottom() - group.top()) && it.standsAlone(group) }
        val column = group - spanning.toSet()
        if (spanning.isEmpty() || column.size < SPANNED_COLUMN_MIN) return null
        return withSpanning(rowOrder(column, direction), spanning, direction)
    }

    private fun withSpanning(ordered: List<Stop>, spanning: List<Stop>, direction: ReadingDirection): List<Stop> {
        val out = ordered.toMutableList()
        val rightToLeft = direction == ReadingDirection.RightToLeft
        for (tall in spanning.sortedBy { if (rightToLeft) -it.anchor.center.x else it.anchor.center.x }) {
            val after = out.indexOfFirst { if (rightToLeft) it.anchor.center.x < tall.anchor.center.x else it.anchor.center.x > tall.anchor.center.x }
            out.add(if (after < 0) out.size else after, tall)
        }
        return out
    }

    private fun Stop.standsAlone(group: List<Stop>) = group.none { other ->
        other !== this && (minOf(other.anchor.bottom, anchor.bottom) - maxOf(other.anchor.top, anchor.top)) >= SPANNING_COMPANY * anchor.height
    }

    private fun sameRow(row: List<Stop>, stop: Stop): Boolean {
        val rowTop = row.top()
        val rowBottom = row.bottom()
        val rowCenterY = (rowTop + rowBottom) / 2f
        return stop.anchor.center.y in rowTop..rowBottom && rowCenterY in stop.anchor.top..stop.anchor.bottom
    }

    private fun List<Stop>.top() = minOf { it.anchor.top }
    private fun List<Stop>.bottom() = maxOf { it.anchor.bottom }

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
