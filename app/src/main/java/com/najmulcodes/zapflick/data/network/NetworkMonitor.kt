package com.najmulcodes.zapflick.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.najmulcodes.zapflick.di.ApplicationScope
import com.najmulcodes.zapflick.domain.network.NetworkKind
import com.najmulcodes.zapflick.domain.network.WifiOnlyPolicy
import com.najmulcodes.zapflick.domain.queue.QueueConfig
import com.najmulcodes.zapflick.domain.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the phone is on Wi-Fi (or any unmetered network), on mobile data, or offline. */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    val kind: StateFlow<NetworkKind> = callbackFlow {
        val manager = connectivity
        if (manager == null) {
            trySend(NetworkKind.NONE)
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(kindOf(capabilities))
            }

            override fun onLost(network: Network) {
                trySend(NetworkKind.NONE)
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, currentKind())

    private fun currentKind(): NetworkKind {
        val manager = connectivity ?: return NetworkKind.NONE
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return NetworkKind.NONE
        return kindOf(capabilities)
    }

    private fun kindOf(capabilities: NetworkCapabilities): NetworkKind = when {
        !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkKind.NONE
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkKind.UNMETERED
        else -> NetworkKind.METERED
    }
}

/** True while "Wi-Fi only" is on and the phone is on mobile data. The queue reads this before starting anything. */
@Singleton
class NetworkGate @Inject constructor(
    settings: SettingsRepository,
    monitor: NetworkMonitor,
    @ApplicationScope scope: CoroutineScope,
) {
    val blocked: StateFlow<Boolean> = combine(settings.settings, monitor.kind) { s, network ->
        WifiOnlyPolicy.isBlocked(s.wifiOnly, network)
    }
        .distinctUntilChanged()
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            WifiOnlyPolicy.isBlocked(settings.settings.value.wifiOnly, monitor.kind.value),
        )
}

/** The queue's limits, read from Settings and the Wi-Fi gate each time the queue looks. */
@Singleton
class SettingsQueueConfig @Inject constructor(
    private val settings: SettingsRepository,
    private val gate: NetworkGate,
) : QueueConfig {
    override val maxConcurrent: Int get() = settings.settings.value.maxConcurrent
    override val canStart: Boolean get() = !gate.blocked.value
}
