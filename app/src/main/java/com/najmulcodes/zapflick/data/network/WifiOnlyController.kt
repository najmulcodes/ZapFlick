package com.najmulcodes.zapflick.data.network

import com.najmulcodes.zapflick.di.ApplicationScope
import com.najmulcodes.zapflick.domain.model.DownloadStatus
import com.najmulcodes.zapflick.domain.network.GateAction
import com.najmulcodes.zapflick.domain.network.WifiOnlyPolicy
import com.najmulcodes.zapflick.domain.queue.DownloadQueue
import com.najmulcodes.zapflick.domain.queue.DownloadQueueManager
import com.najmulcodes.zapflick.domain.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pauses running downloads when Wi-Fi only is on and the phone moves to mobile data, and resumes
 * exactly those downloads when Wi-Fi is back. Downloads the person paused themselves stay paused.
 * Waiting downloads are not started while blocked (the queue checks the gate) and start by
 * themselves when it opens.
 */
@Singleton
class WifiOnlyController @Inject constructor(
    private val gate: NetworkGate,
    private val settings: SettingsRepository,
    private val queue: DownloadQueue,
    private val manager: DownloadQueueManager,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val pausedByGate = LinkedHashSet<Long>()
    private var started = false

    /** Call once from Application.onCreate. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            var wasBlocked = false
            gate.blocked.collect { nowBlocked ->
                try {
                    when (WifiOnlyPolicy.actionFor(wasBlocked, nowBlocked)) {
                        GateAction.PAUSE_RUNNING -> pauseRunning()
                        GateAction.RESUME_PAUSED -> resumePaused()
                        GateAction.NONE -> Unit
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Wi-Fi gate change failed", e)
                }
                wasBlocked = nowBlocked
            }
        }
        // A higher limit in Settings should start waiting downloads at once.
        scope.launch {
            settings.settings.map { it.maxConcurrent }.distinctUntilChanged().collect { manager.refill() }
        }
    }

    private suspend fun pauseRunning() {
        val running = queue.downloads.first()
            .filter { it.item.status == DownloadStatus.RUNNING }
            .map { it.item.id }
        pausedByGate.addAll(running)
        running.forEach { queue.pause(it) }
    }

    private suspend fun resumePaused() {
        val ids = pausedByGate.toList()
        pausedByGate.clear()
        ids.forEach { queue.resume(it) }
        manager.refill()
    }

    private companion object {
        const val TAG = "WifiOnlyController"
    }
}
