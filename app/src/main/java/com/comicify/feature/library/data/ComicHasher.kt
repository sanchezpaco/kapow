package com.comicify.feature.library.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.comicify.feature.library.domain.ComicIdentity
import com.comicify.feature.library.domain.IDENTITY_PREFIX_BYTES
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.InputStream
import javax.inject.Inject

private const val HASH_TAG = "ComicHasher"

class ComicHasher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun hash(documentUri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching { readIdentity(documentUri) }
            .onFailure { error -> Log.w(HASH_TAG, "Could not hash $documentUri", error) }
            .getOrNull()
    }

    private fun readIdentity(documentUri: Uri): String? =
        context.contentResolver.openFileDescriptor(documentUri, "r")?.use { descriptor ->
            val sizeBytes = descriptor.statSize
            if (sizeBytes <= 0) return@use null
            FileInputStream(descriptor.fileDescriptor).use { stream ->
                ComicIdentity.hash(stream.readPrefix(IDENTITY_PREFIX_BYTES), sizeBytes)
            }
        }

    private fun InputStream.readPrefix(limit: Int): ByteArray {
        val buffer = ByteArray(limit)
        var filled = 0
        while (filled < limit) {
            val read = read(buffer, filled, limit - filled)
            if (read < 0) break
            filled += read
        }
        return if (filled == limit) buffer else buffer.copyOf(filled)
    }
}
