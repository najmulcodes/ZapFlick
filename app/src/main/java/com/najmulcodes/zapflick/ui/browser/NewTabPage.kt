package com.najmulcodes.zapflick.ui.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.domain.browser.FavoriteRules
import com.najmulcodes.zapflick.domain.browser.FavoriteSite
import com.najmulcodes.zapflick.domain.browser.HistoryEntry
import com.najmulcodes.zapflick.ui.theme.BrandGradient

private const val GRID_COLUMNS = 4

/** The page shown in an empty tab: search field, favorite sites, recent sites and the help button. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewTabPage(
    favorites: List<FavoriteSite>,
    recents: List<HistoryEntry>,
    onSubmit: (String) -> Unit,
    onOpen: (String) -> Unit,
    onAddFavorite: () -> Unit,
    onFavoriteLongPress: (FavoriteSite) -> Unit,
    onHowToDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val submit = {
        if (query.isNotBlank()) {
            onSubmit(query)
            query = ""
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(32.dp),
            placeholder = { Text(stringResource(R.string.tab_search_hint)) },
            trailingIcon = {
                IconButton(onClick = submit) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.tab_search_go))
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
        )

        val tiles: List<FavoriteSite?> = favorites + null
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            tiles.chunked(GRID_COLUMNS).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { site ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            if (site == null) {
                                AddTile(onClick = onAddFavorite)
                            } else {
                                SiteTile(
                                    title = site.title,
                                    onClick = { onOpen(site.url) },
                                    onLongClick = { onFavoriteLongPress(site) },
                                )
                            }
                        }
                    }
                    repeat(GRID_COLUMNS - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }

        if (recents.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.tab_recent_sites),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recents, key = { it.url }) { entry ->
                        RecentChip(title = entry.title, onClick = { onOpen(entry.url) })
                    }
                }
            }
        }

        FilledTonalButton(onClick = onHowToDownload, shape = CircleShape) {
            Text(stringResource(R.string.how_to_download))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteTile(title: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Monogram(title = title, size = 56)
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 72.dp),
        )
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    val label = stringResource(R.string.tab_add_favorite)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickableSimple(onClick)
            .padding(4.dp)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
        Spacer(modifier = Modifier.height(18.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableSimple(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)

@Composable
private fun RecentChip(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickableSimple(onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Monogram(title = title, size = 24)
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp),
        )
    }
}

/** A site's tile: its first letter on the brand gradient. The site's own logo is never used. */
@Composable
fun Monogram(title: String, size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(BrandGradient),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = FavoriteRules.monogram(title),
            color = Color.White,
            style = if (size >= 40) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
        )
    }
}
