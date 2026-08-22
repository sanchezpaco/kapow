package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min

data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val area: Int get() = width * height

    fun contains(x: Int, y: Int) = x >= left && x < right && y >= top && y < bottom

    fun chebyshevDistanceTo(x: Int, y: Int): Int =
        max(max(0, max(left - x, x - right + 1)), max(0, max(top - y, y - bottom + 1)))

    fun distanceSquaredTo(x: Int, y: Int): Int {
        val dx = max(0, max(left - x, x - right + 1))
        val dy = max(0, max(top - y, y - bottom + 1))
        return dx * dx + dy * dy
    }

    fun covers(other: Box) = left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom

    fun intersect(other: Box) = Box(max(left, other.left), max(top, other.top), min(right, other.right), min(bottom, other.bottom))

    fun union(other: Box) = Box(min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom))

    fun intersectionArea(other: Box): Int {
        val w = min(right, other.right) - max(left, other.left)
        val h = min(bottom, other.bottom) - max(top, other.top)
        return if (w <= 0 || h <= 0) 0 else w * h
    }

    fun inflate(amount: Int, width: Int, height: Int) =
        Box(max(0, left - amount), max(0, top - amount), min(width, right + amount), min(height, bottom + amount))

    fun toRect(width: Int, height: Int) =
        Rect(left / width.toFloat(), top / height.toFloat(), right / width.toFloat(), bottom / height.toFloat())
}
