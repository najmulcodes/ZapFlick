package com.najmulcodes.zapflick.domain.browser

import com.najmulcodes.zapflick.domain.settings.SearchEngine
import java.net.URLEncoder

/** Turns whatever was typed in the address bar into a URL to load. */
object BrowserInput {
    const val HOME_URL = "https://www.google.com"

    fun resolve(raw: String, engine: SearchEngine = SearchEngine.GOOGLE): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val lower = text.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return text
        val looksLikeAddress = ' ' !in text && '.' in text.substringBefore('/')
        return if (looksLikeAddress) {
            "https://$text"
        } else {
            engine.queryUrl + URLEncoder.encode(text, "UTF-8")
        }
    }
}
