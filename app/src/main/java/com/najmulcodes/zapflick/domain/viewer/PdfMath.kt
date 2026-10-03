package com.najmulcodes.zapflick.domain.viewer

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A PDF page size in points (1/72 inch), as reported by PdfRenderer. */
data class PdfPageSize(val width: Int, val height: Int)

data class PixelSize(val width: Int, val height: Int)

/** The pure arithmetic behind the PDF viewer: page scaling, bitmap limits, zoom and cache sizing. */
object PdfMath {
    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 4f
    const val DOUBLE_TAP_ZOOM = 2.5f

    /** Longest bitmap edge. Many GPUs refuse to draw larger textures. */
    const val MAX_BITMAP_DIMENSION = 4096

    /** 8 million pixels is 32 MB as ARGB_8888. */
    const val MAX_BITMAP_PIXELS = 8_000_000L

    const val DEFAULT_CACHE_FRACTION = 6
    private const val MIN_CACHE_BYTES = 8L * 1024 * 1024
    private const val MAX_CACHE_BYTES = 96L * 1024 * 1024

    private const val A4_ASPECT = 1.4142f
    private const val ZOOMED_THRESHOLD = 1.05f

    /** Pixels per PDF point that makes a page exactly [targetWidthPx] wide. 0 when either input is invalid. */
    fun fitWidthScale(pageWidthPts: Int, targetWidthPx: Int): Float =
        if (pageWidthPts <= 0 || targetWidthPx <= 0) 0f else targetWidthPx.toFloat() / pageWidthPts

    /** Size of a page scaled to [targetWidthPx] wide. A page with no usable size is treated as A4. */
    fun pageSizeAtWidth(page: PdfPageSize, targetWidthPx: Int): PixelSize {
        val width = targetWidthPx.coerceAtLeast(1)
        val scale = fitWidthScale(page.width, width)
        val height = if (scale > 0f && page.height > 0) {
            (page.height * scale).roundToInt()
        } else {
            (width * A4_ASPECT).roundToInt()
        }
        return PixelSize(width, height.coerceAtLeast(1))
    }

    /** Shrinks [size] (keeping its aspect ratio) until it fits both limits. Never returns less than 1 px. */
    fun clampBitmapSize(
        size: PixelSize,
        maxDimension: Int = MAX_BITMAP_DIMENSION,
        maxPixels: Long = MAX_BITMAP_PIXELS,
    ): PixelSize {
        if (size.width <= 0 || size.height <= 0) return PixelSize(1, 1)
        val byDimension = min(1.0, maxDimension.toDouble() / max(size.width, size.height))
        val byArea = min(1.0, sqrt(maxPixels.toDouble() / (size.width.toDouble() * size.height)))
        val scale = min(byDimension, byArea)
        if (scale >= 1.0) return size
        return PixelSize(
            width = (size.width * scale).toInt().coerceAtLeast(1),
            height = (size.height * scale).toInt().coerceAtLeast(1),
        )
    }

    /** The bitmap size to render a page at when it is shown [targetWidthPx] wide. */
    fun renderSize(page: PdfPageSize, targetWidthPx: Int): PixelSize =
        clampBitmapSize(pageSizeAtWidth(page, targetWidthPx))

    /**
     * 1-based page for the "page 3 / 120" label. The page counts as current once more than half
     * of the first visible page has scrolled off the top.
     */
    fun currentPage(
        firstVisibleIndex: Int,
        firstVisibleOffsetPx: Int,
        firstVisibleSizePx: Int,
        pageCount: Int,
    ): Int {
        if (pageCount <= 0) return 0
        val mostlyScrolledAway = firstVisibleSizePx > 0 && firstVisibleOffsetPx * 2 > firstVisibleSizePx
        val index = firstVisibleIndex + if (mostlyScrolledAway) 1 else 0
        return (index + 1).coerceIn(1, pageCount)
    }

    fun clampZoom(zoom: Float): Float = if (zoom.isNaN()) MIN_ZOOM else zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /** Double tap zooms in when the page is at normal size and back out when it is already zoomed. */
    fun nextDoubleTapZoom(current: Float): Float = if (current > ZOOMED_THRESHOLD) MIN_ZOOM else DOUBLE_TAP_ZOOM

    /**
     * Horizontal scroll that keeps the content under [anchorX] (viewport pixels) in place when the
     * zoom changes from [oldZoom] to [newZoom].
     */
    fun anchoredScroll(currentScroll: Int, anchorX: Float, oldZoom: Float, newZoom: Float): Int {
        if (oldZoom <= 0f || newZoom <= 0f) return currentScroll
        val scroll = (currentScroll + anchorX) * (newZoom / oldZoom) - anchorX
        return scroll.roundToInt().coerceAtLeast(0)
    }

    /** Bitmap cache size: a fraction of the heap, kept inside sane bounds. */
    fun cacheSizeBytes(maxMemoryBytes: Long, fraction: Int = DEFAULT_CACHE_FRACTION): Int =
        (maxMemoryBytes / fraction.coerceAtLeast(1)).coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES).toInt()
}
