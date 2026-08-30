package com.comicify.feature.reader.data

import android.graphics.Bitmap
import java.io.Closeable

interface ComicSource : Closeable {
    val pageCount: Int
    suspend fun decodePage(index: Int, targetWidth: Int): Bitmap
    suspend fun pageAspect(index: Int): Float
}
