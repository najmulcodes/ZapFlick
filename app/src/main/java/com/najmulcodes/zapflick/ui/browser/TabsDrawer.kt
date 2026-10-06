package com.najmulcodes.zapflick.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.najmulcodes.zapflick.R
import com.najmulcodes.zapflick.domain.browser.FavoriteSite
import com.najmulcodes.zapflick.domain.browser.TabInfo
import com.najmulcodes.zapflick.domain.browser.TabList
import com.najmulcodes.zapflick.domain.browser.hostOf

/** The tab list that slides in from the left over the page. */
@Composable
fun TabsDrawer(
    visible: Boolean,
    tabs: TabList,
    favorites: List<FavoriteSite>,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNewTab: () -> Unit,
    onCloseAll: () -> Unit,
    onFavorite: (FavoriteSite) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onForward: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
        ) {
            Surface(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp,
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    DrawerHeader(onNewTab = onNewTab, onCloseAll = onCloseAll)
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(tabs.tabs, key = { it.id }) { tab ->
                            TabRow(
                                tab = tab,
                                active = tab.id == tabs.activeId,
                                onClick = { onSelect(tab.id) },
                                onClose = { onClose(tab.id) },
                            )
                        }
                    }
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.tabs_favorite_sites),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(favorites, key = { it.id }) { site ->
                            Box(
                                modifier = Modifier
                                    .clickable { onFavorite(site) }
                                    .padding(4.dp),
                            ) { Monogram(title = site.title, size = 40, url = site.url) }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    NavigationRow(
                        canGoBack = canGoBack,
                        canGoForward = canGoForward,
                        onBack = onBack,
                        onHome = onHome,
                        onForward = onForward,
                        onNewTab = onNewTab,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader(onNewTab: () -> Unit, onCloseAll: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.tabs_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.tabs_menu))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tabs_new)) },
                    onClick = {
                        menuOpen = false
                        onNewTab()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tabs_close_all)) },
                    onClick = {
                        menuOpen = false
                        onCloseAll()
                    },
                )
            }
        }
    }
}

@Composable
private fun TabRow(tab: TabInfo, active: Boolean, onClick: () -> Unit, onClose: () -> Unit) {
    val title = tab.title.ifBlank { hostOf(tab.url) ?: stringResource(R.string.tabs_new_tab_title) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (active) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Monogram(title = title, size = 32)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.tabs_close_tab, title))
        }
    }
}

@Composable
private fun NavigationRow(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onForward: () -> Unit,
    onNewTab: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = onBack, enabled = canGoBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.browser_back))
        }
        IconButton(onClick = onHome) {
            Icon(Icons.Outlined.Home, contentDescription = stringResource(R.string.browser_home))
        }
        IconButton(onClick = onForward, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = stringResource(R.string.browser_forward))
        }
        IconButton(onClick = onNewTab) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.tabs_new))
        }
    }
}
