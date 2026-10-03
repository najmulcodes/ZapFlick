package com.najmulcodes.zapflick.domain.viewer

import java.util.Locale

/**
 * Decides whether a document is a PDF or an HTML page.
 *
 * The mime type wins when it says something specific. File managers and download providers often
 * report nothing useful (null, star/star, octet-stream), and only then is the file extension used.
 * Only the last extension counts, so "report.pdf.exe" is not a PDF and "page.html.pdf" is.
 */
object DocumentTypeResolver {

    private val pdfMimeTypes = setOf("application/pdf", "application/x-pdf")
    private val htmlMimeTypes = setOf("text/html", "application/xhtml+xml")

    /** Mime types that carry no information about the content. */
    private val genericMimeTypes = setOf(
        "",
        "*/*",
        "application/octet-stream",
        "binary/octet-stream",
        "application/force-download",
        "application/x-download",
        "application/download",
        "application/unknown",
    )

    private val pdfExtensions = setOf("pdf")
    private val htmlExtensions = setOf("html", "htm", "xhtml")

    fun resolve(mimeType: String?, displayName: String?): DocumentType {
        val mime = normalizeMime(mimeType)
        return when {
            mime in pdfMimeTypes -> DocumentType.Pdf
            mime in htmlMimeTypes -> DocumentType.Html
            mime !in genericMimeTypes -> DocumentType.Unsupported
            else -> fromExtension(displayName)
        }
    }

    /**
     * Tries each mime type in order (for example the one on the Intent, then the one the provider
     * reports) and returns the first answer that is not [DocumentType.Unsupported].
     */
    fun resolveFirst(displayName: String?, vararg mimeTypes: String?): DocumentType {
        for (mime in mimeTypes) {
            val type = resolve(mime, displayName)
            if (type != DocumentType.Unsupported) return type
        }
        return resolve(null, displayName)
    }

    /** Lowercase extension after the last dot, or null when there is none. */
    fun extensionOf(displayName: String?): String? {
        val name = displayName?.trim().orEmpty()
        if (!name.contains('.')) return null
        return name.substringAfterLast('.').trim().lowercase(Locale.ROOT).ifEmpty { null }
    }

    private fun fromExtension(displayName: String?): DocumentType = when (extensionOf(displayName)) {
        in pdfExtensions -> DocumentType.Pdf
        in htmlExtensions -> DocumentType.Html
        else -> DocumentType.Unsupported
    }

    private fun normalizeMime(mimeType: String?): String =
        mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
}
