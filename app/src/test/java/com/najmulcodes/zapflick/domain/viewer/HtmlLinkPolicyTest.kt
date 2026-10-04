package com.najmulcodes.zapflick.domain.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlLinkPolicyTest {

    private fun decide(url: String?) = HtmlLinkPolicy.decide(url)

    @Test
    fun `web links leave the viewer`() {
        assertEquals(LinkDecision.OpenExternally, decide("https://example.com/a?b=1"))
        assertEquals(LinkDecision.OpenExternally, decide("http://example.com"))
        assertEquals(LinkDecision.OpenExternally, decide("HTTPS://EXAMPLE.COM"))
        assertEquals(LinkDecision.OpenExternally, decide("  https://example.com  "))
    }

    @Test
    fun `jumps within the page stay in the page`() {
        assertEquals(LinkDecision.AllowInPage, decide("about:blank"))
        assertEquals(LinkDecision.AllowInPage, decide("about:blank#section-2"))
        assertEquals(LinkDecision.AllowInPage, decide("ABOUT:BLANK#Top"))
        assertEquals(LinkDecision.AllowInPage, decide("about:blank?x=1"))
    }

    @Test
    fun `every other scheme is blocked`() {
        listOf(
            "javascript:alert(1)",
            "file:///sdcard/Download/a.html",
            "content://com.example/doc/1",
            "intent://scan/#Intent;scheme=zxing;end",
            "mailto:someone@example.com",
            "tel:+8801000000000",
            "sms:123",
            "data:text/html,<p>hi</p>",
            "market://details?id=x",
            "ftp://example.com/file",
            "about:config",
            "about:blankish",
            "view-source:https://example.com",
        ).forEach { assertEquals(it, LinkDecision.Block, decide(it)) }
    }

    @Test
    fun `tricks with whitespace or missing schemes are blocked`() {
        assertEquals(LinkDecision.Block, decide("ht\ntp://example.com"))
        assertEquals(LinkDecision.Block, decide("java\tscript:alert(1)"))
        assertEquals(LinkDecision.Block, decide("//example.com"))
        assertEquals(LinkDecision.Block, decide("example.com"))
    }

    @Test
    fun `null and blank are blocked`() {
        assertEquals(LinkDecision.Block, decide(null))
        assertEquals(LinkDecision.Block, decide(""))
        assertEquals(LinkDecision.Block, decide("   "))
    }
}
