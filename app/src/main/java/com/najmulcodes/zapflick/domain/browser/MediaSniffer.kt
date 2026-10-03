package com.najmulcodes.zapflick.domain.browser

import java.net.URLDecoder

enum class MediaKind { VIDEO, STREAM, AUDIO }

/** A media file the browser saw a page request. [referer] is replayed when downloading it. */
data class DetectedMedia(
    val url: String,
    val kind: MediaKind,
    val label: String,
    val referer: String?,
)

/** Decides whether a request made by a web page looks like a downloadable media file. */
object MediaSniffer {
    private const val MAX_LABEL_LENGTH = 70

    private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv", "mov", "m4v", "flv", "avi", "3gp")
    private val STREAM_EXTENSIONS = setOf("m3u8", "mpd")
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "ogg", "opus", "wav", "flac")

    fun classify(url: String): MediaKind? {
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
        // YouTube serves short, expiring chunks from here. The page URL is the right thing to download.
        if ("googlevideo.com" in lower && "videoplayback" in lower) return null

        val path = lower.substringBefore('#').substringBefore('?')
        val extension = path.substringAfterLast('/').substringAfterLast('.', missingDelimiterValue = "")
        return when (extension) {
            in VIDEO_EXTENSIONS -> MediaKind.VIDEO
            in STREAM_EXTENSIONS -> MediaKind.STREAM
            in AUDIO_EXTENSIONS -> MediaKind.AUDIO
            else -> null
        }
    }

    /** Short human-readable name: the file name, or the host when the path has none. */
    fun label(url: String): String {
        val rawName = url.substringBefore('#').substringBefore('?').substringAfterLast('/')
        val name = try {
            URLDecoder.decode(rawName, "UTF-8")
        } catch (e: IllegalArgumentException) {
            rawName
        }
        return name.ifBlank { hostOf(url) ?: url }.take(MAX_LABEL_LENGTH)
    }
}
