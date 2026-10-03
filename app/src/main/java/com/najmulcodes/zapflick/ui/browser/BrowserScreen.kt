package com.najmulcodes.zapflick.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.domain.browser.BrowserInput
import com.najmulcodes.zapflick.domain.browser.RequestSession
import com.najmulcodes.zapflick.ui.components.QualitySheet

private val ALLOWED_SCHEMES = setOf("http", "https", "about", "data", "blob")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onBack: () -> Unit,
    onOpenDownloads: () -> Unit,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val pageUrl by viewModel.pageUrl.collectAsStateWithLifecycle()
    val detected by viewModel.detected.collectAsStateWithLifecycle()
    val panel by viewModel.panel.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()

    var progress by remember { mutableIntStateOf(100) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var addressFocused by remember { mutableStateOf(false) }

    val webView = remember {
        createWebView(
            context = context,
            viewModel = viewModel,
            onProgress = { progress = it },
            onNavigationState = { back, forward ->
                canGoBack = back
                canGoForward = forward
            },
        ).also { it.loadUrl(viewModel.pageUrl.value.ifBlank { BrowserInput.HOME_URL }) }
    }

    // Declared before the Scaffold so it is disposed after the WebView has been detached from the screen.
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { webView.onPause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { webView.onResume() }
    BackHandler(enabled = canGoBack) { webView.goBack() }

    LaunchedEffect(pageUrl, addressFocused) {
        if (!addressFocused) address = pageUrl
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is BrowserEvent.Queued -> context.getString(R.string.added_to_queue, event.title)
                BrowserEvent.QueueFailed -> context.getString(R.string.browser_queue_failed)
            }
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = context.getString(R.string.action_view_downloads),
            )
            if (result == SnackbarResult.ActionPerformed) onOpenDownloads()
        }
    }

    val startPrepare: (url: String, isPage: Boolean, referer: String?) -> Unit = { url, isPage, referer ->
        val session = RequestSession(
            userAgent = webView.settings.userAgentString,
            referer = referer,
            cookies = CookieManager.getInstance().getCookie(url),
        )
        viewModel.prepare(url, isPage, session)
    }

    Scaffold(
        topBar = {
            BrowserTopBar(
                address = address,
                loading = progress < 100,
                progress = progress,
                onAddressChange = { address = it },
                onAddressFocusChange = { addressFocused = it },
                onGo = {
                    BrowserInput.resolve(address)?.let {
                        webView.loadUrl(it)
                        focusManager.clearFocus()
                    }
                },
                onReload = { webView.reload() },
                onClose = onBack,
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    IconButton(onClick = { webView.goBack() }, enabled = canGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.browser_back))
                    }
                    IconButton(onClick = { webView.goForward() }, enabled = canGoForward) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.browser_forward))
                    }
                    IconButton(onClick = { webView.loadUrl(BrowserInput.HOME_URL) }) {
                        Icon(Icons.Filled.Home, stringResource(R.string.browser_home))
                    }
                    TextButton(onClick = onOpenDownloads) {
                        Text(
                            if (activeDownloads > 0) {
                                stringResource(R.string.action_downloads_count, activeDownloads)
                            } else {
                                stringResource(R.string.action_downloads)
                            },
                        )
                    }
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(onClick = viewModel::onDownloadButton) {
                        Text(
                            if (detected.isEmpty()) {
                                stringResource(R.string.action_download)
                            } else {
                                stringResource(R.string.browser_download_count, detected.size)
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        AndroidView(
            factory = { webView },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        )
    }

    when (val current = panel) {
        DownloadPanel.Hidden -> Unit
        DownloadPanel.Targets -> DownloadTargetsSheet(
            pageUrl = pageUrl,
            detected = detected,
            onPickPage = { startPrepare(pageUrl, true, null) },
            onPickMedia = { media -> startPrepare(media.url, false, media.referer) },
            onDismiss = viewModel::onPanelDismiss,
        )
        DownloadPanel.Preparing -> PreparingSheet(onCancel = viewModel::onPanelDismiss)
        is DownloadPanel.Quality -> QualitySheet(
            formats = current.formats,
            onSelect = viewModel::onQualitySelected,
            onDismiss = viewModel::onPanelDismiss,
        )
        is DownloadPanel.Failed -> LookupFailedSheet(
            error = current.error,
            onRetry = viewModel::retry,
            onDismiss = viewModel::onPanelDismiss,
        )
    }
}

@Composable
private fun BrowserTopBar(
    address: String,
    loading: Boolean,
    progress: Int,
    onAddressChange: (String) -> Unit,
    onAddressFocusChange: (Boolean) -> Unit,
    onGo: () -> Unit,
    onReload: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, stringResource(R.string.browser_close))
                }
                OutlinedTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { onAddressFocusChange(it.isFocused) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    placeholder = { Text(stringResource(R.string.browser_address_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onGo() }),
                )
                IconButton(onClick = onReload) {
                    Icon(Icons.Filled.Refresh, stringResource(R.string.browser_reload))
                }
            }
            if (loading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: android.content.Context,
    viewModel: BrowserViewModel,
    onProgress: (Int) -> Unit,
    onNavigationState: (canGoBack: Boolean, canGoForward: Boolean) -> Unit,
): WebView = WebView(context).apply {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.setSupportMultipleWindows(false)
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    // The default agent carries a "; wv" marker that makes Google and others refuse logins or serve a stripped page.
    settings.userAgentString = settings.userAgentString.replace("; wv", "")
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

    webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            if (url != null) viewModel.onPageStarted(url)
            onNavigationState(view?.canGoBack() == true, view?.canGoForward() == true)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            onNavigationState(view?.canGoBack() == true, view?.canGoForward() == true)
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            if (url != null) viewModel.onUrlChanged(url)
            onNavigationState(view?.canGoBack() == true, view?.canGoForward() == true)
        }

        // Pages try to bounce into other apps with intent:// and market:// links; keep them out.
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val scheme = request?.url?.scheme?.lowercase() ?: return true
            return scheme !in ALLOWED_SCHEMES
        }

        // Runs on a WebView network thread: it must not touch the WebView or Compose state.
        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            if (request != null) {
                viewModel.onResourceRequested(request.url.toString(), request.requestHeaders?.get("Referer"))
            }
            return null
        }
    }
    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            onProgress(newProgress)
        }
    }
    setDownloadListener { url, _, _, _, _ -> viewModel.onDirectDownload(url) }
}
