package com.najmulcodes.zapflick.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.najmulcodes.zapflick.domain.model.SavedMedia
import com.najmulcodes.zapflick.domain.repository.MediaFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidMediaFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    private val saver: SettingsAwareMediaSaver,
) : MediaFiles {

    private val privateDir: File get() = File(context.filesDir, PRIVATE_FOLDER)

    override suspend fun delete(media: SavedMedia): Boolean = withContext(Dispatchers.IO) {
        deleteBlocking(Uri.parse(media.uri))
    }

    override suspend fun moveToPrivate(media: SavedMedia): Result<SavedMedia> = withContext(Dispatchers.IO) {
        val source = Uri.parse(media.uri)
        val extension = media.displayName.substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }.take(8)
        val target = File(privateDir.apply { mkdirs() }, UUID.randomUUID().toString() + if (extension.isEmpty()) "" else ".$extension")
        try {
            val input = context.contentResolver.openInputStream(source) ?: throw FileNotFoundException(media.uri)
            input.use { stream -> target.outputStream().use { stream.copyTo(it) } }
            if (target.length() == 0L) throw IOException("The copy is empty")
            // Only once the private copy is whole is the public one removed.
            if (!deleteBlocking(source)) {
                target.delete()
                throw IOException("The public copy could not be removed")
            }
            Result.success(SavedMedia(Uri.fromFile(target).toString(), media.displayName, PRIVATE_LABEL))
        } catch (e: IOException) {
            target.delete()
            Result.failure(e)
        } catch (e: SecurityException) {
            target.delete()
            Result.failure(e)
        }
    }

    override suspend fun restoreFromPrivate(media: SavedMedia): Result<SavedMedia> = withContext(Dispatchers.IO) {
        val file = privateFileOf(media) ?: return@withContext Result.failure(IOException("Not a private file"))
        // The private file has a random name; give the restored one back its real name.
        val temp = File(File(context.cacheDir, "restore").apply { mkdirs() }, safeName(media.displayName))
        try {
            file.copyTo(temp, overwrite = true)
            val saved = saver.save(temp)
            saved.onSuccess { file.delete() }
        } catch (e: IOException) {
            Result.failure(e)
        } finally {
            temp.delete()
        }
    }

    /** The file behind a private item, only if it really lives in the private folder. */
    private fun privateFileOf(media: SavedMedia): File? {
        val path = Uri.parse(media.uri).path ?: return null
        val file = File(path)
        return try {
            if (file.canonicalPath.startsWith(privateDir.canonicalPath + File.separator)) file else null
        } catch (e: IOException) {
            null
        }
    }

    private fun deleteBlocking(uri: Uri): Boolean = try {
        when (uri.scheme) {
            "file" -> {
                val file = File(uri.path.orEmpty())
                !file.exists() || file.delete()
            }
            "content" -> if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } else {
                // 0 rows means it was already gone, which is just as good.
                context.contentResolver.delete(uri, null, null) >= 0
            }
            else -> false
        }
    } catch (e: FileNotFoundException) {
        true
    } catch (e: SecurityException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    } catch (e: UnsupportedOperationException) {
        false
    }

    private fun safeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "file" }

    companion object {
        const val PRIVATE_FOLDER = "private"
        const val PRIVATE_LABEL = "Private folder"
    }
}
