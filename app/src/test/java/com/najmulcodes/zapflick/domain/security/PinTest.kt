package com.najmulcodes.zapflick.domain.security

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinTest {

    private class FakeStore : PinStore {
        var pin: PinRecord? = null
        var lockout = LockoutState()
        override suspend fun loadPin() = pin
        override suspend fun savePin(record: PinRecord?) { pin = record }
        override suspend fun loadLockout() = lockout
        override suspend fun saveLockout(state: LockoutState) { lockout = state }
    }

    private var now = 1_000_000L
    private val store = FakeStore()
    private val manager = PinManager(store, clock = { now }, iterations = 1_000)

    @Test
    fun `only four digits are a valid PIN`() {
        assertTrue(PinPolicy.isValid("0123"))
        assertFalse(PinPolicy.isValid("123"))
        assertFalse(PinPolicy.isValid("12345"))
        assertFalse(PinPolicy.isValid("12a4"))
        assertFalse(PinPolicy.isValid(""))
        assertFalse(PinPolicy.isValid("١٢٣٤")) // other scripts' digits are not accepted
    }

    @Test
    fun `lockout grows with failed attempts`() {
        assertEquals(0L, PinPolicy.lockoutMillis(0))
        assertEquals(0L, PinPolicy.lockoutMillis(2))
        assertEquals(30_000L, PinPolicy.lockoutMillis(3))
        assertEquals(60_000L, PinPolicy.lockoutMillis(4))
        assertEquals(300_000L, PinPolicy.lockoutMillis(5))
        assertEquals(900_000L, PinPolicy.lockoutMillis(6))
        assertEquals(900_000L, PinPolicy.lockoutMillis(50))
    }

    @Test
    fun `auto lock waits thirty seconds`() {
        assertFalse(PinPolicy.shouldAutoLock(null, 100_000))
        assertFalse(PinPolicy.shouldAutoLock(100_000, 100_000 + 30_000))
        assertTrue(PinPolicy.shouldAutoLock(100_000, 100_000 + 30_001))
    }

    @Test
    fun `hash is salted, repeatable and depends on the PIN`() {
        val salt = PinHasher.newSalt()
        assertEquals(PinHasher.SALT_BYTES, salt.size)
        assertArrayEquals(PinHasher.hash("1234", salt, 1_000), PinHasher.hash("1234", salt, 1_000))
        assertFalse(PinHasher.hash("1234", salt, 1_000).contentEquals(PinHasher.hash("1235", salt, 1_000)))
        assertFalse(PinHasher.hash("1234", salt, 1_000).contentEquals(PinHasher.hash("1234", PinHasher.newSalt(), 1_000)))
        assertNotEquals(salt.toList(), PinHasher.newSalt().toList())
        assertEquals(32, PinHasher.hash("1234", salt, 1_000).size)
    }

    @Test
    fun `matches compares in full`() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("4321", salt, 1_000)
        assertTrue(PinHasher.matches("4321", salt, hash, 1_000))
        assertFalse(PinHasher.matches("4320", salt, hash, 1_000))
    }

    @Test
    fun `the PIN is stored hashed, never as typed`() = runBlocking {
        assertTrue(manager.setPin("2468"))
        val record = store.pin!!
        assertFalse(record.hash.contentEquals("2468".toByteArray()))
        assertTrue(manager.hasPin())
    }

    @Test
    fun `an invalid PIN is not saved`() = runBlocking {
        assertFalse(manager.setPin("12"))
        assertFalse(manager.hasPin())
    }

    @Test
    fun `verify reports not set when there is no PIN`() = runBlocking {
        assertEquals(PinResult.NotSet, manager.verify("1234"))
    }

    @Test
    fun `the right PIN works and clears earlier failures`() = runBlocking {
        manager.setPin("1111")
        manager.verify("0000")
        assertEquals(1, store.lockout.failedAttempts)
        assertEquals(PinResult.Correct, manager.verify("1111"))
        assertEquals(LockoutState(), store.lockout)
    }

    @Test
    fun `two wrong tries are free, the third locks for thirty seconds`() = runBlocking {
        manager.setPin("1111")
        val first = manager.verify("0000") as PinResult.Wrong
        assertEquals(2, first.attemptsBeforeLockout)
        val second = manager.verify("0000") as PinResult.Wrong
        assertEquals(1, second.attemptsBeforeLockout)
        val third = manager.verify("0000") as PinResult.Wrong
        assertEquals(30_000L, third.lockedForMillis)
        assertEquals(now + 30_000L, store.lockout.lockedUntilMillis)
    }

    @Test
    fun `while locked even the right PIN is refused`() = runBlocking {
        manager.setPin("1111")
        repeat(3) { manager.verify("0000") }
        now += 10_000
        val refused = manager.verify("1111")
        assertTrue(refused is PinResult.LockedOut)
        assertEquals(20_000L, (refused as PinResult.LockedOut).remainingMillis)
    }

    @Test
    fun `after the lockout the right PIN works again`() = runBlocking {
        manager.setPin("1111")
        repeat(3) { manager.verify("0000") }
        now += 30_001
        assertEquals(PinResult.Correct, manager.verify("1111"))
    }

    @Test
    fun `a wrong try after a lockout locks for longer`() = runBlocking {
        manager.setPin("1111")
        repeat(3) { manager.verify("0000") }
        now += 30_001
        val fourth = manager.verify("0000") as PinResult.Wrong
        assertEquals(60_000L, fourth.lockedForMillis)
    }

    @Test
    fun `the lockout lives in the store, so restarting the app does not reset it`() = runBlocking {
        manager.setPin("1111")
        repeat(3) { manager.verify("0000") }
        val restarted = PinManager(store, clock = { now }, iterations = 1_000)
        assertTrue(restarted.verify("1111") is PinResult.LockedOut)
    }

    @Test
    fun `changing the PIN clears the lockout`() = runBlocking {
        manager.setPin("1111")
        repeat(3) { manager.verify("0000") }
        manager.setPin("2222")
        assertEquals(0L, manager.remainingLockMillis())
        assertEquals(PinResult.Correct, manager.verify("2222"))
    }

    @Test
    fun `a malformed attempt counts as wrong without hashing garbage`() = runBlocking {
        manager.setPin("1111")
        assertTrue(manager.verify("abc") is PinResult.Wrong)
    }

    @Test
    fun `clearing the PIN removes it`() = runBlocking {
        manager.setPin("1111")
        manager.clearPin()
        assertFalse(manager.hasPin())
    }
}
