package com.najmulcodes.zapflick.data.ytdlp

import com.najmulcodes.zapflick.domain.model.AudioFormat
import com.najmulcodes.zapflick.domain.model.FormatSelection

/** One yt-dlp command-line option; [value] is null for bare flags. */
data class YtDlpOption(val name: String, val value: String? = null)

/** Pure translation of a [FormatSelection] into yt-dlp options, so it can be unit tested. */
object YtDlpOptions {
    const val OUTPUT_TEMPLATE = "%(title).100B [%(id)s].%(ext)s"

    // Highest resolution first; among equals prefer h264 + m4a so players are happy.
    private const val VIDEO_SORT = "res,vcodec:h264,acodec:m4a"

    fun forDownload(
        selection: FormatSelection,
        outputDir: String,
        template: String = OUTPUT_TEMPLATE,
    ): List<YtDlpOption> = buildList {
        add(YtDlpOption("--no-playlist"))
        add(YtDlpOption("--no-mtime"))
        add(YtDlpOption("-o", "$outputDir/$template"))
        addAll(formatOptions(selection))
    }

    fun formatOptions(selection: FormatSelection): List<YtDlpOption> = when (selection) {
        FormatSelection.Best -> videoOptions(maxHeight = null)
        is FormatSelection.Resolution -> videoOptions(maxHeight = selection.height)
        is FormatSelection.Audio -> audioOptions(selection.format)
    }

    private fun videoOptions(maxHeight: Int?): List<YtDlpOption> {
        val format = if (maxHeight == null) {
            "bv*+ba/b"
        } else {
            "bv*[height<=$maxHeight]+ba/b[height<=$maxHeight]"
        }
        return listOf(
            YtDlpOption("-f", format),
            YtDlpOption("-S", VIDEO_SORT),
            YtDlpOption("--merge-output-format", "mp4"),
        )
    }

    private fun audioOptions(format: AudioFormat): List<YtDlpOption> {
        val selector = when (format) {
            AudioFormat.MP3 -> "bestaudio/best"
            AudioFormat.M4A -> "bestaudio[ext=m4a]/bestaudio/best"
        }
        return listOf(
            YtDlpOption("-f", selector),
            YtDlpOption("-x"),
            YtDlpOption("--audio-format", format.extension),
            YtDlpOption("--audio-quality", "0"),
        )
    }
}
