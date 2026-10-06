package com.najmulcodes.zapflick.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.najmulcodes.zapflick.di.ApplicationScope
import com.najmulcodes.zapflick.domain.settings.AppSettings
import com.najmulcodes.zapflick.domain.settings.SettingsRepository
import com.najmulcodes.zapflick.domain.settings.enumOrDefault
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/** One DataStore per file name, created once for the process. */
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) : SettingsRepository {

    private val store = context.settingsDataStore

    /**
     * The first value is read before anything else can ask for it, so a download never starts with
     * the defaults while the person's real settings (Wi-Fi only, for one) are still loading. It is
     * a few milliseconds of disk, once.
     */
    override val settings: StateFlow<AppSettings> = store.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, runBlocking { store.data.first().toSettings() })

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings()).sanitized()) }
    }

    private object Keys {
        val customFolderUri = stringPreferencesKey("custom_folder_uri")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val maxConcurrent = intPreferencesKey("max_concurrent")
        val defaultQuality = stringPreferencesKey("default_quality")
        val filenameStyle = stringPreferencesKey("filename_style")
        val blockAds = booleanPreferencesKey("block_ads")
        val recentSites = booleanPreferencesKey("recent_sites")
        val searchEngine = stringPreferencesKey("search_engine")
        val syncToGallery = booleanPreferencesKey("sync_to_gallery")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val theme = stringPreferencesKey("theme")
        val ytDlpChannel = stringPreferencesKey("ytdlp_channel")
        val biometricUnlock = booleanPreferencesKey("biometric_unlock")
        val secureScreens = booleanPreferencesKey("secure_screens")
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            customFolderUri = this[Keys.customFolderUri],
            wifiOnly = this[Keys.wifiOnly] ?: d.wifiOnly,
            maxConcurrent = this[Keys.maxConcurrent] ?: d.maxConcurrent,
            defaultQuality = this[Keys.defaultQuality] ?: d.defaultQuality,
            filenameStyle = enumOrDefault(this[Keys.filenameStyle], d.filenameStyle),
            blockAds = this[Keys.blockAds] ?: d.blockAds,
            recentSites = this[Keys.recentSites] ?: d.recentSites,
            searchEngine = enumOrDefault(this[Keys.searchEngine], d.searchEngine),
            syncToGallery = this[Keys.syncToGallery] ?: d.syncToGallery,
            dynamicColor = this[Keys.dynamicColor] ?: d.dynamicColor,
            theme = enumOrDefault(this[Keys.theme], d.theme),
            ytDlpChannel = enumOrDefault(this[Keys.ytDlpChannel], d.ytDlpChannel),
            biometricUnlock = this[Keys.biometricUnlock] ?: d.biometricUnlock,
            secureScreens = this[Keys.secureScreens] ?: d.secureScreens,
        ).sanitized()
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.write(s: AppSettings) {
        val folder = s.customFolderUri
        if (folder == null) remove(Keys.customFolderUri) else this[Keys.customFolderUri] = folder
        this[Keys.wifiOnly] = s.wifiOnly
        this[Keys.maxConcurrent] = s.maxConcurrent
        this[Keys.defaultQuality] = s.defaultQuality
        this[Keys.filenameStyle] = s.filenameStyle.name
        this[Keys.blockAds] = s.blockAds
        this[Keys.recentSites] = s.recentSites
        this[Keys.searchEngine] = s.searchEngine.name
        this[Keys.syncToGallery] = s.syncToGallery
        this[Keys.dynamicColor] = s.dynamicColor
        this[Keys.theme] = s.theme.name
        this[Keys.ytDlpChannel] = s.ytDlpChannel.name
        this[Keys.biometricUnlock] = s.biometricUnlock
        this[Keys.secureScreens] = s.secureScreens
    }
}

