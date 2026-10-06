package com.najmulcodes.zapflick.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.domain.model.AudioFormat
import com.najmulcodes.zapflick.domain.model.FormatSelection
import com.najmulcodes.zapflick.domain.settings.AppSettings
import com.najmulcodes.zapflick.domain.settings.FilenameStyle
import com.najmulcodes.zapflick.domain.settings.SearchEngine
import com.najmulcodes.zapflick.domain.settings.ThemeMode
import com.najmulcodes.zapflick.domain.settings.YtDlpChannel
import com.najmulcodes.zapflick.ui.components.HowToSheet

private enum class Confirm { Cache, Cookies, History }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenBackgroundSetup: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPinState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            snackbar.showSnackbar(
                context.getString(
                    when (event) {
                        SettingsEvent.CacheCleared -> R.string.settings_cache_cleared
                        SettingsEvent.CookiesCleared -> R.string.settings_cookies_cleared
                        SettingsEvent.HistoryCleared -> R.string.settings_history_cleared
                        SettingsEvent.ClearFailed -> R.string.settings_clear_failed
                    },
                ),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val current = state) {
            SettingsUiState.Loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is SettingsUiState.Content -> SettingsContent(
                state = current,
                viewModel = viewModel,
                onOpenBackgroundSetup = onOpenBackgroundSetup,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState.Content,
    viewModel: SettingsViewModel,
    onOpenBackgroundSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val s = state.settings
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var showHowTo by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                viewModel.setCustomFolder(uri.toString())
            } catch (e: SecurityException) {
                Toast.makeText(context, R.string.settings_folder_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        // ---- Download
        item { Section(R.string.settings_section_download) }
        item {
            ValueRow(
                title = stringResource(R.string.settings_location),
                value = if (s.customFolderUri == null) {
                    stringResource(R.string.settings_location_default)
                } else {
                    Uri.parse(s.customFolderUri).lastPathSegment?.substringAfter(':').orEmpty()
                        .ifEmpty { stringResource(R.string.settings_location_custom) }
                },
                onClick = { folderPicker.launch(null) },
            )
        }
        if (s.customFolderUri != null) {
            item {
                ValueRow(
                    title = stringResource(R.string.settings_location_reset),
                    value = stringResource(R.string.settings_location_default),
                    onClick = { viewModel.setCustomFolder(null) },
                )
            }
        }
        item {
            SwitchRow(
                title = stringResource(R.string.settings_wifi_only),
                subtitle = stringResource(R.string.settings_wifi_only_body),
                checked = s.wifiOnly,
                onChange = viewModel::setWifiOnly,
            )
        }
        item {
            ValueRow(
                title = stringResource(R.string.settings_concurrent),
                value = s.maxConcurrent.toString(),
                onClick = { dialog = SettingsDialog.Concurrent },
            )
        }
        item {
            ValueRow(
                title = stringResource(R.string.settings_default_quality),
                value = qualityName(s.defaultQuality),
                onClick = { dialog = SettingsDialog.Quality },
            )
        }
        item {
            ValueRow(
                title = stringResource(R.string.settings_filename),
                value = filenameName(s.filenameStyle),
                onClick = { dialog = SettingsDialog.Filename },
            )
        }

        // ---- Browser
        item { Section(R.string.settings_section_browser) }
        item {
            SwitchRow(
                title = stringResource(R.string.settings_block_ads),
                subtitle = stringResource(R.string.settings_block_ads_body),
                checked = s.blockAds,
                onChange = viewModel::setBlockAds,
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.settings_recent_sites),
                subtitle = stringResource(R.string.settings_recent_sites_body),
                checked = s.recentSites,
                onChange = viewModel::setRecentSites,
            )
        }
        item {
            ValueRow(
                title = stringResource(R.string.settings_search_engine),
                value = engineName(s.searchEngine),
                onClick = { dialog = SettingsDialog.Engine },
            )
        }
        item { ValueRow(stringResource(R.string.settings_clear_cache), null) { confirm = Confirm.Cache } }
        item { ValueRow(stringResource(R.string.settings_clear_history), null) { confirm = Confirm.History } }
        item { ValueRow(stringResource(R.string.settings_clear_cookies), null) { confirm = Confirm.Cookies } }

        // ---- General
        item { Section(R.string.settings_section_general) }
        item {
            ValueRow(
                title = stringResource(R.string.settings_language),
                value = stringResource(R.string.settings_language_system),
                onClick = { openLanguageSettings(context) },
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.settings_sync_gallery),
                subtitle = stringResource(
                    if (s.syncToGallery) R.string.settings_sync_gallery_on else R.string.settings_sync_gallery_off,
                ),
                checked = s.syncToGallery,
                onChange = viewModel::setSyncToGallery,
            )
        }
        item {
            ValueRow(
                title = stringResource(R.string.settings_theme),
                value = themeName(s.theme),
                onClick = { dialog = SettingsDialog.Theme },
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_body),
                    checked = s.dynamicColor,
                    onChange = viewModel::setDynamicColor,
                )
            }
        }

        // ---- Privacy
        item { Section(R.string.settings_section_privacy) }
        item {
            SwitchRow(
                title = stringResource(R.string.settings_secure_screens),
                subtitle = stringResource(R.string.settings_secure_screens_body),
                checked = s.secureScreens,
                onChange = viewModel::setSecureScreens,
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.settings_biometric),
                subtitle = stringResource(
                    when {
                        !state.hasPin -> R.string.settings_biometric_needs_pin
                        !biometricAvailable -> R.string.settings_biometric_unavailable
                        else -> R.string.settings_biometric_body
                    },
                ),
                checked = s.biometricUnlock && state.hasPin && biometricAvailable,
                enabled = state.hasPin && biometricAvailable,
                onChange = viewModel::setBiometric,
            )
        }

        // ---- yt-dlp
        item { Section(R.string.settings_section_ytdlp) }
        item {
            ValueRow(
                title = stringResource(R.string.settings_ytdlp_version),
                value = state.ytDlpVersion ?: stringResource(R.string.settings_ytdlp_unknown),
                onClick = null,
            )
        }
        item {
            ValueRow(
                title = stringResource(R.string.settings_ytdlp_channel),
                value = channelName(s.ytDlpChannel),
                onClick = { dialog = SettingsDialog.Channel },
            )
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::updateYtDlp,
                    enabled = state.update != YtDlpUpdateState.Running,
                ) {
                    Text(stringResource(R.string.settings_ytdlp_update))
                }
                Text(
                    text = when (val u = state.update) {
                        YtDlpUpdateState.Idle -> stringResource(R.string.settings_ytdlp_update_body)
                        YtDlpUpdateState.Running -> stringResource(R.string.settings_ytdlp_updating)
                        is YtDlpUpdateState.Updated -> stringResource(
                            R.string.settings_ytdlp_updated, u.version ?: stringResource(R.string.settings_ytdlp_unknown),
                        )
                        is YtDlpUpdateState.AlreadyLatest -> stringResource(
                            R.string.settings_ytdlp_latest, u.version ?: stringResource(R.string.settings_ytdlp_unknown),
                        )
                        YtDlpUpdateState.Failed -> stringResource(R.string.settings_ytdlp_failed)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.update == YtDlpUpdateState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---- Help
        item { Section(R.string.settings_section_help) }
        item { ValueRow(stringResource(R.string.how_to_download), null) { showHowTo = true } }
        item { ValueRow(stringResource(R.string.settings_background_setup), null, onOpenBackgroundSetup) }
        item { ValueRow(stringResource(R.string.settings_version), appVersion(context), null) }
    }

    when (dialog) {
        SettingsDialog.Concurrent -> ChoiceDialog(
            title = stringResource(R.string.settings_concurrent),
            options = AppSettings.CONCURRENT_RANGE.map { it to it.toString() },
            selected = s.maxConcurrent,
            onSelect = viewModel::setMaxConcurrent,
            onDismiss = { dialog = null },
        )
        SettingsDialog.Quality -> ChoiceDialog(
            title = stringResource(R.string.settings_default_quality),
            options = QUALITY_KEYS.map { it to qualityName(it) },
            selected = s.defaultQuality,
            onSelect = viewModel::setDefaultQuality,
            onDismiss = { dialog = null },
        )
        SettingsDialog.Filename -> ChoiceDialog(
            title = stringResource(R.string.settings_filename),
            options = FilenameStyle.entries.map { it to filenameName(it) },
            selected = s.filenameStyle,
            onSelect = viewModel::setFilenameStyle,
            onDismiss = { dialog = null },
        )
        SettingsDialog.Engine -> ChoiceDialog(
            title = stringResource(R.string.settings_search_engine),
            options = SearchEngine.entries.map { it to engineName(it) },
            selected = s.searchEngine,
            onSelect = viewModel::setSearchEngine,
            onDismiss = { dialog = null },
        )
        SettingsDialog.Theme -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries.map { it to themeName(it) },
            selected = s.theme,
            onSelect = viewModel::setTheme,
            onDismiss = { dialog = null },
        )
        SettingsDialog.Channel -> ChoiceDialog(
            title = stringResource(R.string.settings_ytdlp_channel),
            options = YtDlpChannel.entries.map { it to channelName(it) },
            selected = s.ytDlpChannel,
            onSelect = viewModel::setChannel,
            onDismiss = { dialog = null },
        )
        null -> Unit
    }

    confirm?.let { which ->
        val (title, action) = when (which) {
            Confirm.Cache -> R.string.settings_clear_cache to viewModel::clearCache
            Confirm.Cookies -> R.string.settings_clear_cookies to viewModel::clearCookies
            Confirm.History -> R.string.settings_clear_history to viewModel::clearHistory
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(title)) },
            text = { Text(stringResource(R.string.settings_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirm = null; action() }) { Text(stringResource(R.string.settings_confirm_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (showHowTo) HowToSheet(onDismiss = { showHowTo = false })
}

private enum class SettingsDialog { Concurrent, Quality, Filename, Engine, Theme, Channel }

private val QUALITY_KEYS: List<String> = listOf(
    AppSettings.QUALITY_ASK,
    FormatSelection.Best.key,
    FormatSelection.Resolution(1080).key,
    FormatSelection.Resolution(720).key,
    FormatSelection.Resolution(480).key,
    FormatSelection.Audio(AudioFormat.MP3).key,
    FormatSelection.Audio(AudioFormat.M4A).key,
)

@Composable
private fun qualityName(key: String): String = when (key) {
    AppSettings.QUALITY_ASK -> stringResource(R.string.settings_quality_ask)
    FormatSelection.Best.key -> stringResource(R.string.format_best)
    FormatSelection.Audio(AudioFormat.MP3).key -> stringResource(R.string.format_audio_mp3)
    FormatSelection.Audio(AudioFormat.M4A).key -> stringResource(R.string.format_audio_m4a)
    else -> (FormatSelection.fromKey(key) as? FormatSelection.Resolution)
        ?.let { stringResource(R.string.format_resolution, it.height) }
        ?: stringResource(R.string.format_best)
}

@Composable
private fun filenameName(style: FilenameStyle): String = stringResource(
    when (style) {
        FilenameStyle.TITLE_ID -> R.string.filename_title_id
        FilenameStyle.TITLE -> R.string.filename_title
        FilenameStyle.UPLOADER_TITLE -> R.string.filename_uploader_title
        FilenameStyle.DATE_TITLE -> R.string.filename_date_title
    },
)

@Composable
private fun engineName(engine: SearchEngine): String = stringResource(
    when (engine) {
        SearchEngine.GOOGLE -> R.string.engine_google
        SearchEngine.DUCKDUCKGO -> R.string.engine_duckduckgo
        SearchEngine.BING -> R.string.engine_bing
    },
)

@Composable
private fun themeName(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.LIGHT -> R.string.theme_light
    },
)

@Composable
private fun channelName(channel: YtDlpChannel): String = stringResource(
    when (channel) {
        YtDlpChannel.STABLE -> R.string.channel_stable
        YtDlpChannel.NIGHTLY -> R.string.channel_nightly
    },
)

private fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
} catch (e: Exception) {
    ""
}

/** Per-app language lives in the system settings from Android 13; older versions only follow the system language. */
private fun openLanguageSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}")),
            )
            return
        } catch (e: Exception) {
            // Fall through to the message below.
        }
    }
    Toast.makeText(context, R.string.settings_language_unavailable, Toast.LENGTH_LONG).show()
}

@Composable
private fun Section(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun ValueRow(title: String, value: String?, onClick: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.RadioButton) {
                                onSelect(value)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
