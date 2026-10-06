package com.najmulcodes.zapflick.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmulcodes.zapflick.domain.model.DownloadRow
import com.najmulcodes.zapflick.domain.queue.DownloadQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The number on the Progress tab: downloads running or waiting. */
@HiltViewModel
class NavBarViewModel @Inject constructor(
    queue: DownloadQueue,
) : ViewModel() {
    val activeCount: StateFlow<Int> = queue.downloads
        .map { rows: List<DownloadRow> -> rows.count { it.item.status.isActive } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
