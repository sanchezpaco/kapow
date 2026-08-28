package com.comicify.feature.reader.data

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files

object ComicSourceFactory {

    suspend fun open(context: Context, uri: Uri, startPage: Int): ComicSource =
        withContext(Dispatchers.IO) {
            val descriptor = openDescriptor(context, uri)
            val source = openSource(context, descriptor, startPage)
            if (source.pageCount == 0) {
                source.close()
                throw ComicSourceException.EmptyArchive()
            }
            source
        }

    private fun openDescriptor(context: Context, uri: Uri): ParcelFileDescriptor =
        try {
            context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw ComicSourceException.ReadFailure(IOException("Cannot open comic descriptor"))
        } catch (e: SecurityException) {
            throw ComicSourceException.AccessLost(e)
        } catch (e: java.io.FileNotFoundException) {
            throw ComicSourceException.ReadFailure(e)
        }

    suspend fun openFolder(context: Context, treeUri: Uri): ComicSource =
        FolderComicSource.fromTree(context, treeUri)

    private suspend fun openSource(context: Context, descriptor: ParcelFileDescriptor, startPage: Int): ComicSource =
        try {
            when (detectFormat(FileInputStream(descriptor.fileDescriptor).channel)) {
                ComicFileFormat.Pdf -> PdfComicSource.fromDescriptor(descriptor)
                ComicFileFormat.Rar -> CbrComicSource.fromDescriptor(descriptor, freshExtractDir(context), startPage)
                ComicFileFormat.Zip -> CbzComicSource.fromFile(copyToCacheFile(context, descriptor))
                ComicFileFormat.Unsupported -> throw ComicSourceException.UnsupportedFormat()
            }
        } catch (e: ComicSourceException) {
            descriptor.close()
            throw e
        } catch (e: IOException) {
            descriptor.close()
            throw ComicSourceException.ReadFailure(e)
        } catch (e: SecurityException) {
            descriptor.close()
            throw ComicSourceException.PasswordProtected(e)
        }

    private fun copyToCacheFile(context: Context, descriptor: ParcelFileDescriptor): File {
        val cacheFile = File.createTempFile("comic", ".dat", context.cacheDir)
        try {
            descriptor.use { FileInputStream(it.fileDescriptor).use { input -> cacheFile.outputStream().use(input::copyTo) } }
        } catch (e: IOException) {
            cacheFile.delete()
            throw e
        }
        return cacheFile
    }

    private fun freshExtractDir(context: Context): File =
        Files.createTempDirectory(context.cacheDir.toPath(), "comic_pages").toFile()

    internal fun detectFormat(channel: FileChannel): ComicFileFormat {
        val magic = ByteBuffer.allocate(MAGIC_BYTE_COUNT)
        channel.read(magic, 0)
        return detectComicFileFormat(magic.array())
    }
}
