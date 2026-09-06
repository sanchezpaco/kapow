package com.comicify.feature.reader.domain

import androidx.compose.ui.graphics.ColorMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val OPAQUE = 255f
private const val MID_GREY = 128f

private data class Rgb(val red: Float, val green: Float, val blue: Float)

private fun ColorMatrix.applyTo(grey: Float) = applyTo(grey, grey, grey)

private fun ColorMatrix.applyTo(red: Float, green: Float, blue: Float): Rgb {
    fun channel(row: Int) =
        this[row, 0] * red + this[row, 1] * green + this[row, 2] * blue + this[row, 3] * OPAQUE + this[row, 4]
    return Rgb(channel(0), channel(1), channel(2))
}

class PageLookTest {

    private val delta = 0.05f

    @Test
    fun originalInstallsNoFilter() {
        assertNull(PageLook.Original.filter())
    }

    @Test
    fun everyOtherLookCarriesAFilter() {
        PageLook.entries.filter { it != PageLook.Original }.forEach { assertNotNull(it.filter()) }
    }

    @Test
    fun brighterLiftsThenAddsContrast() {
        val look = PageLook.Brighter.filter()!!
        assertEquals(14.6f, look.applyTo(0f).red, delta)
        assertEquals(149f, look.applyTo(MID_GREY).red, delta)
    }

    @Test
    fun brighterKeepsGreysNeutral() {
        val grey = PageLook.Brighter.filter()!!.applyTo(MID_GREY)
        assertEquals(grey.red, grey.green, delta)
        assertEquals(grey.green, grey.blue, delta)
    }

    @Test
    fun moreContrastPivotsAboutTheMidLevel() {
        val look = PageLook.MoreContrast.filter()!!
        assertEquals(MID_GREY, look.applyTo(MID_GREY).red, delta)
        assertEquals(-32f, look.applyTo(0f).red, delta)
    }

    @Test
    fun moreContrastPushesColourApart() {
        val plain = PageLook.MoreContrast.filter()!!.applyTo(200f, 120f, 120f)
        assertTrue(plain.red - plain.green > 200f - 120f)
    }

    @Test
    fun paperCoolsTheMidGrey() {
        val grey = PageLook.Paper.filter()!!.applyTo(MID_GREY)
        assertEquals(0.98f * MID_GREY, grey.red, delta)
        assertEquals(MID_GREY, grey.green, delta)
        assertEquals(1.06f * MID_GREY, grey.blue, delta)
    }

    @Test
    fun cyclingWrapsBackToOriginalInThreeSteps() {
        assertEquals(PageLook.Brighter, PageLook.Original.next())
        assertEquals(PageLook.MoreContrast, PageLook.Brighter.next())
        assertEquals(PageLook.Paper, PageLook.MoreContrast.next())
        assertEquals(PageLook.Original, PageLook.Paper.next())
    }

    @Test
    fun unknownStoredNameFallsBackToOriginal() {
        assertEquals(PageLook.Paper, PageLook.named("Paper"))
        assertEquals(PageLook.Original, PageLook.named("Sepia"))
        assertEquals(PageLook.Original, PageLook.named(null))
    }
}
