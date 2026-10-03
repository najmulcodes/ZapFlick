package com.najmulcodes.zapflick.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import com.najmulcodes.zapflick.domain.model.DownloadRow
import com.najmulcodes.zapflick.domain.queue.DownloadQueue
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the process alive and shows the progress notification while anything is queued or running.
 * It holds no download logic: the queue does the work, this only mirrors it and stops itself when
 * the queue is idle.
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var queue: DownloadQueue
    @Inject lateinit var notifier: DownloadNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var latest: List<DownloadRow> = emptyList()
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Required within seconds of startForegroundService(), so do it before anything else.
        render()
        when (intent?.action) {
            ACTION_PAUSE_ALL -> scope.launch { queue.pauseAll() }
            ACTION_CANCEL_ALL -> scope.launch { queue.cancelAll() }
        }
        if (observeJob == null) {
            observeJob = scope.launch {
                queue.downloads.collect { rows ->
                    latest = rows
                    if (rows.any { it.item.status.isActive }) {
                        acquireWakeLock()
                        render()
                    } else {
                        stopWhenIdle()
                    }
                }
            }
        }
        // Not sticky: if the system kills the process, the queue resumes the next time the app opens.
        return START_NOT_STICKY
    }

    /**
     * Android 15 caps dataSync services at six hours a day and calls this when the budget is spent.
     * The service must stop promptly or the system crashes the app; the queue resumes on the next open.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopWhenIdle()
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun render() {
        try {
            ServiceCompat.startForeground(
                this,
                DownloadNotifier.ACTIVE_ID,
                notifier.buildActive(latest),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } catch (e: IllegalStateException) {
            // Android 12+ can refuse a foreground start from the background; downloads still run.
            Log.w(TAG, "Could not enter the foreground", e)
        }
    }

    private fun stopWhenIdle() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** A foreground service keeps the process alive but not the CPU; without this a long download can stall when the screen turns off. */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        const val ACTION_PAUSE_ALL = "com.najmulcodes.zapflick.action.PAUSE_ALL"
        const val ACTION_CANCEL_ALL = "com.najmulcodes.zapflick.action.CANCEL_ALL"
        private const val TAG = "DownloadService"
        private const val WAKE_LOCK_TAG = "ZapFlick:downloads"
        private const val WAKE_LOCK_TIMEOUT_MS = 6L * 60 * 60 * 1000
    }
}
