package com.najmulcodes.zapflick.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.domain.model.AudioFormat
import com.najmulcodes.zapflick.domain.model.FormatSelection

@Composable
fun FormatSelection.label(): String = when (this) {
    FormatSelection.Best -> stringResource(R.string.format_best)
    is FormatSelection.Resolution -> stringResource(R.string.format_resolution, height)
    is FormatSelection.Audio -> when (format) {
        AudioFormat.MP3 -> stringResource(R.string.format_audio_mp3)
        AudioFormat.M4A -> stringResource(R.string.format_audio_m4a)
    }
}
