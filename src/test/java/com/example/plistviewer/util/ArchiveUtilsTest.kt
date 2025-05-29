package com.example.plistviewer.util

import org.junit.Assert.*
import org.junit.Test

class ArchiveUtilsTest {

    @Test
    fun `isKeyedArchive returns true for correct bplist header`() {
        val bytesWithHeader = "bplist00extraData".toByteArray(Charsets.UTF_8)
        assertTrue("Should return true for 'bplist' header", ArchiveUtils.isKeyedArchive(bytesWithHeader))
    }

    @Test
    fun `isKeyedArchive returns true for exact bplist header`() {
        val bytesExactHeader = "bplist".toByteArray(Charsets.UTF_8)
        assertTrue("Should return true for exact 'bplist' header", ArchiveUtils.isKeyedArchive(bytesExactHeader))
    }

    @Test
    fun `isKeyedArchive returns false for incorrect header`() {
        val bytesWithoutHeader = "aplist00extraData".toByteArray(Charsets.UTF_8)
        assertFalse("Should return false for incorrect header", ArchiveUtils.isKeyedArchive(bytesWithoutHeader))
    }

    @Test
    fun `isKeyedArchive returns false for empty byte array`() {
        val emptyBytes = byteArrayOf()
        assertFalse("Should return false for empty byte array", ArchiveUtils.isKeyedArchive(emptyBytes))
    }

    @Test
    fun `isKeyedArchive returns false for byte array shorter than header`() {
        val shortBytes = "bpl".toByteArray(Charsets.UTF_8)
        assertFalse("Should return false for byte array shorter than header", ArchiveUtils.isKeyedArchive(shortBytes))
    }

    @Test
    fun `isKeyedArchive returns false for null-like bytes in header part`() {
        val bytesWithNull = byteArrayOf('b'.code.toByte(), 'p'.code.toByte(), 0.toByte(), 'i'.code.toByte(), 's'.code.toByte(), 't'.code.toByte())
        assertFalse("Should return false if header contains null-like bytes not matching 'l'", ArchiveUtils.isKeyedArchive(bytesWithNull))
    }
}
