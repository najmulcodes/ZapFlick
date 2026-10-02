package com.najmulcodes.vidgrab.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmulcodes.vidgrab.domain.model.DownloadRow
import com.najmulcodes.vidgrab.domain.queue.DownloadQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DownloadsUiState {
    data object Loading : DownloadsUiState
    data class Content(val rows: List<DownloadRow>) : DownloadsUiState
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val queue: DownloadQueue,
) : ViewModel() {

    val uiState: StateFlow<DownloadsUiState> = queue.downloads
        .map<List<DownloadRow>, DownloadsUiState> { DownloadsUiState.Content(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DownloadsUiState.Loading)

    fun pause(id: Long) = launchAction { queue.pause(id) }

    fun resume(id: Long) = launchAction { queue.resume(id) }

    fun cancel(id: Long) = launchAction { queue.cancel(id) }

    fun remove(id: Long) = launchAction { queue.remove(id) }

    fun pauseAll() = launchAction { queue.pauseAll() }

    fun clearFinished() = launchAction { queue.clearFinished() }

    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
