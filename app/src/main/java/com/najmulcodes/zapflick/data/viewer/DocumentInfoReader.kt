package com.najmulcodes.zapflick.data.viewer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.najmulcodes.zapflick.domain.viewer.DocumentInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Reads the file name, size and mime type of a document without opening its content. */
class DocumentInfoReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * A SecurityException means the permission granted with the Intent is gone; it is passed on so
     * the caller can show the "open the file again" state. Any other provider failure only costs
     * us the metadata.
     */
    suspend fun read(uri: Uri): DocumentInfo = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name: String? = null
        var size: Long? = null

        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                resolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameColumn >= 0 && !cursor.isNull(nameColumn)) {
                            name = cursor.getString(nameColumn)
                        }
                        val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) {
                            size = cursor.getLong(sizeColumn).takeIf { it >= 0 }
                        }
                    }
                }
            } catch (e: SecurityException) {
                throw e
            } catch (e: RuntimeException) {
                // Some providers throw on unknown columns; the file may still open fine.
            }
        }

        DocumentInfo(
            displayName = name ?: uri.lastPathSegment,
            sizeBytes = size,
            mimeType = try {
                resolver.getType(uri)
            } catch (e: SecurityException) {
                throw e
            } catch (e: RuntimeException) {
                null
            },
        )
    }

    /**
     * True for a file: link that points into ZapFlick's own private storage. Another app could send
     * such a link to read our files through the viewer, so it is refused.
     */
    suspend fun isOwnPrivateFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (uri.scheme != ContentResolver.SCHEME_FILE) return@withContext false
        val path = uri.path ?: return@withContext false
        try {
            val target = File(path).canonicalPath
            val privateRoots = listOfNotNull(context.dataDir, context.codeCacheDir?.parentFile)
                .map { it.canonicalPath }
            privateRoots.any { root -> target == root || target.startsWith(root + File.separator) }
        } catch (e: IOException) {
            // A path we cannot even resolve is not worth opening.
            true
        }
    }

    private companion object {
        val PROJECTION = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
    }
}
