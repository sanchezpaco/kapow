package com.comicify.feature.reader.data

import com.comicify.feature.reader.domain.ComicOpenError
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class ComicSourceExceptionTest {

    @Test
    fun unsupportedFormatMapsToUnsupportedFormatError() {
        assertEquals(ComicOpenError.UnsupportedFormat, ComicSourceException.UnsupportedFormat().error)
    }

    @Test
    fun emptyArchiveMapsToEmptyArchiveError() {
        assertEquals(ComicOpenError.EmptyArchive, ComicSourceException.EmptyArchive().error)
    }

    @Test
    fun readFailureMapsToReadFailureErrorAndKeepsCause() {
        val cause = IOException("boom")
        val exception = ComicSourceException.ReadFailure(cause)

        assertEquals(ComicOpenError.ReadFailure, exception.error)
        assertEquals(cause, exception.cause)
    }
}
