package com.najmulcodes.vidgrab.data.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatListParserTest {

    private val json = """
        {
          "duration": 100.0,
          "formats": [
            {"format_id": "sb0", "vcodec": "none", "acodec": "none", "height": 90, "filesize": null},
            {"format_id": "140", "vcodec": "none", "acodec": "mp4a.40.2", "height": null, "filesize": 1000},
            {"format_id": "137", "vcodec": "avc1.640028", "acodec": "none", "height": 1080, "filesize": 9000},
            {"format_id": "136", "vcodec": "avc1.4d401f", "acodec": "none", "height": 720, "filesize": null, "filesize_approx": 4000},
            {"format_id": "135", "vcodec": "avc1.4d401e", "acodec": "none", "height": 480, "filesize": null, "tbr": 80.0},
            {"format_id": "134", "vcodec": "avc1.4d401e", "acodec": "none", "height": 360}
          ]
        }
    """.trimIndent()

    @Test
    fun `reads sizes from filesize, then filesize_approx, then bitrate`() {
        val result = FormatListParser.parse(json)

        assertEquals(listOf(1080, 720, 480, 360), result.videoQualities.map { it.height })
        // Video-only streams include the 1000-byte audio stream.
        assertEquals(10_000L, result.videoQualities[0].approxBytes)
        assertEquals(5_000L, result.videoQualities[1].approxBytes)
        // 80 kbit/s over 100 s = 1,000,000 bytes, plus audio.
        assertEquals(1_001_000L, result.videoQualities[2].approxBytes)
        assertNull(result.videoQualities[3].approxBytes)
        assertEquals(1000L, result.audioBytes)
    }

    @Test
    fun `a response without formats gives an empty result`() {
        val result = FormatListParser.parse("""{"title": "x"}""")

        assertEquals(emptyList<Int>(), result.videoQualities.map { it.height })
    }
}
