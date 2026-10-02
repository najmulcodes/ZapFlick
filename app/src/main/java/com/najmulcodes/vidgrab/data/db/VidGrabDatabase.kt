package com.najmulcodes.vidgrab.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DownloadEntity::class], version = 1, exportSchema = false)
abstract class VidGrabDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}
