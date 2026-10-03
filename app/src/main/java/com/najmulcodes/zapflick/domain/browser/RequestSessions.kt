package com.najmulcodes.zapflick.domain.browser

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** What the in-app browser knew when a download was chosen, so yt-dlp can ask the site the same way. */
data class RequestSession(
    val userAgent: String?,
    val referer: String?,
    val cookies: String?,
)

/**
 * In-memory only. Lets downloads started from the browser reuse its login cookies, user agent and
 * referer, which many sites require. Nothing here is persisted or logged.
 */
@Singleton
class RequestSessions @Inject constructor() {
    private val byUrl = ConcurrentHashMap<String, RequestSession>()
    private val byHost = ConcurrentHashMap<String, RequestSession>()

    /** For one exact file URL, such as a sniffed media file. */
    fun rememberUrl(url: String, session: RequestSession) {
        byUrl[url] = session
    }

    /** For a whole site, such as the page a video is on. */
    fun rememberSite(pageUrl: String, session: RequestSession) {
        val host = hostOf(pageUrl) ?: return
        byHost[normalize(host)] = session
    }

    fun forUrl(url: String): RequestSession? {
        byUrl[url]?.let { return it }
        var host = normalize(hostOf(url) ?: return null)
        while (true) {
            byHost[host]?.let { return it }
            val parent = host.substringAfter('.', missingDelimiterValue = "")
            if ('.' !in parent) return null
            host = parent
        }
    }

    private fun normalize(host: String): String =
        host.removePrefix("www.").removePrefix("m.").removePrefix("mobile.")
}
