package com.najmulcodes.zapflick.domain.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiOnlyPolicyTest {

    @Test
    fun `only mobile data is blocked and only when the setting is on`() {
        assertTrue(WifiOnlyPolicy.isBlocked(true, NetworkKind.METERED))
        assertFalse(WifiOnlyPolicy.isBlocked(true, NetworkKind.UNMETERED))
        assertFalse(WifiOnlyPolicy.isBlocked(true, NetworkKind.NONE))
        assertFalse(WifiOnlyPolicy.isBlocked(false, NetworkKind.METERED))
        assertFalse(WifiOnlyPolicy.isBlocked(false, NetworkKind.UNMETERED))
    }

    @Test
    fun `running downloads are paused when the gate closes and resumed when it opens`() {
        assertEquals(GateAction.PAUSE_RUNNING, WifiOnlyPolicy.actionFor(wasBlocked = false, nowBlocked = true))
        assertEquals(GateAction.RESUME_PAUSED, WifiOnlyPolicy.actionFor(wasBlocked = true, nowBlocked = false))
    }

    @Test
    fun `nothing happens when the gate state did not change`() {
        assertEquals(GateAction.NONE, WifiOnlyPolicy.actionFor(false, false))
        assertEquals(GateAction.NONE, WifiOnlyPolicy.actionFor(true, true))
    }
}
