package com.najmulcodes.zapflick.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmulcodes.zapflick.domain.model.DownloadRow
import com.najmulcodes.zapflick.domain.queue.DownloadQueue
import com.najmulcodes.zapflick.ui.security.PrivateSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data object Missing : PlayerUiState

    /** A private file is open but the folder has been locked again (the app was in the background too long). */
    data object Locked : PlayerUiState
    data class Ready(val uri: String, val title: String, val isPrivate: Boolean) : PlayerUiState
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    queue: DownloadQueue,
    session: PrivateSession,
) : ViewModel() {

    private val id: Long = savedState.get<Long>("id") ?: -1L

    val uiState: StateFlow<PlayerUiState> = combine(queue.downloads, session.unlocked) { rows: List<DownloadRow>, unlocked ->
        val item = rows.firstOrNull { it.item.id == id }?.item
        val saved = item?.saved
        when {
            item == null || saved == null -> PlayerUiState.Missing
            item.isPrivate && !unlocked -> PlayerUiState.Locked
            else -> PlayerUiState.Ready(saved.uri, item.title, item.isPrivate)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlayerUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
