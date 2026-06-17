package com.github.kr328.clash.design.compose.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.ProxyRow
import com.github.kr328.clash.design.compose.component.SingleChoiceDialog
import com.github.kr328.clash.design.compose.component.rowDelay
import kotlinx.coroutines.launch

/**
 * Full-screen proxy group browser (1:1 with ProxyDesign, restyled to MD3E).
 * Groups are paged via tabs; each page lists its proxies using the shared
 * [ProxyRow]. The toolbar exposes the per-group URL test and an overflow menu
 * for sort order, routing mode and the "hide non-selectable groups" filter.
 * Clash I/O stays in ProxyActivity; this screen only renders and emits intents.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyScreen(
    groupNames: List<String>,
    groupMap: Map<String, ProxyGroup>,
    useDots: Boolean,
    mode: TunnelState.Mode?,
    sort: ProxySort,
    excludeNotSelectable: Boolean,
    testingGroups: Set<String>,
    initialGroup: String,
    onBack: () -> Unit,
    onSelect: (group: String, proxy: String) -> Unit,
    onUrlTest: (group: String) -> Unit,
    onSetSort: (ProxySort) -> Unit,
    onSetMode: (TunnelState.Mode?) -> Unit,
    onToggleExclude: (Boolean) -> Unit,
    onGroupChanged: (String) -> Unit,
) {
    val initialPage = groupNames.indexOf(initialGroup).coerceAtLeast(0)
    var currentGroup by remember(groupNames) {
        mutableStateOf(groupNames.getOrElse(initialPage) { "" })
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                ProxyTopBar(
                    currentGroup = currentGroup,
                    hasGroups = groupNames.isNotEmpty(),
                    testing = currentGroup in testingGroups,
                    mode = mode,
                    sort = sort,
                    excludeNotSelectable = excludeNotSelectable,
                    onBack = onBack,
                    onUrlTest = onUrlTest,
                    onSetSort = onSetSort,
                    onSetMode = onSetMode,
                    onToggleExclude = onToggleExclude,
                )
            },
        ) { inner ->
            if (groupNames.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.proxy_empty_tips),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Box(modifier = Modifier.padding(inner)) {
                    ProxyPager(
                        groupNames = groupNames,
                        groupMap = groupMap,
                        useDots = useDots,
                        initialPage = initialPage,
                        onSelect = onSelect,
                        onVisibleGroupChanged = {
                            currentGroup = it
                            onGroupChanged(it)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProxyTopBar(
    currentGroup: String,
    hasGroups: Boolean,
    testing: Boolean,
    mode: TunnelState.Mode?,
    sort: ProxySort,
    excludeNotSelectable: Boolean,
    onBack: () -> Unit,
    onUrlTest: (String) -> Unit,
    onSetSort: (ProxySort) -> Unit,
    onSetMode: (TunnelState.Mode?) -> Unit,
    onToggleExclude: (Boolean) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showMode by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(stringResource(R.string.proxy)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_arrow_back),
                    contentDescription = null,
                )
            }
        },
        actions = {
            if (hasGroups) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = { if (currentGroup.isNotEmpty()) onUrlTest(currentGroup) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_flash_on),
                            contentDescription = stringResource(R.string.delay_test),
                        )
                    }
                }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_more_vert),
                        contentDescription = null,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort)) },
                        onClick = { menuOpen = false; showSort = true },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.mode)) },
                        onClick = { menuOpen = false; showMode = true },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.not_selectable)) },
                        trailingIcon = {
                            Checkbox(checked = excludeNotSelectable, onCheckedChange = null)
                        },
                        onClick = {
                            menuOpen = false
                            onToggleExclude(!excludeNotSelectable)
                        },
                    )
                }
            }
        },
    )

    if (showSort) {
        SingleChoiceDialog(
            title = stringResource(R.string.sort),
            options = listOf(
                ProxySort.Default to stringResource(R.string.default_),
                ProxySort.Title to stringResource(R.string.name),
                ProxySort.Delay to stringResource(R.string.delay),
            ),
            selected = sort,
            onSelect = onSetSort,
            onDismiss = { showSort = false },
        )
    }
    if (showMode) {
        SingleChoiceDialog<TunnelState.Mode?>(
            title = stringResource(R.string.mode),
            options = listOf(
                null to stringResource(R.string.dont_modify),
                TunnelState.Mode.Direct to stringResource(R.string.direct_mode),
                TunnelState.Mode.Global to stringResource(R.string.global_mode),
                TunnelState.Mode.Rule to stringResource(R.string.rule_mode),
            ),
            selected = mode,
            onSelect = onSetMode,
            onDismiss = { showMode = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProxyPager(
    groupNames: List<String>,
    groupMap: Map<String, ProxyGroup>,
    useDots: Boolean,
    initialPage: Int,
    onSelect: (String, String) -> Unit,
    onVisibleGroupChanged: (String) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialPage) { groupNames.size }
    val scope = rememberCoroutineScope()
    var showAuto by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage, groupNames) {
        groupNames.getOrNull(pagerState.currentPage)?.let(onVisibleGroupChanged)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage.coerceIn(0, (groupNames.size - 1).coerceAtLeast(0)),
            edgePadding = 12.dp,
        ) {
            groupNames.forEachIndexed { index, name ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(name, maxLines = 1) },
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val name = groupNames[page]
            val group = groupMap[name]
            if (group == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            } else {
                val isSelector = group.type == "Selector"
                val isSmart = group.type == "Smart"
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                ) {
                    items(items = group.proxies, key = { it.name }) { proxy ->
                        Box(modifier = Modifier.animateItem()) {
                            ProxyRow(
                                proxy = proxy,
                                selected = proxy.name == group.now,
                                delay = rowDelay(groupMap, proxy),
                                useDots = useDots,
                                enabled = true,
                                parentIsSmart = isSmart,
                                groupMap = groupMap,
                                onClick = {
                                    if (isSelector) onSelect(name, proxy.name) else showAuto = true
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAuto) {
        AlertDialog(
            onDismissRequest = { showAuto = false },
            confirmButton = {
                TextButton(onClick = { showAuto = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            text = { Text(stringResource(R.string.proxy_group_auto_no_select)) },
        )
    }
}
