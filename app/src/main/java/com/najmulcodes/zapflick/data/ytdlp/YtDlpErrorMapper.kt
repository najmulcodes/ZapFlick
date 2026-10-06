package com.najmulcodes.zapflick.data.ytdlp

import com.najmulcodes.zapflick.domain.model.DownloadError

/** Maps yt-dlp stderr text to typed errors. Order matters: more specific checks come first. */
object YtDlpErrorMapper {
    private val GEO = listOf("in your country", "geo restrict", "geo-restrict", "geo bypass")
    private val INVALID_URL = listOf("unsupported url", "is not a valid url")
    private val SITE_BLOCKED = listOf(
        "http error 403",
        "http error 429",
        "403: forbidden",
        "forbidden",
        "too many requests",
    )
    private val LOGIN = listOf(
        "login required",
        "log in to",
        "sign in to",
        "registered users",
        "confirm your age",
        "age-restricted",
        "age restricted",
        "age verification",
        "use --cookies",
    )
    private val OUTDATED = listOf(
        "unable to extract",
        "please report this issue",
        "confirm you are on the latest version",
        "yt-dlp -u",
        "nsig extraction failed",
    )
    private val UNAVAILABLE = listOf(
        "video unavailable",
        "private video",
        "has been removed",
        "this video is not available",
        "no longer available",
        "has been terminated",
        "http error 404",
        "404: not found",
    )
    private val FORMAT = listOf(
        "requested format is not available",
        "requested format not available",
        "no video formats found",
    )
    private val NETWORK = listOf(
        "unable to download webpage",
        "unable to download json",
        "timed out",
        "temporary failure in name resolution",
        "name or service not known",
        "network is unreachable",
        "connection reset",
        "connection refused",
        "connection aborted",
        "http error 5",
        "urlopen error",
        "failed to resolve",
        "unable to resolve host",
        "ssl:",
        "certificate_verify_failed",
        "eof occurred",
    )

    fun map(message: String?): DownloadError {
        val text = message.orEmpty().lowercase()
        return when {
            GEO.any { it in text } -> DownloadError.GeoBlocked
            INVALID_URL.any { it in text } -> DownloadError.InvalidUrl
            LOGIN.any { it in text } -> DownloadError.LoginRequired
            SITE_BLOCKED.any { it in text } -> DownloadError.SiteBlocked
            OUTDATED.any { it in text } -> DownloadError.OutdatedExtractor
            UNAVAILABLE.any { it in text } -> DownloadError.Unavailable
            FORMAT.any { it in text } -> DownloadError.FormatNotFound
            NETWORK.any { it in text } -> DownloadError.NetworkError
            else -> DownloadError.Unknown(
                message?.lines()?.lastOrNull { it.isNotBlank() }?.trim()?.take(200),
            )
        }
    }
}
