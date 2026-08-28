package com.comicify.feature.reader.domain

sealed interface ComicOpenError {
    data object UnsupportedFormat : ComicOpenError
    data object EmptyArchive : ComicOpenError
    data object ReadFailure : ComicOpenError
    data object PasswordProtected : ComicOpenError
    data object AccessLost : ComicOpenError
}
