package com.comicify.core.ui

import com.comicify.R
import com.comicify.feature.reader.domain.PageLook
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLabelsTest {

    @Test
    fun `every page look reads as its own label`() {
        val labels = PageLook.entries.map { it.labelRes() }
        assertEquals(PageLook.entries.size, labels.toSet().size)
        assertEquals(R.string.reader_page_look_original, PageLook.Original.labelRes())
        assertEquals(R.string.reader_page_look_paper, PageLook.Paper.labelRes())
    }

    @Test
    fun `page fit reads as screen or width`() {
        assertEquals(R.string.reader_page_fit_screen, pageFitLabelRes(fitWidth = false))
        assertEquals(R.string.reader_page_fit_width, pageFitLabelRes(fitWidth = true))
    }
}
