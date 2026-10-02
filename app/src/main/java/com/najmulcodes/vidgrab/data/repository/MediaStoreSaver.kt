package com.najmulcodes.vidgrab.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import com.najmulcodes.vidgrab.domain.model.DownloadError
import com.najmulcodes.vidgrab.domain.model.DownloadException
import com.najmulcodes.vidgrab.domain.model.SavedMedia
import com.najmulcodes.vidgrab.domain.repository.MediaSaver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes files through MediaStore (Movies/ or Music/), so no storage permission is needed.
 * Below Android 10 MediaStore writes need a legacy permission, so those devices get the app's own
 * external files folder instead.
 */
@Singleton
class MediaStoreSaver @Inject constructor(
    @ApplicationContext private val context: Context,
) : MediaSaver {

    override suspend fun save(file: File): Result<SavedMedia> = withContext(Dispatchers.IO) {
        try {
            val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(file)
            } else {
                saveToAppDirectory(file)
            }
            Result.success(saved)
        } catch (e: IOException) {
            Result.failure(DownloadException(DownloadError.StorageError, e))
        } catch (e: SecurityException) {
            Result.failure(DownloadException(DownloadError.StorageError, e))
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveWithMediaStore(file: File): SavedMedia {
        val mime = mimeTypeOf(file)
        val isAudio = mime.startsWith("audio/")
        val directory = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val relativePath = "$directory/$APP_FOLDER"
        val collection = if (isAudio) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        val resolver = context.contentResolver
        val pending = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, pending) ?: throw IOException("MediaStore insert failed")

        try {
            val output = resolver.openOutputStream(uri) ?: throw IOException("Cannot open output stream")
            output.use { out -> file.inputStream().use { input -> input.copyTo(out) } }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return SavedMedia(uri = uri.toString(), displayName = file.name, location = relativePath)
    }

    private fun saveToAppDirectory(file: File): SavedMedia {
        val isAudio = mimeTypeOf(file).startsWith("audio/")
        val directory = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val folder = context.getExternalFilesDir(directory) ?: context.filesDir
        var target = File(folder, file.name)
        var counter = 1
        while (target.exists()) {
            target = File(folder, "${file.nameWithoutExtension} ($counter).${file.extension}")
            counter++
        }
        file.copyTo(target)
        return SavedMedia(
            uri = Uri.fromFile(target).toString(),
            displayName = target.name,
            location = folder.absolutePath,
        )
    }

    private fun mimeTypeOf(file: File): String {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: if (extension in AUDIO_EXTENSIONS) "audio/$extension" else "video/$extension"
    }

    private companion object {
        const val APP_FOLDER = "VidGrab"
        val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "opus", "ogg", "flac", "wav")
    }
}
