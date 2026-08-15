package com.comicify.feature.reader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderComicSourceTest {

    @Test
    fun keepsOnlySupportedImageExtensions() {
        val names = listOf(
            "page1.jpg",
            "page2.JPEG",
            "page3.png",
            "page4.webp",
            "page5.gif",
            "page6.bmp",
            "notes.txt",
            "cover",
            "thumbs.db",
        )
        val ordered = names.inImageReadingOrder { it }
        assertEquals(
            listOf("page1.jpg", "page2.JPEG", "page3.png", "page4.webp", "page5.gif", "page6.bmp"),
            ordered,
        )
    }

    @Test
    fun sortsImagesNaturally() {
        val names = listOf("page10.png", "page2.png", "page1.png", "page20.png")
        val ordered = names.inImageReadingOrder { it }
        assertEquals(
            listOf("page1.png", "page2.png", "page10.png", "page20.png"),
            ordered,
        )
    }

    @Test
    fun filtersAndSortsTogether() {
        val names = listOf("10.jpg", "readme.md", "2.jpg", "1.jpg", "cover.psd")
        val ordered = names.inImageReadingOrder { it }
        assertEquals(listOf("1.jpg", "2.jpg", "10.jpg"), ordered)
    }
}
