package com.najmulcodes.zapflick.domain.viewer

/** What the provider tells us about a document; every field can be missing. */
data class DocumentInfo(
    val displayName: String?,
    val sizeBytes: Long?,
    val mimeType: String?,
)
