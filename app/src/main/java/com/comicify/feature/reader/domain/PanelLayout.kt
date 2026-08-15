package com.comicify.feature.reader.domain

import kotlin.math.max
import kotlin.math.min

private const val CONTAINMENT = 0.8f
private const val SHARED_REGION_OVERLAP = 0.2f
private const val REDUNDANT_CONTAINER_COVERAGE = 0.75f

data class Panel(val body: Box, val frame: Box) {
    fun grown(region: Box) = Panel(body, frame.union(region))
}

object PanelLayout {

    fun attach(panels: List<Panel>, enclosed: List<Box>, isPanelWorthy: (Box) -> Boolean): List<Panel> {
        val result = panels.toMutableList()
        for (region in enclosed.sortedByDescending { it.area }) {
            val hosts = hostsOf(result, region)
            if (hosts.isEmpty() && isPanelWorthy(region)) result.add(Panel(region, region))
            hosts.forEach { result[it] = result[it].grown(region) }
        }
        return result
    }

    private fun hostsOf(panels: List<Panel>, region: Box): List<Int> {
        val shared = panels.indices.filter { panels[it].frame.intersectionArea(region) >= SHARED_REGION_OVERLAP * region.area }
        if (shared.isNotEmpty()) return shared
        val best = panels.indices.maxByOrNull { panels[it].frame.intersectionArea(region) } ?: return emptyList()
        return if (panels[best].frame.intersectionArea(region) > 0) listOf(best) else emptyList()
    }

    fun absorbContained(panels: List<Panel>, isFramed: (Box) -> Boolean): List<Panel> {
        val result = panels.sortedByDescending { it.frame.area }.toMutableList()
        var i = 0
        while (i < result.size) {
            val host = result[i]
            val absorbed = result.drop(i + 1).filter { host.frame.intersectionArea(it.frame) >= CONTAINMENT * it.frame.area && !isFramed(it.body) }
            if (absorbed.isEmpty()) { i++; continue }
            result[i] = absorbed.fold(host) { merged, panel -> merged.grown(panel.frame) }
            result.removeAll(absorbed)
        }
        return result
    }

    fun absorbSlivers(panels: List<Panel>, isSliver: (Box) -> Boolean, reach: Int): List<Panel> {
        val result = panels.toMutableList()
        for (sliver in panels.filter { isSliver(it.body) }) {
            result.remove(sliver)
            val neighbourhood = Box(sliver.frame.left - reach, sliver.frame.top - reach, sliver.frame.right + reach, sliver.frame.bottom + reach)
            val host = result.indices.maxByOrNull { result[it].frame.intersectionArea(neighbourhood) } ?: continue
            if (result[host].frame.intersectionArea(neighbourhood) > 0) result[host] = result[host].grown(sliver.frame)
        }
        return result
    }

    fun dropRedundantContainers(boxes: List<Box>): List<Box> = boxes.filter { host ->
        val covered = boxes.filter { it !== host && contains(host, it) }.sumOf { host.intersectionArea(it) }
        covered < REDUNDANT_CONTAINER_COVERAGE * host.area
    }

    fun readingOrder(boxes: List<Box>): List<Box> {
        val containers = boxes.filter { host -> boxes.any { it !== host && contains(host, it) } }
        val ordered = rowOrder(boxes - containers.toSet()).toMutableList()
        for (container in containers.sortedBy { it.area }) {
            val firstContained = ordered.indexOfFirst { contains(container, it) }
            ordered.add(if (firstContained < 0) ordered.size else firstContained, container)
        }
        return ordered
    }

    private fun contains(host: Box, box: Box) = box.area < host.area && host.intersectionArea(box) >= CONTAINMENT * box.area

    private fun rowOrder(boxes: List<Box>): List<Box> {
        val rows = ArrayList<MutableList<Box>>()
        for (box in boxes.sortedWith(compareBy({ it.top }, { it.left }))) {
            val row = rows.firstOrNull { sameRow(it, box) }
            if (row != null) row.add(box) else rows.add(mutableListOf(box))
        }
        return rows
            .sortedWith(compareBy({ row -> row.minOf { it.top } }, { row -> row.minOf { it.left } }))
            .flatMap { row -> row.sortedWith(compareBy({ it.left }, { it.top })) }
    }

    private fun sameRow(row: List<Box>, box: Box): Boolean {
        val rowTop = row.minOf { it.top }
        val rowBottom = row.maxOf { it.bottom }
        val boxCenter = (box.top + box.bottom) / 2
        val rowCenter = (rowTop + rowBottom) / 2
        return boxCenter in rowTop until rowBottom && rowCenter in box.top until box.bottom
    }

    fun coverage(boxes: List<Box>, content: BooleanArray, width: Int): Float {
        var total = 0
        var covered = 0
        for (i in content.indices) {
            if (!content[i]) continue
            total++
            val x = i % width
            val y = i / width
            if (boxes.any { x >= it.left && x < it.right && y >= it.top && y < it.bottom }) covered++
        }
        return if (total == 0) 0f else covered / total.toFloat()
    }
}
