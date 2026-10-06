package com.najmulcodes.zapflick.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmulcodes.zapflick.data.browser.BrowserData
import com.najmulcodes.zapflick.domain.engine.FailureEntry
import com.najmulcodes.zapflick.domain.engine.FailureLog
import com.najmulcodes.zapflick.domain.engine.YtDlpUpdate
import com.najmulcodes.zapflick.domain.engine.YtDlpUpdater
import com.najmulcodes.zapflick.domain.security.PinManager
import com.najmulcodes.zapflick.domain.settings.AppSettings
import com.najmulcodes.zapflick.domain.settings.FilenameStyle
import com.najmulcodes.zapflick.domain.settings.SearchEngine
import com.najmulcodes.zapflick.domain.settings.SettingsRepository
import com.najmulcodes.zapflick.domain.settings.ThemeMode
import com.najmulcodes.zapflick.domain.settings.YtDlpChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface YtDlpUpdateState {
    data object Idle : YtDlpUpdateState
    data object Running : YtDlpUpdateState
    data class Updated(val version: String?) : YtDlpUpdateState
    data class AlreadyLatest(val version: String?) : YtDlpUpdateState
    data object Failed : YtDlpUpdateState
}

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Content(
        val settings: AppSettings,
        val ytDlpVersion: String?,
        val update: YtDlpUpdateState,
        val hasPin: Boolean,
    ) : SettingsUiState
}

/** One-off messages shown as a snackbar. */
enum class SettingsEvent { CacheCleared, CookiesCleared, HistoryCleared, ClearFailed }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val browserData: BrowserData,
    private val updater: YtDlpUpdater,
    private val pinManager: PinManager,
    private val failureLog: FailureLog,
) : ViewModel() {

    /** The last failures with yt-dlp's own words, so a broken site can be diagnosed instead of guessed at. */
    val failures: StateFlow<List<FailureEntry>> = failureLog.entries

    fun clearFailures() = failureLog.clear()

    private val version = MutableStateFlow<String?>(null)
    private val update = MutableStateFlow<YtDlpUpdateState>(YtDlpUpdateState.Idle)
    private val hasPin = MutableStateFlow(false)

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(repository.settings, version, update, hasPin) { s, v, u, p ->
        SettingsUiState.Content(s, v, u, p) as SettingsUiState
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState.Loading)

    init {
        viewModelScope.launch {
            version.value = updater.currentVersion()
            hasPin.value = pinManager.hasPin()
        }
    }

    /** The PIN may have been set or removed on another screen since this one was created. */
    fun refreshPinState() {
        viewModelScope.launch { hasPin.value = pinManager.hasPin() }
    }

    private fun change(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    fun setWifiOnly(value: Boolean) = change { it.copy(wifiOnly = value) }
    fun setMaxConcurrent(value: Int) = change { it.copy(maxConcurrent = value) }
    fun setDefaultQuality(key: String) = change { it.copy(defaultQuality = key) }
    fun setFilenameStyle(style: FilenameStyle) = change { it.copy(filenameStyle = style) }
    fun setCustomFolder(uri: String?) = change { it.copy(customFolderUri = uri) }
    fun setBlockAds(value: Boolean) = change { it.copy(blockAds = value) }
    fun setRecentSites(value: Boolean) = change { it.copy(recentSites = value) }
    fun setSearchEngine(engine: SearchEngine) = change { it.copy(searchEngine = engine) }
    fun setSyncToGallery(value: Boolean) = change { it.copy(syncToGallery = value) }
    fun setDynamicColor(value: Boolean) = change { it.copy(dynamicColor = value) }
    fun setTheme(mode: ThemeMode) = change { it.copy(theme = mode) }
    fun setChannel(channel: YtDlpChannel) = change { it.copy(ytDlpChannel = channel) }
    fun setBiometric(value: Boolean) = change { it.copy(biometricUnlock = value) }
    fun setSecureScreens(value: Boolean) = change { it.copy(secureScreens = value) }

    fun clearCache() = clear(SettingsEvent.CacheCleared) { browserData.clearCache() }
    fun clearCookies() = clear(SettingsEvent.CookiesCleared) { browserData.clearCookies() }
    fun clearHistory() = clear(SettingsEvent.HistoryCleared) { browserData.clearHistory() }

    private fun clear(done: SettingsEvent, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                _events.send(done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(SettingsEvent.ClearFailed)
            }
        }
    }

    fun updateYtDlp() {
        if (update.value == YtDlpUpdateState.Running) return
        update.value = YtDlpUpdateState.Running
        viewModelScope.launch {
            val channel = repository.settings.value.ytDlpChannel
            updater.update(channel).fold(
                onSuccess = { result ->
                    version.value = result.version ?: updater.currentVersion()
                    update.value = when (result) {
                        is YtDlpUpdate.Updated -> YtDlpUpdateState.Updated(result.version)
                        is YtDlpUpdate.AlreadyLatest -> YtDlpUpdateState.AlreadyLatest(result.version)
                    }
                },
                onFailure = { update.value = YtDlpUpdateState.Failed },
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
