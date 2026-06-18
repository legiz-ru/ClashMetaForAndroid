package com.github.kr328.clash.design.compose.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.design.R
import java.util.Locale

private val DelayGreen = Color(0xFF4CAF50)
private val DelayOrange = Color(0xFFFF9800)
private val DelayRed = Color(0xFFF44336)

enum class ProxySheetSort { Default, Name, Delay }

private fun delayColor(delay: Int): Color = when {
    delay <= 0 -> Color.Transparent
    delay < 200 -> DelayGreen
    delay < 500 -> DelayOrange
    else -> DelayRed
}

private fun resolveDelay(
    groupMap: Map<String, ProxyGroup>,
    groupName: String,
    now: String,
    visited: MutableSet<String> = mutableSetOf(),
): Int {
    if (!visited.add(groupName)) return 0
    val group = groupMap[groupName] ?: return 0
    val selected = group.proxies.find { it.name == now } ?: return 0
    if (!selected.isGroup) return selected.delay
    val nested = groupMap[selected.name] ?: return selected.delay
    if (nested.type == "LoadBalance") {
        return nested.proxies.filter { it.delay in 1..65534 }.minOfOrNull { it.delay } ?: 0
    }
    return resolveDelay(groupMap, selected.name, nested.now, visited)
}

fun selectedProxyName(groupMap: Map<String, ProxyGroup>, groupName: String, now: String): String {
    val group = groupMap[groupName] ?: return now
    val selected = group.proxies.find { it.name == now } ?: return now
    return selected.title.ifEmpty { selected.name }
}

internal fun rowDelay(groupMap: Map<String, ProxyGroup>, proxy: Proxy): Int {
    if (!proxy.isGroup) return proxy.delay
    val nested = groupMap[proxy.name] ?: return proxy.delay
    return if (proxy.type == "LoadBalance") {
        nested.proxies.filter { it.delay in 1..65534 }.minOfOrNull { it.delay } ?: 0
    } else {
        resolveDelay(groupMap, proxy.name, nested.now)
    }
}

private fun sortProxies(proxies: List<Proxy>, sort: ProxySheetSort): List<Proxy> = when (sort) {
    ProxySheetSort.Default -> proxies
    ProxySheetSort.Name -> proxies.sortedBy { it.title.ifEmpty { it.name }.lowercase(Locale.getDefault()) }
    ProxySheetSort.Delay -> proxies.sortedBy { if (it.delay > 0) it.delay else Int.MAX_VALUE }
}

/** Accordion-style card for one proxy group on the Home screen. */
@Composable
fun ProxyGroupCard(
    name: String,
    group: ProxyGroup,
    groupMap: Map<String, ProxyGroup>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (group.icon.isNotEmpty()) {
                RemoteIcon(
                    url = group.icon,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(28.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_vpn_lock),
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(28.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = selectedProxyName(groupMap, name, group.now),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_mdi_chevron_right),
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Inline proxy list for simple-mode profiles (first group's nodes, no group card). */
@Composable
fun SimpleModeProxyList(
    group: ProxyGroup,
    groupMap: Map<String, ProxyGroup>,
    useDots: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelector = group.type == "Selector"
    val isSmart = group.type == "Smart"
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        for (proxy in group.proxies) {
            ProxyRow(
                proxy = proxy,
                selected = proxy.name == group.now,
                delay = rowDelay(groupMap, proxy),
                useDots = useDots,
                enabled = isSelector,
                parentIsSmart = isSmart,
                groupMap = groupMap,
                onClick = { if (isSelector) onSelect(proxy.name) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySelectionSheet(
    groupName: String,
    group: ProxyGroup,
    groupMap: Map<String, ProxyGroup>,
    useDots: Boolean,
    isTv: Boolean,
    onSelect: (String) -> Unit,
    onUrlTest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isSelector = group.type == "Selector"
    val isSmart = group.type == "Smart"
    var sort by remember { mutableStateOf(ProxySheetSort.Default) }
    var showAutoDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollbarColor = MaterialTheme.colorScheme.onSurfaceVariant
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isTv) Modifier.fillMaxSize() else Modifier),
        ) {
            // Top bar: sort | title | url-test
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { showSortDialog = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_sort),
                        contentDescription = stringResource(R.string.sort),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = groupName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                IconButton(onClick = onUrlTest) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_speed),
                        contentDescription = stringResource(R.string.delay),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val sorted = remember(group, sort) { sortProxies(group.proxies, sort) }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isTv) Modifier.weight(1f) else Modifier.heightIn(max = 480.dp))
                    .verticalScrollbar(listState, scrollbarColor)
                    .padding(horizontal = 12.dp),
            ) {
                items(items = sorted) { proxy ->
                    ProxyRow(
                        proxy = proxy,
                        selected = proxy.name == group.now,
                        delay = rowDelay(groupMap, proxy),
                        useDots = useDots,
                        enabled = true,
                        parentIsSmart = isSmart,
                        groupMap = groupMap,
                        onClick = {
                            if (isSelector) {
                                onSelect(proxy.name)
                                onDismiss()
                            } else {
                                showAutoDialog = true
                            }
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
        }
    }

    if (isTv) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface,
            ) {
                content()
            }
        }
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
            content()
        }
    }

    if (showAutoDialog) {
        AlertDialog(
            onDismissRequest = { showAutoDialog = false },
            confirmButton = {
                TextButton(onClick = { showAutoDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            text = { Text(stringResource(R.string.proxy_group_auto_no_select)) },
        )
    }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text(stringResource(R.string.sort)) },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            text = {
                Column {
                    SortOption(R.string.default_, ProxySheetSort.Default, sort) {
                        sort = it; showSortDialog = false
                    }
                    SortOption(R.string.name, ProxySheetSort.Name, sort) {
                        sort = it; showSortDialog = false
                    }
                    SortOption(R.string.delay, ProxySheetSort.Delay, sort) {
                        sort = it; showSortDialog = false
                    }
                }
            },
        )
    }
}

@Composable
private fun SortOption(
    labelRes: Int,
    value: ProxySheetSort,
    current: ProxySheetSort,
    onPick: (ProxySheetSort) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(value) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = current == value, onClick = { onPick(value) })
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
internal fun ProxyRow(
    proxy: Proxy,
    selected: Boolean,
    delay: Int,
    useDots: Boolean,
    enabled: Boolean,
    parentIsSmart: Boolean,
    groupMap: Map<String, ProxyGroup>,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val name = proxy.title.ifEmpty { proxy.name }

    val nestedSmart = if (!parentIsSmart && proxy.type == "Smart") groupMap[proxy.name] else null
    val hasSmartData = nestedSmart?.proxies?.any { it.rank.isNotEmpty() && it.weight > 0.0 } == true

    val weightMessage: String? = remember(proxy, parentIsSmart, nestedSmart, hasSmartData) {
        when {
            parentIsSmart -> {
                val weight = (proxy.weight * 100).toInt()
                when (proxy.rank) {
                    "MostUsed" -> context.getString(R.string.proxies_smart_most_used_tip, weight)
                    "OccasionalUsed" -> context.getString(R.string.proxies_smart_occasional_used_tip, weight)
                    "RarelyUsed" -> context.getString(R.string.proxies_smart_rarely_used_tip, weight)
                    else -> context.getString(R.string.proxies_smart_no_data)
                }
            }
            nestedSmart != null -> {
                if (!hasSmartData) {
                    context.getString(R.string.proxies_smart_no_data)
                } else {
                    nestedSmart.proxies.filter { it.rank.isNotEmpty() }.joinToString("\n") { p ->
                        val label = when (p.rank) {
                            "MostUsed" -> context.getString(R.string.proxies_smart_most_used)
                            "OccasionalUsed" -> context.getString(R.string.proxies_smart_occasional_used)
                            "RarelyUsed" -> context.getString(R.string.proxies_smart_rarely_used)
                            else -> p.rank
                        }
                        "${p.title.ifEmpty { p.name }}: $label (${(p.weight * 100).toInt()})"
                    }.ifEmpty { context.getString(R.string.proxies_smart_no_data) }
                }
            }
            else -> null
        }
    }
    var showWeight by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) scheme.secondaryContainer else Color.Transparent,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (selected) BorderStroke(1.dp, scheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = proxy.subtitle.ifEmpty { proxy.type },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val tint = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant
            if (parentIsSmart) {
                val iconRes = when (proxy.rank) {
                    "MostUsed" -> R.drawable.ic_mdi_shield
                    "OccasionalUsed" -> R.drawable.ic_mdi_shield_half_full
                    "RarelyUsed" -> R.drawable.ic_mdi_shield_outline
                    else -> R.drawable.ic_mdi_timelapse
                }
                ShieldIcon(iconRes, tint) { showWeight = true }
            } else if (nestedSmart != null) {
                val iconRes = if (hasSmartData) R.drawable.ic_mdi_shield_check_outline
                else R.drawable.ic_mdi_timelapse
                ShieldIcon(iconRes, tint) { showWeight = true }
            }

            DelayBadge(delay = delay, useDots = useDots)
        }
    }

    if (showWeight && weightMessage != null) {
        AlertDialog(
            onDismissRequest = { showWeight = false },
            confirmButton = {
                TextButton(onClick = { showWeight = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            text = { Text(weightMessage) },
        )
    }
}

@Composable
private fun ShieldIcon(iconRes: Int, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun DelayBadge(delay: Int, useDots: Boolean) {
    if (delay <= 0) return
    if (useDots) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(delayColor(delay)),
        )
    } else {
        val isTimeout = delay == 65535
        Text(
            text = if (isTimeout) stringResource(R.string.timeout)
            else stringResource(R.string.format_delay_ms, delay),
            style = MaterialTheme.typography.labelMedium,
            color = if (isTimeout) DelayRed else delayColor(delay),
        )
    }
}
