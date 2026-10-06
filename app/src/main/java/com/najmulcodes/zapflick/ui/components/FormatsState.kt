package com.najmulcodes.zapflick.ui.components

import com.najmulcodes.zapflick.domain.model.AvailableFormats

/** Progress of the background lookup that fills the quality sheet. */
sealed interface FormatsState {
    data object Loading : FormatsState
    data class Loaded(val formats: AvailableFormats) : FormatsState
    data object Unavailable : FormatsState
}
