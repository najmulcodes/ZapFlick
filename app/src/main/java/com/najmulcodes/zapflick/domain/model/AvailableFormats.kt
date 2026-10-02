package com.najmulcodes.zapflick.domain.model

/** One selectable resolution with the estimated download size (video plus audio), if known. */
data class VideoQuality(
    val height: Int,
    val approxBytes: Long?,
)

data class AvailableFormats(
    /** Highest resolution first. */
    val videoQualities: List<VideoQuality>,
    /** Estimated size of the best audio-only stream, if the site reports one. */
    val audioBytes: Long?,
    val hasAudio: Boolean,
) {
    val bestVideoBytes: Long? get() = videoQualities.firstOrNull()?.approxBytes
}
