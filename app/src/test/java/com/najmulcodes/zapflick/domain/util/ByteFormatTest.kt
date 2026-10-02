package com.najmulcodes.zapflick.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormatTest {
    @Test
    fun `small values stay in bytes`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
    }

    @Test
    fun `scales through the units`() {
        assertEquals("1 KB", formatBytes(1024))
        assertEquals("5.0 MB", formatBytes(5L * 1024 * 1024))
        assertEquals("1.5 GB", formatBytes(1536L * 1024 * 1024))
    }

    @Test
    fun `large numbers in a unit drop the decimal`() {
        assertEquals("250 MB", formatBytes(250L * 1024 * 1024))
    }
}
