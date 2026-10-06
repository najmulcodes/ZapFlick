package com.najmulcodes.zapflick.domain.browser

import java.util.Locale

data class FavoriteSite(val id: Long, val title: String, val url: String, val position: Int)

object DefaultFavorites {
    /** Title and address. Tiles show a monogram, never the site's logo. */
    val SEED: List<Pair<String, String>> = listOf(
        "Facebook" to "https://m.facebook.com",
        "Instagram" to "https://www.instagram.com",
        "Vimeo" to "https://vimeo.com",
        "Dailymotion" to "https://www.dailymotion.com",
        "TikTok" to "https://www.tiktok.com",
        "X" to "https://x.com",
    )
}

object FavoriteRules {
    /** A cleaned-up address and title for a new favorite, or null when the address is unusable. */
    fun normalize(title: String, address: String): Pair<String, String>? {
        val text = address.trim()
        val lower = text.lowercase(Locale.ROOT)
        // A search phrase is turned into a search address by the browser; that is not a site worth pinning.
        val isAddress = lower.startsWith("http://") || lower.startsWith("https://") ||
            (' ' !in text && '.' in text.substringBefore('/'))
        if (!isAddress) return null
        val url = BrowserInput.resolve(text) ?: return null
        val host = hostOf(url) ?: return null
        val cleanTitle = title.trim().ifEmpty { host.removePrefix("www.") }
        return cleanTitle to url
    }

    /** The letter shown on a site's tile. */
    fun monogram(title: String): String {
        val first = title.trim().firstOrNull { it.isLetterOrDigit() } ?: return "?"
        return first.toString().uppercase(Locale.ROOT)
    }
}

object ListReorder {
    /** Moves the item at [from] to [to]. An out-of-range move returns the list unchanged. */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        val mutable = list.toMutableList()
        mutable.add(to, mutable.removeAt(from))
        return mutable
    }
}

object HistoryRules {
    const val MAX_ENTRIES = 500

    /** Only real web pages are remembered, never about:, data: or error pages. */
    fun shouldRecord(url: String?): Boolean {
        val lower = url?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    fun titleFor(url: String, title: String?): String =
        title?.trim().takeUnless { it.isNullOrEmpty() } ?: hostOf(url) ?: url
}

data class HistoryEntry(val url: String, val title: String, val visitedAt: Long)

object RecentSites {
    /** The newest page of each site, newest first: the "Recently used websites" row. */
    fun pick(history: List<HistoryEntry>, max: Int = 8): List<HistoryEntry> {
        val seen = HashSet<String>()
        val result = ArrayList<HistoryEntry>()
        for (entry in history.sortedByDescending { it.visitedAt }) {
            val host = hostOf(entry.url)?.removePrefix("www.") ?: continue
            if (seen.add(host)) result += entry
            if (result.size >= max) break
        }
        return result
    }
}
