package com.najmulcodes.zapflick.domain.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CharsetDetectorTest {

    private fun bytes(text: String, charset: java.nio.charset.Charset = Charsets.UTF_8) = text.toByteArray(charset)

    private fun name(data: ByteArray): String = CharsetDetector.detect(data).charset.name()

    @Test
    fun `no marker and no meta tag means UTF-8`() {
        assertEquals("UTF-8", name(bytes("<p>hello</p>")))
    }

    @Test
    fun `empty input is UTF-8 and decodes to an empty string`() {
        assertEquals("UTF-8", name(ByteArray(0)))
        assertEquals("", CharsetDetector.decode(ByteArray(0)))
    }

    @Test
    fun `a UTF-8 byte order mark is detected and removed`() {
        val data = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + bytes("বাংলা")
        val detection = CharsetDetector.detect(data)
        assertEquals("UTF-8", detection.charset.name())
        assertEquals(3, detection.bomLength)
        assertEquals("বাংলা", CharsetDetector.decode(data))
    }

    @Test
    fun `UTF-16 byte order marks are detected in both orders`() {
        val little = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + bytes("hi <b>é</b>", Charsets.UTF_16LE)
        val big = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + bytes("hi <b>é</b>", Charsets.UTF_16BE)
        assertEquals("UTF-16LE", name(little))
        assertEquals("UTF-16BE", name(big))
        assertEquals(2, CharsetDetector.detect(little).bomLength)
        assertEquals("hi <b>é</b>", CharsetDetector.decode(little))
        assertEquals("hi <b>é</b>", CharsetDetector.decode(big))
    }

    @Test
    fun `a byte order mark beats a meta tag that disagrees`() {
        val data = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            bytes("""<meta charset="iso-8859-1"><p>é</p>""")
        assertEquals("UTF-8", name(data))
        assertEquals("""<meta charset="iso-8859-1"><p>é</p>""", CharsetDetector.decode(data))
    }

    @Test
    fun `meta charset is read with and without quotes and in any case`() {
        assertEquals("UTF-8", name(bytes("""<meta charset="utf-8">""")))
        assertEquals("Shift_JIS", name(bytes("""<META CHARSET=shift_jis>""")))
        assertEquals("windows-1251", name(bytes("""<meta charset='windows-1251'>""")))
        assertEquals("windows-1251", name(bytes("""<meta charset = " windows-1251 ">""")))
    }

    @Test
    fun `old style http-equiv content type is read`() {
        val html = """<meta http-equiv="Content-Type" content="text/html; charset=windows-1251">"""
        assertEquals("windows-1251", name(bytes(html)))
    }

    @Test
    fun `latin-1 labels mean Windows-1252 like in browsers`() {
        assertEquals("windows-1252", name(bytes("""<meta charset="iso-8859-1"><p>x</p>""")))
        assertEquals("windows-1252", name(bytes("""<meta charset="latin1">""")))
        assertEquals("windows-1252", name(bytes("""<meta charset="us-ascii">""")))
    }

    @Test
    fun `a Windows-1252 file decodes curly quotes correctly`() {
        val data = bytes("""<meta charset="iso-8859-1">""") + byteArrayOf(0x93.toByte(), 'a'.code.toByte(), 0x94.toByte())
        assertEquals("""<meta charset="iso-8859-1">""" + "\u201Ca\u201D", CharsetDetector.decode(data))
    }

    @Test
    fun `a UTF-16 label in a readable meta tag means UTF-8`() {
        assertEquals("UTF-8", name(bytes("""<meta charset="utf-16">""")))
        assertEquals("UTF-8", name(bytes("""<meta charset="UTF-16LE">""")))
        assertEquals("UTF-8", name(bytes("""<meta charset="utf-32">""")))
    }

    @Test
    fun `an unknown or invalid label falls back to UTF-8`() {
        assertEquals("UTF-8", name(bytes("""<meta charset="no-such-charset">""")))
        assertEquals("UTF-8", name(bytes("""<meta charset="">""")))
        assertEquals("UTF-8", name(bytes("""<meta charset="???">""")))
    }

    @Test
    fun `a meta tag past the scan window is ignored`() {
        val padding = " ".repeat(CharsetDetector.META_SCAN_BYTES + 10)
        assertEquals("UTF-8", name(bytes(padding + """<meta charset="windows-1251">""")))
    }

    @Test
    fun `a meta tag just inside the scan window is used`() {
        val padding = " ".repeat(CharsetDetector.META_SCAN_BYTES - 40)
        assertEquals("windows-1251", name(bytes(padding + """<meta charset="windows-1251">""")))
    }

    @Test
    fun `length limits what is looked at and decoded`() {
        val data = bytes("<p>abc</p>")
        assertEquals("<p>a", CharsetDetector.decode(data, length = 4))
        assertEquals("", CharsetDetector.decode(data, length = 0))
        assertEquals("<p>abc</p>", CharsetDetector.decode(data, length = 999))
    }

    @Test
    fun `charsetForLabel handles blank and known labels`() {
        assertNull(CharsetDetector.charsetForLabel("   "))
        assertNull(CharsetDetector.charsetForLabel("nope-nope"))
        assertEquals("UTF-8", CharsetDetector.charsetForLabel("UTF8")?.name())
        assertEquals("windows-1252", CharsetDetector.charsetForLabel("  CP1252 ")?.name())
    }

    @Test
    fun `invalid UTF-8 bytes become replacement characters instead of failing`() {
        val text = CharsetDetector.decode(byteArrayOf('a'.code.toByte(), 0xFF.toByte(), 'b'.code.toByte()))
        assertEquals("a\uFFFDb", text)
    }
}
