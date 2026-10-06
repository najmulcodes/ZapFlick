package com.najmulcodes.zapflick.data.browser

import android.content.Context
import com.najmulcodes.zapflick.domain.browser.HostBlocklist
import com.najmulcodes.zapflick.domain.browser.hostOf
import com.najmulcodes.zapflick.domain.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether a page request is an ad or tracker. The host list ships inside the app
 * (assets/adblock_hosts.txt) and is read the first time it is needed; it is never downloaded.
 */
@Singleton
class AdBlocker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private val list: HostBlocklist by lazy { load() }

    /** Reads the list ahead of time, off the main thread, so the first page does not wait for it. */
    suspend fun preload() {
        withContext(Dispatchers.Default) { list.size }
    }

    /** Called from WebView's network threads, so it only reads. */
    fun shouldBlock(url: String): Boolean {
        if (!settings.settings.value.blockAds) return false
        return list.isBlocked(hostOf(url))
    }

    private fun load(): HostBlocklist = try {
        context.assets.open(ASSET).bufferedReader().use { reader -> HostBlocklist.parse(reader.lineSequence()) }
    } catch (e: IOException) {
        HostBlocklist.EMPTY
    }

    private companion object {
        const val ASSET = "adblock_hosts.txt"
    }
}
