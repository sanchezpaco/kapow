package com.comicify.feature.reader.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object ComicSourceFactory {

    suspend fun open(context: Context, uri: Uri): ComicSource =
        withContext(Dispatchers.IO) {
            val cacheFile = copyToCacheFile(context, uri)
            val source = openSource(cacheFile)
            if (source.pageCount == 0) {
                source.close()
                throw ComicSourceException.EmptyArchive()
            }
            source
        }

    suspend fun openFolder(context: Context, treeUri: Uri): ComicSource =
        FolderComicSource.fromTree(context, treeUri)

    private fun copyToCacheFile(context: Context, uri: Uri): File {
        val cacheFile = File.createTempFile("comic", ".dat", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw ComicSourceException.ReadFailure(IOException("Cannot open comic stream"))
            input.use { stream -> cacheFile.outputStream().use { output -> stream.copyTo(output) } }
        } catch (e: ComicSourceException) {
            cacheFile.delete()
            throw e
        } catch (e: IOException) {
            cacheFile.delete()
            throw ComicSourceException.ReadFailure(e)
        }
        return cacheFile
    }

    private suspend fun openSource(cacheFile: File): ComicSource =
        try {
            when (detectFormat(cacheFile)) {
                ComicFileFormat.Pdf -> PdfComicSource.fromFile(cacheFile)
                ComicFileFormat.Rar -> CbrComicSource.fromFile(cacheFile)
                ComicFileFormat.Zip -> CbzComicSource.fromFile(cacheFile)
                ComicFileFormat.Unsupported -> throw ComicSourceException.UnsupportedFormat()
            }
        } catch (e: ComicSourceException) {
            cacheFile.delete()
            throw e
        } catch (e: IOException) {
            cacheFile.delete()
            throw ComicSourceException.ReadFailure(e)
        }

    internal fun detectFormat(file: File): ComicFileFormat {
        val magic = ByteArray(MAGIC_BYTE_COUNT)
        file.inputStream().use { it.read(magic) }
        return detectComicFileFormat(magic)
    }
}
