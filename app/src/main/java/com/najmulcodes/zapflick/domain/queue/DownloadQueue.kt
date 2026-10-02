package com.najmulcodes.zapflick.domain.queue

import com.najmulcodes.zapflick.domain.model.DownloadRow
import com.najmulcodes.zapflick.domain.model.FormatSelection
import com.najmulcodes.zapflick.domain.model.VideoMetadata
import kotlinx.coroutines.flow.Flow

/** What the UI and the notification need from the download queue. */
interface DownloadQueue {
    /** Every item, newest first, with live progress for the running ones. */
    val downloads: Flow<List<DownloadRow>>

    suspend fun enqueue(metadata: VideoMetadata, selection: FormatSelection): Long

    /** Stops a running download but keeps its partial data so [resume] continues where it left off. */
    suspend fun pause(id: Long)

    /** Also the "retry" for failed and cancelled items. */
    suspend fun resume(id: Long)

    suspend fun cancel(id: Long)

    /** Cancels if needed and deletes the item from the list. A finished file stays on the device. */
    suspend fun remove(id: Long)

    suspend fun pauseAll()

    suspend fun cancelAll()

    suspend fun clearFinished()

    /** Call once at app start: re-queues downloads interrupted by process death and starts them. */
    suspend fun recover()
}
