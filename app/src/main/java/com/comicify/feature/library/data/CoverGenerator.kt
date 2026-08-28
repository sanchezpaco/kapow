package com.comicify.feature.library.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.comicify.feature.reader.data.ComicSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val COVER_WIDTH_PX = 360
private const val COVER_QUALITY = 85

data class GeneratedCover(val pageCount: Int, val coverPath: String)

class CoverGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun generate(comicId: Long, documentUri: Uri): GeneratedCover = withContext(Dispatchers.IO) {
        val source = ComicSourceFactory.open(context, documentUri, startPage = 0)
        try {
            val bitmap = source.decodePage(0, COVER_WIDTH_PX)
            GeneratedCover(pageCount = source.pageCount, coverPath = writeCover(comicId, bitmap).absolutePath)
        } finally {
            source.close()
        }
    }

    private fun writeCover(comicId: Long, bitmap: Bitmap): File {
        val directory = File(context.filesDir, "covers").apply { mkdirs() }
        val file = File(directory, "$comicId.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, COVER_QUALITY, it) }
        return file
    }
}
