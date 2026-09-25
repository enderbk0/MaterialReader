package com.enderbk.materialreader.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun formatBytesHandlesUnits() {
        assertEquals("—", formatBytes(null))
        assertEquals("—", formatBytes(-5))
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("2.4 MB", formatBytes(2_516_582))
        assertEquals("3.0 GB", formatBytes(3L * 1024 * 1024 * 1024))
    }

    @Test
    fun formatLastOpenedHandlesNever() {
        assertEquals("Never opened", formatLastOpened(0))
        assertEquals("Never opened", formatLastOpened(-1))
    }

    @Test
    fun formatPageIndicatorIsOneBased() {
        assertEquals("1 / 10", formatPageIndicator(1, 10))
        assertEquals("10 / 10", formatPageIndicator(10, 10))
    }
}
