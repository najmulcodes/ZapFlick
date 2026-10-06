package com.najmulcodes.zapflick.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.najmulcodes.zapflick.data.ui.UiPrefs
import com.najmulcodes.zapflick.domain.library.DownloadLists
import com.najmulcodes.zapflick.domain.model.DownloadItem
import com.najmulcodes.zapflick.domain.queue.DownloadQueue
import com.najmulcodes.zapflick.domain.security.PinEntry
import com.najmulcodes.zapflick.domain.security.PinManager
import com.najmulcodes.zapflick.domain.security.PinPolicy
import com.najmulcodes.zapflick.domain.security.PinResult
import com.najmulcodes.zapflick.domain.security.SetPinFlow
import com.najmulcodes.zapflick.domain.security.SetPinOutcome
import com.najmulcodes.zapflick.domain.security.SetPinStep
import com.najmulcodes.zapflick.domain.settings.SettingsRepository
import com.najmulcodes.zapflick.domain.usecase.DeleteFinishedUseCase
import com.najmulcodes.zapflick.domain.usecase.RestoreFromPrivateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the PIN keypad is showing. */
data class PinUiState(
    val mode: Mode = Mode.Loading,
    val entry: PinEntry = PinEntry(),
    val message: Message? = null,
    val lockedSeconds: Int = 0,
    val biometricEnabled: Boolean = false,
) {
    enum class Mode { Loading, SetEnter, SetConfirm, Unlock, Unlocked }

    sealed interface Message {
        data object Mismatch : Message
        data class Wrong(val attemptsLeft: Int) : Message
        data object SaveFailed : Message
    }
}

sealed interface PrivateEvent {
    data class Restored(val count: Int) : PrivateEvent
    data class RestoreFailed(val count: Int) : PrivateEvent
    data class Deleted(val count: Int) : PrivateEvent
    data class DeleteFailed(val count: Int) : PrivateEvent
}

/**
 * The gate in front of the private folder: set a PIN the first time, enter it afterwards, and
 * list what is inside once it is unlocked. The PIN is a privacy gate, not encryption.
 */
@HiltViewModel
class PrivateViewModel @Inject constructor(
    private val pinManager: PinManager,
    private val session: PrivateSession,
    settings: SettingsRepository,
    queue: DownloadQueue,
    private val restoreFromPrivate: RestoreFromPrivateUseCase,
    private val deleteFinished: DeleteFinishedUseCase,
    private val prefs: UiPrefs,
) : ViewModel() {

    private val gate = MutableStateFlow(PinUiState())
    private var setStep: SetPinStep = SetPinStep.Enter
    private var countdown: Job? = null

    val pinState: StateFlow<PinUiState> = combine(gate, session.unlocked, settings.settings) { g, unlocked, s ->
        val mode = when {
            g.mode == PinUiState.Mode.Loading -> g.mode
            g.mode == PinUiState.Mode.Unlock && unlocked -> PinUiState.Mode.Unlocked
            g.mode == PinUiState.Mode.Unlocked && !unlocked -> PinUiState.Mode.Unlock
            else -> g.mode
        }
        g.copy(mode = mode, biometricEnabled = s.biometricUnlock)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PinUiState())

    val items: StateFlow<List<DownloadItem>> = queue.downloads
        .let { flow ->
            combine(flow, session.unlocked) { rows, unlocked ->
                if (unlocked) DownloadLists.privateItems(rows).map { it.item } else emptyList()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _events = Channel<PrivateEvent>(Channel.BUFFERED)
    val events: Flow<PrivateEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch { refreshMode() }
    }

    private suspend fun refreshMode() {
        val hasPin = pinManager.hasPin()
        gate.update {
            it.copy(
                mode = when {
                    !hasPin -> PinUiState.Mode.SetEnter
                    session.unlocked.value -> PinUiState.Mode.Unlocked
                    else -> PinUiState.Mode.Unlock
                },
                entry = PinEntry(),
                message = null,
            )
        }
        if (hasPin) startLockCountdown()
    }

    fun press(key: Char) {
        val current = gate.value
        if (current.lockedSeconds > 0) return
        if (current.mode != PinUiState.Mode.SetEnter && current.mode != PinUiState.Mode.SetConfirm && current.mode != PinUiState.Mode.Unlock) return
        val entry = current.entry.press(key)
        gate.update { it.copy(entry = entry, message = null) }
        if (entry.isComplete) viewModelScope.launch { onComplete(entry.digits) }
    }

    fun backspace() {
        gate.update { it.copy(entry = it.entry.backspace(), message = null) }
    }

    private suspend fun onComplete(pin: String) {
        when (gate.value.mode) {
            PinUiState.Mode.SetEnter, PinUiState.Mode.SetConfirm -> onSetEntry(pin)
            PinUiState.Mode.Unlock -> onUnlockEntry(pin)
            else -> Unit
        }
    }

    private suspend fun onSetEntry(pin: String) {
        when (val outcome = SetPinFlow.onComplete(setStep, pin)) {
            is SetPinOutcome.AskAgain -> {
                setStep = outcome.step
                gate.update { it.copy(mode = PinUiState.Mode.SetConfirm, entry = PinEntry(), message = null) }
            }
            SetPinOutcome.Mismatch -> {
                setStep = SetPinStep.Enter
                gate.update { it.copy(mode = PinUiState.Mode.SetEnter, entry = PinEntry(), message = PinUiState.Message.Mismatch) }
            }
            is SetPinOutcome.Save -> {
                if (pinManager.setPin(outcome.pin)) {
                    setStep = SetPinStep.Enter
                    session.unlock()
                    gate.update { it.copy(mode = PinUiState.Mode.Unlocked, entry = PinEntry(), message = null) }
                } else {
                    gate.update { it.copy(mode = PinUiState.Mode.SetEnter, entry = PinEntry(), message = PinUiState.Message.SaveFailed) }
                }
            }
        }
    }

    private suspend fun onUnlockEntry(pin: String) {
        when (val result = pinManager.verify(pin)) {
            PinResult.Correct -> {
                session.unlock()
                gate.update { it.copy(mode = PinUiState.Mode.Unlocked, entry = PinEntry(), message = null, lockedSeconds = 0) }
            }
            PinResult.NotSet -> refreshMode()
            is PinResult.Wrong -> {
                gate.update { it.copy(entry = PinEntry(), message = PinUiState.Message.Wrong(result.attemptsBeforeLockout)) }
                if (result.lockedForMillis > 0L) startLockCountdown()
            }
            is PinResult.LockedOut -> {
                gate.update { it.copy(entry = PinEntry()) }
                startLockCountdown()
            }
        }
    }

    /** Unlocks after the system fingerprint or face prompt succeeded. */
    fun unlockWithBiometric() {
        session.unlock()
        gate.update { it.copy(mode = PinUiState.Mode.Unlocked, entry = PinEntry(), message = null) }
    }

    /** Ticks the "try again in N seconds" text down while a lockout is running. */
    private fun startLockCountdown() {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            while (true) {
                val remaining = pinManager.remainingLockMillis()
                val seconds = ((remaining + 999L) / 1000L).toInt()
                gate.update { it.copy(lockedSeconds = seconds) }
                if (remaining <= 0L) break
                delay(500L)
            }
        }
    }

    fun lockNow() {
        session.lock()
    }

    /** Opening the folder counts as having seen what is in it, so the "new" dot goes away. */
    fun markSeen(count: Int) {
        prefs.privateSeenCount = count
    }

    fun restore(items: List<DownloadItem>) {
        viewModelScope.launch {
            val result = restoreFromPrivate(items)
            if (result.succeeded > 0) _events.send(PrivateEvent.Restored(result.succeeded))
            if (result.failed > 0) _events.send(PrivateEvent.RestoreFailed(result.failed))
        }
    }

    fun delete(items: List<DownloadItem>) {
        viewModelScope.launch {
            val result = deleteFinished(items)
            if (result.succeeded > 0) _events.send(PrivateEvent.Deleted(result.succeeded))
            if (result.failed > 0) _events.send(PrivateEvent.DeleteFailed(result.failed))
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
