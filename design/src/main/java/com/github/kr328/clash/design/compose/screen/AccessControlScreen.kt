package com.github.kr328.clash.design.compose.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.github.kr328.clash.design.compose.component.SingleChoiceDialog
import com.github.kr328.clash.design.model.AppInfo
import com.github.kr328.clash.design.model.AppInfoSort

/**
 * VPN access-control app picker (1:1 with AccessControlDesign, restyled to
 * MD3E). A checkable app list with an inline search, plus an overflow menu for
 * bulk selection (all/none/invert), clipboard import/export, sort order,
 * reverse and system-app visibility. Selection lives in the activity and is
 * persisted on exit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessControlScreen(
    apps: List<AppInfo>,
    selected: Set<String>,
    sort: AppInfoSort,
    reverse: Boolean,
    systemApps: Boolean,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onSelectInvert: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onSetSort: (AppInfoSort) -> Unit,
    onSetReverse: (Boolean) -> Unit,
    onSetSystemApps: (Boolean) -> Unit,
) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showSort by remember { mutableStateOf(false) }

    val visibleApps = remember(apps, searching, query) {
        if (!searching || query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                if (searching) {
                    SearchTopBar(
                        query = query,
                        onQueryChange = { query = it },
                        onClose = { searching = false; query = "" },
                    )
                } else {
                    AccessControlTopBar(
                        sort = sort,
                        reverse = reverse,
                        systemApps = systemApps,
                        onBack = onBack,
                        onStartSearch = { searching = true },
                        onSelectAll = onSelectAll,
                        onSelectNone = onSelectNone,
                        onSelectInvert = onSelectInvert,
                        onImport = onImport,
                        onExport = onExport,
                        onOpenSort = { showSort = true },
                        onSetReverse = onSetReverse,
                        onSetSystemApps = onSetSystemApps,
                    )
                }
            },
        ) { inner ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
            ) {
                items(items = visibleApps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        checked = app.packageName in selected,
                        onToggle = { onToggle(app.packageName) },
                    )
                }
            }
        }
    }

    if (showSort) {
        SingleChoiceDialog(
            title = stringResource(R.string.sort),
            options = listOf(
                AppInfoSort.Label to stringResource(R.string.name),
                AppInfoSort.PackageName to stringResource(R.string.package_name),
                AppInfoSort.InstallTime to stringResource(R.string.install_time),
                AppInfoSort.UpdateTime to stringResource(R.string.update_time),
            ),
            selected = sort,
            onSelect = onSetSort,
            onDismiss = { showSort = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccessControlTopBar(
    sort: AppInfoSort,
    reverse: Boolean,
    systemApps: Boolean,
    onBack: () -> Unit,
    onStartSearch: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onSelectInvert: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onOpenSort: () -> Unit,
    onSetReverse: (Boolean) -> Unit,
    onSetSystemApps: (Boolean) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(R.string.access_control_packages)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_arrow_back),
                    contentDescription = null,
                )
            }
        },
        actions = {
            IconButton(onClick = onStartSearch) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_search),
                    contentDescription = stringResource(R.string.search),
                )
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_more_vert),
                    contentDescription = null,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.select_all)) },
                    onClick = { menuOpen = false; onSelectAll() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.select_none)) },
                    onClick = { menuOpen = false; onSelectNone() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.select_invert)) },
                    onClick = { menuOpen = false; onSelectInvert() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.import_from_clipboard)) },
                    onClick = { menuOpen = false; onImport() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.export_to_clipboard)) },
                    onClick = { menuOpen = false; onExport() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort)) },
                    onClick = { menuOpen = false; onOpenSort() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reverse)) },
                    trailingIcon = { Checkbox(checked = reverse, onCheckedChange = null) },
                    onClick = { menuOpen = false; onSetReverse(!reverse) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.system_apps)) },
                    trailingIcon = { Checkbox(checked = systemApps, onCheckedChange = null) },
                    onClick = { menuOpen = false; onSetSystemApps(!systemApps) },
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_arrow_back),
                    contentDescription = null,
                )
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRow(app: AppInfo, checked: Boolean, onToggle: () -> Unit) {
    val painter = remember(app) { BitmapPainter(app.icon.toBitmap(96, 96).asImageBitmap()) }
    ListItem(
        headlineContent = {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Image(painter = painter, contentDescription = null, modifier = Modifier.size(40.dp))
        },
        trailingContent = {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    )
}
