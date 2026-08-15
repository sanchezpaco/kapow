package com.comicify.feature.reader.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

private const val INSET_CONTAINMENT = 0.8f
private const val INSET_MAX_AREA_SHARE = 0.6f
private const val MAX_SEPARATOR_TILT_DEGREES = 60
private const val SEPARATOR_TILT_STEP_DEGREES = 2
private const val OUTLIER_SHARE = 0.12f
private const val MIN_PROFILE_LINES = 4

private val SLOPES = (-MAX_SEPARATOR_TILT_DEGREES..MAX_SEPARATOR_TILT_DEGREES step SEPARATOR_TILT_STEP_DEGREES)
    .map { tan(Math.toRadians(it.toDouble())).toFloat() }

class PanelBodies(segmentation: Segmentation, private val width: Int, private val height: Int, private val isFramed: (Box) -> Boolean) {

    private val boxes = segmentation.components.map { it.box }.toMutableList()
    private val profiles = EdgeProfiles.of(segmentation, width, height).toMutableList()
    private val alive = BooleanArray(boxes.size) { true }

    fun merged(): List<Box> {
        var changed = true
        while (changed) {
            changed = false
            outer@ for (a in boxes.indices) {
                if (!alive[a]) continue
                for (b in a + 1 until boxes.size) {
                    if (!alive[b] || !shouldMerge(a, b)) continue
                    boxes[a] = boxes[a].union(boxes[b])
                    profiles[a] = profiles[a].union(profiles[b])
                    alive[b] = false
                    changed = true
                    break@outer
                }
            }
        }
        return boxes.indices.filter { alive[it] }.map { boxes[it] }
    }

    private fun shouldMerge(a: Int, b: Int): Boolean {
        val overlap = boxes[a].intersectionArea(boxes[b])
        if (isInset(boxes[a], boxes[b], overlap) && isFramed(smaller(boxes[a], boxes[b]))) return false
        return !separable(profiles[a], profiles[b])
    }

    private fun smaller(a: Box, b: Box) = if (a.area <= b.area) a else b

    private fun isInset(a: Box, b: Box, overlap: Int): Boolean {
        val small = min(a.area, b.area)
        val large = max(a.area, b.area)
        return overlap >= INSET_CONTAINMENT * small && small <= INSET_MAX_AREA_SHARE * large
    }

    private fun separable(a: EdgeProfiles, b: EdgeProfiles): Boolean =
        separableAlong(a.last, b.first) || separableAlong(b.last, a.first) ||
            separableAlong(a.bottom, b.top) || separableAlong(b.bottom, a.top)

    private fun separableAlong(nearSide: IntArray, farSide: IntArray): Boolean {
        val nearLines = nearSide.indices.filter { nearSide[it] >= 0 }
        val farLines = farSide.indices.filter { farSide[it] >= 0 }
        if (nearLines.size < MIN_PROFILE_LINES || farLines.size < MIN_PROFILE_LINES) return true
        return SLOPES.any { slope ->
            val upper = percentile(nearLines.map { nearSide[it] - slope * it }, 1f - OUTLIER_SHARE)
            val lower = percentile(farLines.map { farSide[it] - slope * it }, OUTLIER_SHARE)
            upper < lower
        }
    }

    private fun percentile(values: List<Float>, share: Float): Float {
        val sorted = values.sorted()
        return sorted[(share * (sorted.size - 1)).toInt()]
    }
}

class EdgeProfiles(val first: IntArray, val last: IntArray, val top: IntArray, val bottom: IntArray) {

    fun union(other: EdgeProfiles) = EdgeProfiles(
        first = merge(first, other.first, ::minPresent),
        last = merge(last, other.last, ::max),
        top = merge(top, other.top, ::minPresent),
        bottom = merge(bottom, other.bottom, ::max),
    )

    private fun merge(a: IntArray, b: IntArray, pick: (Int, Int) -> Int) = IntArray(a.size) { pick(a[it], b[it]) }

    private fun minPresent(a: Int, b: Int) = if (a < 0) b else if (b < 0) a else min(a, b)

    companion object {
        fun of(segmentation: Segmentation, width: Int, height: Int): List<EdgeProfiles> {
            val slot = HashMap<Int, Int>()
            segmentation.components.forEachIndexed { index, component -> slot[component.label] = index }
            val profiles = List(segmentation.components.size) {
                EdgeProfiles(IntArray(height) { -1 }, IntArray(height) { -1 }, IntArray(width) { -1 }, IntArray(width) { -1 })
            }
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = slot[segmentation.labels[y * width + x]] ?: continue
                    val profile = profiles[index]
                    if (profile.first[y] < 0) profile.first[y] = x
                    profile.last[y] = x
                    if (profile.top[x] < 0) profile.top[x] = y
                    profile.bottom[x] = y
                }
            }
            return profiles
        }
    }
}
