package com.najmulcodes.zapflick.domain.viewer

import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException
import java.nio.charset.UnsupportedCharsetException
import java.util.Locale

/**
 * Works out how an HTML file's bytes are encoded: a byte order mark first, then a charset
 * declared in a meta tag near the top, then UTF-8.
 */
object CharsetDetector {

    /** [bomLength] bytes at the start are the byte order mark and are not part of the text. */
    data class Detection(val charset: Charset, val bomLength: Int)

    /** How far into the file a meta tag is looked for. */
    const val META_SCAN_BYTES = 4096

    private val metaCharset = Regex(
        """<meta[^>]*?charset\s*=\s*["']?\s*([a-z0-9_.:\-]+)""",
        RegexOption.IGNORE_CASE,
    )

    /** Names browsers treat as Windows-1252, which is what old "Latin-1" pages really contain. */
    private val windows1252Labels = setOf(
        "iso-8859-1", "iso8859-1", "iso_8859-1", "latin1", "latin-1", "l1", "ibm819",
        "ascii", "us-ascii", "cp1252", "x-cp1252", "windows-1252",
    )

    fun detect(bytes: ByteArray, length: Int = bytes.size): Detection {
        val size = length.coerceIn(0, bytes.size)
        byteOrderMark(bytes, size)?.let { return it }
        return Detection(charsetFromMeta(bytes, size) ?: Charsets.UTF_8, bomLength = 0)
    }

    /** The file's text, without any byte order mark. Invalid bytes become replacement characters. */
    fun decode(bytes: ByteArray, length: Int = bytes.size): String {
        val size = length.coerceIn(0, bytes.size)
        val detection = detect(bytes, size)
        return String(bytes, detection.bomLength, size - detection.bomLength, detection.charset)
    }

    /**
     * The charset a meta tag label stands for, or null when it is empty or unknown. A label for
     * UTF-16 or UTF-32 cannot be right for a file whose meta tag is readable as ASCII, so (like
     * browsers) it means UTF-8.
     */
    fun charsetForLabel(label: String): Charset? {
        val name = label.trim().lowercase(Locale.ROOT)
        return when {
            name.isEmpty() -> null
            name.startsWith("utf-16") || name.startsWith("utf16") || name.startsWith("utf-32") ||
                name.startsWith("utf32") -> Charsets.UTF_8
            name in windows1252Labels -> lookup("windows-1252")
            else -> lookup(name)
        }
    }

    private fun charsetFromMeta(bytes: ByteArray, size: Int): Charset? {
        val head = String(bytes, 0, minOf(size, META_SCAN_BYTES), Charsets.ISO_8859_1)
        val label = metaCharset.find(head)?.groupValues?.get(1) ?: return null
        return charsetForLabel(label)
    }

    private fun byteOrderMark(bytes: ByteArray, size: Int): Detection? {
        fun at(index: Int): Int = bytes[index].toInt() and 0xFF
        if (size >= 3 && at(0) == 0xEF && at(1) == 0xBB && at(2) == 0xBF) {
            return Detection(Charsets.UTF_8, 3)
        }
        // UTF-32 first: its little-endian mark starts with the UTF-16 one.
        if (size >= 4 && at(0) == 0xFF && at(1) == 0xFE && at(2) == 0x00 && at(3) == 0x00) {
            lookup("UTF-32LE")?.let { return Detection(it, 4) }
        }
        if (size >= 4 && at(0) == 0x00 && at(1) == 0x00 && at(2) == 0xFE && at(3) == 0xFF) {
            lookup("UTF-32BE")?.let { return Detection(it, 4) }
        }
        if (size >= 2 && at(0) == 0xFF && at(1) == 0xFE) return Detection(Charsets.UTF_16LE, 2)
        if (size >= 2 && at(0) == 0xFE && at(1) == 0xFF) return Detection(Charsets.UTF_16BE, 2)
        return null
    }

    private fun lookup(name: String): Charset? = try {
        Charset.forName(name)
    } catch (e: IllegalCharsetNameException) {
        null
    } catch (e: UnsupportedCharsetException) {
        null
    }
}
