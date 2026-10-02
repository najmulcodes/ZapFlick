package com.najmulcodes.zapflick.ui.home

import com.najmulcodes.zapflick.domain.model.AvailableFormats
import com.najmulcodes.zapflick.domain.model.DownloadError
import com.najmulcodes.zapflick.domain.model.FormatSelection
import com.najmulcodes.zapflick.domain.model.VideoMetadata

/** Progress of the background lookup that fills the quality sheet. */
sealed interface FormatsState {
    data object Loading : FormatsState
    data class Loaded(val formats: AvailableFormats) : FormatsState
    data object Unavailable : FormatsState
}

sealed interface HomeUiState {
    data object Idle : HomeUiState
    data object Fetching : HomeUiState
    data class Ready(
        val metadata: VideoMetadata,
        val formats: FormatsState = FormatsState.Loading,
        /** Set once the video has been added to the queue. */
        val queuedSelection: FormatSelection? = null,
    ) : HomeUiState
    data class Error(val error: DownloadError) : HomeUiState
}
