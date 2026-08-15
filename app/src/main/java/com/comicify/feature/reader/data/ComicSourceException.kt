package com.comicify.feature.reader.data

import com.comicify.feature.reader.domain.ComicOpenError

sealed class ComicSourceException(val error: ComicOpenError, cause: Throwable? = null) : Exception(cause) {
    class UnsupportedFormat : ComicSourceException(ComicOpenError.UnsupportedFormat)
    class EmptyArchive : ComicSourceException(ComicOpenError.EmptyArchive)
    class ReadFailure(cause: Throwable) : ComicSourceException(ComicOpenError.ReadFailure, cause)
}
