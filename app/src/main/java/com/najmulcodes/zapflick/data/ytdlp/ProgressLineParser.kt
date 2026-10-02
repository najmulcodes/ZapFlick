package com.najmulcodes.zapflick.data.ytdlp

/** The library reports percent and ETA itself; speed has to be read from yt-dlp's output line. */
object ProgressLineParser {
    private val SPEED = Regex("""\bat\s+(\d+(?:\.\d+)?\s?[KMGTP]?i?B/s)""")

    /** "[download]  45.2% of 10.00MiB at 1.20MiB/s ETA 00:05" -> "1.20MiB/s"; null if unknown. */
    fun parseSpeed(line: String): String? =
        SPEED.find(line)?.groupValues?.get(1)?.replace(" ", "")
}
