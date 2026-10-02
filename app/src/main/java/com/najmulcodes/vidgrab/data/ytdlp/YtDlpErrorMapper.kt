package com.najmulcodes.vidgrab.data.ytdlp

import com.najmulcodes.vidgrab.domain.model.DownloadError

/** Maps yt-dlp stderr text to typed errors. Order matters: more specific checks come first. */
object YtDlpErrorMapper {
    private val GEO = listOf("in your country", "geo restrict", "geo-restrict", "geo bypass")
    private val INVALID_URL = listOf("unsupported url", "is not a valid url")
    private val UNAVAILABLE = listOf(
        "video unavailable",
        "private video",
        "has been removed",
        "this video is not available",
        "no longer available",
        "has been terminated",
    )
    private val FORMAT = listOf(
        "requested format is not available",
        "requested format not available",
        "no video formats found",
    )
    private val NETWORK = listOf(
        "unable to download webpage",
        "timed out",
        "temporary failure in name resolution",
        "network is unreachable",
        "connection reset",
        "connection refused",
        "http error 5",
        "urlopen error",
        "failed to resolve",
        "unable to resolve host",
    )

    fun map(message: String?): DownloadError {
        val text = message.orEmpty().lowercase()
        return when {
            GEO.any { it in text } -> DownloadError.GeoBlocked
            INVALID_URL.any { it in text } -> DownloadError.InvalidUrl
            UNAVAILABLE.any { it in text } -> DownloadError.Unavailable
            FORMAT.any { it in text } -> DownloadError.FormatNotFound
            NETWORK.any { it in text } -> DownloadError.NetworkError
            else -> DownloadError.Unknown(
                message?.lines()?.lastOrNull { it.isNotBlank() }?.trim()?.take(200),
            )
        }
    }
}
