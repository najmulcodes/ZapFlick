package com.najmulcodes.zapflick.ui.browser

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.domain.browser.AddressDisplay
import com.najmulcodes.zapflick.domain.browser.ErrorPageHtml
import com.najmulcodes.zapflick.domain.browser.FavoriteSite
import com.najmulcodes.zapflick.domain.browser.RequestSession
import com.najmulcodes.zapflick.domain.browser.UserAgents
import com.najmulcodes.zapflick.domain.engine.FailureReport
import com.najmulcodes.zapflick.ui.components.HowToSheet
import com.najmulcodes.zapflick.ui.components.QualitySheet
import com.najmulcodes.zapflick.ui.util.appVersionName
import com.najmulcodes.zapflick.ui.util.copyText
import com.najmulcodes.zapflick.ui.util.shareText

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The Tab destination: a new-tab page or a web page, with tabs, ad blocking and the download button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabScreen(
    incomingShare: String?,
    onShareConsumed: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProgress: () -> Unit,
    onFullscreenChange: (Boolean) -> Unit,
    onTypingChange: (Boolean) -> Unit,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val recents by viewModel.recentSites.collectAsStateWithLifecycle()
    val detected by viewModel.detected.collectAsStateWithLifecycle()
    val panel by viewModel.panel.collectAsStateWithLifecycle()
    val pageUrl by viewModel.pageUrl.collectAsStateWithLifecycle()
    val blocked by viewModel.blockedCount.collectAsStateWithLifecycle()
    val desktop by viewModel.desktopSite.collectAsStateWithLifecycle()
    val autoPrepare by viewModel.autoPrepare.collectAsStateWithLifecycle()
    val failures by viewModel.failures.collectAsStateWithLifecycle()
    val ytDlpVersion by viewModel.ytDlpVersion.collectAsStateWithLifecycle()
    val activeTab = tabs.active

    var progress by remember { mutableIntStateOf(100) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var addressFocused by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var showHowTo by remember { mutableStateOf(false) }
    var showAddFavorite by remember { mutableStateOf(false) }
    var favoriteMenu by remember { mutableStateOf<FavoriteSite?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var linkMenu by remember { mutableStateOf<LinkHit?>(null) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    val webView = remember {
        createBrowserWebView(
            context = context,
            callbacks = BrowserCallbacks(
                onPageStarted = viewModel::onPageStarted,
                onPageFinished = viewModel::onPageFinished,
                onUrlChanged = viewModel::onUrlChanged,
                onTitle = viewModel::onTitleChanged,
                onProgress = { progress = it },
                onNavigationState = { back, forward ->
                    canGoBack = back
                    canGoForward = forward
                },
                onResource = viewModel::onResourceRequested,
                onDirectDownload = viewModel::onDirectDownload,
                shouldBlock = viewModel.adBlocker::shouldBlock,
                onBlocked = viewModel::onAdBlocked,
                onShowCustomView = { view, callback ->
                    customView = view
                    customCallback = callback
                },
                onHideCustomView = {
                    customView = null
                    customCallback = null
                },
                onLongPress = { hit -> linkMenu = hit },
                errorPage = { url ->
                    ErrorPageHtml.build(
                        title = context.getString(R.string.browser_error_title),
                        message = context.getString(R.string.browser_error_message),
                        retryLabel = context.getString(R.string.browser_error_retry),
                        url = url,
                    )
                },
            ),
        )
    }
    val mobileAgent = remember(webView) { mobileUserAgent(webView) }
    val desktopAgent = remember(mobileAgent) { UserAgents.toDesktop(mobileAgent) }

    // Declared before the Scaffold so it is disposed after the WebView has been detached from the screen.
    DisposableEffect(webView) {
        onDispose {
            // Leaving this screen (to Progress, say) must not lose the page: keep its state for the way back.
            val shown = webView.url
            if (shown != null && shown != "about:blank") {
                val state = Bundle()
                webView.saveState(state)
                viewModel.savePageState(viewModel.tabs.value.activeId, state)
            }
            webView.stopLoading()
            webView.destroy()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { webView.onPause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { webView.onResume() }

    // Only the tab on screen has a live page. Leaving a tab saves its page state; coming back restores it.
    var shownTabId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(tabs.activeId) {
        val previous = shownTabId
        val nowActive = viewModel.tabs.value.active
        if (previous == nowActive.id) return@LaunchedEffect
        val current = webView.url
        if (previous != null && viewModel.tabs.value.tabs.any { it.id == previous } &&
            current != null && current != "about:blank"
        ) {
            val state = Bundle()
            webView.saveState(state)
            viewModel.savePageState(previous, state)
        }
        shownTabId = nowActive.id
        val saved = viewModel.pageStateOf(nowActive.id)
        when {
            saved != null -> webView.restoreState(saved)
            nowActive.url.isNotBlank() -> webView.loadUrl(nowActive.url)
            else -> webView.loadUrl("about:blank")
        }
    }

    var appliedDesktop by remember { mutableStateOf(false) }
    LaunchedEffect(desktop) {
        if (desktop == appliedDesktop) return@LaunchedEffect
        appliedDesktop = desktop
        webView.settings.userAgentString = if (desktop) desktopAgent else mobileAgent
        webView.settings.loadWithOverviewMode = desktop
        if (!activeTab.isNewTabPage) webView.reload()
    }

    LaunchedEffect(activeTab.url, addressFocused) {
        if (!addressFocused) address = activeTab.url
    }
    // While typing an address the bottom bar steps aside so the keyboard has the room.
    LaunchedEffect(addressFocused) { onTypingChange(addressFocused) }
    DisposableEffect(Unit) { onDispose { onTypingChange(false) } }
    LaunchedEffect(progress) { if (progress >= 100) refreshing = false }

    fun loadAddress(url: String) {
        viewModel.navigatingTo(url)
        webView.loadUrl(url)
    }

    val startPrepare: (url: String, isPage: Boolean, referer: String?) -> Unit = { url, isPage, referer ->
        val session = RequestSession(
            userAgent = webView.settings.userAgentString,
            referer = referer,
            cookies = CookieManager.getInstance().getCookie(url),
        )
        viewModel.prepare(url, isPage, session)
    }

    LaunchedEffect(incomingShare) {
        if (incomingShare != null) {
            viewModel.onSharedText(incomingShare)
            onShareConsumed()
        }
    }
    LaunchedEffect(autoPrepare) {
        autoPrepare?.let { url ->
            startPrepare(url, true, null)
            viewModel.onAutoPrepareConsumed()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is BrowserEvent.Queued -> context.getString(R.string.added_to_queue, event.title)
                BrowserEvent.QueueFailed -> context.getString(R.string.browser_queue_failed)
                BrowserEvent.TabLimitReached -> context.getString(R.string.tabs_limit_reached)
                BrowserEvent.NoLinkInShare -> context.getString(R.string.share_no_link)
                BrowserEvent.FavoriteAdded -> context.getString(R.string.favorite_added)
                BrowserEvent.FavoriteRejected -> context.getString(R.string.favorite_rejected)
            }
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (event is BrowserEvent.Queued) context.getString(R.string.action_view_progress) else null,
            )
            if (result == SnackbarResult.ActionPerformed) onOpenProgress()
        }
    }

    // Fullscreen video: the page's video view fills the screen, bars are hidden, landscape is allowed.
    DisposableEffect(customView) {
        val activity = context.findActivity()
        val fullscreen = customView != null
        onFullscreenChange(fullscreen)
        if (activity != null && fullscreen) {
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
        onDispose {
            if (activity != null && fullscreen) {
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            onFullscreenChange(false)
        }
    }

    BackHandler(enabled = canGoBack && !activeTab.isNewTabPage) { webView.goBack() }
    BackHandler(enabled = drawerOpen) { drawerOpen = false }
    BackHandler(enabled = customView != null) {
        customCallback?.onCustomViewHidden()
        customView = null
        customCallback = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                BrowserTopBar(
                    tabCount = tabs.tabs.size,
                    showAddress = !activeTab.isNewTabPage,
                    address = address,
                    loading = progress < 100 && !activeTab.isNewTabPage,
                    progress = progress,
                    adBlockOn = settings.blockAds,
                    blockedCount = blocked,
                    desktopSite = desktop,
                    onTabs = { drawerOpen = true },
                    onAddressChange = { address = it },
                    onAddressFocusChange = { addressFocused = it },
                    onGo = {
                        viewModel.resolveAddress(address)?.let {
                            loadAddress(it)
                            focusManager.clearFocus()
                        }
                    },
                    onReload = { webView.reload() },
                    onShield = onOpenSettings,
                    onHelp = { showHowTo = true },
                    onSettings = onOpenSettings,
                    onNewTab = { viewModel.newTab() },
                    onDesktopSite = viewModel::setDesktopSite,
                    onAddToFavorites = { viewModel.addFavorite(activeTab.title, activeTab.url) },
                    onOpenProgress = onOpenProgress,
                    onCloseAllTabs = viewModel::closeAllTabs,
                    onShare = { shareText(context, activeTab.url) },
                    onCopyLink = {
                        copyText(context, "link", activeTab.url)
                        Toast.makeText(context, R.string.browser_link_copied, Toast.LENGTH_SHORT).show()
                    },
                )
            },
            floatingActionButton = {
                if (!activeTab.isNewTabPage) {
                    ExtendedFloatingActionButton(onClick = viewModel::onDownloadButton) {
                        Text(
                            if (detected.isEmpty()) {
                                stringResource(R.string.action_download)
                            } else {
                                stringResource(R.string.browser_download_count, detected.size)
                            },
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            // No pull-to-refresh: it steals drags from scrollable boxes inside pages (age gates, pop-ups).
            // The reload button in the address bar does the same job.
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize(),
                    // An invisible view gets no touches, so the page cannot be used under the new-tab page.
                    update = { it.visibility = if (activeTab.isNewTabPage) View.INVISIBLE else View.VISIBLE },
                )
                if (activeTab.isNewTabPage) {
                    NewTabPage(
                        favorites = favorites,
                        recents = recents,
                        onSubmit = { text -> viewModel.resolveAddress(text)?.let(::loadAddress) },
                        onOpen = ::loadAddress,
                        onAddFavorite = { showAddFavorite = true },
                        onFavoriteLongPress = { favoriteMenu = it },
                        onHowToDownload = { showHowTo = true },
                    )
                }
            }
        }

        TabsDrawer(
            visible = drawerOpen,
            tabs = tabs,
            favorites = favorites,
            canGoBack = canGoBack && !activeTab.isNewTabPage,
            canGoForward = canGoForward && !activeTab.isNewTabPage,
            onDismiss = { drawerOpen = false },
            onSelect = {
                viewModel.selectTab(it)
                drawerOpen = false
            },
            onClose = viewModel::closeTab,
            onNewTab = {
                viewModel.newTab()
                drawerOpen = false
            },
            onCloseAll = {
                viewModel.closeAllTabs()
                drawerOpen = false
            },
            onFavorite = {
                loadAddress(it.url)
                drawerOpen = false
            },
            onBack = { webView.goBack() },
            onForward = { webView.goForward() },
            onHome = {
                viewModel.navigatingTo("")
                webView.loadUrl("about:blank")
                drawerOpen = false
            },
        )

        customView?.let { view ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                AndroidView(factory = { view }, modifier = Modifier.fillMaxSize())
            }
        }
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
            detectedCount = detected.size,
            onRetry = viewModel::retry,
            onPickFile = viewModel::onDownloadButton,
            onCopyDetails = {
                val report = FailureReport.build(failures.take(3), ytDlpVersion, appVersionName(context))
                copyText(context, "ZapFlick details", report)
                Toast.makeText(context, R.string.failure_details_copied, Toast.LENGTH_SHORT).show()
            },
            onDismiss = viewModel::onPanelDismiss,
        )
    }

    if (showHowTo) HowToSheet(onDismiss = { showHowTo = false })

    linkMenu?.let { hit ->
        AlertDialog(
            onDismissRequest = { linkMenu = null },
            title = {
                Text(
                    text = AddressDisplay.compact(hit.url).ifEmpty { hit.url },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Column {
                    TextButton(onClick = { viewModel.newTab(hit.url); linkMenu = null }) {
                        Text(stringResource(R.string.link_open_new_tab))
                    }
                    TextButton(onClick = { loadAddress(hit.url); linkMenu = null }) {
                        Text(stringResource(R.string.link_open_here))
                    }
                    TextButton(
                        onClick = {
                            copyText(context, "link", hit.url)
                            Toast.makeText(context, R.string.browser_link_copied, Toast.LENGTH_SHORT).show()
                            linkMenu = null
                        },
                    ) { Text(stringResource(R.string.browser_copy_link)) }
                    TextButton(onClick = { shareText(context, hit.url); linkMenu = null }) {
                        Text(stringResource(R.string.browser_share_page))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { linkMenu = null }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }

    if (showAddFavorite) {
        AddFavoriteDialog(
            onDismiss = { showAddFavorite = false },
            onAdd = { title, url ->
                viewModel.addFavorite(title, url)
                showAddFavorite = false
            },
        )
    }

    favoriteMenu?.let { site ->
        AlertDialog(
            onDismissRequest = { favoriteMenu = null },
            title = { Text(site.title) },
            text = {
                Column {
                    TextButton(onClick = { viewModel.moveFavorite(site.id, -1); favoriteMenu = null }) {
                        Text(stringResource(R.string.favorite_move_earlier))
                    }
                    TextButton(onClick = { viewModel.moveFavorite(site.id, 1); favoriteMenu = null }) {
                        Text(stringResource(R.string.favorite_move_later))
                    }
                    TextButton(onClick = { viewModel.removeFavorite(site.id); favoriteMenu = null }) {
                        Text(stringResource(R.string.action_remove))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { favoriteMenu = null }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

@Composable
private fun AddFavoriteDialog(onDismiss: () -> Unit, onAdd: (title: String, url: String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.favorite_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.favorite_name)) },
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.favorite_address)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(title, url) }, enabled = url.isNotBlank()) {
                Text(stringResource(R.string.favorite_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun BrowserTopBar(
    tabCount: Int,
    showAddress: Boolean,
    address: String,
    loading: Boolean,
    progress: Int,
    adBlockOn: Boolean,
    blockedCount: Int,
    desktopSite: Boolean,
    onTabs: () -> Unit,
    onAddressChange: (String) -> Unit,
    onAddressFocusChange: (Boolean) -> Unit,
    onGo: () -> Unit,
    onReload: () -> Unit,
    onShield: () -> Unit,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
    onNewTab: () -> Unit,
    onDesktopSite: (Boolean) -> Unit,
    onAddToFavorites: () -> Unit,
    onOpenProgress: () -> Unit,
    onCloseAllTabs: () -> Unit,
    onShare: () -> Unit,
    onCopyLink: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tabsLabel = stringResource(R.string.tabs_open_count, tabCount)
                IconButton(onClick = onTabs, modifier = Modifier.semantics { contentDescription = tabsLabel }) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = tabCount.toString(), style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (showAddress) {
                    AddressField(
                        address = address,
                        onAddressChange = onAddressChange,
                        onFocusChange = onAddressFocusChange,
                        onGo = onGo,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onReload) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.browser_reload))
                    }
                } else {
                    Box(modifier = Modifier.weight(1f))
                }

                if (!showAddress || blockedCount > 0) {
                    val shieldLabel = if (adBlockOn) {
                        stringResource(R.string.browser_adblock_on, blockedCount)
                    } else {
                        stringResource(R.string.browser_adblock_off)
                    }
                    IconButton(onClick = onShield, modifier = Modifier.semantics { contentDescription = shieldLabel }) {
                        Icon(
                            painterResource(if (adBlockOn) R.drawable.ic_shield_check else R.drawable.ic_shield),
                            contentDescription = null,
                            tint = if (adBlockOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showAddress && blockedCount > 0) {
                        Text(text = blockedCount.toString(), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (!showAddress) {
                    IconButton(onClick = onHelp) {
                        Icon(Icons.Outlined.Info, stringResource(R.string.how_to_download))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, stringResource(R.string.settings_title))
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, stringResource(R.string.viewer_menu_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tabs_new)) },
                            onClick = { menuOpen = false; onNewTab() },
                        )
                        if (showAddress) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_desktop_site)) },
                                leadingIcon = { Checkbox(checked = desktopSite, onCheckedChange = null) },
                                onClick = { menuOpen = false; onDesktopSite(!desktopSite) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_share_page)) },
                                onClick = { menuOpen = false; onShare() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.browser_copy_link)) },
                                onClick = { menuOpen = false; onCopyLink() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.favorite_add_current)) },
                                onClick = { menuOpen = false; onAddToFavorites() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.nav_progress)) },
                            onClick = { menuOpen = false; onOpenProgress() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tabs_close_all)) },
                            onClick = { menuOpen = false; onCloseAllTabs() },
                        )
                        if (showAddress) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.how_to_download)) },
                                onClick = { menuOpen = false; onHelp() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_title)) },
                                onClick = { menuOpen = false; onSettings() },
                            )
                        }
                    }
                }
            }
            if (loading) {
                LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
