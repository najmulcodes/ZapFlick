package com.najmulcodes.zapflick.domain.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentTypeResolverTest {

    @Test
    fun `trusts a specific mime type`() {
        assertEquals(DocumentType.Pdf, DocumentTypeResolver.resolve("application/pdf", "file.bin"))
        assertEquals(DocumentType.Html, DocumentTypeResolver.resolve("text/html", "file.bin"))
        assertEquals(DocumentType.Html, DocumentTypeResolver.resolve("application/xhtml+xml", null))
        assertEquals(DocumentType.Pdf, DocumentTypeResolver.resolve("application/x-pdf", null))
    }

    @Test
    fun `mime type is case-insensitive and ignores parameters`() {
        assertEquals(DocumentType.Html, DocumentTypeResolver.resolve("Text/HTML; charset=utf-8", null))
        assertEquals(DocumentType.Pdf, DocumentTypeResolver.resolve("  APPLICATION/PDF  ", null))
    }

    @Test
    fun `octet-stream falls back to the extension`() {
        assertEquals(DocumentType.Pdf, DocumentTypeResolver.resolve("application/octet-stream", "invoice.pdf"))
        assertEquals(DocumentType.Html, DocumentTypeResolver.resolve("application/octet-stream", "page.html"))
        assertEquals(DocumentType.Html, DocumentTypeResolver.resolve("application/octet-stream", "page.htm"))
        assertEquals(DocumentType.Unsupported, DocumentTypeResolver.resolve("application/octet-stream", "photo.png"))
    }

    @Test
    fun `other generic mime types also fall back to the extension`() {
        assertEquals(DocumentType.Pdf, DocumentTypeResolver.resolve("*/*", "a.pdf"))
        assertEquals(DocumentType.Pdf, DocumentTypeResolver.resolve("binary/octet-stream", "a.pdf"))
        assertEquals(DocumentType.Pdf, DocumentTypeResolver.resolve("application/force-download", "a.pdf"))
        assertEquals(DocumentType.Pdf, DocumentTypeResolver.resolve("", "a.pdf"))
    }

    @Test
    fun `extensions are matched in any case`() {
        assertEquals(DocumentType.Pdf, DocumentTypeResolver.resolve(null, "SCAN.PDF"))
        assertEquals(DocumentType.Html, DocumentTypeResolver.resolve(null, "Index.HTML"))
        assertEquals(DocumentType.Html, DocumentTypeResolver.resolve(null, "Index.HtM"))
        assertEquals(DocumentType.Pdf, DocumentTypeResolver.resolve(null, "  scan.pdf  "))
    }

    @Test
    fun `only the last extension counts`() {
        assertEquals(DocumentType.Pdf, DocumentTypeResolver.resolve(null, "page.html.pdf"))
        assertEquals(DocumentType.Html, DocumentTypeResolver.resolve(null, "report.v2.final.html"))
        assertEquals(DocumentType.Unsupported, DocumentTypeResolver.resolve(null, "report.pdf.exe"))
        assertEquals(DocumentType.Unsupported, DocumentTypeResolver.resolve(null, "archive.tar.gz"))
    }

    @Test
    fun `a specific but unrelated mime type is not overridden by the name`() {
        assertEquals(DocumentType.Unsupported, DocumentTypeResolver.resolve("image/png", "trick.pdf"))
        assertEquals(DocumentType.Unsupported, DocumentTypeResolver.resolve("text/plain", "notes.html"))
    }

    @Test
    fun `null and blank inputs are unsupported`() {
        assertEquals(DocumentType.Unsupported, DocumentTypeResolver.resolve(null, null))
        assertEquals(DocumentType.Unsupported, DocumentTypeResolver.resolve("", ""))
        assertEquals(DocumentType.Unsupported, DocumentTypeResolver.resolve("   ", "   "))
        assertEquals(DocumentType.Unsupported, DocumentTypeResolver.resolve(null, "noextension"))
        assertEquals(DocumentType.Unsupported, DocumentTypeResolver.resolve(null, "trailingdot."))
    }

    @Test
    fun `a known mime type needs no name`() {
        assertEquals(DocumentType.Pdf, DocumentTypeResolver.resolve("application/pdf", null))
    }

    @Test
    fun `extensionOf returns the lowercase last extension`() {
        assertEquals("pdf", DocumentTypeResolver.extensionOf("A.B.PDF"))
        assertNull(DocumentTypeResolver.extensionOf(null))
        assertNull(DocumentTypeResolver.extensionOf("plain"))
        assertNull(DocumentTypeResolver.extensionOf("dot."))
    }

    @Test
    fun `resolveFirst skips an unsupported answer and tries the next mime type`() {
        assertEquals(
            DocumentType.Pdf,
            DocumentTypeResolver.resolveFirst("x", "*/*", "application/pdf"),
        )
        assertEquals(
            DocumentType.Pdf,
            DocumentTypeResolver.resolveFirst(null, "text/plain", "application/pdf"),
        )
    }

    @Test
    fun `resolveFirst falls back to the name and finally to unsupported`() {
        assertEquals(DocumentType.Html, DocumentTypeResolver.resolveFirst("a.html", null, null))
        assertEquals(DocumentType.Unsupported, DocumentTypeResolver.resolveFirst("a.zip", "image/png", null))
        assertEquals(DocumentType.Unsupported, DocumentTypeResolver.resolveFirst(null))
    }
}
