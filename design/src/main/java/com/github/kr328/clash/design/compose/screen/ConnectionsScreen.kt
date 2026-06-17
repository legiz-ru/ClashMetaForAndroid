package com.github.kr328.clash.design.compose.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.ConnectionGroup
import com.github.kr328.clash.design.util.toBytesString

/**
 * Live connections grouped by app (1:1 with ConnectionsDesign, restyled to
 * MD3E). Active/Closed tabs, a filter field, a total-speed readout and toolbar
 * actions for pause/resume, closed-connection caching and kill-all. Tapping a
 * group opens its per-connection list. Snapshot polling, grouping, filtering
 * and caching live in ConnectionsActivity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    activeGroups: List<ConnectionGroup>,
    closedGroups: List<ConnectionGroup>,
    speed: String,
    paused: Boolean,
    caching: Boolean,
    onBack: () -> Unit,
    onTogglePause: () -> Unit,
    onToggleCache: () -> Unit,
    onKillAll: () -> Unit,
    onFilterChange: (String) -> Unit,
    onOpenGroup: (ConnectionGroup, Boolean) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showKillConfirm by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = speed,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_arrow_back),
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onTogglePause) {
                            Icon(
                                painter = painterResource(
                                    if (paused) R.drawable.ic_baseline_play_arrow
                                    else R.drawable.ic_baseline_pause
                                ),
                                contentDescription = stringResource(
                                    if (paused) R.string.connections_resume else R.string.connections_pause
                                ),
                            )
                        }
                        IconButton(onClick = onToggleCache) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_restore),
                                contentDescription = null,
                                tint = if (caching) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { showKillConfirm = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_clear_all),
                                contentDescription = null,
                            )
                        }
                    },
                )
            },
        ) { inner ->
            Column(modifier = Modifier.padding(inner).fillMaxSize()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; onFilterChange(it) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_search),
                            contentDescription = null,
                        )
                    },
                    placeholder = { Text(stringResource(R.string.search)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.connections_active)) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.connections_closed)) },
                    )
                }

                val groups = if (selectedTab == 1) closedGroups else activeGroups
                if (groups.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(items = groups, key = { it.packageName }) { group ->
                            ConnectionGroupRow(
                                group = group,
                                onClick = { onOpenGroup(group, selectedTab == 1) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showKillConfirm) {
        AlertDialog(
            onDismissRequest = { showKillConfirm = false },
            title = { Text(stringResource(R.string.connections_kill_all_confirm_title)) },
            text = { Text(stringResource(R.string.connections_kill_all_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = { showKillConfirm = false; onKillAll() }) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showKillConfirm = false }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionGroupRow(group: ConnectionGroup, onClick: () -> Unit) {
    val painter = remember(group.icon) {
        group.icon?.let { BitmapPainter(it.toBitmap(96, 96).asImageBitmap()) }
    }
    ListItem(
        headlineContent = { Text(group.appName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text("↑ ${group.uploadSpeed.toBytesString()}/s  ↓ ${group.downloadSpeed.toBytesString()}/s")
        },
        leadingContent = {
            if (painter != null) {
                Image(painter = painter, contentDescription = null, modifier = Modifier.size(40.dp))
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Color.Unspecified,
                )
            }
        },
        trailingContent = { Text(group.connections.size.toString()) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
