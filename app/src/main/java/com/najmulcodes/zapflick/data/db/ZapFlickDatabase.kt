package com.najmulcodes.zapflick.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DownloadEntity::class, FavoriteSiteEntity::class, HistoryEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ZapFlickDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
}
