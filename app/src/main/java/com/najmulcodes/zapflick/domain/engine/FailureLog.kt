package com.najmulcodes.zapflick.domain.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** One failed lookup or download, with the raw text yt-dlp printed. */
data class FailureEntry(
    val timeMillis: Long,
    val operation: String,
    val url: String,
    val errorCode: String,
    val raw: String,
)

/** Keeps the last few failures in memory so the real reason can be read and copied. Not persisted. */
@Singleton
class FailureLog @Inject constructor() {
    private val _entries = MutableStateFlow<List<FailureEntry>>(emptyList())
    val entries: StateFlow<List<FailureEntry>> = _entries.asStateFlow()

    fun record(entry: FailureEntry) {
        _entries.update { current -> (listOf(entry) + current).take(MAX_ENTRIES) }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    private companion object {
        const val MAX_ENTRIES = 20
    }
}

/** The text copied from the failure screens. Pure, so it can be tested. */
object FailureReport {
    private const val MAX_RAW = 1500

    fun build(entries: List<FailureEntry>, ytDlpVersion: String?, appVersion: String?): String = buildString {
        appendLine("ZapFlick ${appVersion ?: "?"}, yt-dlp ${ytDlpVersion ?: "?"}")
        if (entries.isEmpty()) {
            append("No failures recorded.")
            return@buildString
        }
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        entries.forEach { entry ->
            appendLine()
            appendLine("[${format.format(Date(entry.timeMillis))}] ${entry.operation} -> ${entry.errorCode}")
            appendLine(entry.url)
            appendLine(entry.raw.trim().take(MAX_RAW))
        }
    }.trimEnd()
}
