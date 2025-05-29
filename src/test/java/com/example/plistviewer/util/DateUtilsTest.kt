package com.example.plistviewer.util

import org.junit.Assert.* // For JUnit assertions like assertEquals, assertNotNull
import org.junit.Test // For @Test annotation
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class DateUtilsTest {

    @Test
    fun `APPLE_EPOCH constant has correct value`() {
        assertEquals("APPLE_EPOCH constant should be 978307200000L", 978307200000L, DateUtils.APPLE_EPOCH)
    }

    @Test
    fun `convertTimeIntervalSinceReferenceDateToZonedDateTime handles zero seconds`() {
        // 0 seconds since 2001-01-01 00:00:00 UTC
        val secondsSince2001 = 0.0
        val expectedDateTime = ZonedDateTime.of(2001, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"))
            .withZoneSameInstant(ZoneId.systemDefault()) // Adjust to system default for comparison

        val actualDateTime = DateUtils.convertTimeIntervalSinceReferenceDateToZonedDateTime(secondsSince2001)
        
        // Compare epoch seconds and zone to avoid issues with nanosecond precision differences on some systems
        assertEquals(expectedDateTime.toEpochSecond(), actualDateTime.toEpochSecond())
        assertEquals(expectedDateTime.zone, actualDateTime.zone)
    }

    @Test
    fun `convertTimeIntervalSinceReferenceDateToZonedDateTime handles positive seconds`() {
        // Example: 1 day (24*60*60 seconds) after 2001-01-01 00:00:00 UTC
        val secondsInADay = 24.0 * 60.0 * 60.0
        val expectedDateTime = ZonedDateTime.of(2001, 1, 2, 0, 0, 0, 0, ZoneId.of("UTC"))
            .withZoneSameInstant(ZoneId.systemDefault())

        val actualDateTime = DateUtils.convertTimeIntervalSinceReferenceDateToZonedDateTime(secondsInADay)
        
        assertEquals(expectedDateTime.toEpochSecond(), actualDateTime.toEpochSecond())
        assertEquals(expectedDateTime.zone, actualDateTime.zone)
    }
    
    @Test
    fun `formatZonedDateTimeForDisplay handles null input`() {
        val expectedOutput = "Invalid Date"
        val actualOutput = DateUtils.formatZonedDateTimeForDisplay(null)
        assertEquals(expectedOutput, actualOutput)
    }

    @Test
    fun `formatZonedDateTimeForDisplay formats ZonedDateTime correctly`() {
        // Use a fixed ZonedDateTime to ensure consistent output for the test
        // Note: The output of MEDIUM style can vary slightly by locale/system, but structure should hold.
        // For more robust tests, one might compare parts of the date or use a fixed locale if possible.
        val testDateTime = ZonedDateTime.of(2023, 10, 26, 14, 30, 15, 0, ZoneId.of("UTC"))
        
        // Expected format for MEDIUM style (can be locale-dependent)
        // Example: "Oct 26, 2023, 2:30:15 PM" (for en_US, UTC)
        // To make it less fragile, let's format it with the same formatter for expected value
        val expectedFormattedString = testDateTime.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))

        val actualFormattedString = DateUtils.formatZonedDateTimeForDisplay(testDateTime)
        
        assertNotNull("Formatted string should not be null", actualFormattedString)
        assertFalse("Formatted string should not be 'Invalid Date'", actualFormattedString == "Invalid Date")
        assertEquals("Formatted date-time string does not match expected.", expectedFormattedString, actualFormattedString)
    }
}
