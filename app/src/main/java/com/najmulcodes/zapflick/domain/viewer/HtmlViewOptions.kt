package com.najmulcodes.zapflick.domain.viewer

import java.util.Locale

/**
 * What the person has switched on for the HTML file on screen. Everything starts off, and a new
 * file starts from the defaults again: nothing here is saved.
 */
data class HtmlViewOptions(
    val scriptsEnabled: Boolean = false,
    val onlineContentAllowed: Boolean = false,
    val showSource: Boolean = false,
)

object HtmlLimits {
    /** Largest HTML file ZapFlick reads into memory. */
    const val MAX_BYTES: Long = 10L * 1024 * 1024

    /** True when a size reported by the provider is already known to be too big. */
    fun exceeds(sizeBytes: Long?): Boolean = sizeBytes != null && sizeBytes > MAX_BYTES
}

enum class LinkDecision { OpenExternally, AllowInPage, Block }

/**
 * What to do when a page in the viewer tries to navigate. Web links leave the viewer for a
 * browser; jumps within the page (an anchor such as #top) stay; everything else is blocked,
 * including javascript:, file:, content:, intent:, mailto: and data: links.
 */
object HtmlLinkPolicy {

    fun decide(url: String?): LinkDecision {
        val value = url?.trim().orEmpty()
        if (value.isEmpty()) return LinkDecision.Block
        val lower = value.lowercase(Locale.ROOT)
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") -> LinkDecision.OpenExternally
            isInPageAnchor(lower) -> LinkDecision.AllowInPage
            else -> LinkDecision.Block
        }
    }

    /**
     * The page is loaded without a base address, so "#top" resolves to "about:blank#top".
     * Nothing else under about: is allowed.
     */
    private fun isInPageAnchor(lower: String): Boolean {
        val base = "about:blank"
        if (!lower.startsWith(base)) return false
        val rest = lower.substring(base.length)
        return rest.isEmpty() || rest.startsWith("#") || rest.startsWith("?")
    }
}

data class SourcePreview(val text: String, val truncated: Boolean)

/** Keeps the "View source" screen responsive for large files. */
object SourcePreviewer {
    const val MAX_CHARS = 100_000
    const val MAX_LINE_CHARS = 2_000

    fun limit(text: String, maxChars: Int = MAX_CHARS): SourcePreview {
        if (maxChars <= 0) return SourcePreview("", truncated = text.isNotEmpty())
        if (text.length <= maxChars) return SourcePreview(text, truncated = false)
        var end = maxChars
        // Do not cut an emoji or other two-char character in half.
        if (Character.isHighSurrogate(text[end - 1])) end--
        return SourcePreview(text.substring(0, end), truncated = true)
    }

    /**
     * Splits text into display lines: any line break style is accepted, and a line longer than
     * [maxLineChars] is cut into pieces, so one minified line cannot become one giant item.
     */
    fun lines(text: String, maxLineChars: Int = MAX_LINE_CHARS): List<String> {
        if (text.isEmpty()) return emptyList()
        val limit = maxLineChars.coerceAtLeast(1)
        val result = ArrayList<String>()
        for (line in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            if (line.length <= limit) {
                result += line
                continue
            }
            var start = 0
            while (start < line.length) {
                var end = minOf(start + limit, line.length)
                if (end < line.length && Character.isHighSurrogate(line[end - 1])) end--
                result += line.substring(start, end)
                start = end
            }
        }
        return result
    }
}
