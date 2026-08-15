package com.comicify.core.window

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class HingeOcclusion(val topDp: Dp, val bottomDp: Dp)

data class TabletopSplit(val pageHeight: Dp, val hingeHeight: Dp, val controlsHeight: Dp)

private val ProportionalPageFraction = 0.62f

fun splitAtHinge(containerHeight: Dp, hinge: HingeOcclusion?): TabletopSplit {
    if (hinge != null && hinge.fitsWithin(containerHeight)) {
        return TabletopSplit(
            pageHeight = hinge.topDp,
            hingeHeight = hinge.bottomDp - hinge.topDp,
            controlsHeight = containerHeight - hinge.bottomDp,
        )
    }
    val pageHeight = containerHeight * ProportionalPageFraction
    return TabletopSplit(
        pageHeight = pageHeight,
        hingeHeight = 0.dp,
        controlsHeight = containerHeight - pageHeight,
    )
}

private fun HingeOcclusion.fitsWithin(containerHeight: Dp): Boolean =
    topDp >= 0.dp && bottomDp > topDp && bottomDp <= containerHeight
