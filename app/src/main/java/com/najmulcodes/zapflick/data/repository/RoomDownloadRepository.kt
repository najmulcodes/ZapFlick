package com.najmulcodes.zapflick.data.repository

import com.najmulcodes.zapflick.data.db.DownloadDao
import com.najmulcodes.zapflick.data.db.DownloadEntity
import com.najmulcodes.zapflick.data.db.toDomain
import com.najmulcodes.zapflick.domain.model.DownloadError
import com.najmulcodes.zapflick.domain.model.DownloadItem
import com.najmulcodes.zapflick.domain.model.DownloadStatus
import com.najmulcodes.zapflick.domain.model.FormatSelection
import com.najmulcodes.zapflick.domain.model.SavedMedia
import com.najmulcodes.zapflick.domain.model.VideoMetadata
import com.najmulcodes.zapflick.domain.repository.DownloadRepository
import com.najmulcodes.zapflick.domain.repository.PrivateItemsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomDownloadRepository @Inject constructor(
    private val dao: DownloadDao,
) : DownloadRepository, PrivateItemsStore {

    override fun observeAll(): Flow<List<DownloadItem>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun get(id: Long): DownloadItem? = dao.get(id)?.toDomain()

    override suspend fun add(metadata: VideoMetadata, selection: FormatSelection): Long {
        val now = now()
        return dao.insert(
            DownloadEntity(
                url = metadata.sourceUrl,
                title = metadata.title,
                thumbnailUrl = metadata.thumbnailUrl,
                uploader = metadata.uploader,
                durationSeconds = metadata.durationSeconds,
                formatKey = selection.key,
                status = DownloadStatus.QUEUED.name,
                errorCode = null,
                errorDetail = null,
                savedUri = null,
                savedName = null,
                savedLocation = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun nextQueued(limit: Int): List<DownloadItem> =
        dao.nextQueued(limit).map { it.toDomain() }

    override suspend fun withStatus(vararg statuses: DownloadStatus): List<DownloadItem> =
        dao.withStatus(statuses.map { it.name }).map { it.toDomain() }

    override suspend fun setStatus(id: Long, status: DownloadStatus) {
        dao.setStatus(id, status.name, now())
    }

    override suspend fun requeue(id: Long): Boolean = dao.requeue(id, now()) > 0

    override suspend fun markCompleted(id: Long, media: SavedMedia) {
        dao.markCompleted(id, media.uri, media.displayName, media.location, now())
    }

    override suspend fun markFailed(id: Long, error: DownloadError) {
        val detail = (error as? DownloadError.Unknown)?.detail
        dao.markFailed(id, error.code, detail, now())
    }

    override suspend fun requeueRunning() {
        dao.requeueRunning(now())
    }

    override suspend fun delete(id: Long) {
        dao.delete(id)
    }

    override suspend fun clearFinished(): List<Long> {
        val ids = dao.finishedIds()
        dao.deleteFinished()
        return ids
    }

    override suspend fun setPrivate(id: Long, isPrivate: Boolean, media: SavedMedia) {
        dao.setPrivate(id, isPrivate, media.uri, media.location, now())
    }

    private fun now(): Long = System.currentTimeMillis()
}
