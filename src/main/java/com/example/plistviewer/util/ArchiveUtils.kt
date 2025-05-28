package com.example.plistviewer.util

/**
 * Utilities for handling archives.
 */
object ArchiveUtils {

    private val KEYED_ARCHIVE_MAGIC_HEADER = "bplist".toByteArray(Charsets.UTF_8)

    /**
     * Checks if the given byte array represents a keyed archive by looking for the "bplist" magic header.
     * NSKeyedArchiver files start with "bplist00" typically. This checks for the "bplist" part.
     *
     * @param bytes The byte array to check.
     * @return True if it appears to be a keyed archive, false otherwise.
     */
    fun isKeyedArchive(bytes: ByteArray): Boolean {
        if (bytes.size < KEYED_ARCHIVE_MAGIC_HEADER.size) {
            return false
        }
        // Check if the initial bytes match "bplist"
        for (i in KEYED_ARCHIVE_MAGIC_HEADER.indices) {
            if (bytes[i] != KEYED_ARCHIVE_MAGIC_HEADER[i]) {
                return false
            }
        }
        return true
    }
}
