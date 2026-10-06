package com.najmulcodes.zapflick.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmulcodes.zapflick.data.network.NetworkGate
import com.najmulcodes.zapflick.domain.library.DownloadLists
import com.najmulcodes.zapflick.domain.model.DownloadRow
import com.najmulcodes.zapflick.domain.model.DownloadStatus
import com.najmulcodes.zapflick.domain.queue.DownloadQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProgressUiState {
    data object Loading : ProgressUiState

    data class Content(val rows: List<DownloadRow>) : ProgressUiState {
        val hasActive: Boolean get() = rows.any { it.item.status.isActive }
        val hasStopped: Boolean get() = rows.any { it.item.status == DownloadStatus.FAILED || it.item.status == DownloadStatus.CANCELLED }
    }
}

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val queue: DownloadQueue,
    gate: NetworkGate,
) : ViewModel() {

    val uiState: StateFlow<ProgressUiState> = queue.downloads
        .map<List<DownloadRow>, ProgressUiState> { ProgressUiState.Content(DownloadLists.inProgress(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ProgressUiState.Loading)

    /** True while Wi-Fi only is holding downloads back and something is waiting. */
    val waitingForWifi: StateFlow<Boolean> = combine(gate.blocked, uiState) { blocked, state ->
        blocked && state is ProgressUiState.Content && state.rows.any { it.item.status != DownloadStatus.FAILED && it.item.status != DownloadStatus.CANCELLED }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    fun pause(id: Long) = launchAction { queue.pause(id) }
    fun resume(id: Long) = launchAction { queue.resume(id) }
    fun cancel(id: Long) = launchAction { queue.cancel(id) }
    fun remove(id: Long) = launchAction { queue.remove(id) }
    fun pauseAll() = launchAction { queue.pauseAll() }

    /** Removes failed and cancelled entries. Finished downloads and the private folder are not touched. */
    fun clearStopped() = launchAction {
        val stopped = (uiState.value as? ProgressUiState.Content)?.rows.orEmpty()
            .filter { it.item.status == DownloadStatus.FAILED || it.item.status == DownloadStatus.CANCELLED }
        stopped.forEach { queue.remove(it.item.id) }
    }

    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
