package com.najmulcodes.vidgrab.ui.downloads

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.najmulcodes.vidgrab.R
import com.najmulcodes.vidgrab.domain.model.DownloadRow
import com.najmulcodes.vidgrab.domain.model.DownloadStatus
import com.najmulcodes.vidgrab.domain.model.SavedMedia
import com.najmulcodes.vidgrab.ui.util.canOpen
import com.najmulcodes.vidgrab.ui.util.displayMessage
import com.najmulcodes.vidgrab.ui.util.label
import com.najmulcodes.vidgrab.ui.util.openMedia
import com.najmulcodes.vidgrab.ui.util.summary
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            DownloadsUiState.Loading -> Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is DownloadsUiState.Content -> if (current.rows.isEmpty()) {
                EmptyDownloads(modifier = Modifier.padding(padding))
            } else {
                DownloadList(
                    rows = current.rows,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onCancel = viewModel::cancel,
                    onRemove = viewModel::remove,
                    onPauseAll = viewModel::pauseAll,
                    onClearFinished = viewModel::clearFinished,
                    onOpen = { media ->
                        if (!openMedia(context, media)) {
                            Toast.makeText(context, R.string.toast_cannot_open, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun EmptyDownloads(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.downloads_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.downloads_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DownloadList(
    rows: List<DownloadRow>,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onPauseAll: () -> Unit,
    onClearFinished: () -> Unit,
    onOpen: (SavedMedia) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasActive = rows.any { it.item.status.isActive }
    val hasFinished = rows.any { it.item.status.isFinished }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (hasActive || hasFinished) {
            item(key = "bulk-actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    if (hasActive) {
                        OutlinedButton(onClick = onPauseAll, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.action_pause_all))
                        }
                    }
                    if (hasFinished) {
                        OutlinedButton(onClick = onClearFinished, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.action_clear_finished))
                        }
                    }
                }
            }
        }
        items(rows, key = { it.item.id }) { row ->
            DownloadCard(
                row = row,
                onPause = { onPause(row.item.id) },
                onResume = { onResume(row.item.id) },
                onCancel = { onCancel(row.item.id) },
                onRemove = { onRemove(row.item.id) },
                onOpen = onOpen,
            )
        }
    }
}

@Composable
private fun DownloadCard(
    row: DownloadRow,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onOpen: (SavedMedia) -> Unit,
) {
    val item = row.item
    val context = LocalContext.current

    val statusText = when (item.status) {
        DownloadStatus.QUEUED -> stringResource(R.string.status_queued)
        DownloadStatus.RUNNING -> row.progress?.summary(context) ?: stringResource(R.string.status_starting)
        DownloadStatus.PAUSED -> stringResource(R.string.status_paused)
        DownloadStatus.COMPLETED -> stringResource(
            R.string.status_saved,
            item.saved?.location.orEmpty(),
        )
        DownloadStatus.FAILED -> item.error?.displayMessage() ?: stringResource(R.string.error_unknown)
        DownloadStatus.CANCELLED -> stringResource(R.string.status_cancelled)
    }
    val statusColor = when (item.status) {
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = stringResource(R.string.thumbnail_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(112.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = item.selection.label(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (item.status == DownloadStatus.RUNNING) {
                val progress = row.progress
                if (progress == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    val fraction = (progress.percent / 100f).coerceIn(0f, 1f)
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                }
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when (item.status) {
                    DownloadStatus.QUEUED, DownloadStatus.RUNNING -> {
                        TextButton(onClick = onPause) { Text(stringResource(R.string.action_pause)) }
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                    }
                    DownloadStatus.PAUSED -> {
                        TextButton(onClick = onResume) { Text(stringResource(R.string.action_resume)) }
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                    }
                    DownloadStatus.COMPLETED -> {
                        item.saved?.takeIf { it.canOpen() }?.let { media ->
                            TextButton(onClick = { onOpen(media) }) { Text(stringResource(R.string.action_open)) }
                        }
                        TextButton(onClick = onRemove) { Text(stringResource(R.string.action_remove)) }
                    }
                    DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                        TextButton(onClick = onResume) { Text(stringResource(R.string.action_retry)) }
                        TextButton(onClick = onRemove) { Text(stringResource(R.string.action_remove)) }
                    }
                }
            }
        }
    }
}
