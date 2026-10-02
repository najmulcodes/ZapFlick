package com.najmulcodes.zapflick.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String?,
    val uploader: String?,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Long?,
    @ColumnInfo(name = "format_key") val formatKey: String,
    /** [com.najmulcodes.zapflick.domain.model.DownloadStatus] name. */
    val status: String,
    @ColumnInfo(name = "error_code") val errorCode: String?,
    @ColumnInfo(name = "error_detail") val errorDetail: String?,
    @ColumnInfo(name = "saved_uri") val savedUri: String?,
    @ColumnInfo(name = "saved_name") val savedName: String?,
    @ColumnInfo(name = "saved_location") val savedLocation: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
