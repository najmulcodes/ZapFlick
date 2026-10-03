package com.najmulcodes.zapflick.ui.viewer

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.najmulcodes.zapflick.ui.util.openDocumentElsewhere
import com.najmulcodes.zapflick.ui.util.shareDocument

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    viewModel: ViewerViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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

    val title = when (val current = state) {
        is ViewerUiState.Pdf -> current.info.displayName
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
                    if (state is ViewerUiState.Pdf && handOffMime != null) {
                        IconButton(onClick = shareAction) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.viewer_action_share))
                        }
                        OverflowMenu(onOpenElsewhere = openElsewhereAction)
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

                is ViewerUiState.Error -> ErrorContent(
                    message = current.error.displayMessage(),
                    onOpenElsewhere = openElsewhereAction.takeIf { handOffMime != null },
                )
            }
        }
    }
}

@Composable
private fun OverflowMenu(onOpenElsewhere: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.viewer_menu_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
