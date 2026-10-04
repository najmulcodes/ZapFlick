package com.najmulcodes.zapflick.ui.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.domain.viewer.ViewerError

@Composable
fun ViewerError.displayMessage(): String = stringResource(
    when (this) {
        ViewerError.AccessLost -> R.string.viewer_error_access_lost
        ViewerError.NotReadable -> R.string.viewer_error_not_readable
        ViewerError.PasswordProtected -> R.string.viewer_error_password
        ViewerError.Corrupt -> R.string.viewer_error_corrupt
        ViewerError.Unsupported -> R.string.viewer_error_unsupported
        ViewerError.TooLarge -> R.string.viewer_error_too_large
        ViewerError.NoDocument -> R.string.viewer_error_no_document
    },
)
