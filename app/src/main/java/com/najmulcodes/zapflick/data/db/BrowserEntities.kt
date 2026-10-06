package com.najmulcodes.zapflick.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorite_sites")
data class FavoriteSiteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val position: Int,
)

@Entity(
    tableName = "browser_history",
    indices = [Index(value = ["url"], unique = true), Index(value = ["visited_at"])],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    @ColumnInfo(name = "visited_at") val visitedAt: Long,
)

@Dao
abstract class FavoriteDao {
    @Query("SELECT * FROM favorite_sites ORDER BY position ASC, id ASC")
    abstract fun observeAll(): Flow<List<FavoriteSiteEntity>>

    @Query("SELECT COUNT(*) FROM favorite_sites")
    abstract suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM favorite_sites")
    abstract suspend fun maxPosition(): Int

    @Insert
    abstract suspend fun insert(entity: FavoriteSiteEntity): Long

    @Insert
    abstract suspend fun insertAll(entities: List<FavoriteSiteEntity>)

    @Query("DELETE FROM favorite_sites WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("UPDATE favorite_sites SET position = :position WHERE id = :id")
    abstract suspend fun setPosition(id: Long, position: Int)

    /** Rewrites the order in one transaction so a half-finished reorder is never seen. */
    @Transaction
    open suspend fun applyOrder(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { index, id -> setPosition(id, index) }
    }
}

@Dao
abstract class HistoryDao {
    @Query("SELECT * FROM browser_history ORDER BY visited_at DESC LIMIT :limit")
    abstract fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    /** One row per address: visiting a page again only moves it to the top. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(entity: HistoryEntity)

    @Query(
        "DELETE FROM browser_history WHERE id NOT IN " +
            "(SELECT id FROM browser_history ORDER BY visited_at DESC LIMIT :keep)",
    )
    abstract suspend fun trimTo(keep: Int)

    @Query("DELETE FROM browser_history")
    abstract suspend fun clear()

    @Transaction
    open suspend fun record(entity: HistoryEntity, keep: Int) {
        upsert(entity)
        trimTo(keep)
    }
}
