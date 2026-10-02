package com.najmulcodes.zapflick.data.db

import com.najmulcodes.zapflick.domain.model.DownloadError
import com.najmulcodes.zapflick.domain.model.DownloadItem
import com.najmulcodes.zapflick.domain.model.DownloadStatus
import com.najmulcodes.zapflick.domain.model.FormatSelection
import com.najmulcodes.zapflick.domain.model.SavedMedia

internal fun DownloadEntity.toDomain(): DownloadItem = DownloadItem(
    id = id,
    url = url,
    title = title,
    thumbnailUrl = thumbnailUrl,
    uploader = uploader,
    durationSeconds = durationSeconds,
    selection = FormatSelection.fromKey(formatKey),
    status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.FAILED),
    error = errorCode?.let { DownloadError.fromCode(it, errorDetail) },
    saved = savedUri?.let { SavedMedia(uri = it, displayName = savedName.orEmpty(), location = savedLocation.orEmpty()) },
    createdAt = createdAt,
    updatedAt = updatedAt,
)
