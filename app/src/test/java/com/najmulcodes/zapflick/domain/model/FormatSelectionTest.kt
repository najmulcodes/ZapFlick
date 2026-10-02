package com.najmulcodes.zapflick.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatSelectionTest {

    @Test
    fun `keys round-trip`() {
        listOf(
            FormatSelection.Best,
            FormatSelection.Resolution(720),
            FormatSelection.Audio(AudioFormat.MP3),
            FormatSelection.Audio(AudioFormat.M4A),
        ).forEach { selection ->
            assertEquals(selection, FormatSelection.fromKey(selection.key))
        }
    }

    @Test
    fun `unknown or malformed keys fall back to best`() {
        assertEquals(FormatSelection.Best, FormatSelection.fromKey(null))
        assertEquals(FormatSelection.Best, FormatSelection.fromKey("nonsense"))
        assertEquals(FormatSelection.Best, FormatSelection.fromKey("res:abc"))
        assertEquals(FormatSelection.Best, FormatSelection.fromKey("audio:wav"))
    }

    @Test
    fun `error codes round-trip`() {
        listOf(
            DownloadError.Unavailable,
            DownloadError.GeoBlocked,
            DownloadError.NetworkError,
            DownloadError.FormatNotFound,
            DownloadError.Cancelled,
            DownloadError.InvalidUrl,
            DownloadError.StorageError,
        ).forEach { error ->
            assertEquals(error, DownloadError.fromCode(error.code))
        }
        assertEquals(DownloadError.Unknown("x"), DownloadError.fromCode("anything", "x"))
    }
}
