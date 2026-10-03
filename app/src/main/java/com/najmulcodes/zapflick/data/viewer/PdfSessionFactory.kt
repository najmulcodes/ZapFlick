package com.najmulcodes.zapflick.data.viewer

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.najmulcodes.zapflick.di.ApplicationScope
import com.najmulcodes.zapflick.domain.viewer.PdfPageSize
import com.najmulcodes.zapflick.domain.viewer.ViewerError
import com.najmulcodes.zapflick.domain.viewer.ViewerException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject

/**
 * Opens a PDF for rendering. The file stays where it is: the content is read through a descriptor
 * from the ContentResolver. Only when that descriptor cannot seek (PdfRenderer needs to) is the
 * file copied into the app's private cache, and the copy is deleted when the session closes.
 */
class PdfSessionFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val closeScope: CoroutineScope,
) {

    /** @throws ViewerException with the reason the file could not be opened. */
    suspend fun open(uri: Uri): PdfSession = withContext(Dispatchers.IO) {
        purgeStaleCopies()

        var cacheCopy: File? = null
        var descriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            var opened = openDescriptor(uri)
            descriptor = opened
            if (!opened.isSeekable()) {
                opened.close()
                descriptor = null
                val copy = copyToCache(uri)
                cacheCopy = copy
                opened = openCopy(copy)
                descriptor = opened
            }

            val created = createRenderer(opened)
            renderer = created
            if (created.pageCount <= 0) throw ViewerException(ViewerError.Corrupt)

            val pages = readPageSizes(created)
            PdfSession(
                descriptor = opened,
                renderer = created,
                cacheCopy = cacheCopy,
                pages = pages,
                closeScope = closeScope,
            )
        } catch (e: Throwable) {
            // Failure or cancellation before the session took ownership: release everything.
            runCatching { renderer?.close() }
            runCatching { descriptor?.close() }
            cacheCopy?.delete()
            throw e
        }
    }

    private fun openDescriptor(uri: Uri): ParcelFileDescriptor {
        try {
            return context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw ViewerException(ViewerError.NotReadable)
        } catch (e: SecurityException) {
            throw ViewerException(ViewerError.AccessLost, e)
        } catch (e: FileNotFoundException) {
            throw ViewerException(ViewerError.NotReadable, e)
        }
    }

    private fun openCopy(copy: File): ParcelFileDescriptor = try {
        ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
    } catch (e: IOException) {
        throw ViewerException(ViewerError.NotReadable, e)
    }

    /** A pipe or socket cannot seek, and PdfRenderer refuses it. */
    private fun ParcelFileDescriptor.isSeekable(): Boolean = try {
        Os.lseek(fileDescriptor, 0, OsConstants.SEEK_CUR)
        true
    } catch (e: ErrnoException) {
        false
    }

    private suspend fun copyToCache(uri: Uri): File {
        val directory = File(context.cacheDir, CACHE_DIRECTORY).apply { mkdirs() }
        val target = File.createTempFile("doc-", ".pdf", directory)
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw ViewerException(ViewerError.NotReadable)
            input.use { source ->
                target.outputStream().use { sink ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        currentCoroutineContext().ensureActive()
                    }
                }
            }
            return target
        } catch (e: SecurityException) {
            target.delete()
            throw ViewerException(ViewerError.AccessLost, e)
        } catch (e: IOException) {
            target.delete()
            throw ViewerException(ViewerError.NotReadable, e)
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
    }

    /**
     * PdfRenderer throws SecurityException for a password-protected file and IOException when the
     * file is not a readable PDF. (A SecurityException from the Uri itself was already handled
     * when the descriptor was opened, so here it can only mean a password.)
     */
    private fun createRenderer(descriptor: ParcelFileDescriptor): PdfRenderer = try {
        PdfRenderer(descriptor)
    } catch (e: SecurityException) {
        throw ViewerException(ViewerError.PasswordProtected, e)
    } catch (e: IOException) {
        throw ViewerException(ViewerError.Corrupt, e)
    } catch (e: IllegalArgumentException) {
        throw ViewerException(ViewerError.Corrupt, e)
    }

    /** Page sizes are read up front so the list has its real height before any page is drawn. */
    private suspend fun readPageSizes(renderer: PdfRenderer): List<PdfPageSize> {
        val sizes = ArrayList<PdfPageSize>(renderer.pageCount)
        for (index in 0 until renderer.pageCount) {
            currentCoroutineContext().ensureActive()
            try {
                val page = renderer.openPage(index)
                try {
                    sizes += PdfPageSize(page.width, page.height)
                } finally {
                    page.close()
                }
            } catch (e: RuntimeException) {
                throw ViewerException(ViewerError.Corrupt, e)
            }
        }
        return sizes
    }

    /** Copies left behind by a process that died before cleaning up. */
    private fun purgeStaleCopies() {
        val directory = File(context.cacheDir, CACHE_DIRECTORY)
        val cutoff = System.currentTimeMillis() - STALE_COPY_AGE_MS
        directory.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
    }

    private companion object {
        const val CACHE_DIRECTORY = "viewer"
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val STALE_COPY_AGE_MS = 24L * 60 * 60 * 1000
    }
}
