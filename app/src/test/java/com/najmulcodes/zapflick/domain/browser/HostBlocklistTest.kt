package com.najmulcodes.zapflick.domain.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostBlocklistTest {

    private val list = HostBlocklist(setOf("ads.example.com", "tracker.net", "doubleclick.net"))

    @Test
    fun `a listed host is blocked`() {
        assertTrue(list.isBlocked("ads.example.com"))
        assertTrue(list.isBlocked("tracker.net"))
    }

    @Test
    fun `subdomains of a listed host are blocked`() {
        assertTrue(list.isBlocked("x.ads.example.com"))
        assertTrue(list.isBlocked("a.b.c.tracker.net"))
    }

    @Test
    fun `the parent of a listed host is not blocked`() {
        assertFalse(list.isBlocked("example.com"))
        assertFalse(list.isBlocked("www.example.com"))
    }

    @Test
    fun `unrelated hosts and look-alikes are not blocked`() {
        assertFalse(list.isBlocked("news.org"))
        assertFalse(list.isBlocked("mytracker.net"))
        assertFalse(list.isBlocked("tracker.net.evil.org"))
    }

    @Test
    fun `matching ignores case, spaces and a trailing dot`() {
        assertTrue(list.isBlocked("ADS.Example.COM"))
        assertTrue(list.isBlocked(" tracker.net. "))
    }

    @Test
    fun `a bare top-level domain is never matched`() {
        assertFalse(HostBlocklist(setOf("net")).isBlocked("anything.net"))
    }

    @Test
    fun `null and blank hosts are not blocked`() {
        assertFalse(list.isBlocked(null))
        assertFalse(list.isBlocked(""))
        assertFalse(list.isBlocked("   "))
        assertFalse(HostBlocklist.EMPTY.isBlocked("ads.example.com"))
    }

    @Test
    fun `a hosts file is parsed`() {
        val text = """
            # Title: sample list
            127.0.0.1 localhost
            127.0.0.1	localhost.localdomain
            ::1 ip6-localhost
            0.0.0.0 0.0.0.0
            0.0.0.0 ads.one.com
            0.0.0.0   Ads.Two.com   # trailing comment
            127.0.0.1 tracker.three.net

            0.0.0.0 broadcasthost
        """.trimIndent()
        val parsed = HostBlocklist.parse(text.lineSequence())
        assertEquals(3, parsed.size)
        assertTrue(parsed.isBlocked("ads.one.com"))
        assertTrue(parsed.isBlocked("ads.two.com"))
        assertTrue(parsed.isBlocked("tracker.three.net"))
        assertFalse(parsed.isBlocked("localhost"))
    }

    @Test
    fun `a plain domain-per-line list is parsed and junk lines are skipped`() {
        val parsed = HostBlocklist.parse(sequenceOf("ads.plain.com", "  ", "# note", "nodots", "1.2.3.4 only-one-field-ip"))
        assertEquals(1, parsed.size)
        assertTrue(parsed.isBlocked("ads.plain.com"))
    }
}
