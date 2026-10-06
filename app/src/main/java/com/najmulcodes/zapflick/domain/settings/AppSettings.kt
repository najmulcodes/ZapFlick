package com.najmulcodes.zapflick.domain.settings

import com.najmulcodes.zapflick.domain.model.FormatSelection
import kotlinx.coroutines.flow.StateFlow

/** Returns the entry called [name], or [default] for a missing or unknown name (a setting from an older or newer build). */
inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

enum class SearchEngine(val queryUrl: String, val homeUrl: String) {
    GOOGLE("https://www.google.com/search?q=", "https://www.google.com"),
    DUCKDUCKGO("https://duckduckgo.com/?q=", "https://duckduckgo.com"),
    BING("https://www.bing.com/search?q=", "https://www.bing.com"),
}

enum class ThemeMode { SYSTEM, DARK, LIGHT }

enum class YtDlpChannel { STABLE, NIGHTLY }

/** How downloaded files are named. A fixed list, so a typo can never become a broken yt-dlp template. */
enum class FilenameStyle(val template: String) {
    TITLE_ID("%(title).100B [%(id)s].%(ext)s"),
    TITLE("%(title).120B.%(ext)s"),
    UPLOADER_TITLE("%(uploader).40B - %(title).100B.%(ext)s"),
    DATE_TITLE("%(upload_date)s - %(title).100B.%(ext)s"),
}

/** Everything the person can change in Settings. The defaults are what a fresh install uses. */
data class AppSettings(
    /** A folder picked with the system folder picker, or null for Movies/ZapFlick and Music/ZapFlick. */
    val customFolderUri: String? = null,
    val wifiOnly: Boolean = false,
    val maxConcurrent: Int = DEFAULT_CONCURRENT,
    /** [QUALITY_ASK], or the key of a [FormatSelection]. */
    val defaultQuality: String = QUALITY_ASK,
    val filenameStyle: FilenameStyle = FilenameStyle.TITLE_ID,
    val blockAds: Boolean = true,
    val recentSites: Boolean = true,
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    /** On: finished files show in the gallery. Off: they stay in app storage, hidden from other apps. */
    val syncToGallery: Boolean = true,
    val dynamicColor: Boolean = false,
    val theme: ThemeMode = ThemeMode.DARK,
    val ytDlpChannel: YtDlpChannel = YtDlpChannel.STABLE,
    val biometricUnlock: Boolean = false,
    /** Blocks screenshots and the recents preview on the PIN and private screens. */
    val secureScreens: Boolean = true,
) {
    /** The quality to use without asking, or null when the person wants to choose each time. */
    fun defaultSelection(): FormatSelection? =
        if (defaultQuality == QUALITY_ASK) null else FormatSelection.fromKey(defaultQuality)

    /** Pulls stored values back into their allowed range. */
    fun sanitized(): AppSettings = copy(maxConcurrent = maxConcurrent.coerceIn(CONCURRENT_RANGE))

    companion object {
        const val QUALITY_ASK = "ask"
        const val DEFAULT_CONCURRENT = 2
        val CONCURRENT_RANGE = 1..4
    }
}

/** The one place settings are read and written. The stream always holds the latest value. */
interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}
