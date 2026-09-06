package com.comicify.feature.library.domain

import java.security.MessageDigest

const val IDENTITY_PREFIX_BYTES = 512 * 1024

private const val IDENTITY_DIGEST = "SHA-1"

object ComicIdentity {

    fun hash(prefix: ByteArray, sizeBytes: Long): String {
        val digest = MessageDigest.getInstance(IDENTITY_DIGEST)
        digest.update(prefix)
        digest.update(sizeBytes.toString().toByteArray())
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
}
