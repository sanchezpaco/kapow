package com.comicify.feature.library.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ComicIdentityTest {

    @Test
    fun `same bytes and size hash the same`() {
        val prefix = "KAPOW".repeat(1000).toByteArray()

        assertEquals(ComicIdentity.hash(prefix, 4096), ComicIdentity.hash(prefix.copyOf(), 4096))
    }

    @Test
    fun `size takes part in the identity`() {
        val prefix = ByteArray(64) { it.toByte() }

        assertNotEquals(ComicIdentity.hash(prefix, 4096), ComicIdentity.hash(prefix, 4097))
    }

    @Test
    fun `different bytes hash differently`() {
        val prefix = ByteArray(64) { it.toByte() }
        val altered = prefix.copyOf().also { it[63] = 0 }

        assertNotEquals(ComicIdentity.hash(prefix, 4096), ComicIdentity.hash(altered, 4096))
    }

    @Test
    fun `hash is forty lowercase hex characters`() {
        val hash = ComicIdentity.hash(ByteArray(16), 16)

        assertEquals(40, hash.length)
        assertEquals(hash, hash.lowercase())
        assertEquals(true, hash.all { it in "0123456789abcdef" })
    }

    @Test
    fun `an empty prefix still hashes`() {
        assertEquals(ComicIdentity.hash(ByteArray(0), 0), ComicIdentity.hash(ByteArray(0), 0))
        assertNotEquals(ComicIdentity.hash(ByteArray(0), 0), ComicIdentity.hash(ByteArray(0), 1))
    }
}
