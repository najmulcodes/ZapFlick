package com.najmulcodes.zapflick.data.browser

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** The "Clear ..." actions in Settings. WebView objects may only be touched on the main thread. */
@Singleton
class BrowserData @Inject constructor(
    @ApplicationContext private val context: Context,
    private val history: HistoryRepository,
) {
    suspend fun clearCache() = withContext(Dispatchers.Main) {
        val webView = WebView(context)
        try {
            webView.clearCache(true)
            WebStorage.getInstance().deleteAllData()
        } finally {
            webView.destroy()
        }
    }

    suspend fun clearCookies() = withContext(Dispatchers.Main) {
        val cookies = CookieManager.getInstance()
        suspendCancellableCoroutine { continuation ->
            cookies.removeAllCookies { continuation.resume(Unit) }
        }
        cookies.flush()
    }

    suspend fun clearHistory() = history.clear()
}
