package com.najmulcodes.zapflick.ui.viewer

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.domain.viewer.HtmlViewOptions
import com.najmulcodes.zapflick.ui.util.openDocumentElsewhere
import com.najmulcodes.zapflick.ui.util.openWebLink
import com.najmulcodes.zapflick.ui.util.shareDocument

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    viewModel: ViewerViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val htmlOptions by viewModel.htmlOptions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uri = viewModel.documentUri
    val handOffMime = state.handOffMimeType.takeIf { viewModel.canHandOff }

    fun showNoApp() {
        Toast.makeText(context, R.string.toast_cannot_open, Toast.LENGTH_SHORT).show()
    }

    val shareAction: () -> Unit = {
        if (uri != null && handOffMime != null && !shareDocument(context, uri, handOffMime)) showNoApp()
    }
    val openElsewhereAction: () -> Unit = {
        if (uri != null && handOffMime != null && !openDocumentElsewhere(context, uri, handOffMime)) showNoApp()
    }
    val openLinkAction: (String) -> Unit = { url ->
        if (!openWebLink(context, url)) showNoApp()
    }

    // Back closes the source view first, then leaves the viewer.
    BackHandler(enabled = state is ViewerUiState.Html && htmlOptions.showSource) {
        viewModel.setShowSource(false)
    }

    val title = when (val current = state) {
        is ViewerUiState.Pdf -> current.info.displayName
        is ViewerUiState.Html -> current.info.displayName
        is ViewerUiState.Error -> current.info?.displayName
        ViewerUiState.Loading -> null
    } ?: stringResource(R.string.viewer_title_fallback)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    val isHtml = state is ViewerUiState.Html
                    val showable = isHtml || state is ViewerUiState.Pdf
                    if (showable && handOffMime != null) {
                        IconButton(onClick = shareAction) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.viewer_action_share))
                        }
                    }
                    if (showable && (isHtml || handOffMime != null)) {
                        ViewerMenu(
                            htmlOptions = htmlOptions.takeIf { isHtml },
                            canOpenElsewhere = handOffMime != null,
                            onScriptsChange = viewModel::setScriptsEnabled,
                            onOnlineChange = viewModel::setOnlineContentAllowed,
                            onSourceChange = viewModel::setShowSource,
                            onOpenElsewhere = openElsewhereAction,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val current = state) {
                ViewerUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                is ViewerUiState.Pdf -> PdfViewer(
                    pages = current.pages,
                    cachedPage = viewModel::cachedPage,
                    renderPage = viewModel::renderPage,
                )

                is ViewerUiState.Html -> HtmlContent(
                    html = current.html,
                    options = htmlOptions,
                    onOpenLink = openLinkAction,
                )

                is ViewerUiState.Error -> ErrorContent(
                    message = current.error.displayMessage(),
                    onOpenElsewhere = openElsewhereAction.takeIf { handOffMime != null },
                )
            }
        }
    }
}

@Composable
private fun HtmlContent(
    html: String,
    options: HtmlViewOptions,
    onOpenLink: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HtmlNotice(options)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            HtmlViewer(html = html, options = options, onOpenLink = onOpenLink)
            if (options.showSource) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HtmlSourceView(html = html)
                }
            }
        }
    }
}

/**
 * Says what is off, and what is on. The note about missing local images and styles can be
 * dismissed; the warning shown while scripts or online content are enabled cannot.
 */
@Composable
private fun HtmlNotice(options: HtmlViewOptions) {
    var dismissed by rememberSaveable { mutableStateOf(false) }
    val warning = options.scriptsEnabled || options.onlineContentAllowed
    if (!warning && dismissed) return

    val message = stringResource(
        when {
            options.scriptsEnabled && options.onlineContentAllowed -> R.string.viewer_html_notice_both
            options.scriptsEnabled -> R.string.viewer_html_notice_scripts
            options.onlineContentAllowed -> R.string.viewer_html_notice_online
            else -> R.string.viewer_html_notice_default
        },
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = if (warning) 16.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
            )
            if (!warning) {
                IconButton(onClick = { dismissed = true }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.viewer_html_notice_dismiss))
                }
            }
        }
    }
}

/**
 * The overflow menu. For an HTML file it also holds the three switches; they all start off and
 * are only remembered while this file is open.
 */
@Composable
private fun ViewerMenu(
    htmlOptions: HtmlViewOptions?,
    canOpenElsewhere: Boolean,
    onScriptsChange: (Boolean) -> Unit,
    onOnlineChange: (Boolean) -> Unit,
    onSourceChange: (Boolean) -> Unit,
    onOpenElsewhere: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.viewer_menu_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (htmlOptions != null) {
                CheckableMenuItem(
                    label = stringResource(R.string.viewer_menu_scripts),
                    checked = htmlOptions.scriptsEnabled,
                    onChange = {
                        expanded = false
                        onScriptsChange(it)
                    },
                )
                CheckableMenuItem(
                    label = stringResource(R.string.viewer_menu_online),
                    checked = htmlOptions.onlineContentAllowed,
                    onChange = {
                        expanded = false
                        onOnlineChange(it)
                    },
                )
                CheckableMenuItem(
                    label = stringResource(R.string.viewer_menu_source),
                    checked = htmlOptions.showSource,
                    onChange = {
                        expanded = false
                        onSourceChange(it)
                    },
                )
                if (canOpenElsewhere) HorizontalDivider()
            }
            if (canOpenElsewhere) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.viewer_action_open_with)) },
                    onClick = {
                        expanded = false
                        onOpenElsewhere()
                    },
                )
            }
        }
    }
}

@Composable
private fun CheckableMenuItem(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
        onClick = { onChange(!checked) },
    )
}

@Composable
private fun ErrorContent(
    message: String,
    onOpenElsewhere: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (onOpenElsewhere != null) {
            Button(onClick = onOpenElsewhere) {
                Text(stringResource(R.string.viewer_action_open_elsewhere))
            }
        }
    }
}
