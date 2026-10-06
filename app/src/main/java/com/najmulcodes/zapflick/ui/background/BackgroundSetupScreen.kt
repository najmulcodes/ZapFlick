package com.najmulcodes.zapflick.ui.background

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.domain.library.OemFamily
import com.najmulcodes.zapflick.domain.library.OemGuide
import com.najmulcodes.zapflick.ui.util.BatteryOptimization

/**
 * Keeps downloads alive with the screen off. Phone makers add their own switches on top of
 * Android's, and their menus differ between models, so the steps are plain text rather than
 * shortcuts into those menus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var restricted by remember { mutableStateOf(BatteryOptimization.isRestricted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { restricted = BatteryOptimization.isRestricted(context) }
    val family = remember { OemGuide.familyOf(Build.MANUFACTURER) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.background_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.background_intro), style = MaterialTheme.typography.bodyLarge)

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.background_step_battery_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            if (restricted) R.string.background_battery_restricted else R.string.background_battery_ok,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (restricted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    if (restricted) {
                        Button(onClick = { BatteryOptimization.requestExemption(context) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.background_allow))
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.background_step_settings_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.background_step_settings_body), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { openAppSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.background_open_app_settings))
                    }
                }
            }

            // The detected phone first, then the rest.
            val order = listOf(family) + OemFamily.entries.filter { it != family && it != OemFamily.OTHER }
            order.filter { it != OemFamily.OTHER }.forEach { oem ->
                OemSteps(oem = oem, detected = oem == family)
            }
            Text(stringResource(R.string.background_other_phones), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OemSteps(oem: OemFamily, detected: Boolean) {
    val (title, body) = when (oem) {
        OemFamily.VIVO -> R.string.oem_vivo_title to R.string.oem_vivo_steps
        OemFamily.TECNO_INFINIX -> R.string.oem_tecno_title to R.string.oem_tecno_steps
        OemFamily.XIAOMI -> R.string.oem_xiaomi_title to R.string.oem_xiaomi_steps
        OemFamily.SAMSUNG -> R.string.oem_samsung_title to R.string.oem_samsung_steps
        OemFamily.OTHER -> return
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (detected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (detected) stringResource(R.string.oem_detected, stringResource(title)) else stringResource(title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The app's own page in system settings, where "Battery" and "Allow background activity" live. */
private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (e2: ActivityNotFoundException) {
            // No settings app to open; nothing more can be done from here.
        }
    }
}
