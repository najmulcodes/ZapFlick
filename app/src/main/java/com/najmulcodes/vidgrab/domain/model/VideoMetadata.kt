package com.najmulcodes.vidgrab.domain.model

data class VideoMetadata(
    val sourceUrl: String,
    val title: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val uploader: String?,
)
