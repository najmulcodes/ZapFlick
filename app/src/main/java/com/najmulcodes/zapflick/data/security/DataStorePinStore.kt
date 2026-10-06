package com.najmulcodes.zapflick.data.security

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.najmulcodes.zapflick.domain.security.LockoutState
import com.najmulcodes.zapflick.domain.security.PinRecord
import com.najmulcodes.zapflick.domain.security.PinStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(name = "security")

/** The salted PIN hash and the failed-attempt counter. Never the PIN itself. */
@Singleton
class DataStorePinStore @Inject constructor(
    @ApplicationContext context: Context,
) : PinStore {

    private val store = context.securityDataStore

    override suspend fun loadPin(): PinRecord? {
        val prefs = store.data.first()
        val salt = prefs[SALT] ?: return null
        val hash = prefs[HASH] ?: return null
        return try {
            PinRecord(
                salt = Base64.decode(salt, Base64.NO_WRAP),
                hash = Base64.decode(hash, Base64.NO_WRAP),
                iterations = prefs[ITERATIONS] ?: return null,
            )
        } catch (e: IllegalArgumentException) {
            // A damaged value counts as "no PIN", so the person can set a new one.
            null
        }
    }

    override suspend fun savePin(record: PinRecord?) {
        store.edit { prefs ->
            if (record == null) {
                prefs.remove(SALT)
                prefs.remove(HASH)
                prefs.remove(ITERATIONS)
            } else {
                prefs[SALT] = Base64.encodeToString(record.salt, Base64.NO_WRAP)
                prefs[HASH] = Base64.encodeToString(record.hash, Base64.NO_WRAP)
                prefs[ITERATIONS] = record.iterations
            }
        }
    }

    override suspend fun loadLockout(): LockoutState {
        val prefs = store.data.first()
        return LockoutState(prefs[FAILED] ?: 0, prefs[LOCKED_UNTIL] ?: 0L)
    }

    override suspend fun saveLockout(state: LockoutState) {
        store.edit { prefs ->
            prefs[FAILED] = state.failedAttempts
            prefs[LOCKED_UNTIL] = state.lockedUntilMillis
        }
    }

    private companion object {
        val SALT = stringPreferencesKey("pin_salt")
        val HASH = stringPreferencesKey("pin_hash")
        val ITERATIONS = intPreferencesKey("pin_iterations")
        val FAILED = intPreferencesKey("pin_failed_attempts")
        val LOCKED_UNTIL = longPreferencesKey("pin_locked_until")
    }
}
