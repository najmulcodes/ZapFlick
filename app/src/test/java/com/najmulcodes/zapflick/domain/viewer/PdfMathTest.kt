package com.najmulcodes.zapflick.domain.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfMathTest {

    @Test
    fun `fit width scale is pixels per point`() {
        assertEquals(2f, PdfMath.fitWidthScale(pageWidthPts = 500, targetWidthPx = 1000), 0.0001f)
        assertEquals(0.5f, PdfMath.fitWidthScale(pageWidthPts = 1000, targetWidthPx = 500), 0.0001f)
    }

    @Test
    fun `fit width scale is zero for invalid input`() {
        assertEquals(0f, PdfMath.fitWidthScale(0, 1000), 0f)
        assertEquals(0f, PdfMath.fitWidthScale(-5, 1000), 0f)
        assertEquals(0f, PdfMath.fitWidthScale(500, 0), 0f)
    }

    @Test
    fun `page keeps its aspect ratio when scaled to width`() {
        assertEquals(PixelSize(50, 100), PdfMath.pageSizeAtWidth(PdfPageSize(100, 200), 50))
        assertEquals(PixelSize(1080, 1398), PdfMath.pageSizeAtWidth(PdfPageSize(612, 792), 1080))
        assertEquals(PixelSize(400, 200), PdfMath.pageSizeAtWidth(PdfPageSize(200, 100), 400))
    }

    @Test
    fun `a page with no size is treated as A4`() {
        assertEquals(PixelSize(1000, 1414), PdfMath.pageSizeAtWidth(PdfPageSize(0, 0), 1000))
        assertEquals(PixelSize(1000, 1414), PdfMath.pageSizeAtWidth(PdfPageSize(0, 500), 1000))
        assertEquals(PixelSize(1000, 1414), PdfMath.pageSizeAtWidth(PdfPageSize(500, 0), 1000))
    }

    @Test
    fun `a zero target width still yields a valid size`() {
        val size = PdfMath.pageSizeAtWidth(PdfPageSize(100, 200), 0)
        assertEquals(1, size.width)
        assertTrue(size.height >= 1)
    }

    @Test
    fun `a size inside the limits is unchanged`() {
        assertEquals(PixelSize(1080, 1920), PdfMath.clampBitmapSize(PixelSize(1080, 1920)))
    }

    @Test
    fun `a very tall page is limited by the longest edge`() {
        val clamped = PdfMath.clampBitmapSize(PixelSize(1000, 20_000), maxDimension = 4096, maxPixels = Long.MAX_VALUE)
        assertEquals(4096, clamped.height)
        assertEquals(204, clamped.width)
    }

    @Test
    fun `a large page is limited by the pixel count`() {
        val clamped = PdfMath.clampBitmapSize(PixelSize(4000, 4000), maxDimension = 4096, maxPixels = 4_000_000)
        assertTrue(clamped.width.toLong() * clamped.height <= 4_000_000)
        assertEquals(clamped.width, clamped.height)
        assertEquals(2000, clamped.width)
    }

    @Test
    fun `clamping preserves the aspect ratio`() {
        val clamped = PdfMath.clampBitmapSize(PixelSize(8000, 4000), maxDimension = 4096, maxPixels = Long.MAX_VALUE)
        assertEquals(4096, clamped.width)
        assertEquals(2048, clamped.height)
    }

    @Test
    fun `clamping never returns zero`() {
        val clamped = PdfMath.clampBitmapSize(PixelSize(1, 100_000), maxDimension = 4096, maxPixels = 8_000_000)
        assertTrue(clamped.width >= 1)
        assertTrue(clamped.height >= 1)
        assertEquals(PixelSize(1, 1), PdfMath.clampBitmapSize(PixelSize(0, 0)))
        assertEquals(PixelSize(1, 1), PdfMath.clampBitmapSize(PixelSize(-3, 10)))
    }

    @Test
    fun `render size respects the default limits at maximum zoom`() {
        val size = PdfMath.renderSize(PdfPageSize(612, 792), targetWidthPx = 1080 * 4)
        assertTrue(size.width <= PdfMath.MAX_BITMAP_DIMENSION)
        assertTrue(size.height <= PdfMath.MAX_BITMAP_DIMENSION)
        assertTrue(size.width.toLong() * size.height <= PdfMath.MAX_BITMAP_PIXELS)
    }

    @Test
    fun `current page switches once more than half of the first page is gone`() {
        assertEquals(1, PdfMath.currentPage(0, 0, 1000, 10))
        assertEquals(1, PdfMath.currentPage(0, 500, 1000, 10))
        assertEquals(2, PdfMath.currentPage(0, 501, 1000, 10))
        assertEquals(3, PdfMath.currentPage(2, 100, 1000, 10))
    }

    @Test
    fun `current page stays inside the document`() {
        assertEquals(10, PdfMath.currentPage(9, 900, 1000, 10))
        assertEquals(1, PdfMath.currentPage(-4, 0, 1000, 10))
        assertEquals(0, PdfMath.currentPage(0, 0, 1000, 0))
        assertEquals(1, PdfMath.currentPage(0, 0, 0, 5))
    }

    @Test
    fun `zoom is clamped`() {
        assertEquals(PdfMath.MIN_ZOOM, PdfMath.clampZoom(0.2f), 0f)
        assertEquals(PdfMath.MAX_ZOOM, PdfMath.clampZoom(99f), 0f)
        assertEquals(2f, PdfMath.clampZoom(2f), 0f)
        assertEquals(PdfMath.MIN_ZOOM, PdfMath.clampZoom(Float.NaN), 0f)
    }

    @Test
    fun `double tap toggles between normal and zoomed`() {
        assertEquals(PdfMath.DOUBLE_TAP_ZOOM, PdfMath.nextDoubleTapZoom(1f), 0f)
        assertEquals(PdfMath.MIN_ZOOM, PdfMath.nextDoubleTapZoom(PdfMath.DOUBLE_TAP_ZOOM), 0f)
        assertEquals(PdfMath.MIN_ZOOM, PdfMath.nextDoubleTapZoom(3.7f), 0f)
        assertEquals(PdfMath.DOUBLE_TAP_ZOOM, PdfMath.nextDoubleTapZoom(1.02f), 0f)
    }

    @Test
    fun `anchored scroll keeps the point under the finger in place`() {
        // Tapping at x=300 with no scroll, zooming 1x to 2x: the content point at 300 moves to 600,
        // so scrolling by 300 brings it back under the finger.
        assertEquals(300, PdfMath.anchoredScroll(0, 300f, 1f, 2f))
        // Zooming back out returns to the left edge.
        assertEquals(0, PdfMath.anchoredScroll(300, 300f, 2f, 1f))
    }

    @Test
    fun `anchored scroll never goes negative or divides by zero`() {
        assertEquals(0, PdfMath.anchoredScroll(0, 0f, 4f, 1f))
        assertEquals(50, PdfMath.anchoredScroll(50, 10f, 0f, 2f))
    }

    @Test
    fun `cache is a fraction of the heap within bounds`() {
        assertEquals(64 * 1024 * 1024, PdfMath.cacheSizeBytes(384L * 1024 * 1024, fraction = 6))
        assertEquals(8 * 1024 * 1024, PdfMath.cacheSizeBytes(16L * 1024 * 1024))
        assertEquals(96 * 1024 * 1024, PdfMath.cacheSizeBytes(4L * 1024 * 1024 * 1024))
        assertEquals(96 * 1024 * 1024, PdfMath.cacheSizeBytes(4L * 1024 * 1024 * 1024, fraction = 0))
    }
}
