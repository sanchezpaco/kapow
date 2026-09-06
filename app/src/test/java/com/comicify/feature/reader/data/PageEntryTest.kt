package com.comicify.feature.reader.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageEntryTest {

    @Test
    fun acceptsImagesAtRootAndInFolders() {
        assertTrue("page001.png".isPageEntry())
        assertTrue("Issue 01/page001.JPG".isPageEntry())
    }

    @Test
    fun rejectsNonImages() {
        assertFalse("ComicInfo.xml".isPageEntry())
        assertFalse("page001".isPageEntry())
    }

    @Test
    fun rejectsPaxHeadersWrittenByBsdTar() {
        assertFalse("PaxHeader/page001.png".isPageEntry())
        assertFalse("Issue 01/PaxHeader/page001.png".isPageEntry())
    }

    @Test
    fun rejectsAppleResourceForks() {
        assertFalse("__MACOSX/page001.png".isPageEntry())
        assertFalse("__MACOSX/._page001.png".isPageEntry())
        assertFalse("._page001.png".isPageEntry())
        assertFalse("Issue 01\\._page001.png".isPageEntry())
    }
}
