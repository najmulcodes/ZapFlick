package com.najmulcodes.zapflick.data.viewer

import android.content.Context
import android.net.Uri
import com.najmulcodes.zapflick.domain.viewer.CharsetDetector
import com.najmulcodes.zapflick.domain.viewer.HtmlLimits
import com.najmulcodes.zapflick.domain.viewer.ViewerError
import com.najmulcodes.zapflick.domain.viewer.ViewerException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject

/** Reads an HTML file into a string. Nothing is written anywhere. */
class HtmlDocumentReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * The size the provider reported is only a hint (it can be missing or wrong), so the limit is
     * enforced again while reading.
     *
     * @throws ViewerException [ViewerError.TooLarge], [ViewerError.AccessLost] or [ViewerError.NotReadable]
     */
    suspend fun read(uri: Uri, reportedSizeBytes: Long?): String = withContext(Dispatchers.IO) {
        if (HtmlLimits.exceeds(reportedSizeBytes)) throw ViewerException(ViewerError.TooLarge)

        val stream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: SecurityException) {
            throw ViewerException(ViewerError.AccessLost, e)
        } catch (e: FileNotFoundException) {
            throw ViewerException(ViewerError.NotReadable, e)
        } ?: throw ViewerException(ViewerError.NotReadable)

        try {
            stream.use { input ->
                val initial = (reportedSizeBytes ?: DEFAULT_BUFFER_BYTES.toLong())
                    .coerceIn(1L, HtmlLimits.MAX_BYTES)
                    .toInt()
                val output = ByteArrayOutputStream(initial)
                val buffer = ByteArray(DEFAULT_BUFFER_BYTES)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > HtmlLimits.MAX_BYTES) throw ViewerException(ViewerError.TooLarge)
                    output.write(buffer, 0, read)
                    currentCoroutineContext().ensureActive()
                }
                CharsetDetector.decode(output.toByteArray())
            }
        } catch (e: SecurityException) {
            throw ViewerException(ViewerError.AccessLost, e)
        } catch (e: IOException) {
            throw ViewerException(ViewerError.NotReadable, e)
        }
    }

    private companion object {
        const val DEFAULT_BUFFER_BYTES = 64 * 1024
    }
}
