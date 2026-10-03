package com.najmulcodes.zapflick.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.domain.browser.DetectedMedia
import com.najmulcodes.zapflick.domain.browser.MediaKind
import com.najmulcodes.zapflick.domain.model.DownloadError
import com.najmulcodes.zapflick.ui.util.displayMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadTargetsSheet(
    pageUrl: String,
    detected: List<DetectedMedia>,
    onPickPage: () -> Unit,
    onPickMedia: (DetectedMedia) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canDownloadPage = pageUrl.startsWith("http", ignoreCase = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.browser_targets_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.browser_target_page)) },
                    supportingContent = {
                        Text(
                            text = if (canDownloadPage) pageUrl else stringResource(R.string.browser_target_page_hint),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.clickable(enabled = canDownloadPage, onClick = onPickPage),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.browser_detected_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            if (detected.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.browser_detected_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(detected, key = { it.url }) { media ->
                    ListItem(
                        overlineContent = { Text(stringResource(media.kind.labelRes())) },
                        headlineContent = { Text(media.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(media.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.clickable { onPickMedia(media) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreparingSheet(onCancel: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = sheetState) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 3.dp)
            Text(
                text = stringResource(R.string.browser_preparing),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookupFailedSheet(
    error: DownloadError,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = error.displayMessage(), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_try_again)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        }
    }
}

private fun MediaKind.labelRes(): Int = when (this) {
    MediaKind.VIDEO -> R.string.browser_kind_video
    MediaKind.STREAM -> R.string.browser_kind_stream
    MediaKind.AUDIO -> R.string.browser_kind_audio
}
