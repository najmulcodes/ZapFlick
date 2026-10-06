package com.najmulcodes.zapflick.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.ui.browser.Monogram

/** Three short steps, opened from the Tab page and from Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowToSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.how_to_title), style = MaterialTheme.typography.titleLarge)
            Step(1, stringResource(R.string.how_to_step1_title), stringResource(R.string.how_to_step1_body))
            Step(2, stringResource(R.string.how_to_step2_title), stringResource(R.string.how_to_step2_body))
            Step(3, stringResource(R.string.how_to_step3_title), stringResource(R.string.how_to_step3_body))
        }
    }
}

@Composable
private fun Step(number: Int, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Monogram(title = number.toString(), size = 36)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
