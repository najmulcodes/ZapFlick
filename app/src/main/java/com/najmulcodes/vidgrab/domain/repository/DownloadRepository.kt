package com.najmulcodes.vidgrab.domain.repository

import com.najmulcodes.vidgrab.domain.model.DownloadError
import com.najmulcodes.vidgrab.domain.model.DownloadItem
import com.najmulcodes.vidgrab.domain.model.DownloadStatus
import com.najmulcodes.vidgrab.domain.model.FormatSelection
import com.najmulcodes.vidgrab.domain.model.SavedMedia
import com.najmulcodes.vidgrab.domain.model.VideoMetadata
import kotlinx.coroutines.flow.Flow

/** Persistent queue and history. The queue manager is its only writer. */
interface DownloadRepository {
    fun observeAll(): Flow<List<DownloadItem>>

    suspend fun get(id: Long): DownloadItem?

    /** Inserts a new item in the QUEUED state and returns its id. */
    suspend fun add(metadata: VideoMetadata, selection: FormatSelection): Long

    /** Oldest queued items first. */
    suspend fun nextQueued(limit: Int): List<DownloadItem>

    suspend fun withStatus(vararg statuses: DownloadStatus): List<DownloadItem>

    suspend fun setStatus(id: Long, status: DownloadStatus)

    /** PAUSED, FAILED or CANCELLED -> QUEUED. Returns false if the item was in another state. */
    suspend fun requeue(id: Long): Boolean

    suspend fun markCompleted(id: Long, media: SavedMedia)

    suspend fun markFailed(id: Long, error: DownloadError)

    /** After a process death: anything still marked RUNNING was interrupted, so queue it again. */
    suspend fun requeueRunning()

    suspend fun delete(id: Long)

    /** Removes completed, failed and cancelled items and returns their ids. */
    suspend fun clearFinished(): List<Long>
}
