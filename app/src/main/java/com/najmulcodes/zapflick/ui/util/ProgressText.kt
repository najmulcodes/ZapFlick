package com.najmulcodes.zapflick.ui.util

import android.content.Context
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.domain.model.DownloadProgress
import com.najmulcodes.zapflick.domain.util.formatDuration

/** "45% • 1.20MiB/s • ETA 0:05" — shared by the screen and the notification. */
fun DownloadProgress.summary(context: Context): String {
    val parts = buildList {
        add(context.getString(R.string.progress_percent, percent.toInt()))
        speed?.let { add(it) }
        etaSeconds?.let { add(context.getString(R.string.progress_eta, formatDuration(it))) }
    }
    return parts.joinToString(" • ")
}
