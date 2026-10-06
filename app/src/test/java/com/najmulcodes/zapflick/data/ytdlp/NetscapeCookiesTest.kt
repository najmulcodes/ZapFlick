package com.najmulcodes.zapflick.data.ytdlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetscapeCookiesTest {

    @Test
    fun `writes the header line and one tab separated line per cookie`() {
        val text = NetscapeCookies.format("https://www.reddit.com/r/x", "a=1; b=two").orEmpty()
        val lines = text.lines().filter { it.isNotBlank() }
        assertEquals("# Netscape HTTP Cookie File", lines[0])
        assertEquals(".reddit.com\tTRUE\t/\tTRUE\t2147483647\ta\t1", lines[1])
        assertEquals(".reddit.com\tTRUE\t/\tTRUE\t2147483647\tb\ttwo", lines[2])
        assertEquals(3, lines.size)
    }

    @Test
    fun `plain http cookies are not marked secure`() {
        val text = NetscapeCookies.format("http://example.com/a", "k=v").orEmpty()
        assertTrue(text.contains(".example.com\tTRUE\t/\tFALSE\t"))
    }

    @Test
    fun `values may contain equals signs`() {
        val text = NetscapeCookies.format("https://example.com", "t=ab=cd").orEmpty()
        assertTrue(text.contains("\tt\tab=cd"))
    }

    @Test
    fun `tabs and line breaks inside a cookie cannot break the file format`() {
        val text = NetscapeCookies.format("https://example.com", "k=a\tb\nc").orEmpty()
        val cookieLine = text.lines().first { it.startsWith(".example.com") }
        assertEquals(7, cookieLine.split('\t').size)
    }

    @Test
    fun `nothing to write when there are no usable cookies`() {
        assertNull(NetscapeCookies.format("https://example.com", ""))
        assertNull(NetscapeCookies.format("https://example.com", "novalue"))
        assertNull(NetscapeCookies.format("not a url", "a=1"))
    }
}
