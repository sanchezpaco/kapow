package com.comicify.core.window

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class TabletopSplitTest {

    @Test
    fun splitsExactlyAtHingeWhenBoundsFitTheContainer() {
        val split = splitAtHinge(containerHeight = 800.dp, hinge = HingeOcclusion(topDp = 470.dp, bottomDp = 490.dp))

        assertEquals(470.dp, split.pageHeight)
        assertEquals(20.dp, split.hingeHeight)
        assertEquals(310.dp, split.controlsHeight)
    }

    @Test
    fun fallsBackToProportionalSplitWhenHingeIsMissing() {
        val split = splitAtHinge(containerHeight = 800.dp, hinge = null)

        assertEquals(496.dp, split.pageHeight)
        assertEquals(0.dp, split.hingeHeight)
        assertEquals(304.dp, split.controlsHeight)
    }

    @Test
    fun fallsBackToProportionalSplitWhenHingeExtendsPastTheContainer() {
        val split = splitAtHinge(containerHeight = 800.dp, hinge = HingeOcclusion(topDp = 780.dp, bottomDp = 820.dp))

        assertEquals(496.dp, split.pageHeight)
        assertEquals(0.dp, split.hingeHeight)
    }

    @Test
    fun fallsBackToProportionalSplitWhenHingeHasNoHeight() {
        val split = splitAtHinge(containerHeight = 800.dp, hinge = HingeOcclusion(topDp = 470.dp, bottomDp = 470.dp))

        assertEquals(496.dp, split.pageHeight)
        assertEquals(0.dp, split.hingeHeight)
    }

    @Test
    fun fallsBackToProportionalSplitWhenHingeStartsBeforeTheContainer() {
        val split = splitAtHinge(containerHeight = 800.dp, hinge = HingeOcclusion(topDp = (-10).dp, bottomDp = 490.dp))

        assertEquals(496.dp, split.pageHeight)
        assertEquals(0.dp, split.hingeHeight)
    }

    @Test
    fun splitAlwaysAccountsForTheFullContainerHeight() {
        val split = splitAtHinge(containerHeight = 800.dp, hinge = HingeOcclusion(topDp = 470.dp, bottomDp = 490.dp))

        assertEquals(800.dp, split.pageHeight + split.hingeHeight + split.controlsHeight)
    }
}
