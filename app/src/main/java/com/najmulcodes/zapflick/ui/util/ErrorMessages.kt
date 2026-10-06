package com.najmulcodes.zapflick.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.domain.model.DownloadError

@Composable
fun DownloadError.displayMessage(): String = when (this) {
    DownloadError.Unavailable -> stringResource(R.string.error_unavailable)
    DownloadError.GeoBlocked -> stringResource(R.string.error_geo_blocked)
    DownloadError.NetworkError -> stringResource(R.string.error_network)
    DownloadError.FormatNotFound -> stringResource(R.string.error_format_not_found)
    DownloadError.Cancelled -> stringResource(R.string.error_cancelled)
    DownloadError.InvalidUrl -> stringResource(R.string.error_invalid_url)
    DownloadError.StorageError -> stringResource(R.string.error_storage)
    DownloadError.LoginRequired -> stringResource(R.string.error_login_required)
    DownloadError.SiteBlocked -> stringResource(R.string.error_site_blocked)
    DownloadError.OutdatedExtractor -> stringResource(R.string.error_outdated_extractor)
    is DownloadError.Unknown -> detail
        ?.let { stringResource(R.string.error_unknown_detail, it) }
        ?: stringResource(R.string.error_unknown)
}
