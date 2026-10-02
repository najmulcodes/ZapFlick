package com.najmulcodes.vidgrab.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.najmulcodes.vidgrab.domain.model.DownloadRow
import com.najmulcodes.vidgrab.domain.queue.DownloadQueue
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
                    if (rows.any { it.item.status.isActive }) render() else stopWhenIdle()
                }
            }
        }
        // Not sticky: if the system kills the process, the queue resumes the next time the app opens.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
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
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_PAUSE_ALL = "com.najmulcodes.vidgrab.action.PAUSE_ALL"
        const val ACTION_CANCEL_ALL = "com.najmulcodes.vidgrab.action.CANCEL_ALL"
        private const val TAG = "DownloadService"
    }
}
