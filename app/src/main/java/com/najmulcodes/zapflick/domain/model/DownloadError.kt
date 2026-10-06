package com.najmulcodes.zapflick.domain.model

/** Typed failure reasons. The UI maps each one to a human-readable string. */
sealed interface DownloadError {
    /** Stable identifier used to store the error in the database. */
    val code: String

    data object Unavailable : DownloadError { override val code = "unavailable" }
    data object GeoBlocked : DownloadError { override val code = "geo_blocked" }
    data object NetworkError : DownloadError { override val code = "network" }
    data object FormatNotFound : DownloadError { override val code = "format_not_found" }
    data object Cancelled : DownloadError { override val code = "cancelled" }
    data object InvalidUrl : DownloadError { override val code = "invalid_url" }
    data object StorageError : DownloadError { override val code = "storage" }

    /** The site wants a login or an age confirmation. */
    data object LoginRequired : DownloadError { override val code = "login_required" }

    /** The site refused the request (HTTP 403 or 429). */
    data object SiteBlocked : DownloadError { override val code = "site_blocked" }

    /** The site changed and the bundled yt-dlp can no longer read it. Updating yt-dlp fixes this. */
    data object OutdatedExtractor : DownloadError { override val code = "outdated_extractor" }

    data class Unknown(val detail: String? = null) : DownloadError {
        override val code = "unknown"
    }

    companion object {
        fun fromCode(code: String?, detail: String? = null): DownloadError = when (code) {
            Unavailable.code -> Unavailable
            GeoBlocked.code -> GeoBlocked
            NetworkError.code -> NetworkError
            FormatNotFound.code -> FormatNotFound
            Cancelled.code -> Cancelled
            InvalidUrl.code -> InvalidUrl
            StorageError.code -> StorageError
            LoginRequired.code -> LoginRequired
            SiteBlocked.code -> SiteBlocked
            OutdatedExtractor.code -> OutdatedExtractor
            else -> Unknown(detail)
        }
    }
}

class DownloadException(
    val error: DownloadError,
    cause: Throwable? = null,
) : Exception(error.toString(), cause)

fun Throwable.toDownloadError(): DownloadError =
    (this as? DownloadException)?.error ?: DownloadError.Unknown(message)
