package com.najmulcodes.vidgrab.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class QualityOptionsTest {

    @Test
    fun `without format info only the always-available options are offered`() {
        val selections = QualityOptions.from(null).map { it.selection }

        assertEquals(
            listOf(
                FormatSelection.Best,
                FormatSelection.Audio(AudioFormat.MP3),
                FormatSelection.Audio(AudioFormat.M4A),
            ),
            selections,
        )
    }

    @Test
    fun `lists every resolution the site offers`() {
        val formats = AvailableFormats(
            videoQualities = listOf(VideoQuality(1080, 100), VideoQuality(480, 40)),
            audioBytes = 10,
            hasAudio = true,
        )

        val selections = QualityOptions.from(formats).map { it.selection }

        assertEquals(
            listOf(
                FormatSelection.Best,
                FormatSelection.Resolution(1080),
                FormatSelection.Resolution(480),
                FormatSelection.Audio(AudioFormat.MP3),
                FormatSelection.Audio(AudioFormat.M4A),
            ),
            selections,
        )
        assertEquals(100L, QualityOptions.from(formats).first().approxBytes)
    }

    @Test
    fun `hides audio options when the video has no audio track`() {
        val formats = AvailableFormats(
            videoQualities = listOf(VideoQuality(720, 70)),
            audioBytes = null,
            hasAudio = false,
        )

        val selections = QualityOptions.from(formats).map { it.selection }

        assertEquals(listOf(FormatSelection.Best, FormatSelection.Resolution(720)), selections)
    }
}
