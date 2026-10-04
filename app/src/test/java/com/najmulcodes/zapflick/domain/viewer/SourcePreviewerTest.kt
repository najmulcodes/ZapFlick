package com.najmulcodes.zapflick.domain.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcePreviewerTest {

    @Test
    fun `short text is kept whole`() {
        val preview = SourcePreviewer.limit("<p>hi</p>", maxChars = 100)
        assertEquals("<p>hi</p>", preview.text)
        assertFalse(preview.truncated)
    }

    @Test
    fun `text exactly at the limit is not truncated`() {
        val preview = SourcePreviewer.limit("abcde", maxChars = 5)
        assertEquals("abcde", preview.text)
        assertFalse(preview.truncated)
    }

    @Test
    fun `long text is cut at the limit and marked`() {
        val preview = SourcePreviewer.limit("abcdefgh", maxChars = 5)
        assertEquals("abcde", preview.text)
        assertTrue(preview.truncated)
    }

    @Test
    fun `a surrogate pair is never split`() {
        val text = "ab" + "\uD83D\uDE00" + "cd" // two chars, an emoji (two UTF-16 units), two chars
        val preview = SourcePreviewer.limit(text, maxChars = 3)
        assertEquals("ab", preview.text)
        assertTrue(preview.truncated)
    }

    @Test
    fun `a limit of zero keeps nothing`() {
        assertEquals(SourcePreview("", truncated = true), SourcePreviewer.limit("abc", maxChars = 0))
        assertEquals(SourcePreview("", truncated = false), SourcePreviewer.limit("", maxChars = 0))
    }

    @Test
    fun `lines are split on every line break style`() {
        assertEquals(listOf("a", "b", "c", "d"), SourcePreviewer.lines("a\nb\r\nc\rd"))
    }

    @Test
    fun `empty text has no lines and a trailing break keeps an empty last line`() {
        assertEquals(emptyList<String>(), SourcePreviewer.lines(""))
        assertEquals(listOf("a", ""), SourcePreviewer.lines("a\n"))
    }

    @Test
    fun `a very long line is cut into pieces`() {
        val lines = SourcePreviewer.lines("x".repeat(25), maxLineChars = 10)
        assertEquals(listOf(10, 10, 5), lines.map { it.length })
        assertEquals("x".repeat(25), lines.joinToString(""))
    }

    @Test
    fun `cutting a long line does not split a surrogate pair`() {
        val line = "a" + "\uD83D\uDE00" + "b"
        val pieces = SourcePreviewer.lines(line, maxLineChars = 2)
        assertEquals(line, pieces.joinToString(""))
        pieces.forEach { assertFalse(Character.isHighSurrogate(it.last())) }
    }

    @Test
    fun `a nonsense line length still makes progress`() {
        assertEquals(listOf("a", "b"), SourcePreviewer.lines("ab", maxLineChars = 0))
    }
}
