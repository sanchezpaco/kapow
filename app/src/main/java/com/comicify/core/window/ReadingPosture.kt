package com.comicify.core.window

enum class ReadingPosture {
    CompactSingle,
    UnfoldedSingle,
    UnfoldedSpread,
    Tabletop,
}

data class WindowState(
    val widthDp: Int,
    val isLandscape: Boolean,
    val isHalfOpen: Boolean,
    val hingeIsHorizontal: Boolean,
)

private const val EXPANDED_WIDTH_DP = 840
private const val MEDIUM_WIDTH_DP = 600

fun WindowState.toPosture(): ReadingPosture = when {
    isHalfOpen && hingeIsHorizontal -> ReadingPosture.Tabletop
    widthDp >= EXPANDED_WIDTH_DP && isLandscape -> ReadingPosture.UnfoldedSpread
    widthDp >= MEDIUM_WIDTH_DP -> ReadingPosture.UnfoldedSingle
    else -> ReadingPosture.CompactSingle
}
