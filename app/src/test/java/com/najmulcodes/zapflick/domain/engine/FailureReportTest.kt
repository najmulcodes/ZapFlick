package com.najmulcodes.zapflick.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureReportTest {

    private fun entry(raw: String, time: Long = 0L) = FailureEntry(
        timeMillis = time,
        operation = "lookup",
        url = "https://example.com/v/1",
        errorCode = "site_blocked",
        raw = raw,
    )

    @Test
    fun `an empty log says so and still names the versions`() {
        val text = FailureReport.build(emptyList(), "2026.09.01", "0.3.0")
        assertTrue(text.contains("ZapFlick 0.3.0, yt-dlp 2026.09.01"))
        assertTrue(text.contains("No failures recorded."))
    }

    @Test
    fun `a failure shows what was tried and what yt-dlp said`() {
        val text = FailureReport.build(listOf(entry("HTTP Error 403: Blocked")), null, null)
        assertTrue(text.contains("lookup -> site_blocked"))
        assertTrue(text.contains("https://example.com/v/1"))
        assertTrue(text.contains("HTTP Error 403: Blocked"))
        assertTrue(text.startsWith("ZapFlick ?, yt-dlp ?"))
    }

    @Test
    fun `very long output is cut`() {
        val text = FailureReport.build(listOf(entry("x".repeat(5000))), "v", "a")
        assertFalse(text.contains("x".repeat(1600)))
        assertTrue(text.contains("x".repeat(1500)))
    }

    @Test
    fun `the log keeps the newest twenty entries first`() {
        val log = FailureLog()
        repeat(25) { log.record(entry("n$it", time = it.toLong())) }
        val kept = log.entries.value
        assertEquals(20, kept.size)
        assertEquals("n24", kept.first().raw)
        log.clear()
        assertTrue(log.entries.value.isEmpty())
    }
}
