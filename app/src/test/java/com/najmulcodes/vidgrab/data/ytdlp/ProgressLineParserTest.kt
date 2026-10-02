package com.najmulcodes.vidgrab.data.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressLineParserTest {

    @Test
    fun `parses speed from a download line`() {
        val line = "[download]  45.2% of   10.00MiB at    1.20MiB/s ETA 00:05"
        assertEquals("1.20MiB/s", ProgressLineParser.parseSpeed(line))
    }

    @Test
    fun `parses kilobyte speeds`() {
        assertEquals("512.00KiB/s", ProgressLineParser.parseSpeed("[download]   3.0% of 10MiB at 512.00KiB/s ETA 00:20"))
    }

    @Test
    fun `parses speed from the final line`() {
        assertEquals("2.5MiB/s", ProgressLineParser.parseSpeed("[download] 100% of 10.00MiB in 00:04 at 2.5MiB/s"))
    }

    @Test
    fun `returns null while the speed is unknown`() {
        assertNull(ProgressLineParser.parseSpeed("[download]   0.0% of 10.00MiB at Unknown B/s ETA Unknown"))
    }

    @Test
    fun `returns null for unrelated output`() {
        assertNull(ProgressLineParser.parseSpeed("[info] Downloading 1 format(s): 137+140"))
    }
}
