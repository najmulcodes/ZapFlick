package com.najmulcodes.vidgrab.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.najmulcodes.vidgrab.domain.model.DownloadItem
import com.najmulcodes.vidgrab.domain.queue.QueueHost
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidQueueHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifier: DownloadNotifier,
) : QueueHost {

    override fun ensureServiceRunning() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        } catch (e: IllegalStateException) {
            // Starting a foreground service from the background can be refused; the queue still runs.
            Log.w(TAG, "Could not start the download service", e)
        }
    }

    override fun onFinished(item: DownloadItem, success: Boolean) {
        notifier.notifyFinished(item.id, item.title, success)
    }

    private companion object {
        const val TAG = "AndroidQueueHost"
    }
}
