package com.najmulcodes.zapflick.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmulcodes.zapflick.data.browser.AdBlocker
import com.najmulcodes.zapflick.data.browser.FavoritesRepository
import com.najmulcodes.zapflick.data.browser.HistoryRepository
import com.najmulcodes.zapflick.domain.browser.BrowserInput
import com.najmulcodes.zapflick.domain.browser.DetectedMedia
import com.najmulcodes.zapflick.domain.browser.FavoriteSite
import com.najmulcodes.zapflick.domain.browser.HistoryEntry
import com.najmulcodes.zapflick.domain.browser.RecentSites
import com.najmulcodes.zapflick.domain.browser.TabList
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
import com.najmulcodes.zapflick.domain.settings.AppSettings
import com.najmulcodes.zapflick.domain.settings.SettingsRepository
import com.najmulcodes.zapflick.domain.usecase.ExtractUrlUseCase
import com.najmulcodes.zapflick.domain.usecase.FetchFormatsUseCase
import com.najmulcodes.zapflick.domain.usecase.FetchMetadataUseCase
import com.najmulcodes.zapflick.domain.usecase.StartDownloadUseCase
import com.najmulcodes.zapflick.ui.components.FormatsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    data object TabLimitReached : BrowserEvent
    data object NoLinkInShare : BrowserEvent
    data object FavoriteAdded : BrowserEvent
    data object FavoriteRejected : BrowserEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val fetchMetadata: FetchMetadataUseCase,
    private val fetchFormats: FetchFormatsUseCase,
    private val startDownload: StartDownloadUseCase,
    private val sessions: RequestSessions,
    private val tabManager: TabManager,
    private val settingsRepository: SettingsRepository,
    private val favoritesRepository: FavoritesRepository,
    private val historyRepository: HistoryRepository,
    private val extractUrl: ExtractUrlUseCase,
    val adBlocker: AdBlocker,
    queue: DownloadQueue,
) : ViewModel() {

    val tabs: StateFlow<TabList> = tabManager.state

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    val favorites: StateFlow<List<FavoriteSite>> = favoritesRepository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** The "Recently used websites" row: empty while the setting is off, and nothing is recorded then either. */
    val recentSites: StateFlow<List<HistoryEntry>> = settingsRepository.settings
        .map { it.recentSites }
        .distinctUntilChanged()
        .flatMapLatest { enabled ->
            if (enabled) historyRepository.recent(RECENT_FETCH).map { RecentSites.pick(it) } else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _blockedCount = MutableStateFlow(0)
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    private val _desktopSite = MutableStateFlow(false)
    val desktopSite: StateFlow<Boolean> = _desktopSite.asStateFlow()

    /** A shared link whose download sheet should open as soon as its page has started loading. */
    private val _autoPrepare = MutableStateFlow<String?>(null)
    val autoPrepare: StateFlow<String?> = _autoPrepare.asStateFlow()

    init {
        viewModelScope.launch { favoritesRepository.ensureSeeded() }
        viewModelScope.launch { adBlocker.preload() }
    }

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
        if (isInternalUrl(url)) return
        if (url != _pageUrl.value) {
            _detected.value = emptyList()
            _blockedCount.value = 0
        }
        _pageUrl.value = url
        tabManager.update(tabManager.state.value.activeId, url = url)
    }

    /** In-page navigation (single-page apps) keeps the spotted files. */
    fun onUrlChanged(url: String) {
        if (isInternalUrl(url)) return
        _pageUrl.value = url
        tabManager.update(tabManager.state.value.activeId, url = url)
    }

    fun onTitleChanged(title: String) {
        if (title.isNotBlank()) tabManager.update(tabManager.state.value.activeId, title = title)
    }

    /** A page finished loading: remember it in the history, unless that is switched off. */
    fun onPageFinished(url: String, title: String?) {
        if (!settingsRepository.settings.value.recentSites) return
        viewModelScope.launch { historyRepository.record(url, title) }
    }

    fun onAdBlocked() {
        _blockedCount.update { it + 1 }
    }

    fun setDesktopSite(enabled: Boolean) {
        _desktopSite.value = enabled
    }

    /** Where the address bar text should go, using the chosen search engine. */
    fun resolveAddress(text: String): String? =
        BrowserInput.resolve(text, settingsRepository.settings.value.searchEngine)

    /** Shows a page in the current tab right away (the WebView is told to load it by the screen). */
    fun navigatingTo(url: String) {
        tabManager.update(tabManager.state.value.activeId, url = url)
        _pageUrl.value = url
        _detected.value = emptyList()
        _blockedCount.value = 0
    }

    fun selectTab(id: Long) = tabManager.select(id)

    fun closeTab(id: Long) = tabManager.close(id)

    fun closeAllTabs() = tabManager.closeAll()

    fun newTab(url: String = "") {
        if (!tabManager.openNewTab(url)) _events.trySend(BrowserEvent.TabLimitReached)
    }

    /** Lets the screen keep a tab's page state (history, scroll) while another tab is on screen. */
    fun pageStateOf(id: Long) = tabManager.pageState(id)

    fun savePageState(id: Long, bundle: android.os.Bundle) = tabManager.putPageState(id, bundle)

    /** A link shared into ZapFlick: open it in a new tab and offer to download it. */
    fun onSharedText(text: String) {
        val url = extractUrl(text)
        if (url == null) {
            _events.trySend(BrowserEvent.NoLinkInShare)
            return
        }
        if (!tabManager.openNewTab(url)) {
            // All 20 tabs are open: reuse the current one rather than refuse the link.
            tabManager.update(tabManager.state.value.activeId, url = url)
        }
        _pageUrl.value = url
        _detected.value = emptyList()
        _autoPrepare.value = url
    }

    fun onAutoPrepareConsumed() {
        _autoPrepare.value = null
    }

    fun addFavorite(title: String, address: String) {
        viewModelScope.launch {
            val added = favoritesRepository.add(title, address)
            _events.send(if (added) BrowserEvent.FavoriteAdded else BrowserEvent.FavoriteRejected)
        }
    }

    fun removeFavorite(id: Long) {
        viewModelScope.launch { favoritesRepository.remove(id) }
    }

    fun moveFavorite(id: Long, delta: Int) {
        viewModelScope.launch { favoritesRepository.move(id, delta) }
    }

    private fun isInternalUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("about:") || lower.startsWith("data:")
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
                    val preset = settingsRepository.settings.value.defaultSelection()
                    if (preset != null) {
                        // A default quality is set in Settings: no sheet, straight to the queue.
                        _panel.value = DownloadPanel.Quality(metadata, FormatsState.Loading)
                        onQualitySelected(preset)
                    } else {
                        _panel.value = DownloadPanel.Quality(metadata, FormatsState.Loading)
                        loadFormats(metadata.sourceUrl)
                    }
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
        const val RECENT_FETCH = 60
        val BLOCKED_SESSION_SITES = listOf("youtube.com", "youtu.be", "google.com", "googlevideo.com")
    }
}
