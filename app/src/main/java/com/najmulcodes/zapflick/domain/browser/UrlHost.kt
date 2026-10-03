package com.najmulcodes.zapflick.domain.browser

/** Lower-cased host of an http(s) URL, or null. Hand-rolled because real-world media URLs often break java.net.URI. */
internal fun hostOf(url: String): String? {
    val authority = url.substringAfter("://", missingDelimiterValue = "")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
    val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
    return host.ifBlank { null }
}
