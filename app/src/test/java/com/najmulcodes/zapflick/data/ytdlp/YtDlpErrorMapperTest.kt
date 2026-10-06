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
        val error = YtDlpErrorMapper.map("WARNING: something\nERROR: The thing did something odd\n\n")
        assertTrue(error is DownloadError.Unknown)
        assertEquals("ERROR: The thing did something odd", (error as DownloadError.Unknown).detail)
    }

    @Test
    fun `null message is unknown without detail`() {
        assertEquals(DownloadError.Unknown(null), YtDlpErrorMapper.map(null))
    }

    @Test
    fun `a refused request is reported as blocked, not as a network problem`() {
        assertEquals(
            DownloadError.SiteBlocked,
            YtDlpErrorMapper.map("ERROR: [Reddit] abc: Unable to download JSON metadata: HTTP Error 403: Blocked"),
        )
        assertEquals(
            DownloadError.SiteBlocked,
            YtDlpErrorMapper.map("ERROR: Unable to download webpage: HTTP Error 429: Too Many Requests"),
        )
    }

    @Test
    fun `sites that want a login or an age check`() {
        assertEquals(
            DownloadError.LoginRequired,
            YtDlpErrorMapper.map("ERROR: This content is only available for registered users. Use --cookies"),
        )
        assertEquals(DownloadError.LoginRequired, YtDlpErrorMapper.map("ERROR: Please confirm your age to continue"))
        assertEquals(
            DownloadError.LoginRequired,
            YtDlpErrorMapper.map("ERROR: Sign in to confirm you are not a bot. Use --cookies-from-browser"),
        )
    }

    @Test
    fun `a broken extractor asks for a yt-dlp update`() {
        val message = "ERROR: [PornHub] xyz: Unable to extract video url; please report this issue on " +
            "https://github.com/yt-dlp/yt-dlp/issues. Confirm you are on the latest version using yt-dlp -U"
        assertEquals(DownloadError.OutdatedExtractor, YtDlpErrorMapper.map(message))
    }

    @Test
    fun `digits inside a video id never look like an http status`() {
        assertEquals(
            DownloadError.Unavailable,
            YtDlpErrorMapper.map("ERROR: [PornHub] 6429403abc: Video unavailable"),
        )
    }

    @Test
    fun `a missing page is unavailable`() {
        assertEquals(DownloadError.Unavailable, YtDlpErrorMapper.map("ERROR: Unable to download webpage: HTTP Error 404: Not Found"))
    }

    @Test
    fun `dns and tls failures are network problems`() {
        assertEquals(
            DownloadError.NetworkError,
            YtDlpErrorMapper.map("ERROR: Unable to download webpage: <urlopen error [Errno -2] Name or service not known>"),
        )
        assertEquals(
            DownloadError.NetworkError,
            YtDlpErrorMapper.map("<urlopen error [SSL: CERTIFICATE_VERIFY_FAILED] certificate verify failed>"),
        )
    }

    @Test
    fun `new error codes survive a round trip through the database`() {
        listOf(DownloadError.LoginRequired, DownloadError.SiteBlocked, DownloadError.OutdatedExtractor).forEach {
            assertEquals(it, DownloadError.fromCode(it.code))
        }
    }
}
