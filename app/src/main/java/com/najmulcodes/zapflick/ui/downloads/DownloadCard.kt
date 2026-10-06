package com.najmulcodes.zapflick.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.domain.model.DownloadRow
import com.najmulcodes.zapflick.domain.model.DownloadStatus
import com.najmulcodes.zapflick.domain.model.SavedMedia
import com.najmulcodes.zapflick.ui.util.displayMessage
import com.najmulcodes.zapflick.ui.util.label
import com.najmulcodes.zapflick.ui.util.openMedia
import com.najmulcodes.zapflick.ui.util.summary

/** One download as a card with its progress and actions. Shared by the Progress and Private screens. */
@Composable
internal fun DownloadCard(
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
                        item.saved?.let { media ->
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
