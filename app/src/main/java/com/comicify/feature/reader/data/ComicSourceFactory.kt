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
                ArchiveFormat.Zip -> CbzComicSource.fromFile(cacheFile)
                ArchiveFormat.Rar -> CbrComicSource.fromFile(cacheFile)
                ArchiveFormat.Unsupported -> throw ComicSourceException.UnsupportedFormat()
            }
        } catch (e: ComicSourceException) {
            cacheFile.delete()
            throw e
        } catch (e: IOException) {
            cacheFile.delete()
            throw ComicSourceException.ReadFailure(e)
        }

    internal fun detectFormat(file: File): ArchiveFormat {
        val magic = ByteArray(4)
        file.inputStream().use { it.read(magic) }
        val isZip = magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()
        val isRar = magic[0] == 'R'.code.toByte() &&
            magic[1] == 'a'.code.toByte() &&
            magic[2] == 'r'.code.toByte() &&
            magic[3] == '!'.code.toByte()
        return when {
            isZip -> ArchiveFormat.Zip
            isRar -> ArchiveFormat.Rar
            else -> ArchiveFormat.Unsupported
        }
    }
}

internal enum class ArchiveFormat { Zip, Rar, Unsupported }
