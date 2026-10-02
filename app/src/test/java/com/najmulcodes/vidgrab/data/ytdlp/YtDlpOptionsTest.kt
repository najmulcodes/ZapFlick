package com.najmulcodes.vidgrab.data.ytdlp

import com.najmulcodes.vidgrab.domain.model.AudioFormat
import com.najmulcodes.vidgrab.domain.model.FormatSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpOptionsTest {

    private fun List<YtDlpOption>.valueOf(name: String): String? = firstOrNull { it.name == name }?.value
    private fun List<YtDlpOption>.has(name: String): Boolean = any { it.name == name }

    @Test
    fun `best selects best video plus audio and merges to mp4`() {
        val options = YtDlpOptions.formatOptions(FormatSelection.Best)
        assertEquals("bv*+ba/b", options.valueOf("-f"))
        assertEquals("mp4", options.valueOf("--merge-output-format"))
        assertNotNull(options.valueOf("-S"))
    }

    @Test
    fun `resolution caps the height on both the merged and the single-file branch`() {
        val options = YtDlpOptions.formatOptions(FormatSelection.Resolution(1080))
        assertEquals("bv*[height<=1080]+ba/b[height<=1080]", options.valueOf("-f"))
    }

    @Test
    fun `every supported resolution produces a height filter`() {
        FormatSelection.Resolution.SUPPORTED_HEIGHTS.forEach { height ->
            val format = YtDlpOptions.formatOptions(FormatSelection.Resolution(height)).valueOf("-f")
            assertTrue(format.orEmpty().contains("height<=$height"))
        }
    }

    @Test
    fun `mp3 extracts audio and converts to mp3`() {
        val options = YtDlpOptions.formatOptions(FormatSelection.Audio(AudioFormat.MP3))
        assertTrue(options.has("-x"))
        assertEquals("mp3", options.valueOf("--audio-format"))
        assertNull(options.valueOf("--merge-output-format"))
    }

    @Test
    fun `m4a prefers a native m4a stream`() {
        val options = YtDlpOptions.formatOptions(FormatSelection.Audio(AudioFormat.M4A))
        assertEquals("m4a", options.valueOf("--audio-format"))
        assertTrue(options.valueOf("-f").orEmpty().startsWith("bestaudio[ext=m4a]"))
    }

    @Test
    fun `download options always stay single-video and write into the work dir`() {
        val options = YtDlpOptions.forDownload(FormatSelection.Best, "/data/work")
        assertTrue(options.has("--no-playlist"))
        assertEquals("/data/work/${YtDlpOptions.OUTPUT_TEMPLATE}", options.valueOf("-o"))
    }
}
