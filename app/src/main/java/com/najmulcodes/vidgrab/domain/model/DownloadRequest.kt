package com.najmulcodes.vidgrab.domain.model

data class DownloadRequest(
    val url: String,
    val title: String,
    val selection: FormatSelection = FormatSelection.Best,
)

data class DownloadProgress(
    val percent: Float,
    val etaSeconds: Long?,
    val speed: String?,
)

/** A finished file as it ended up in public storage. */
data class SavedMedia(
    val uri: String,
    val displayName: String,
    /** Human-readable folder, e.g. "Movies/VidGrab". */
    val location: String,
)
