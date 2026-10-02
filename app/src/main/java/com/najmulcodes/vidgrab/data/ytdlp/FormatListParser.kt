package com.najmulcodes.vidgrab.data.ytdlp

import com.najmulcodes.vidgrab.domain.model.AvailableFormats
import com.najmulcodes.vidgrab.domain.model.FormatCatalog
import com.najmulcodes.vidgrab.domain.model.RawFormat
import org.json.JSONObject

/** Reads the `formats` array of `yt-dlp --dump-single-json` into [AvailableFormats]. */
object FormatListParser {

    fun parse(json: String): AvailableFormats {
        val root = JSONObject(json)
        val duration = root.doubleOrNull("duration")
        val array = root.optJSONArray("formats") ?: return FormatCatalog.build(emptyList())

        val formats = ArrayList<RawFormat>(array.length())
        for (index in 0 until array.length()) {
            val format = array.optJSONObject(index) ?: continue
            formats += RawFormat(
                height = format.intOrNull("height"),
                vcodec = format.stringOrNull("vcodec"),
                acodec = format.stringOrNull("acodec"),
                bytes = format.longOrNull("filesize")
                    ?: format.longOrNull("filesize_approx")
                    ?: estimateBytes(format.doubleOrNull("tbr"), duration),
            )
        }
        return FormatCatalog.build(formats)
    }

    /** Many streams report only a bitrate (kbit/s): bytes = kbit/s * 1000 / 8 * seconds. */
    private fun estimateBytes(kbitPerSecond: Double?, durationSeconds: Double?): Long? {
        if (kbitPerSecond == null || durationSeconds == null) return null
        return (kbitPerSecond * durationSeconds * BYTES_PER_KILOBIT).toLong().takeIf { it > 0 }
    }

    private const val BYTES_PER_KILOBIT = 125.0

    // isNull() is true for both a missing key and a JSON null; optString() would turn null into "null".
    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.longOrNull(key: String): Long? =
        if (isNull(key)) null else optLong(key).takeIf { it > 0 }

    private fun JSONObject.intOrNull(key: String): Int? =
        if (isNull(key)) null else optInt(key).takeIf { it > 0 }

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() && it > 0 }
}
