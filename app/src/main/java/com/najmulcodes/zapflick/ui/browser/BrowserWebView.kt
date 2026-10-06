package com.najmulcodes.zapflick.ui.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.view.View
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.najmulcodes.zapflick.domain.browser.UserAgents
import java.io.ByteArrayInputStream

private val ALLOWED_SCHEMES = setOf("http", "https", "about", "data", "blob")

/** Everything the WebView reports, as plain callbacks, so the browser screen owns all the state. */
class BrowserCallbacks(
    val onPageStarted: (String) -> Unit,
    val onPageFinished: (url: String, title: String?) -> Unit,
    val onUrlChanged: (String) -> Unit,
    val onTitle: (String) -> Unit,
    val onProgress: (Int) -> Unit,
    val onNavigationState: (canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    val onResource: (url: String, referer: String?) -> Unit,
    val onDirectDownload: (String) -> Unit,
    /** Runs on a network thread: must only read. */
    val shouldBlock: (String) -> Boolean,
    val onBlocked: () -> Unit,
    val onShowCustomView: (View, WebChromeClient.CustomViewCallback) -> Unit,
    val onHideCustomView: () -> Unit,
    /** The page shown when a site cannot be loaded. */
    val errorPage: (failingUrl: String) -> String,
)

private val EMPTY_BODY = ByteArray(0)

/** An answer with nothing in it: the ad's request ends at once and the page carries on. */
private fun blockedResponse() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(EMPTY_BODY))

/** The phone browser user agent without the WebView marker, which makes Google and others refuse logins. */
fun mobileUserAgent(webView: WebView): String = UserAgents.withoutWebViewMarker(webView.settings.userAgentString)

@SuppressLint("SetJavaScriptEnabled")
fun createBrowserWebView(context: Context, callbacks: BrowserCallbacks): WebView = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.mediaPlaybackRequiresUserGesture = true
    // Needed for onCreateWindow; pop-ups are then refused unless the person tapped something.
    settings.setSupportMultipleWindows(true)
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.setGeolocationEnabled(false)
    settings.userAgentString = UserAgents.withoutWebViewMarker(settings.userAgentString)
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

    webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            if (url != null) callbacks.onPageStarted(url)
            callbacks.onNavigationState(view.canGoBack(), view.canGoForward())
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (url != null) callbacks.onPageFinished(url, view.title)
            callbacks.onNavigationState(view.canGoBack(), view.canGoForward())
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            if (url != null) callbacks.onUrlChanged(url)
            callbacks.onNavigationState(view.canGoBack(), view.canGoForward())
        }

        // Pages try to bounce into other apps with intent:// and market:// links; keep them out.
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url?.scheme?.lowercase() ?: return true
            return scheme !in ALLOWED_SCHEMES
        }

        // Runs on a WebView network thread: it must not touch the WebView or Compose state.
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url.toString()
            if (callbacks.shouldBlock(url)) {
                callbacks.onBlocked()
                return blockedResponse()
            }
            callbacks.onResource(url, request.requestHeaders?.get("Referer"))
            return null
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            val failing = request.url.toString()
            view.loadDataWithBaseURL(failing, callbacks.errorPage(failing), "text/html", "UTF-8", failing)
        }

        // A bad certificate is never accepted silently.
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
        }

        // Without this the whole app dies when the page's renderer process is killed.
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean = true
    }

    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            callbacks.onProgress(newProgress)
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (title != null) callbacks.onTitle(title)
        }

        // Camera, microphone and the like are never handed to a web page.
        override fun onPermissionRequest(request: PermissionRequest) {
            request.deny()
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            callbacks.onShowCustomView(view, callback)
        }

        override fun onHideCustomView() {
            callbacks.onHideCustomView()
        }

        /**
         * A pop-up opened by a tap loads in the same tab; one the page opened by itself is refused.
         * The new window needs a WebView to receive the address; it is thrown away right after.
         */
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (!isUserGesture) return false
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            val catcher = WebView(view.context)
            catcher.webViewClient = object : WebViewClient() {
                private var done = false

                private fun forward(target: String?) {
                    if (done) return
                    done = true
                    if (target != null && target != "about:blank") view.loadUrl(target)
                    catcher.post { catcher.destroy() }
                }

                override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                    forward(request.url.toString())
                    return true
                }

                override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) {
                    v.stopLoading()
                    forward(url)
                }
            }
            transport.webView = catcher
            resultMsg.sendToTarget()
            return true
        }
    }
    setDownloadListener { url, _, _, _, _ -> callbacks.onDirectDownload(url) }
}
