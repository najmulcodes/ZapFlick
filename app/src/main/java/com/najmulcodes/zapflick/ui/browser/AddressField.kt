package com.najmulcodes.zapflick.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.domain.browser.AddressDisplay

/**
 * The address bar of a real browser: at rest a pill showing the site with a lock; a tap turns it into
 * an editable field with the whole address selected, so typing replaces it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressField(
    address: String,
    onAddressChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }

    if (!editing) {
        val secure = address.startsWith("https://", ignoreCase = true)
        val label = AddressDisplay.compact(address).ifEmpty { stringResource(R.string.browser_address_hint) }
        val iconLabel = if (secure) stringResource(R.string.browser_secure) else stringResource(R.string.browser_not_secure)
        Surface(
            onClick = {
                editing = true
                onFocusChange(true)
            },
            modifier = modifier.height(44.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (address.isNotBlank()) {
                    Icon(
                        imageVector = if (secure) Icons.Outlined.Lock else Icons.Outlined.Warning,
                        contentDescription = iconLabel,
                        modifier = Modifier.size(16.dp),
                        tint = if (secure) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        return
    }

    val focusRequester = remember { FocusRequester() }
    var gainedFocus by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(TextFieldValue(address, TextRange(0, address.length))) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    OutlinedTextField(
        value = value,
        onValueChange = {
            value = it
            onAddressChange(it.text)
        },
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    gainedFocus = true
                } else if (gainedFocus) {
                    editing = false
                    onFocusChange(false)
                }
            },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        placeholder = { Text(stringResource(R.string.browser_address_hint)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
    )
}
