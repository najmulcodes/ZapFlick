package com.najmulcodes.zapflick.domain.model

/** The few fields of a yt-dlp format entry that the quality picker needs. */
data class RawFormat(
    val height: Int?,
    val vcodec: String?,
    val acodec: String?,
    val bytes: Long?,
) {
    // yt-dlp writes "none" for a missing stream. Storyboards have neither, so they match nothing.
    val hasVideo: Boolean get() = vcodec != NONE
    val hasAudio: Boolean get() = acodec != NONE

    private companion object {
        const val NONE = "none"
    }
}

/** Turns the raw format list into the options shown in the quality sheet. */
object FormatCatalog {

    fun build(formats: List<RawFormat>): AvailableFormats {
        val audioBytes = formats
            .filter { !it.hasVideo && it.hasAudio }
            .mapNotNull { it.bytes }
            .maxOrNull()

        val byHeight = formats
            .filter { it.hasVideo }
            .mapNotNull { format -> format.height?.takeIf { it > 0 }?.let { height -> height to format } }
            .groupBy({ it.first }, { it.second })

        val qualities = byHeight
            .map { (height, group) ->
                // The largest stream at a height is the best-quality one yt-dlp can pick there.
                val pick = group.maxByOrNull { it.bytes ?: -1L }
                val total = pick?.let { format ->
                    format.bytes?.let { bytes ->
                        if (format.hasAudio) bytes else bytes + (audioBytes ?: 0L)
                    }
                }
                VideoQuality(height = height, approxBytes = total)
            }
            .sortedByDescending { it.height }

        return AvailableFormats(
            videoQualities = qualities,
            audioBytes = audioBytes,
            hasAudio = formats.any { it.hasAudio },
        )
    }
}
