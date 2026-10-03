package com.najmulcodes.zapflick.data.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.najmulcodes.zapflick.domain.viewer.PdfMath
import com.najmulcodes.zapflick.domain.viewer.PdfPageSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One open PDF. PdfRenderer allows a single open page at a time, so every render waits on [mutex]
 * and runs on the IO dispatcher, and every page is closed in a finally block.
 *
 * Rendered pages are cached by page and width. Evicted bitmaps are left to the garbage collector,
 * never recycled, because the UI may still be drawing them.
 */
class PdfSession internal constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val cacheCopy: File?,
    val pages: List<PdfPageSize>,
    private val closeScope: CoroutineScope,
) {
    private data class PageKey(val index: Int, val widthPx: Int)

    private val mutex = Mutex()

    /** Guarded by [mutex]. */
    private var closed = false

    private val cache = object : LruCache<PageKey, Bitmap>(
        PdfMath.cacheSizeBytes(Runtime.getRuntime().maxMemory()),
    ) {
        override fun sizeOf(key: PageKey, value: Bitmap): Int = value.byteCount
    }

    /** A page that is already rendered at this width, or null. Safe to call from the main thread. */
    fun cachedPage(index: Int, widthPx: Int): Bitmap? = cache.get(PageKey(index, widthPx))

    /** Returns null when the page cannot be drawn (closed session, bad page, out of memory). */
    suspend fun renderPage(index: Int, widthPx: Int): Bitmap? {
        val key = PageKey(index, widthPx)
        cache.get(key)?.let { return it }
        val page = pages.getOrNull(index) ?: return null
        val target = PdfMath.renderSize(page, widthPx)

        return withContext(Dispatchers.IO) {
            mutex.withLock {
                // Another request may have drawn this page while we waited for the lock.
                cache.get(key)?.let { return@withLock it }
                if (closed) return@withLock null
                val bitmap = drawLocked(index, target.width, target.height)
                if (bitmap != null) cache.put(key, bitmap)
                bitmap
            }
        }
    }

    /** Must be called with [mutex] held. */
    private fun drawLocked(index: Int, width: Int, height: Int): Bitmap? {
        return try {
            val page = renderer.openPage(index)
            try {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                // With no transform the page is scaled to fill the bitmap.
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } finally {
                page.close()
            }
        } catch (e: OutOfMemoryError) {
            // Drop what we cached so the next attempt has room.
            cache.evictAll()
            null
        } catch (e: IllegalStateException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Releases the renderer, the descriptor and any cache copy. It waits for a render in progress
     * (closing mid-render would crash natively), so it runs on the application scope rather than
     * a scope that is being cancelled.
     */
    fun close() {
        closeScope.launch(Dispatchers.IO) {
            mutex.withLock {
                if (closed) return@withLock
                closed = true
                cache.evictAll()
                runCatching { renderer.close() }
                runCatching { descriptor.close() }
                cacheCopy?.delete()
            }
        }
    }
}
