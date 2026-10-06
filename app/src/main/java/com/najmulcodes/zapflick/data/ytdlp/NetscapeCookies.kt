package com.najmulcodes.zapflick.data.ytdlp

/**
 * Turns the cookie header the in-app browser holds for a page into a Netscape cookies.txt that
 * yt-dlp reads with --cookies. A file is more reliable than a Cookie header: extractors that set
 * their own cookies (age confirmation, over-18 flags) merge with it instead of being overridden.
 */
object NetscapeCookies {
    private const val FAR_FUTURE = "2147483647"

    fun format(url: String, cookieHeader: String): String? {
        val host = hostOf(url) ?: return null
        val domain = "." + host.removePrefix("www.").removePrefix("m.").removePrefix("mobile.")
        val secure = if (url.startsWith("https://", ignoreCase = true)) "TRUE" else "FALSE"
        val lines = cookieHeader.split(';').mapNotNull { part ->
            val name = part.substringBefore('=', "").trim().clean()
            val value = part.substringAfter('=', "").trim().clean()
            if (name.isEmpty()) null else "$domain\tTRUE\t/\t$secure\t$FAR_FUTURE\t$name\t$value"
        }
        if (lines.isEmpty()) return null
        return buildString {
            appendLine("# Netscape HTTP Cookie File")
            lines.forEach { appendLine(it) }
        }
    }

    private fun String.clean(): String = filterNot { it == '\t' || it == '\r' || it == '\n' }

    private fun hostOf(url: String): String? {
        val authority = url.substringAfter("://", missingDelimiterValue = "")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        return authority.substringAfterLast('@').substringBefore(':').lowercase().ifBlank { null }
    }
}
