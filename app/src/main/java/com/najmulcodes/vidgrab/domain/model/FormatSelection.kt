package com.najmulcodes.vidgrab.domain.model

enum class AudioFormat(val extension: String) {
    MP3("mp3"),
    M4A("m4a"),
}

/** What the user asked for. Translated to yt-dlp options in the data layer. */
sealed interface FormatSelection {
    /** Compact, stable representation used to persist a selection. */
    val key: String

    data object Best : FormatSelection {
        override val key = "best"
    }

    data class Resolution(val height: Int) : FormatSelection {
        override val key = "res:$height"

        companion object {
            val SUPPORTED_HEIGHTS = listOf(2160, 1440, 1080, 720, 480)
        }
    }

    data class Audio(val format: AudioFormat) : FormatSelection {
        override val key = "audio:${format.extension}"
    }

    companion object {
        /** Unknown or malformed keys fall back to [Best]. */
        fun fromKey(key: String?): FormatSelection {
            if (key == null) return Best
            return when {
                key.startsWith("res:") ->
                    key.removePrefix("res:").toIntOrNull()?.let(::Resolution) ?: Best
                key.startsWith("audio:") -> {
                    val ext = key.removePrefix("audio:")
                    AudioFormat.entries.firstOrNull { it.extension == ext }?.let(::Audio) ?: Best
                }
                else -> Best
            }
        }
    }
}
