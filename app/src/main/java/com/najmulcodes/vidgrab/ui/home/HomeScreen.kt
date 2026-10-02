package com.najmulcodes.vidgrab.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.najmulcodes.vidgrab.R
import com.najmulcodes.vidgrab.ui.components.EmptyState
import com.najmulcodes.vidgrab.ui.components.ErrorCard
import com.najmulcodes.vidgrab.ui.components.FetchingCard
import com.najmulcodes.vidgrab.ui.components.MetadataCard
import com.najmulcodes.vidgrab.ui.components.QualitySheet
import com.najmulcodes.vidgrab.ui.components.UrlInputCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    incomingShare: String?,
    onShareConsumed: () -> Unit,
    onOpenDownloads: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetVisible by viewModel.sheetVisible.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    RequestNotificationPermissionOnce()

    LaunchedEffect(incomingShare) {
        if (incomingShare != null) {
            viewModel.onSharedText(incomingShare)
            onShareConsumed()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
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
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UrlInputCard(
                url = urlInput,
                enabled = uiState != HomeUiState.Fetching,
                onUrlChange = viewModel::onUrlChange,
                onPaste = {
                    clipboard.getText()?.text?.let { viewModel.onSharedText(it) }
                },
                onFetch = viewModel::onFetchClick,
            )

            when (val state = uiState) {
                HomeUiState.Idle -> EmptyState()
                HomeUiState.Fetching -> FetchingCard()
                is HomeUiState.Ready -> MetadataCard(
                    metadata = state.metadata,
                    queuedSelection = state.queuedSelection,
                    onDownload = viewModel::onDownloadClick,
                    onViewDownloads = onOpenDownloads,
                    onNewLink = viewModel::onNewLink,
                )
                is HomeUiState.Error -> ErrorCard(
                    error = state.error,
                    onRetry = viewModel::onFetchClick,
                )
            }
        }
    }

    val ready = uiState as? HomeUiState.Ready
    if (sheetVisible && ready != null) {
        QualitySheet(
            formats = ready.formats,
            onSelect = viewModel::onQualitySelected,
            onDismiss = viewModel::onSheetDismiss,
        )
    }
}

/** Android 13+ needs a runtime grant for the progress notification; downloads work without it. */
@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
