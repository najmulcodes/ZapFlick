package com.najmulcodes.zapflick.domain.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressDisplayTest {

    @Test
    fun `shows only the site`() {
        assertEquals("reddit.com", AddressDisplay.compact("https://www.reddit.com/r/foo?x=1"))
        assertEquals("m.example.com", AddressDisplay.compact("https://m.example.com/a/b"))
    }

    @Test
    fun `an empty or blank page shows nothing`() {
        assertEquals("", AddressDisplay.compact(""))
        assertEquals("", AddressDisplay.compact("about:blank"))
        assertEquals("", AddressDisplay.compact("   "))
    }

    @Test
    fun `text that is not an address is kept, shortened`() {
        assertEquals("weird", AddressDisplay.compact("weird"))
        assertEquals(60, AddressDisplay.compact("w".repeat(200)).length)
    }
}
