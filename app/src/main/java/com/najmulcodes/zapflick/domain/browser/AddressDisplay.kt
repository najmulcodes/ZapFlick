package com.najmulcodes.zapflick.domain.browser

/** What the address pill shows while it is not being edited: the site, not the whole URL. */
object AddressDisplay {
    fun compact(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || trimmed == "about:blank") return ""
        val host = hostOf(trimmed) ?: return trimmed.take(MAX_FALLBACK)
        return host.removePrefix("www.")
    }

    private const val MAX_FALLBACK = 60
}
