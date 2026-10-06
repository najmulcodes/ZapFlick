package com.najmulcodes.zapflick.data.browser

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.najmulcodes.zapflick.data.db.FavoriteDao
import com.najmulcodes.zapflick.data.db.FavoriteSiteEntity
import com.najmulcodes.zapflick.data.db.HistoryDao
import com.najmulcodes.zapflick.data.db.HistoryEntity
import com.najmulcodes.zapflick.domain.browser.DefaultFavorites
import com.najmulcodes.zapflick.domain.browser.FavoriteRules
import com.najmulcodes.zapflick.domain.browser.FavoriteSite
import com.najmulcodes.zapflick.domain.browser.HistoryEntry
import com.najmulcodes.zapflick.domain.browser.HistoryRules
import com.najmulcodes.zapflick.domain.browser.ListReorder
import com.najmulcodes.zapflick.domain.browser.TabList
import com.najmulcodes.zapflick.domain.browser.TabListCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepository @Inject constructor(
    private val dao: FavoriteDao,
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val favorites: Flow<List<FavoriteSite>> = dao.observeAll().map { rows ->
        rows.map { FavoriteSite(it.id, it.title, it.url, it.position) }
    }

    /** Puts the default sites in once. Deleting them all later does not bring them back. */
    suspend fun ensureSeeded() {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        if (dao.count() == 0) {
            dao.insertAll(
                DefaultFavorites.SEED.mapIndexed { index, (title, url) ->
                    FavoriteSiteEntity(title = title, url = url, position = index)
                },
            )
        }
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
    }

    /** False when the address is not a usable site, or when the site is already there. */
    suspend fun add(title: String, address: String): Boolean {
        val (cleanTitle, url) = FavoriteRules.normalize(title, address) ?: return false
        if (favorites.first().any { it.url.equals(url, ignoreCase = true) }) return false
        dao.insert(FavoriteSiteEntity(title = cleanTitle, url = url, position = dao.maxPosition() + 1))
        return true
    }

    suspend fun remove(id: Long) = dao.delete(id)

    /** Moves a favorite one place earlier ([delta] -1) or later (+1). */
    suspend fun move(id: Long, delta: Int) {
        val current = favorites.first()
        val from = current.indexOfFirst { it.id == id }
        if (from < 0) return
        val reordered = ListReorder.move(current, from, from + delta)
        if (reordered !== current) dao.applyOrder(reordered.map { it.id })
    }

    private companion object {
        const val PREFS = "zapflick_browser"
        const val KEY_SEEDED = "favorites_seeded"
    }
}

@Singleton
class HistoryRepository @Inject constructor(
    private val dao: HistoryDao,
) {
    fun recent(limit: Int): Flow<List<HistoryEntry>> = dao.observeRecent(limit).map { rows ->
        rows.map { HistoryEntry(it.url, it.title, it.visitedAt) }
    }

    suspend fun record(url: String, title: String?) {
        if (!HistoryRules.shouldRecord(url)) return
        dao.record(
            HistoryEntity(url = url, title = HistoryRules.titleFor(url, title), visitedAt = System.currentTimeMillis()),
            HistoryRules.MAX_ENTRIES,
        )
    }

    suspend fun clear() = dao.clear()
}

private val Context.browserDataStore: DataStore<Preferences> by preferencesDataStore(name = "browser")

/** Remembers which tabs were open (where each one was, not its page history) across app restarts. */
@Singleton
class TabsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.browserDataStore

    suspend fun load(): TabList? = TabListCodec.decode(store.data.first()[KEY])

    suspend fun save(list: TabList) {
        store.edit { it[KEY] = TabListCodec.encode(list) }
    }

    private companion object {
        val KEY = stringPreferencesKey("tabs")
    }
}
