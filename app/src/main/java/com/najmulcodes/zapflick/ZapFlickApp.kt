package com.najmulcodes.zapflick

import android.app.Application
import android.util.Log
import com.najmulcodes.zapflick.domain.engine.DownloadEngine
import com.najmulcodes.zapflick.domain.model.DownloadException
import com.najmulcodes.zapflick.domain.queue.DownloadQueue
import com.najmulcodes.zapflick.service.DownloadNotifier
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ZapFlickApp : Application() {

    @Inject lateinit var notifier: DownloadNotifier
    @Inject lateinit var engine: DownloadEngine
    @Inject lateinit var queue: DownloadQueue

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannel()
        // Unpacking python/yt-dlp takes a few seconds on first launch; do it off the main thread.
        appScope.launch {
            try {
                engine.initialize()
            } catch (e: DownloadException) {
                Log.e(TAG, "yt-dlp initialisation failed", e)
            }
        }
        // Downloads cut short by process death continue where they left off.
        appScope.launch {
            try {
                queue.recover()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not restore the download queue", e)
            }
        }
    }

    private companion object {
        const val TAG = "ZapFlickApp"
    }
}
