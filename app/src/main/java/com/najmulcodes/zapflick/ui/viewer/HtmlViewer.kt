package com.najmulcodes.zapflick.ui.viewer

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.domain.viewer.HtmlLinkPolicy
import com.najmulcodes.zapflick.domain.viewer.HtmlViewOptions
import com.najmulcodes.zapflick.domain.viewer.LinkDecision

/** Plain holders the WebView callbacks use; they are not Compose state on purpose. */
private class HtmlLoadState {
    var loadedKey: String? = null
    var clearHistoryPending = false
}

/**
 * Shows an HTML file that came from somewhere else, so it is treated as hostile.
 *
 * The fixed settings below never change. Only the two switches in [options] do: scripts and
 * network access, both off until the person turns them on. The text is loaded without a base
 * address, so nothing next to the file (images, style sheets) is ever read from storage.
 *
 * The WebView stays in the composition while the source view is open (it is only hidden), so
 * switching back does not reload the page.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlViewer(
    html: String,
    options: HtmlViewOptions,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnOpenLink by rememberUpdatedState(onOpenLink)
    var canGoBack by remember { mutableStateOf(false) }
    var rendererGone by remember { mutableStateOf(false) }
    val loadState = remember { HtmlLoadState() }

    val webView = remember {
        WebView(context).apply {
            // Pages are written for a white background whatever the app theme is.
            setBackgroundColor(Color.WHITE)
            applyFixedSafetySettings(settings)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    return when (HtmlLinkPolicy.decide(url)) {
                        LinkDecision.AllowInPage -> false
                        LinkDecision.OpenExternally -> {
                            currentOnOpenLink(url)
                            true
                        }
                        LinkDecision.Block -> true
                    }
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    canGoBack = view.canGoBack()
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    // Reloading after a switch adds a history entry for the same page; drop it.
                    if (loadState.clearHistoryPending) {
                        loadState.clearHistoryPending = false
                        view.clearHistory()
                        canGoBack = false
                    }
                }

                // Without this the whole app dies when the page's renderer process is killed.
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    rendererGone = true
                    return true
                }
            }
        }
    }

    // Declared before the AndroidView so it is disposed after the WebView has been detached.
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { webView.onPause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { webView.onResume() }
    BackHandler(enabled = canGoBack && !options.showSource && !rendererGone) { webView.goBack() }

    if (rendererGone) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.viewer_html_renderer_gone),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }

    val renderKey = "${options.scriptsEnabled}|${options.onlineContentAllowed}|${html.hashCode()}"
    AndroidView(
        factory = { webView },
        modifier = modifier.fillMaxSize(),
        update = { view ->
            view.settings.javaScriptEnabled = options.scriptsEnabled
            view.settings.blockNetworkLoads = !options.onlineContentAllowed
            // An invisible view gets no touches, so the page cannot be used under the source view.
            view.visibility = if (options.showSource) View.INVISIBLE else View.VISIBLE
            if (loadState.loadedKey != renderKey) {
                loadState.loadedKey = renderKey
                loadState.clearHistoryPending = true
                view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
    )
}

/** Settings that stay the same whatever the person switches on. */
@Suppress("DEPRECATION")
private fun applyFixedSafetySettings(settings: WebSettings) {
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.allowFileAccessFromFileURLs = false
    settings.allowUniversalAccessFromFileURLs = false
    settings.domStorageEnabled = false
    settings.databaseEnabled = false
    settings.setGeolocationEnabled(false)
    settings.setSupportMultipleWindows(false)
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.mediaPlaybackRequiresUserGesture = true
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    settings.cacheMode = WebSettings.LOAD_NO_CACHE

    // Pinch to zoom and fit-to-width, like a normal browser page.
    settings.setSupportZoom(true)
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
}
