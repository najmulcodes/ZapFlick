package com.najmulcodes.zapflick.data.ytdlp

import com.najmulcodes.zapflick.domain.model.DownloadError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpErrorMapperTest {

    @Test
    fun `geo restrictions win over generic unavailable text`() {
        val message = "ERROR: [youtube] abc: This video is not available in your country"
        assertEquals(DownloadError.GeoBlocked, YtDlpErrorMapper.map(message))
    }

    @Test
    fun `unavailable and private videos`() {
        assertEquals(DownloadError.Unavailable, YtDlpErrorMapper.map("ERROR: [youtube] abc: Video unavailable"))
        assertEquals(DownloadError.Unavailable, YtDlpErrorMapper.map("ERROR: Private video. Sign in if you have access"))
    }

    @Test
    fun `format not found`() {
        assertEquals(
            DownloadError.FormatNotFound,
            YtDlpErrorMapper.map("ERROR: Requested format is not available. Use --list-formats"),
        )
    }

    @Test
    fun `network problems`() {
        assertEquals(
            DownloadError.NetworkError,
            YtDlpErrorMapper.map("ERROR: Unable to download webpage: <urlopen error timed out>"),
        )
        assertEquals(DownloadError.NetworkError, YtDlpErrorMapper.map("Temporary failure in name resolution"))
    }

    @Test
    fun `unsupported urls`() {
        assertEquals(DownloadError.InvalidUrl, YtDlpErrorMapper.map("ERROR: Unsupported URL: https://example.com"))
    }

    @Test
    fun `anything else keeps the last non-blank line as detail`() {
        val error = YtDlpErrorMapper.map("WARNING: something\nERROR: Sign in to confirm you are not a bot\n\n")
        assertTrue(error is DownloadError.Unknown)
        assertEquals("ERROR: Sign in to confirm you are not a bot", (error as DownloadError.Unknown).detail)
    }

    @Test
    fun `null message is unknown without detail`() {
        assertEquals(DownloadError.Unknown(null), YtDlpErrorMapper.map(null))
    }
}
