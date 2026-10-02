package com.najmulcodes.zapflick.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatTest {
    @Test
    fun `formats minutes and seconds`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("1:05", formatDuration(65))
        assertEquals("59:59", formatDuration(3599))
    }

    @Test
    fun `formats hours`() {
        assertEquals("1:02:05", formatDuration(3725))
    }

    @Test
    fun `negative values clamp to zero`() {
        assertEquals("0:00", formatDuration(-5))
    }
}
