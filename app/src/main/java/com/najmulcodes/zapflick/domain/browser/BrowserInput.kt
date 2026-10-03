package com.najmulcodes.zapflick.domain.browser

import java.net.URLEncoder

/** Turns whatever was typed in the address bar into a URL to load. */
object BrowserInput {
    const val HOME_URL = "https://www.google.com"
    private const val SEARCH_URL = "https://www.google.com/search?q="

    fun resolve(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val lower = text.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return text
        val looksLikeAddress = ' ' !in text && '.' in text.substringBefore('/')
        return if (looksLikeAddress) {
            "https://$text"
        } else {
            SEARCH_URL + URLEncoder.encode(text, "UTF-8")
        }
    }
}
