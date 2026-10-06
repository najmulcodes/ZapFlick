package com.najmulcodes.zapflick.domain.settings

import com.najmulcodes.zapflick.domain.model.FormatSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `fresh install defaults`() {
        val s = AppSettings()
        assertNull(s.customFolderUri)
        assertFalse(s.wifiOnly)
        assertEquals(2, s.maxConcurrent)
        assertTrue(s.blockAds)
        assertTrue(s.syncToGallery)
        assertFalse(s.dynamicColor)
        assertEquals(ThemeMode.DARK, s.theme)
        assertEquals(SearchEngine.GOOGLE, s.searchEngine)
        assertEquals(YtDlpChannel.STABLE, s.ytDlpChannel)
        assertTrue(s.secureScreens)
        assertFalse(s.biometricUnlock)
    }

    @Test
    fun `concurrent downloads stay between one and four`() {
        assertEquals(1, AppSettings(maxConcurrent = 0).sanitized().maxConcurrent)
        assertEquals(1, AppSettings(maxConcurrent = -7).sanitized().maxConcurrent)
        assertEquals(3, AppSettings(maxConcurrent = 3).sanitized().maxConcurrent)
        assertEquals(4, AppSettings(maxConcurrent = 99).sanitized().maxConcurrent)
    }

    @Test
    fun `default quality asks unless a quality is chosen`() {
        assertNull(AppSettings().defaultSelection())
        assertEquals(FormatSelection.Best, AppSettings(defaultQuality = "best").defaultSelection())
        assertEquals(FormatSelection.Resolution(720), AppSettings(defaultQuality = "res:720").defaultSelection())
        assertEquals(FormatSelection.Best, AppSettings(defaultQuality = "garbage").defaultSelection())
    }

    @Test
    fun `stored names that no longer exist fall back to the default`() {
        assertEquals(ThemeMode.LIGHT, enumOrDefault("LIGHT", ThemeMode.DARK))
        assertEquals(ThemeMode.DARK, enumOrDefault("SEPIA", ThemeMode.DARK))
        assertEquals(ThemeMode.DARK, enumOrDefault(null, ThemeMode.DARK))
        assertEquals(SearchEngine.BING, enumOrDefault("BING", SearchEngine.GOOGLE))
    }

    @Test
    fun `every file name style is a complete yt-dlp template that keeps the extension`() {
        FilenameStyle.entries.forEach { style ->
            assertTrue(style.name, style.template.endsWith(".%(ext)s"))
            assertTrue(style.name, "%(title)" in style.template)
            assertFalse(style.name, '/' in style.template)
        }
    }

    @Test
    fun `search engines have distinct query and home addresses`() {
        assertEquals(3, SearchEngine.entries.map { it.queryUrl }.toSet().size)
        SearchEngine.entries.forEach {
            assertTrue(it.queryUrl.startsWith("https://"))
            assertTrue(it.queryUrl.endsWith("="))
            assertTrue(it.homeUrl.startsWith("https://"))
        }
    }
}
