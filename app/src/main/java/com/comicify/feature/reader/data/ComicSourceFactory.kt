package com.comicify.feature.reader.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ComicSourceFactory {

    suspend fun open(context: Context, uri: Uri): ComicSource =
        withContext(Dispatchers.IO) {
            val cacheFile = File.createTempFile("comic", ".dat", context.cacheDir)
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open comic stream" }
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            when (detectFormat(cacheFile)) {
                ComicFileFormat.Pdf -> PdfComicSource.fromFile(cacheFile)
                ComicFileFormat.Rar -> CbrComicSource.fromFile(cacheFile)
                ComicFileFormat.Zip -> CbzComicSource.fromFile(cacheFile)
            }
        }

    suspend fun openFolder(context: Context, treeUri: Uri): ComicSource =
        FolderComicSource.fromTree(context, treeUri)

    private fun detectFormat(file: File): ComicFileFormat {
        val magic = ByteArray(MAGIC_BYTE_COUNT)
        file.inputStream().use { it.read(magic) }
        return detectComicFileFormat(magic)
    }
}
