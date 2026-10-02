package com.najmulcodes.zapflick.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmulcodes.zapflick.domain.model.AvailableFormats
import com.najmulcodes.zapflick.domain.model.DownloadError
import com.najmulcodes.zapflick.domain.model.FormatSelection
import com.najmulcodes.zapflick.domain.model.toDownloadError
import com.najmulcodes.zapflick.domain.queue.DownloadQueue
import com.najmulcodes.zapflick.domain.usecase.ExtractUrlUseCase
import com.najmulcodes.zapflick.domain.usecase.FetchFormatsUseCase
import com.najmulcodes.zapflick.domain.usecase.FetchMetadataUseCase
import com.najmulcodes.zapflick.domain.usecase.StartDownloadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val extractUrl: ExtractUrlUseCase,
    private val fetchMetadata: FetchMetadataUseCase,
    private val fetchFormats: FetchFormatsUseCase,
    private val startDownload: StartDownloadUseCase,
    queue: DownloadQueue,
) : ViewModel() {

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _sheetVisible = MutableStateFlow(false)
    val sheetVisible: StateFlow<Boolean> = _sheetVisible.asStateFlow()

    /** Queued plus running downloads, shown on the "Downloads" button. */
    val activeDownloads: StateFlow<Int> = queue.downloads
        .map { rows -> rows.count { it.item.status.isActive } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    private var fetchJob: Job? = null
    private var formatsJob: Job? = null

    fun onUrlChange(value: String) {
        _urlInput.value = value
    }

    /** Called for share-intent text and for pasted text: pulls out the link and fetches it. */
    fun onSharedText(text: String) {
        val url = extractUrl(text)
        if (url == null) {
            _urlInput.value = text
            _uiState.value = HomeUiState.Error(DownloadError.InvalidUrl)
            return
        }
        _urlInput.value = url
        fetch(url)
    }

    fun onFetchClick() {
        fetch(_urlInput.value)
    }

    fun onDownloadClick() {
        if (_uiState.value is HomeUiState.Ready) _sheetVisible.value = true
    }

    fun onSheetDismiss() {
        _sheetVisible.value = false
    }

    fun onQualitySelected(selection: FormatSelection) {
        val ready = _uiState.value as? HomeUiState.Ready ?: return
        _sheetVisible.value = false
        _uiState.value = ready.copy(queuedSelection = selection)
        viewModelScope.launch {
            try {
                startDownload(ready.metadata, selection)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.toDownloadError())
            }
        }
    }

    fun onNewLink() {
        fetchJob?.cancel()
        formatsJob?.cancel()
        _sheetVisible.value = false
        _urlInput.value = ""
        _uiState.value = HomeUiState.Idle
    }

    private fun fetch(input: String) {
        fetchJob?.cancel()
        formatsJob?.cancel()
        _sheetVisible.value = false
        _uiState.value = HomeUiState.Fetching
        fetchJob = viewModelScope.launch {
            fetchMetadata(input).fold(
                onSuccess = { metadata ->
                    _uiState.value = HomeUiState.Ready(metadata)
                    loadFormats(metadata.sourceUrl)
                },
                onFailure = { _uiState.value = HomeUiState.Error(it.toDownloadError()) },
            )
        }
    }

    /** Starts early so the sizes are usually ready by the time the sheet opens. */
    private fun loadFormats(url: String) {
        formatsJob = viewModelScope.launch {
            val result: Result<AvailableFormats> = fetchFormats(url)
            val state: FormatsState = result.fold(
                onSuccess = { FormatsState.Loaded(it) },
                onFailure = { FormatsState.Unavailable },
            )
            _uiState.update { current ->
                if (current is HomeUiState.Ready && current.metadata.sourceUrl == url) {
                    current.copy(formats = state)
                } else {
                    current
                }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
