package com.comicify.feature.library.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.comicify.feature.library.domain.ComicCover
import com.comicify.feature.reader.data.ComicSource
import com.comicify.feature.reader.data.ComicSourceException
import com.comicify.feature.reader.data.ComicSourceFactory
import com.comicify.feature.reader.data.ambientColorInt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

private const val COVER_TAG = "CoverGenerator"
internal const val COVER_WIDTH_PX = 360
private const val COVER_QUALITY = 85
private const val COVER_DIRECTORY = "covers"

data class GeneratedCover(
    val pageCount: Int,
    val coverPage: Int,
    val coverPath: String,
    val ambient: Int,
    val comicInfoXml: String?,
)

class CoverGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun generate(comicId: Long, documentUri: Uri, chosenPage: Int): GeneratedCover = withContext(Dispatchers.IO) {
        val source = ComicSourceFactory.open(context, documentUri, startPage = chosenPage)
        try {
            val page = ComicCover.page(chosenPage, source.pageCount)
            val bitmap = source.decodePage(page, COVER_WIDTH_PX)
            GeneratedCover(
                pageCount = source.pageCount,
                coverPage = page,
                coverPath = writeCover(comicId, page, bitmap).absolutePath,
                ambient = bitmap.ambientColorInt(),
                comicInfoXml = readComicInfoXml(source),
            )
        } finally {
            source.close()
        }
    }

    fun deleteCovers(comicId: Long) {
        coverDirectory().listFiles()?.forEach { file ->
            if (ComicCover.belongsTo(file.name, comicId)) file.delete()
        }
    }

    private suspend fun readComicInfoXml(source: ComicSource): String? =
        try {
            source.comicInfoXml()
        } catch (e: ComicSourceException) {
            Log.w(COVER_TAG, "ComicInfo.xml could not be read", e)
            null
        } catch (e: IOException) {
            Log.w(COVER_TAG, "ComicInfo.xml could not be read", e)
            null
        }

    private fun writeCover(comicId: Long, page: Int, bitmap: Bitmap): File {
        val file = File(coverDirectory().apply { mkdirs() }, ComicCover.fileName(comicId, page))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, COVER_QUALITY, it) }
        return file
    }

    private fun coverDirectory(): File = File(context.filesDir, COVER_DIRECTORY)
}
