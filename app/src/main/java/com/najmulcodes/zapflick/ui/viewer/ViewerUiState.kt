package com.najmulcodes.zapflick.ui.viewer

import com.najmulcodes.zapflick.domain.viewer.DocumentInfo
import com.najmulcodes.zapflick.domain.viewer.DocumentType
import com.najmulcodes.zapflick.domain.viewer.PdfPageSize
import com.najmulcodes.zapflick.domain.viewer.ViewerError

sealed interface ViewerUiState {
    data object Loading : ViewerUiState

    data class Pdf(
        val info: DocumentInfo,
        val pages: List<PdfPageSize>,
    ) : ViewerUiState

    data class Error(
        val error: ViewerError,
        val info: DocumentInfo?,
        val documentType: DocumentType,
    ) : ViewerUiState
}

/** The type to tell another app about, or null while there is nothing to hand over. */
val ViewerUiState.handOffMimeType: String?
    get() = when (this) {
        ViewerUiState.Loading -> null
        is ViewerUiState.Pdf -> DocumentType.Pdf.mimeType
        is ViewerUiState.Error -> when {
            !error.canOpenElsewhere -> null
            documentType != DocumentType.Unsupported -> documentType.mimeType
            else -> info?.mimeType ?: DocumentType.Unsupported.mimeType
        }
    }
