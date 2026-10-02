package com.najmulcodes.zapflick.domain.queue

import com.najmulcodes.zapflick.domain.model.DownloadItem
import java.io.File

/** Platform side effects of the queue (foreground service, notifications), kept out of its logic. */
interface QueueHost {
    /** Keeps the process alive while downloads run. Safe to call repeatedly. */
    fun ensureServiceRunning()

    fun onFinished(item: DownloadItem, success: Boolean)
}

/** Where each download keeps its partial files, so a paused download can resume. */
interface WorkDirProvider {
    /** Creates the directory if needed. */
    fun dirFor(id: Long): File

    fun delete(id: Long)
}
