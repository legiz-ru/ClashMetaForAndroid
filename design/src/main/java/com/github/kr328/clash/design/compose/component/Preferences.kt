package com.github.kr328.clash.design.compose.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R

/**
 * Reusable MD3-Expressive preference building blocks, replacing the View-based
 * preference DSL (Switch / Clickable / SelectableList / Category). Leading icons
 * stay monochrome per the design brief.
 */

/**
 * Top bar (back + title) + a LazyColumn body. Using a lazy list (rather than a
 * plain verticalScroll Column) is what makes D-pad focus traversal/scrolling
 * work on TV. Screens emit their rows via `item { }` / `items { }`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferenceScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val scrollbarColor = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_arrow_back),
                                contentDescription = null,
                            )
                        }
                    },
                    actions = actions,
                )
            },
        ) { inner ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScrollbar(listState, scrollbarColor)
                    .padding(inner),
                contentPadding = PaddingValues(vertical = 8.dp),
                content = content,
            )
        }
    }
}

@Composable
fun SwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    // Self-updating state: seeded from [checked], re-seeded when the caller passes a new
    // value (hoisted/flow-driven screens), but toggled locally on tap so LazyColumn rows
    // reflect the change immediately without an external recompose trigger.
    var current by remember(checked) { mutableStateOf(checked) }
    val change = { value: Boolean -> current = value; onCheckedChange(value) }
    PreferenceContainer(
        modifier = modifier,
        icon = icon,
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = { change(!current) },
        trailing = {
            Switch(checked = current, onCheckedChange = change, enabled = enabled)
        },
    )
}

@Composable
fun PreferenceRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    PreferenceContainer(
        modifier = modifier,
        icon = icon,
        title = title,
        summary = summary,
        enabled = enabled,
        onClick = onClick,
        trailing = null,
    )
}

@Composable
private fun PreferenceContainer(
    modifier: Modifier,
    @DrawableRes icon: Int?,
    title: String,
    summary: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val alpha = if (enabled) 1f else 0.4f
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(
                enabled = enabled,
                interactionSource = source,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .background(
                if (focused) scheme.primary.copy(alpha = 0.14f) else Color.Transparent,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = scheme.onSurfaceVariant.copy(alpha = alpha),
                modifier = Modifier
                    .padding(end = 20.dp)
                    .size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(16.dp))
            trailing()
        }
    }
}

/** Single-choice dialog used for `selectableList` preferences (e.g. dark mode). */
@Composable
fun <T> SingleChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        text = {
            Column {
                for ((value, label) in options) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = value == selected,
                                onClick = {
                                    onSelect(value)
                                    onDismiss()
                                },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = {
                                onSelect(value)
                                onDismiss()
                            },
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
    )
}
