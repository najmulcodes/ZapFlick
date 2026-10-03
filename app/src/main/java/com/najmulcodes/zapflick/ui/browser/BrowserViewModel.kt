package com.najmulcodes.zapflick.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmulcodes.zapflick.domain.browser.DetectedMedia
import com.najmulcodes.zapflick.domain.browser.MediaKind
import com.najmulcodes.zapflick.domain.browser.MediaSniffer
import com.najmulcodes.zapflick.domain.browser.RequestSession
import com.najmulcodes.zapflick.domain.browser.RequestSessions
import com.najmulcodes.zapflick.domain.model.AvailableFormats
import com.najmulcodes.zapflick.domain.model.DownloadError
import com.najmulcodes.zapflick.domain.model.FormatSelection
import com.najmulcodes.zapflick.domain.model.VideoMetadata
import com.najmulcodes.zapflick.domain.model.toDownloadError
import com.najmulcodes.zapflick.domain.queue.DownloadQueue
import com.najmulcodes.zapflick.domain.usecase.FetchFormatsUseCase
import com.najmulcodes.zapflick.domain.usecase.FetchMetadataUseCase
import com.najmulcodes.zapflick.domain.usecase.StartDownloadUseCase
import com.najmulcodes.zapflick.ui.home.FormatsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the bottom sheet over the browser is currently showing. */
sealed interface DownloadPanel {
    data object Hidden : DownloadPanel

    /** Pick what to download: the page itself or a file the browser spotted. */
    data object Targets : DownloadPanel

    data object Preparing : DownloadPanel

    data class Quality(val metadata: VideoMetadata, val formats: FormatsState) : DownloadPanel

    data class Failed(val error: DownloadError) : DownloadPanel
}

sealed interface BrowserEvent {
    data class Queued(val title: String) : BrowserEvent
    data object QueueFailed : BrowserEvent
}

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val fetchMetadata: FetchMetadataUseCase,
    private val fetchFormats: FetchFormatsUseCase,
    private val startDownload: StartDownloadUseCase,
    private val sessions: RequestSessions,
    queue: DownloadQueue,
) : ViewModel() {

    /** The page being shown. Kept here so coming back from another screen reopens the same page. */
    private val _pageUrl = MutableStateFlow("")
    val pageUrl: StateFlow<String> = _pageUrl.asStateFlow()

    private val _detected = MutableStateFlow<List<DetectedMedia>>(emptyList())
    val detected: StateFlow<List<DetectedMedia>> = _detected.asStateFlow()

    private val _panel = MutableStateFlow<DownloadPanel>(DownloadPanel.Hidden)
    val panel: StateFlow<DownloadPanel> = _panel.asStateFlow()

    private val _events = Channel<BrowserEvent>(Channel.BUFFERED)
    val events: Flow<BrowserEvent> = _events.receiveAsFlow()

    val activeDownloads: StateFlow<Int> = queue.downloads
        .map { rows -> rows.count { it.item.status.isActive } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    private var prepareJob: Job? = null
    private var formatsJob: Job? = null
    private var lastTarget: Target? = null

    private data class Target(val url: String, val isPage: Boolean, val session: RequestSession?)

    /** A new top-level page started loading: whatever was spotted on the last page no longer applies. */
    fun onPageStarted(url: String) {
        if (url != _pageUrl.value) _detected.value = emptyList()
        _pageUrl.value = url
    }

    /** In-page navigation (single-page apps) keeps the spotted files. */
    fun onUrlChanged(url: String) {
        _pageUrl.value = url
    }

    /** Called from the WebView's network thread for every request the page makes. */
    fun onResourceRequested(url: String, referer: String?) {
        val kind = MediaSniffer.classify(url) ?: return
        addDetected(url, kind, referer)
    }

    /** The WebView decided a click is a file download (Content-Disposition and similar). */
    fun onDirectDownload(url: String) {
        if (!url.startsWith("http", ignoreCase = true)) return
        addDetected(url, MediaSniffer.classify(url) ?: MediaKind.VIDEO, referer = null)
        _panel.value = DownloadPanel.Targets
    }

    fun onDownloadButton() {
        _panel.value = DownloadPanel.Targets
    }

    fun onPanelDismiss() {
        prepareJob?.cancel()
        formatsJob?.cancel()
        _panel.value = DownloadPanel.Hidden
    }

    /**
     * Looks up [url] with yt-dlp and moves on to the quality sheet. [session] carries the browser's
     * cookies, user agent and referer so sites that need a login or a referer still work.
     */
    fun prepare(url: String, isPage: Boolean, session: RequestSession?) {
        lastTarget = Target(url, isPage, session)
        if (session != null) {
            if (isPage) {
                if (!isSessionBlocked(url)) sessions.rememberSite(url, session)
            } else {
                sessions.rememberUrl(url, session)
            }
        }
        prepareJob?.cancel()
        formatsJob?.cancel()
        _panel.value = DownloadPanel.Preparing
        prepareJob = viewModelScope.launch {
            fetchMetadata(url).fold(
                onSuccess = { metadata ->
                    _panel.value = DownloadPanel.Quality(metadata, FormatsState.Loading)
                    loadFormats(metadata.sourceUrl)
                },
                onFailure = { _panel.value = DownloadPanel.Failed(it.toDownloadError()) },
            )
        }
    }

    fun retry() {
        val target = lastTarget ?: return
        prepare(target.url, target.isPage, target.session)
    }

    fun onQualitySelected(selection: FormatSelection) {
        val quality = _panel.value as? DownloadPanel.Quality ?: return
        _panel.value = DownloadPanel.Hidden
        viewModelScope.launch {
            try {
                startDownload(quality.metadata, selection)
                _events.send(BrowserEvent.Queued(quality.metadata.title))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(BrowserEvent.QueueFailed)
            }
        }
    }

    private fun addDetected(url: String, kind: MediaKind, referer: String?) {
        val media = DetectedMedia(
            url = url,
            kind = kind,
            label = MediaSniffer.label(url),
            referer = referer ?: _pageUrl.value.ifBlank { null },
        )
        _detected.update { current ->
            if (current.any { it.url == url }) current else (current + media).takeLast(MAX_DETECTED)
        }
    }

    /** Starts early so sizes are usually ready by the time the person looks at the sheet. */
    private fun loadFormats(url: String) {
        formatsJob = viewModelScope.launch {
            val result: Result<AvailableFormats> = fetchFormats(url)
            val state: FormatsState = result.fold(
                onSuccess = { FormatsState.Loaded(it) },
                onFailure = { FormatsState.Unavailable },
            )
            _panel.update { current ->
                if (current is DownloadPanel.Quality && current.metadata.sourceUrl == url) {
                    current.copy(formats = state)
                } else {
                    current
                }
            }
        }
    }

    /** yt-dlp warns that sending YouTube login cookies can get an account flagged, so never do it silently. */
    private fun isSessionBlocked(url: String): Boolean {
        val host = url.substringAfter("://", "").substringBefore('/').substringBefore(':').lowercase()
        return BLOCKED_SESSION_SITES.any { host == it || host.endsWith(".$it") }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MAX_DETECTED = 30
        val BLOCKED_SESSION_SITES = listOf("youtube.com", "youtu.be", "google.com", "googlevideo.com")
    }
}
