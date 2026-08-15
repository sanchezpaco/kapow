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
                ArchiveFormat.Rar -> CbrComicSource.fromFile(cacheFile)
                ArchiveFormat.Zip -> CbzComicSource.fromFile(cacheFile)
            }
        }

    suspend fun openFolder(context: Context, treeUri: Uri): ComicSource =
        FolderComicSource.fromTree(context, treeUri)

    private fun detectFormat(file: File): ArchiveFormat {
        val magic = ByteArray(4)
        file.inputStream().use { it.read(magic) }
        val isRar = magic[0] == 'R'.code.toByte() &&
            magic[1] == 'a'.code.toByte() &&
            magic[2] == 'r'.code.toByte() &&
            magic[3] == '!'.code.toByte()
        return if (isRar) ArchiveFormat.Rar else ArchiveFormat.Zip
    }
}

private enum class ArchiveFormat { Zip, Rar }
