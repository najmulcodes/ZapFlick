package com.najmulcodes.vidgrab.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtractUrlUseCaseTest {
    private val extractUrl = ExtractUrlUseCase()

    @Test
    fun `returns a plain url unchanged`() {
        assertEquals("https://youtu.be/abc123", extractUrl("https://youtu.be/abc123"))
    }

    @Test
    fun `extracts the url from share text with a caption`() {
        val shared = "Watch this video https://example.com/watch?v=42&t=10 it is great"
        assertEquals("https://example.com/watch?v=42&t=10", extractUrl(shared))
    }

    @Test
    fun `strips trailing punctuation`() {
        assertEquals("https://example.com/a", extractUrl("see (https://example.com/a)."))
        assertEquals("https://example.com/a", extractUrl("https://example.com/a,"))
    }

    @Test
    fun `returns the first url when there are several`() {
        assertEquals("https://a.example/1", extractUrl("https://a.example/1 and https://b.example/2"))
    }

    @Test
    fun `matches an uppercase scheme`() {
        assertEquals("HTTPS://EXAMPLE.COM/X", extractUrl("HTTPS://EXAMPLE.COM/X"))
    }

    @Test
    fun `returns null when there is no url`() {
        assertNull(extractUrl("just some words"))
        assertNull(extractUrl(""))
    }
}
