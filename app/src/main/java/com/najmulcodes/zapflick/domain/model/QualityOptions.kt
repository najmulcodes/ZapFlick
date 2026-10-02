package com.najmulcodes.zapflick.domain.model

/** One row of the quality sheet. [approxBytes] is an estimate, or null when the site reports no size. */
data class QualityOption(
    val selection: FormatSelection,
    val approxBytes: Long?,
)

object QualityOptions {

    /**
     * The choices to offer. With [formats] unknown (still loading, or the lookup failed) only the
     * options that always work are returned, so the user is never stuck waiting.
     */
    fun from(formats: AvailableFormats?): List<QualityOption> = buildList {
        add(QualityOption(FormatSelection.Best, formats?.bestVideoBytes))
        formats?.videoQualities?.forEach { quality ->
            add(QualityOption(FormatSelection.Resolution(quality.height), quality.approxBytes))
        }
        if (formats == null || formats.hasAudio) {
            add(QualityOption(FormatSelection.Audio(AudioFormat.MP3), formats?.audioBytes))
            add(QualityOption(FormatSelection.Audio(AudioFormat.M4A), formats?.audioBytes))
        }
    }
}
