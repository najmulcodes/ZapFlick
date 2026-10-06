package com.najmulcodes.zapflick.domain.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinEntryTest {

    @Test
    fun `digits are added up to four`() {
        var e = PinEntry()
        "12345".forEach { e = e.press(it) }
        assertEquals("1234", e.digits)
        assertTrue(e.isComplete)
    }

    @Test
    fun `only digits are accepted`() {
        val e = PinEntry().press('a').press(' ').press('-').press('7')
        assertEquals("7", e.digits)
        assertFalse(e.isComplete)
    }

    @Test
    fun `backspace removes the last digit and is safe when empty`() {
        assertEquals("12", PinEntry("123").backspace().digits)
        assertEquals("", PinEntry().backspace().digits)
    }

    @Test
    fun `the first entry asks for the PIN again`() {
        assertEquals(
            SetPinOutcome.AskAgain(SetPinStep.Confirm("1234")),
            SetPinFlow.onComplete(SetPinStep.Enter, "1234"),
        )
    }

    @Test
    fun `matching entries save the PIN`() {
        assertEquals(SetPinOutcome.Save("1234"), SetPinFlow.onComplete(SetPinStep.Confirm("1234"), "1234"))
    }

    @Test
    fun `different entries start over`() {
        assertEquals(SetPinOutcome.Mismatch, SetPinFlow.onComplete(SetPinStep.Confirm("1234"), "1235"))
    }
}
