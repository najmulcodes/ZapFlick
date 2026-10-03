package com.najmulcodes.zapflick.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.najmulcodes.zapflick.ui.util.BatteryOptimization

/** Shown until background activity is allowed (or the person says "not now"); re-checked on every resume. */
@Composable
fun BatteryOptimizationCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var restricted by remember { mutableStateOf(BatteryOptimization.isRestricted(context)) }
    var dismissed by remember { mutableStateOf(BatteryOptimization.isDismissed(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        restricted = BatteryOptimization.isRestricted(context)
    }
    if (!restricted || dismissed) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.battery_card_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.battery_card_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { BatteryOptimization.requestExemption(context) }) {
                    Text(stringResource(R.string.battery_card_allow))
                }
                TextButton(
                    onClick = {
                        BatteryOptimization.dismiss(context)
                        dismissed = true
                    },
                ) {
                    Text(stringResource(R.string.battery_card_dismiss))
                }
            }
        }
    }
}
