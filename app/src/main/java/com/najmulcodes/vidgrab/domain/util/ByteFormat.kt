package com.najmulcodes.vidgrab.domain.util

import java.util.Locale

private val UNITS = listOf("KB", "MB", "GB", "TB")

/** 1536 -> "2 KB", 5_242_880 -> "5.0 MB". Binary units, labelled the way Android labels them. */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    var value = bytes.toDouble()
    var index = -1
    do {
        value /= 1024.0
        index++
    } while (value >= 1024.0 && index < UNITS.lastIndex)
    val decimals = if (index == 0 || value >= 100.0) 0 else 1
    return String.format(Locale.ROOT, "%.${decimals}f %s", value, UNITS[index])
}
