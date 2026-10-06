package com.najmulcodes.zapflick.ui.security

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.domain.model.DownloadItem
import com.najmulcodes.zapflick.ui.util.label

private tailrec fun Context.fragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.fragmentActivity()
    else -> null
}

/**
 * The private folder destination. It shows the PIN screen until the folder is unlocked, then the
 * list. Screenshots and the recents preview are blocked on both (unless turned off in Settings).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateFolderScreen(
    secureScreens: Boolean,
    onBack: () -> Unit,
    onPlay: (Long) -> Unit,
    viewModel: PrivateViewModel = hiltViewModel(),
) {
    val pin by viewModel.pinState.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf<DownloadItem?>(null) }

    SecureScreen(enabled = secureScreens)

    LaunchedEffect(pin.mode, items.size) {
        if (pin.mode == PinUiState.Mode.Unlocked) viewModel.markSeen(items.size)
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val text = when (event) {
                is PrivateEvent.Restored -> context.resources.getQuantityString(R.plurals.private_restored, event.count, event.count)
                is PrivateEvent.RestoreFailed -> context.resources.getQuantityString(R.plurals.private_restore_failed, event.count, event.count)
                is PrivateEvent.Deleted -> context.resources.getQuantityString(R.plurals.finished_deleted, event.count, event.count)
                is PrivateEvent.DeleteFailed -> context.resources.getQuantityString(R.plurals.finished_delete_failed, event.count, event.count)
            }
            snackbar.showSnackbar(text)
        }
    }

    when (pin.mode) {
        PinUiState.Mode.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        PinUiState.Mode.Unlocked -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.private_title)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.lockNow(); onBack() }) {
                                Icon(Icons.Outlined.Lock, contentDescription = stringResource(R.string.private_lock_now))
                            }
                        },
                    )
                },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { padding ->
                if (items.isEmpty()) {
                    Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.private_empty),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(padding).fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "note") {
                            Text(
                                stringResource(R.string.private_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(items, key = { it.id }) { item ->
                            PrivateRow(
                                item = item,
                                onPlay = { onPlay(item.id) },
                                onRestore = { viewModel.restore(listOf(item)) },
                                onDelete = { confirmDelete = item },
                            )
                        }
                    }
                }
            }
        }
        else -> {
            BackHandler(onBack = onBack)
            val biometricAction = rememberBiometricAction(enabled = pin.biometricEnabled, onSuccess = viewModel::unlockWithBiometric)
            PinScreen(
                state = pin,
                onDigit = viewModel::press,
                onBackspace = viewModel::backspace,
                onBiometric = biometricAction,
            )
        }
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(pluralStringResource(R.plurals.finished_delete_title, 1, 1)) },
            text = { Text(stringResource(R.string.finished_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; viewModel.delete(listOf(item)) }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** A function that shows the system fingerprint or face prompt, or null when it cannot be used here. */
@Composable
private fun rememberBiometricAction(enabled: Boolean, onSuccess: () -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val activity = context.fragmentActivity()
    val available = remember(enabled) {
        enabled && BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
    if (!available || activity == null) return null
    val title = stringResource(R.string.pin_biometric_title)
    val cancel = stringResource(R.string.action_cancel)
    return {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setNegativeButtonText(cancel)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build(),
        )
    }
}

@Composable
private fun PrivateRow(item: DownloadItem, onPlay: () -> Unit, onRestore: () -> Unit, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.clickable(onClick = onPlay),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.selection.label(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onPlay) { Text(stringResource(R.string.action_play)) }
                TextButton(onClick = onRestore) { Text(stringResource(R.string.private_restore)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}
