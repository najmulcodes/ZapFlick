package com.najmulcodes.zapflick.ui.finished

import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmulcodes.zapflick.data.ui.UiPrefs
import com.najmulcodes.zapflick.domain.library.DownloadLists
import com.najmulcodes.zapflick.domain.library.SelectionState
import com.najmulcodes.zapflick.domain.library.StorageSummary
import com.najmulcodes.zapflick.domain.model.DownloadItem
import com.najmulcodes.zapflick.domain.model.DownloadRow
import com.najmulcodes.zapflick.domain.queue.DownloadQueue
import com.najmulcodes.zapflick.domain.security.PinManager
import com.najmulcodes.zapflick.domain.usecase.DeleteFinishedUseCase
import com.najmulcodes.zapflick.domain.usecase.MoveToPrivateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface FinishedUiState {
    data object Loading : FinishedUiState

    data class Content(
        val items: List<DownloadItem>,
        val selection: SelectionState,
        val asGrid: Boolean,
        val storage: StorageSummary?,
        val hasNewPrivate: Boolean,
    ) : FinishedUiState
}

sealed interface FinishedEvent {
    data class Deleted(val count: Int) : FinishedEvent
    data class DeleteFailed(val count: Int) : FinishedEvent
    data class MovedToPrivate(val count: Int) : FinishedEvent
    data class MoveFailed(val count: Int) : FinishedEvent
    data object NeedsPin : FinishedEvent
}

@HiltViewModel
class FinishedViewModel @Inject constructor(
    queue: DownloadQueue,
    private val deleteFinished: DeleteFinishedUseCase,
    private val moveToPrivateUseCase: MoveToPrivateUseCase,
    private val pinManager: PinManager,
    private val prefs: UiPrefs,
) : ViewModel() {

    private val selection = MutableStateFlow(SelectionState())
    private val asGrid = MutableStateFlow(prefs.finishedAsGrid)
    private val storage = MutableStateFlow<StorageSummary?>(null)

    private val rows: Flow<List<DownloadRow>> = queue.downloads

    val uiState: StateFlow<FinishedUiState> = combine(rows, selection, asGrid, storage) { all, sel, grid, space ->
        val finished = DownloadLists.finished(all).map { it.item }
        val privateCount = DownloadLists.privateItems(all).size
        FinishedUiState.Content(
            items = finished,
            selection = sel.retainOnly(finished.map { it.id }),
            asGrid = grid,
            storage = space,
            hasNewPrivate = privateCount > prefs.privateSeenCount,
        ) as FinishedUiState
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FinishedUiState.Loading)

    private val _events = Channel<FinishedEvent>(Channel.BUFFERED)
    val events: Flow<FinishedEvent> = _events.receiveAsFlow()

    init {
        refreshStorage()
    }

    fun toggleSelected(id: Long) = selection.update { it.toggle(id) }

    fun startSelecting(id: Long) = selection.update { if (it.isSelecting) it else it.toggle(id) }

    fun selectAll() {
        val ids = (uiState.value as? FinishedUiState.Content)?.items.orEmpty().map { it.id }
        selection.update { if (it.isAllSelected(ids)) it.clear() else it.selectAll(ids) }
    }

    fun clearSelection() = selection.update { it.clear() }

    fun setGrid(grid: Boolean) {
        prefs.finishedAsGrid = grid
        asGrid.value = grid
    }

    fun deleteItems(items: List<DownloadItem>) {
        viewModelScope.launch {
            val result = deleteFinished(items)
            selection.update { it.clear() }
            if (result.succeeded > 0) _events.send(FinishedEvent.Deleted(result.succeeded))
            if (result.failed > 0) _events.send(FinishedEvent.DeleteFailed(result.failed))
            refreshStorage()
        }
    }

    /** The private folder needs a PIN first; with none set the screen is sent to set one. */
    fun moveToPrivate(items: List<DownloadItem>) {
        viewModelScope.launch {
            if (!pinManager.hasPin()) {
                _events.send(FinishedEvent.NeedsPin)
                return@launch
            }
            val result = moveToPrivateUseCase(items)
            selection.update { it.clear() }
            if (result.succeeded > 0) _events.send(FinishedEvent.MovedToPrivate(result.succeeded))
            if (result.failed > 0) _events.send(FinishedEvent.MoveFailed(result.failed))
            refreshStorage()
        }
    }

    fun refreshStorage() {
        viewModelScope.launch {
            storage.value = try {
                withContext(Dispatchers.IO) {
                    val stat = StatFs(Environment.getExternalStorageDirectory().path)
                    StorageSummary.of(stat.totalBytes, stat.availableBytes)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
