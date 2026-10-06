package com.najmulcodes.zapflick.domain.browser

import com.najmulcodes.zapflick.domain.settings.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserHelpersTest {

    @Test
    fun `search words use the chosen engine`() {
        assertEquals("https://duckduckgo.com/?q=cat+videos", BrowserInput.resolve("cat videos", SearchEngine.DUCKDUCKGO))
        assertEquals("https://www.bing.com/search?q=hi", BrowserInput.resolve("hi", SearchEngine.BING))
        assertEquals("https://www.google.com/search?q=hi", BrowserInput.resolve("hi"))
    }

    @Test
    fun `addresses do not depend on the engine`() {
        assertEquals("https://example.com/a", BrowserInput.resolve("example.com/a", SearchEngine.BING))
        assertEquals("http://x.org", BrowserInput.resolve("http://x.org", SearchEngine.BING))
        assertNull(BrowserInput.resolve("   "))
    }

    @Test
    fun `desktop user agent drops the phone parts`() {
        val mobile = "Mozilla/5.0 (Linux; Android 14; V2250) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Chrome/126.0.0.0 Mobile Safari/537.36"
        val desktop = UserAgents.toDesktop(mobile)
        assertTrue("X11; Linux x86_64" in desktop)
        assertFalse("Android" in desktop)
        assertFalse("Mobile" in desktop)
        assertTrue("Chrome/126.0.0.0 Safari/537.36" in desktop)
    }

    @Test
    fun `desktop user agent leaves a desktop agent alone`() {
        val desktop = "Mozilla/5.0 (X11; Linux x86_64) Chrome/126.0.0.0 Safari/537.36"
        assertEquals(desktop, UserAgents.toDesktop(desktop))
    }

    @Test
    fun `the webview marker is removed`() {
        assertEquals("Mozilla (Linux; Android 14) Chrome", UserAgents.withoutWebViewMarker("Mozilla (Linux; Android 14; wv) Chrome").replace("; V2250", ""))
        assertEquals("a b", UserAgents.withoutWebViewMarker("a b"))
    }

    @Test
    fun `error page escapes everything it shows`() {
        val html = ErrorPageHtml.build("T<1>", "M & \"q\"", "Retry", "https://x.com/?a=1&b=\"2\"<script>")
        assertFalse("<script>" in html)
        assertTrue("T&lt;1&gt;" in html)
        assertTrue("M &amp; &quot;q&quot;" in html)
        assertTrue("href=\"https://x.com/?a=1&amp;b=&quot;2&quot;&lt;script&gt;\"" in html)
        assertTrue(">Retry</a>" in html)
    }

    @Test
    fun `escape handles apostrophes`() {
        assertEquals("it&#39;s", ErrorPageHtml.escape("it's"))
    }

    @Test
    fun `favorites are normalised`() {
        assertEquals("Example" to "https://example.com", FavoriteRules.normalize(" Example ", "example.com"))
        assertEquals("example.com" to "https://www.example.com", FavoriteRules.normalize("", "www.example.com"))
        assertEquals("X" to "https://x.com", FavoriteRules.normalize("X", "https://x.com"))
    }

    @Test
    fun `search phrases and blanks are not favorites`() {
        assertNull(FavoriteRules.normalize("t", "cat videos"))
        assertNull(FavoriteRules.normalize("t", ""))
        assertNull(FavoriteRules.normalize("t", "localhost"))
    }

    @Test
    fun `monogram is the first letter or digit in upper case`() {
        assertEquals("F", FavoriteRules.monogram("facebook"))
        assertEquals("7", FavoriteRules.monogram("  7digital"))
        assertEquals("B", FavoriteRules.monogram("--bing"))
        assertEquals("?", FavoriteRules.monogram("  "))
        assertEquals("?", FavoriteRules.monogram("!!!"))
    }

    @Test
    fun `seed has the six default sites`() {
        assertEquals(6, DefaultFavorites.SEED.size)
        assertEquals(6, DefaultFavorites.SEED.map { it.second }.toSet().size)
        DefaultFavorites.SEED.forEach { (_, url) -> assertTrue(url.startsWith("https://")) }
    }

    @Test
    fun `reorder moves one item and ignores bad indices`() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), ListReorder.move(list, 0, 2))
        assertEquals(listOf("a", "d", "b", "c"), ListReorder.move(list, 3, 1))
        assertEquals(list, ListReorder.move(list, 1, 1))
        assertEquals(list, ListReorder.move(list, -1, 2))
        assertEquals(list, ListReorder.move(list, 0, 9))
    }

    @Test
    fun `history records only web pages`() {
        assertTrue(HistoryRules.shouldRecord("https://example.com"))
        assertTrue(HistoryRules.shouldRecord("HTTP://example.com"))
        assertFalse(HistoryRules.shouldRecord("about:blank"))
        assertFalse(HistoryRules.shouldRecord("data:text/html,x"))
        assertFalse(HistoryRules.shouldRecord(""))
        assertFalse(HistoryRules.shouldRecord(null))
        assertEquals(500, HistoryRules.MAX_ENTRIES)
    }

    @Test
    fun `history title falls back to the host and then the address`() {
        assertEquals("Hello", HistoryRules.titleFor("https://a.com", " Hello "))
        assertEquals("a.com", HistoryRules.titleFor("https://a.com/x", ""))
        assertEquals("a.com", HistoryRules.titleFor("https://a.com/x", null))
    }
}
