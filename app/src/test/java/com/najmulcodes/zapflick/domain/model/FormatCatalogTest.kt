package com.najmulcodes.zapflick.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatCatalogTest {

    private fun video(height: Int, bytes: Long?, acodec: String = "none") =
        RawFormat(height = height, vcodec = "avc1", acodec = acodec, bytes = bytes)

    private fun audio(bytes: Long?) = RawFormat(height = null, vcodec = "none", acodec = "mp4a", bytes = bytes)

    @Test
    fun `lists resolutions highest first and adds the audio size to video-only streams`() {
        val result = FormatCatalog.build(
            listOf(video(720, 70), video(1080, 100), video(360, 30), audio(10)),
        )

        assertEquals(listOf(1080, 720, 360), result.videoQualities.map { it.height })
        assertEquals(listOf<Long?>(110, 80, 40), result.videoQualities.map { it.approxBytes })
        assertEquals(10L, result.audioBytes)
        assertEquals(110L, result.bestVideoBytes)
        assertTrue(result.hasAudio)
    }

    @Test
    fun `a stream that already has audio is not charged for it twice`() {
        val result = FormatCatalog.build(listOf(video(720, 70, acodec = "mp4a"), audio(10)))

        assertEquals(70L, result.videoQualities.single().approxBytes)
    }

    @Test
    fun `picks the largest stream at a resolution`() {
        val result = FormatCatalog.build(listOf(video(1080, 60), video(1080, 90), audio(10)))

        assertEquals(1, result.videoQualities.size)
        assertEquals(100L, result.videoQualities.single().approxBytes)
    }

    @Test
    fun `unknown sizes stay unknown`() {
        val result = FormatCatalog.build(listOf(video(720, null), audio(10)))

        assertNull(result.videoQualities.single().approxBytes)
    }

    @Test
    fun `ignores storyboards and entries without a height`() {
        val storyboard = RawFormat(height = 90, vcodec = "none", acodec = "none", bytes = 5)
        val noHeight = RawFormat(height = null, vcodec = "avc1", acodec = "none", bytes = 5)

        val result = FormatCatalog.build(listOf(storyboard, noHeight))

        assertTrue(result.videoQualities.isEmpty())
        assertFalse(result.hasAudio)
    }
}
