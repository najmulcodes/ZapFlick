package com.najmulcodes.zapflick.domain.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RequestSessionsTest {
    private val sessions = RequestSessions()
    private val site = RequestSession(userAgent = "UA", referer = "https://www.example.com/", cookies = "a=1")
    private val file = RequestSession(userAgent = "UA", referer = "https://page.example.org/", cookies = null)

    @Test
    fun `an exact url wins over a site session`() {
        sessions.rememberSite("https://www.example.com/watch", site)
        sessions.rememberUrl("https://www.example.com/v.mp4", file)
        assertEquals(file, sessions.forUrl("https://www.example.com/v.mp4"))
    }

    @Test
    fun `a site session covers its subdomains and mobile variants`() {
        sessions.rememberSite("https://www.example.com/watch?v=1", site)
        assertEquals(site, sessions.forUrl("https://m.example.com/x"))
        assertEquals(site, sessions.forUrl("https://video.cdn.example.com/a.mp4"))
    }

    @Test
    fun `unrelated hosts get nothing`() {
        sessions.rememberSite("https://www.example.com/watch", site)
        assertNull(sessions.forUrl("https://example.org/a"))
        assertNull(sessions.forUrl("https://notexample.com/a"))
    }

    @Test
    fun `a bare top level domain never matches`() {
        sessions.rememberSite("https://www.example.com/watch", site)
        assertNull(sessions.forUrl("https://com/a"))
    }
}
