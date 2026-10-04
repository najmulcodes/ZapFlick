package com.najmulcodes.zapflick.domain.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlViewOptionsTest {

    @Test
    fun `everything is off by default`() {
        val options = HtmlViewOptions()
        assertFalse(options.scriptsEnabled)
        assertFalse(options.onlineContentAllowed)
        assertFalse(options.showSource)
    }

    @Test
    fun `size limit is ten megabytes and only known oversize files fail early`() {
        assertEquals(10L * 1024 * 1024, HtmlLimits.MAX_BYTES)
        assertFalse(HtmlLimits.exceeds(null))
        assertFalse(HtmlLimits.exceeds(0))
        assertFalse(HtmlLimits.exceeds(HtmlLimits.MAX_BYTES))
        assertTrue(HtmlLimits.exceeds(HtmlLimits.MAX_BYTES + 1))
    }
}
