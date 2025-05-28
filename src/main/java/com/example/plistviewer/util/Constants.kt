package com.example.plistviewer.util

object Constants {
    // PNG_SIG = byteArrayOf(0x89, 'P','N','G'.toByte(), 0x0D,0x0A,0x1A,0x0A)
    val PNG_SIG = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte())

    // JPEG_SIG = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    val JPEG_SIG = byteArrayOf(0xFF.toByte(), 0xD8.toByte())

    // APPLE_EPOCH = milliseconds from 1970-01-01 to 2001-01-01.
    // Value: 978307200000L
    const val APPLE_EPOCH = 978307200000L
}
