package com.najmulcodes.zapflick.ui.finished

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.domain.model.DownloadItem
import com.najmulcodes.zapflick.domain.model.SavedMedia
import com.najmulcodes.zapflick.domain.util.formatBytes
import com.najmulcodes.zapflick.domain.viewer.DocumentType
import com.najmulcodes.zapflick.domain.viewer.DocumentTypeResolver
import com.najmulcodes.zapflick.ui.util.label
import com.najmulcodes.zapflick.ui.util.openDocumentElsewhere
import com.najmulcodes.zapflick.ui.util.openMedia
import com.najmulcodes.zapflick.ui.viewer.ViewerActivity
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishedScreen(
    onOpenPrivate: () -> Unit,
    onPlay: (Long) -> Unit,
    viewModel: FinishedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf<List<DownloadItem>?>(null) }
    var needsPin by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FinishedEvent.Deleted ->
                    snackbar.showSnackbar(context.resources.getQuantityString(R.plurals.finished_deleted, event.count, event.count))
                is FinishedEvent.DeleteFailed ->
                    snackbar.showSnackbar(context.resources.getQuantityString(R.plurals.finished_delete_failed, event.count, event.count))
                is FinishedEvent.MovedToPrivate ->
                    snackbar.showSnackbar(context.resources.getQuantityString(R.plurals.finished_moved_private, event.count, event.count))
                is FinishedEvent.MoveFailed ->
                    snackbar.showSnackbar(context.resources.getQuantityString(R.plurals.finished_move_failed, event.count, event.count))
                FinishedEvent.NeedsPin -> needsPin = true
            }
        }
    }

    val openItem: (DownloadItem) -> Unit = { item ->
        item.saved?.let { saved -> openSaved(context, saved, onPlay = { onPlay(item.id) }) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            val content = state as? FinishedUiState.Content
            if (content != null && content.selection.isSelecting) {
                SelectionBar(
                    count = content.selection.count,
                    onClose = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAll,
                    onMoveToPrivate = {
                        viewModel.moveToPrivate(content.items.filter { it.id in content.selection.selected })
                    },
                    onDelete = { confirmDelete = content.items.filter { it.id in content.selection.selected } },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.finished_title)) },
                    actions = {
                        Box {
                            IconButton(onClick = onOpenPrivate) {
                                Icon(Icons.Outlined.Lock, contentDescription = stringResource(R.string.finished_private_folder))
                            }
                            if (content?.hasNewPrivate == true) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 10.dp, end = 10.dp)
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF3B30)),
                                )
                            }
                        }
                        if (content != null) {
                            IconButton(onClick = { viewModel.setGrid(!content.asGrid) }) {
                                Icon(
                                    Icons.Outlined.Menu,
                                    contentDescription = stringResource(
                                        if (content.asGrid) R.string.finished_show_list else R.string.finished_show_grid,
                                    ),
                                )
                            }
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            (state as? FinishedUiState.Content)?.storage?.let { StorageFooter(it.usedBytes, it.totalBytes, it.fraction) }
        },
    ) { padding ->
        when (val current = state) {
            FinishedUiState.Loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is FinishedUiState.Content -> if (current.items.isEmpty()) {
                Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.finished_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else if (current.asGrid) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(current.items, key = { it.id }) { item ->
                        FinishedTile(
                            item = item,
                            selected = item.id in current.selection.selected,
                            selecting = current.selection.isSelecting,
                            grid = true,
                            onClick = { if (current.selection.isSelecting) viewModel.toggleSelected(item.id) else openItem(item) },
                            onLongClick = { viewModel.startSelecting(item.id) },
                            onOpenElsewhere = { openElsewhere(context, item) },
                            onDelete = { confirmDelete = listOf(item) },
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(current.items, key = { it.id }) { item ->
                        FinishedTile(
                            item = item,
                            selected = item.id in current.selection.selected,
                            selecting = current.selection.isSelecting,
                            grid = false,
                            onClick = { if (current.selection.isSelecting) viewModel.toggleSelected(item.id) else openItem(item) },
                            onLongClick = { viewModel.startSelecting(item.id) },
                            onOpenElsewhere = { openElsewhere(context, item) },
                            onDelete = { confirmDelete = listOf(item) },
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { items ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(pluralStringResource(R.plurals.finished_delete_title, items.size, items.size)) },
            text = { Text(stringResource(R.string.finished_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; viewModel.deleteItems(items) }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (needsPin) {
        AlertDialog(
            onDismissRequest = { needsPin = false },
            title = { Text(stringResource(R.string.finished_needs_pin_title)) },
            text = { Text(stringResource(R.string.finished_needs_pin_body)) },
            confirmButton = {
                TextButton(onClick = { needsPin = false; onOpenPrivate() }) { Text(stringResource(R.string.finished_set_pin)) }
            },
            dismissButton = { TextButton(onClick = { needsPin = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/**
 * PDF and HTML files open in ZapFlick's own viewer, files in app storage open in the in-app player
 * (no other app can read them), and everything else goes to whatever app the person picks.
 */
private fun openSaved(context: Context, saved: SavedMedia, onPlay: () -> Unit) {
    val uri = Uri.parse(saved.uri)
    when {
        saved.uri.startsWith("file:") -> onPlay()
        DocumentTypeResolver.resolve(null, saved.displayName) != DocumentType.Unsupported -> {
            context.startActivity(Intent(context, ViewerActivity::class.java).setAction(Intent.ACTION_VIEW).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
        else -> if (!openMedia(context, saved)) {
            Toast.makeText(context, R.string.toast_cannot_open, Toast.LENGTH_SHORT).show()
        }
    }
}

private fun openElsewhere(context: Context, item: DownloadItem) {
    val saved = item.saved ?: return
    if (saved.uri.startsWith("file:")) {
        Toast.makeText(context, R.string.finished_app_storage_note, Toast.LENGTH_SHORT).show()
        return
    }
    val mime = android.webkit.MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(saved.displayName.substringAfterLast('.', "").lowercase()) ?: "*/*"
    if (!openDocumentElsewhere(context, Uri.parse(saved.uri), mime)) {
        Toast.makeText(context, R.string.toast_cannot_open, Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onMoveToPrivate: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel))
            }
        },
        title = { Text(stringResource(R.string.finished_selected, count)) },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.finished_select_all))
            }
            IconButton(onClick = onMoveToPrivate) {
                Icon(Icons.Outlined.Lock, contentDescription = stringResource(R.string.finished_move_private))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        },
    )
}

@Composable
private fun StorageFooter(used: Long, total: Long, fraction: Float) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Text(
                text = stringResource(R.string.finished_storage, formatBytes(used), formatBytes(total)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FinishedTile(
    item: DownloadItem,
    selected: Boolean,
    selecting: Boolean,
    grid: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenElsewhere: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        val thumbnail: @Composable (Modifier) -> Unit = { modifier ->
            Box(modifier = modifier) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = stringResource(R.string.thumbnail_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                if (selected) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = stringResource(R.string.finished_selected_item),
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(3.dp),
                    )
                }
            }
        }
        val details: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    item.selection.label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val actions: @Composable () -> Unit = {
            if (!selecting) {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.viewer_menu_more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_open)) },
                            onClick = { menu = false; onClick() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.viewer_action_open_with)) },
                            onClick = { menu = false; onOpenElsewhere() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        }
        if (grid) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                thumbnail(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                Row(verticalAlignment = Alignment.Top) {
                    details(Modifier.weight(1f))
                    actions()
                }
            }
        } else {
            Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                thumbnail(Modifier.width(112.dp).aspectRatio(16f / 9f))
                details(Modifier.weight(1f))
                actions()
            }
        }
    }
}
