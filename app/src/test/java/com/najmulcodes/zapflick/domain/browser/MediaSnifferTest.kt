package com.najmulcodes.zapflick.domain.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSnifferTest {

    @Test
    fun `recognises common video files`() {
        assertEquals(MediaKind.VIDEO, MediaSniffer.classify("https://cdn.example.com/a/clip.mp4"))
        assertEquals(MediaKind.VIDEO, MediaSniffer.classify("https://cdn.example.com/clip.WEBM"))
    }

    @Test
    fun `ignores the query string when reading the extension`() {
        assertEquals(MediaKind.VIDEO, MediaSniffer.classify("https://cdn.example.com/clip.mp4?token=abc&e=1.5"))
        assertNull(MediaSniffer.classify("https://example.com/page?file=clip.mp4"))
    }

    @Test
    fun `recognises streaming playlists and audio`() {
        assertEquals(MediaKind.STREAM, MediaSniffer.classify("https://x.example.com/live/master.m3u8"))
        assertEquals(MediaKind.STREAM, MediaSniffer.classify("https://x.example.com/manifest.mpd"))
        assertEquals(MediaKind.AUDIO, MediaSniffer.classify("https://x.example.com/song.mp3"))
    }

    @Test
    fun `ignores stream segments, pages and non-http urls`() {
        assertNull(MediaSniffer.classify("https://x.example.com/seg-12.ts"))
        assertNull(MediaSniffer.classify("https://x.example.com/chunk.m4s"))
        assertNull(MediaSniffer.classify("https://x.example.com/index.html"))
        assertNull(MediaSniffer.classify("blob:https://x.example.com/1234"))
        assertNull(MediaSniffer.classify("data:video/mp4;base64,AAAA"))
    }

    @Test
    fun `ignores youtube chunk requests`() {
        assertNull(
            MediaSniffer.classify("https://rr1---sn-abc.googlevideo.com/videoplayback?expire=1&mime=video%2Fmp4"),
        )
    }

    @Test
    fun `label is the decoded file name`() {
        assertEquals("my clip.mp4", MediaSniffer.label("https://cdn.example.com/a/my%20clip.mp4?x=1"))
    }

    @Test
    fun `label falls back to the host`() {
        assertEquals("cdn.example.com", MediaSniffer.label("https://cdn.example.com/"))
    }

    @Test
    fun `label survives malformed escapes`() {
        assertEquals("bad%zz.mp4", MediaSniffer.label("https://cdn.example.com/bad%zz.mp4"))
    }
}
