package com.najmulcodes.zapflick.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.domain.security.PinPolicy

/**
 * The dark PIN screen: four dots and a numeric keypad. Used to set a PIN (enter, then confirm)
 * and to unlock the private folder.
 */
@Composable
fun PinScreen(
    state: PinUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val title = when (state.mode) {
        PinUiState.Mode.SetConfirm -> R.string.pin_confirm_title
        PinUiState.Mode.Unlock -> R.string.pin_enter_title
        else -> R.string.pin_set_title
    }
    val locked = state.lockedSeconds > 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(top = 48.dp),
        ) {
            Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
            Dots(filled = state.entry.digits.length)
            Text(
                text = when {
                    locked -> stringResource(R.string.pin_locked, state.lockedSeconds)
                    state.message == PinUiState.Message.Mismatch -> stringResource(R.string.pin_mismatch)
                    state.message == PinUiState.Message.SaveFailed -> stringResource(R.string.pin_save_failed)
                    state.message is PinUiState.Message.Wrong -> {
                        val left = (state.message as PinUiState.Message.Wrong).attemptsLeft
                        if (left > 0) stringResource(R.string.pin_wrong_left, left) else stringResource(R.string.pin_wrong)
                    }
                    state.mode == PinUiState.Mode.SetEnter -> stringResource(R.string.pin_set_hint)
                    state.mode == PinUiState.Mode.SetConfirm -> stringResource(R.string.pin_confirm_hint)
                    else -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.message != null || locked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Keypad(enabled = !locked, onDigit = onDigit, onBackspace = onBackspace)
            if (onBiometric != null && state.mode == PinUiState.Mode.Unlock) {
                TextButton(onClick = onBiometric, enabled = !locked) {
                    Text(stringResource(R.string.pin_use_biometric))
                }
            }
            Text(
                text = stringResource(R.string.pin_not_encryption),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Dots(filled: Int) {
    val label = stringResource(R.string.pin_digits_entered, filled, PinPolicy.PIN_LENGTH)
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        repeat(PinPolicy.PIN_LENGTH) { index ->
            val on = index < filled
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .then(
                        if (on) Modifier.background(MaterialTheme.colorScheme.primary)
                        else Modifier.border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                    ),
            )
        }
    }
}

@Composable
private fun Keypad(enabled: Boolean, onDigit: (Char) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf("123", "456", "789")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { digit -> Key(label = digit.toString(), enabled = enabled) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(modifier = Modifier.size(72.dp))
            Key(label = "0", enabled = enabled) { onDigit('0') }
            val backspaceLabel = stringResource(R.string.pin_backspace)
            Key(label = stringResource(R.string.pin_backspace_glyph), enabled = true, description = backspaceLabel, onClick = onBackspace)
        }
    }
}

@Composable
private fun Key(label: String, enabled: Boolean, description: String? = null, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { if (description != null) contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
