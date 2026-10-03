package com.najmulcodes.zapflick.domain.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserInputTest {

    @Test
    fun `blank input loads nothing`() {
        assertNull(BrowserInput.resolve("   "))
    }

    @Test
    fun `full urls are kept as typed`() {
        assertEquals("https://example.com/a?b=1", BrowserInput.resolve("  https://example.com/a?b=1 "))
        assertEquals("http://example.com", BrowserInput.resolve("http://example.com"))
    }

    @Test
    fun `bare domains get https`() {
        assertEquals("https://example.com", BrowserInput.resolve("example.com"))
        assertEquals("https://example.com/watch?v=1", BrowserInput.resolve("example.com/watch?v=1"))
    }

    @Test
    fun `anything else becomes a search`() {
        assertEquals("https://www.google.com/search?q=funny+cats", BrowserInput.resolve("funny cats"))
        assertEquals("https://www.google.com/search?q=cats", BrowserInput.resolve("cats"))
    }
}
