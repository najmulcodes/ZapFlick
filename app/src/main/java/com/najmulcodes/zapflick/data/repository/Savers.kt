package com.najmulcodes.zapflick.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.najmulcodes.zapflick.domain.model.DownloadError
import com.najmulcodes.zapflick.domain.model.DownloadException
import com.najmulcodes.zapflick.domain.model.SavedMedia
import com.najmulcodes.zapflick.domain.repository.MediaSaver
import com.najmulcodes.zapflick.domain.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Saves into a folder the person picked with the system folder picker. No storage permission is involved. */
@Singleton
class TreeUriSaver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun save(file: File, treeUri: Uri): Result<SavedMedia> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
            val created = DocumentsContract.createDocument(resolver, parent, mime, file.name)
                ?: throw IOException("Could not create the file in the chosen folder")
            try {
                val output = resolver.openOutputStream(created) ?: throw IOException("Cannot open the new file")
                output.use { out -> file.inputStream().use { it.copyTo(out) } }
            } catch (e: Exception) {
                runCatching { DocumentsContract.deleteDocument(resolver, created) }
                throw e
            }
            Result.success(
                SavedMedia(
                    uri = created.toString(),
                    displayName = file.name,
                    location = treeUri.lastPathSegment?.substringAfter(':').orEmpty().ifEmpty { "Chosen folder" },
                ),
            )
        } catch (e: IOException) {
            Result.failure(DownloadException(DownloadError.StorageError, e))
        } catch (e: SecurityException) {
            // The permission to the folder was revoked or the folder was deleted.
            Result.failure(DownloadException(DownloadError.StorageError, e))
        } catch (e: IllegalArgumentException) {
            Result.failure(DownloadException(DownloadError.StorageError, e))
        }
    }
}

/** App-private storage: hidden from the gallery and other apps, and removed when ZapFlick is uninstalled. */
@Singleton
class AppStorageSaver @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaSaver {

    override suspend fun save(file: File): Result<SavedMedia> = withContext(Dispatchers.IO) {
        try {
            val folder = File(context.filesDir, FOLDER).apply { mkdirs() }
            var target = File(folder, file.name)
            var counter = 1
            while (target.exists()) {
                target = File(folder, "${file.nameWithoutExtension} ($counter).${file.extension}")
                counter++
            }
            file.copyTo(target)
            Result.success(SavedMedia(uri = Uri.fromFile(target).toString(), displayName = target.name, location = LOCATION))
        } catch (e: IOException) {
            Result.failure(DownloadException(DownloadError.StorageError, e))
        } catch (e: SecurityException) {
            Result.failure(DownloadException(DownloadError.StorageError, e))
        }
    }

    companion object {
        const val FOLDER = "library"
        const val LOCATION = "App storage"
    }
}

/** Picks where a finished file goes from the current Settings. */
@Singleton
class SettingsAwareMediaSaver @Inject constructor(
    private val settings: SettingsRepository,
    private val mediaStore: MediaStoreSaver,
    private val tree: TreeUriSaver,
    private val appStorage: AppStorageSaver,
) : MediaSaver {

    override suspend fun save(file: File): Result<SavedMedia> {
        val current = settings.settings.value
        val folder = current.customFolderUri
        return when {
            folder != null -> tree.save(file, Uri.parse(folder))
            !current.syncToGallery -> appStorage.save(file)
            else -> mediaStore.save(file)
        }
    }
}
