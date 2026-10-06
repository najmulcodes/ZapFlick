package com.najmulcodes.zapflick.ui.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.ui.components.HowToSheet
import com.najmulcodes.zapflick.ui.downloads.DownloadCard
import com.najmulcodes.zapflick.ui.util.BatteryOptimization

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onOpenBackgroundSetup: () -> Unit,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val waitingForWifi by viewModel.waitingForWifi.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var restricted by remember { mutableStateOf(BatteryOptimization.isRestricted(context)) }
    var showHowTo by remember { mutableStateOf(false) }

    // The banner disappears as soon as the exemption is granted, including from the system dialog.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { restricted = BatteryOptimization.isRestricted(context) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.progress_title)) }) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (restricted) StabilityBanner(onClick = onOpenBackgroundSetup)
            if (waitingForWifi) {
                Text(
                    text = stringResource(R.string.progress_waiting_wifi),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            when (val current = state) {
                ProgressUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is ProgressUiState.Content -> if (current.rows.isEmpty()) {
                    EmptyProgress(onHowTo = { showHowTo = true })
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "bulk") {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                if (current.hasActive) {
                                    OutlinedButton(onClick = viewModel::pauseAll, modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.action_pause_all))
                                    }
                                }
                                if (current.hasStopped) {
                                    OutlinedButton(onClick = viewModel::clearStopped, modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.action_clear_stopped))
                                    }
                                }
                            }
                        }
                        items(current.rows, key = { it.item.id }) { row ->
                            DownloadCard(
                                row = row,
                                onPause = { viewModel.pause(row.item.id) },
                                onResume = { viewModel.resume(row.item.id) },
                                onCancel = { viewModel.cancel(row.item.id) },
                                onRemove = { viewModel.remove(row.item.id) },
                                onOpen = {},
                            )
                        }
                    }
                }
            }
        }
    }
    if (showHowTo) HowToSheet(onDismiss = { showHowTo = false })
}

@Composable
private fun StabilityBanner(onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.progress_stability_banner),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun EmptyProgress(onHowTo: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.progress_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(onClick = onHowTo) { Text(stringResource(R.string.how_to_download)) }
    }
}
