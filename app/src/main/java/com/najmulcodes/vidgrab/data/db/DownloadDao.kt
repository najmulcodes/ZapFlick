package com.najmulcodes.vidgrab.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert
    suspend fun insert(entity: DownloadEntity): Long

    @Query("SELECT * FROM downloads ORDER BY created_at DESC, id DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY created_at ASC, id ASC LIMIT :limit")
    suspend fun nextQueued(limit: Int): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE status IN (:statuses) ORDER BY created_at ASC, id ASC")
    suspend fun withStatus(statuses: List<String>): List<DownloadEntity>

    @Query("UPDATE downloads SET status = :status, updated_at = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, now: Long)

    @Query(
        "UPDATE downloads SET status = 'QUEUED', error_code = NULL, error_detail = NULL, " +
            "updated_at = :now WHERE id = :id AND status IN ('PAUSED', 'FAILED', 'CANCELLED')",
    )
    suspend fun requeue(id: Long, now: Long): Int

    @Query(
        "UPDATE downloads SET status = 'COMPLETED', saved_uri = :uri, saved_name = :name, " +
            "saved_location = :location, error_code = NULL, error_detail = NULL, " +
            "updated_at = :now WHERE id = :id",
    )
    suspend fun markCompleted(id: Long, uri: String, name: String, location: String, now: Long)

    @Query(
        "UPDATE downloads SET status = 'FAILED', error_code = :code, error_detail = :detail, " +
            "updated_at = :now WHERE id = :id",
    )
    suspend fun markFailed(id: Long, code: String, detail: String?, now: Long)

    @Query("UPDATE downloads SET status = 'QUEUED', updated_at = :now WHERE status = 'RUNNING'")
    suspend fun requeueRunning(now: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT id FROM downloads WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun finishedIds(): List<Long>

    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun deleteFinished()
}
