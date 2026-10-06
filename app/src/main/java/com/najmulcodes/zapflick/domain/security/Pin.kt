package com.najmulcodes.zapflick.domain.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinPolicy {
    const val PIN_LENGTH = 4
    const val FREE_ATTEMPTS = 2
    const val AUTO_LOCK_GRACE_MS = 30_000L

    private val LOCKOUT_STEPS_MS = longArrayOf(30_000L, 60_000L, 5 * 60_000L, 15 * 60_000L)

    fun isValid(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    /**
     * How long to refuse further tries after [failedAttempts] wrong PINs in a row: nothing for the
     * first two, then 30 seconds, 1 minute, 5 minutes, and 15 minutes from the sixth on.
     */
    fun lockoutMillis(failedAttempts: Int): Long {
        if (failedAttempts <= FREE_ATTEMPTS) return 0L
        val step = (failedAttempts - FREE_ATTEMPTS - 1).coerceAtMost(LOCKOUT_STEPS_MS.lastIndex)
        return LOCKOUT_STEPS_MS[step]
    }

    /** True once the app has been in the background longer than the grace period. */
    fun shouldAutoLock(backgroundedAtMillis: Long?, nowMillis: Long): Boolean =
        backgroundedAtMillis != null && nowMillis - backgroundedAtMillis > AUTO_LOCK_GRACE_MS
}

/**
 * PBKDF2 with a random salt. This protects the PIN if the stored value leaks; it is a privacy
 * gate for the private folder, not encryption of the files.
 */
object PinHasher {
    const val DEFAULT_ITERATIONS = 120_000
    const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun newSalt(random: SecureRandom = SecureRandom()): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    fun hash(pin: String, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Constant-time comparison, so the time taken does not reveal how much of the PIN was right. */
    fun matches(pin: String, salt: ByteArray, expected: ByteArray, iterations: Int = DEFAULT_ITERATIONS): Boolean =
        MessageDigest.isEqual(hash(pin, salt, iterations), expected)
}

class PinRecord(val salt: ByteArray, val hash: ByteArray, val iterations: Int)

data class LockoutState(val failedAttempts: Int = 0, val lockedUntilMillis: Long = 0L)

/** Where the PIN hash and the failed-attempt counter are kept, so a lockout survives restarting the app. */
interface PinStore {
    suspend fun loadPin(): PinRecord?
    suspend fun savePin(record: PinRecord?)
    suspend fun loadLockout(): LockoutState
    suspend fun saveLockout(state: LockoutState)
}

sealed interface PinResult {
    data object Correct : PinResult
    data object NotSet : PinResult

    /** [attemptsBeforeLockout] is how many more wrong tries are allowed before the next lockout. */
    data class Wrong(val failedAttempts: Int, val attemptsBeforeLockout: Int, val lockedForMillis: Long) : PinResult

    data class LockedOut(val remainingMillis: Long) : PinResult
}

class PinManager(
    private val store: PinStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val iterations: Int = PinHasher.DEFAULT_ITERATIONS,
) {
    suspend fun hasPin(): Boolean = store.loadPin() != null

    /** Saves a new PIN and clears any lockout. Returns false for a PIN that is not exactly four digits. */
    suspend fun setPin(pin: String): Boolean {
        if (!PinPolicy.isValid(pin)) return false
        val salt = PinHasher.newSalt()
        store.savePin(PinRecord(salt, PinHasher.hash(pin, salt, iterations), iterations))
        store.saveLockout(LockoutState())
        return true
    }

    suspend fun remainingLockMillis(): Long {
        val lockout = store.loadLockout()
        return (lockout.lockedUntilMillis - clock()).coerceAtLeast(0L)
    }

    suspend fun verify(pin: String): PinResult {
        val record = store.loadPin() ?: return PinResult.NotSet
        val remaining = remainingLockMillis()
        if (remaining > 0L) return PinResult.LockedOut(remaining)

        if (PinPolicy.isValid(pin) && PinHasher.matches(pin, record.salt, record.hash, record.iterations)) {
            store.saveLockout(LockoutState())
            return PinResult.Correct
        }

        val failed = store.loadLockout().failedAttempts + 1
        val lockFor = PinPolicy.lockoutMillis(failed)
        store.saveLockout(LockoutState(failed, if (lockFor > 0L) clock() + lockFor else 0L))
        val untilLock = if (lockFor > 0L) 0 else (PinPolicy.FREE_ATTEMPTS + 1 - failed).coerceAtLeast(0)
        return PinResult.Wrong(failed, untilLock, lockFor)
    }

    suspend fun clearPin() {
        store.savePin(null)
        store.saveLockout(LockoutState())
    }
}
