package com.najmulcodes.zapflick.ui.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.najmulcodes.zapflick.domain.security.PinPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the private folder is unlocked right now. It locks by itself when the app has been in
 * the background for more than 30 seconds (the whole app, not one screen: switching between
 * ZapFlick's own screens never locks it).
 */
@Singleton
class PrivateSession @Inject constructor() {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private var backgroundedAt: Long? = null
    private var observing = false

    /** Call once from Application.onCreate. */
    fun start(clock: () -> Long = System::currentTimeMillis) {
        if (observing) return
        observing = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                backgroundedAt = clock()
            }

            override fun onStart(owner: LifecycleOwner) {
                if (PinPolicy.shouldAutoLock(backgroundedAt, clock())) lock()
                backgroundedAt = null
            }
        })
    }

    fun unlock() {
        _unlocked.value = true
    }

    fun lock() {
        _unlocked.value = false
    }
}
