package com.najmulcodes.zapflick.domain.model

/** One row of the download queue / history. */
data class DownloadItem(
    val id: Long,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val uploader: String?,
    val durationSeconds: Long?,
    val selection: FormatSelection,
    val status: DownloadStatus,
    val error: DownloadError?,
    val saved: SavedMedia?,
    val createdAt: Long,
    val updatedAt: Long,
    /** Moved into the private folder: hidden from the gallery and from the Finished list. */
    val isPrivate: Boolean = false,
) {
    fun toRequest(): DownloadRequest = DownloadRequest(url = url, title = title, selection = selection)
}

/** A [DownloadItem] plus its live progress; progress only exists while the item is running. */
data class DownloadRow(
    val item: DownloadItem,
    val progress: DownloadProgress?,
)
