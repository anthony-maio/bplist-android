package com.example.plistviewer.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.DateTimeParseException // For handling potential errors if a pre-formatted string was passed by mistake

object DateUtils {

    const val APPLE_EPOCH = 978307200000L

    fun convertTimeIntervalSinceReferenceDateToZonedDateTime(secondsSince2001: Double): ZonedDateTime {
        val millisSince1970 = (secondsSince2001 * 1000.0).toLong() + APPLE_EPOCH
        val instant = Instant.ofEpochMilli(millisSince1970)
        return instant.atZone(ZoneId.systemDefault())
    }

    /**
     * Formats a ZonedDateTime object for display.
     * Uses DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).
     *
     * @param dateTime The ZonedDateTime to format. Can be null.
     * @return A string representation of the formatted date/time,
     *         or "Invalid Date" if the input is null or formatting fails.
     */
    fun formatZonedDateTimeForDisplay(dateTime: ZonedDateTime?): String {
        return try {
            dateTime?.format(
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            ) ?: "Invalid Date" // Handles null dateTime
        } catch (e: DateTimeParseException) {
            // This catch block might be relevant if we were parsing, less so for formatting.
            // However, if dateTime.toString() was somehow passed and it's unformattable by this formatter.
            "Invalid Date Format"
        } catch (e: Exception) {
            // Catch any other unexpected exception during formatting.
            // Log.e("DateUtils", "Error formatting date: $dateTime", e) // Consider logging
            "Error Formatting Date"
        }
    }
}
